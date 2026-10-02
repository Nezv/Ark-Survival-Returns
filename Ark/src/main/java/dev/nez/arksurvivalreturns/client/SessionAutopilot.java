package dev.nez.arksurvivalreturns.client;

import java.util.HashMap;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.aquatic.AquaticCreatureEntity;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.recorder.RecorderPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Scripted player for a session recorded with nobody at the keyboard ({@code tools/session_run.py}).
 * Off unless the client was launched with {@code -Darksurvivalreturns.autopilot=approach|stand}, and idle
 * until the recording starts. {@code approach} walks up to the nearest wild creature, stands in front of
 * it, backs away and picks the next one; {@code stand} only stays in the world. Each step is marked in
 * the recording with a note that starts with {@code auto:}.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class SessionAutopilot {
    private static final String MODE = System.getProperty("arksurvivalreturns.autopilot", "");
    private static final int SETTLE_TICKS = 200, HOLD_TICKS = 300, RETREAT_TICKS = 160, APPROACH_TICKS = 700,
            WANDER_TICKS = 240, REVISIT_TICKS = 1200, RESPAWN_TICKS = 40;
    private static final double RANGE = 96, STOP_GAP = 5;

    private enum Step { WAIT, APPROACH, HOLD, RETREAT, WANDER }

    /** The movement the script asks for, read by the player where the keyboard would be. */
    private static final class Drive extends ClientInput {
        boolean forward, left, right, jump, sprint;

        void clear() { forward = left = right = jump = sprint = false; }

        @Override public void tick() {
            keyPresses = new Input(forward, false, left, right, jump, false, sprint);
            moveVector = new Vec2(left == right ? 0 : left ? 1 : -1, forward ? 1 : 0).normalized();
        }
    }

    private static final Drive DRIVE = new Drive();
    private static final HashMap<UUID, Integer> VISITED = new HashMap<>();
    private static Step step = Step.WAIT;
    private static int left = SETTLE_TICKS, clock, deadFor, blockedFor, sidestep, targetId = -1;
    private static boolean sideLeft;
    private static float heading;

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if (MODE.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) { reset(); return; }
        // Nobody is there to close a menu, and a pausing one stops the integrated server and the recording with it.
        if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (!SessionRecorderClient.recording()) {
            if (player.input == DRIVE) player.input = new KeyboardInput(mc.options);
            reset();
            return;
        }
        clock++;
        if (player.isDeadOrDying()) {
            if (++deadFor == RESPAWN_TICKS) { mark("respawn"); player.respawn(); }
            rest(SETTLE_TICKS);
            return;
        }
        deadFor = 0;
        if (player.input != DRIVE) player.input = DRIVE;
        DRIVE.clear();
        if (!MODE.equals("approach")) return;
        switch (step) {
            case WAIT -> { if (--left <= 0) choose(mc, player); }
            case APPROACH -> approach(mc, player);
            case HOLD -> hold(mc, player);
            case RETREAT, WANDER -> { walk(player, step == Step.RETREAT); if (--left <= 0) rest(step == Step.RETREAT ? 100 : 20); }
        }
    }

    private static void reset() {
        step = Step.WAIT;
        left = SETTLE_TICKS;
        clock = deadFor = blockedFor = sidestep = 0;
        targetId = -1;
        VISITED.clear();
    }

    private static void rest(int ticks) { step = Step.WAIT; left = ticks; targetId = -1; }

    /** The nearest wild creature the player can walk to and has not just visited; predators count as closer. */
    private static void choose(Minecraft mc, LocalPlayer player) {
        CreatureEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof CreatureEntity creature) || !creature.isAlive() || creature.isTamed()) continue;
            if (creature instanceof AquaticCreatureEntity || creature.isInWater()) continue;
            Integer seen = VISITED.get(creature.getUUID());
            if (seen != null && clock - seen < REVISIT_TICKS) continue;
            double flat = Math.hypot(creature.getX() - player.getX(), creature.getZ() - player.getZ());
            if (flat > RANGE || Math.abs(creature.getY() - player.getY()) > 12) continue;
            double score = flat * (creature.species().predator ? 0.6 : 1.0);
            if (score < bestScore) { bestScore = score; best = creature; }
        }
        if (best == null) {
            step = Step.WANDER;
            left = WANDER_TICKS;
            heading = player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * 140;
            mark("wander");
            return;
        }
        step = Step.APPROACH;
        left = APPROACH_TICKS;
        targetId = best.getId();
        mark("approach " + name(best));
    }

    private static void approach(Minecraft mc, LocalPlayer player) {
        if (!(mc.level.getEntity(targetId) instanceof CreatureEntity creature) || !creature.isAlive()) { mark("lost"); rest(60); return; }
        face(player, creature);
        if (WildlifeSenses.bodyDistance(player, creature) <= STOP_GAP) {
            VISITED.put(creature.getUUID(), clock);
            step = Step.HOLD;
            left = HOLD_TICKS;
            mark("hold " + name(creature));
            return;
        }
        if (--left <= 0) {
            VISITED.put(creature.getUUID(), clock);
            mark("unreached " + name(creature));
            rest(60);
            return;
        }
        walk(player, false);
    }

    private static void hold(Minecraft mc, LocalPlayer player) {
        var creature = mc.level.getEntity(targetId) instanceof CreatureEntity found && found.isAlive() ? found : null;
        if (creature != null) face(player, creature);
        turn(player);
        boolean hurt = player.getHealth() < 8;
        if (!hurt && --left > 0) return;
        // Straight away from the creature, or from where it last stood.
        heading = Mth.wrapDegrees(heading + 180);
        step = Step.RETREAT;
        left = RETREAT_TICKS;
        mark(hurt ? "retreat hurt" : "retreat");
    }

    private static void face(LocalPlayer player, Entity entity) {
        heading = (float) (Mth.atan2(-(entity.getX() - player.getX()), entity.getZ() - player.getZ()) * Mth.RAD_TO_DEG);
    }

    private static void turn(LocalPlayer player) {
        player.setYRot(Mth.approachDegrees(player.getYRot(), heading, 20));
        player.setXRot(Mth.approach(player.getXRot(), 0, 10));
    }

    private static void walk(LocalPlayer player, boolean sprint) {
        turn(player);
        DRIVE.forward = true;
        DRIVE.sprint = sprint;
        if (player.isInWater()) DRIVE.jump = true;
        // Blocked: jump the step; when that does not help, slide along the obstacle for a moment.
        if (player.horizontalCollision) {
            blockedFor++;
            if (player.onGround()) DRIVE.jump = true;
        } else if (blockedFor > 0) blockedFor--;
        if (blockedFor > 12) { blockedFor = 0; sidestep = 25; sideLeft = !sideLeft; }
        if (sidestep > 0) {
            sidestep--;
            if (sideLeft) DRIVE.left = true; else DRIVE.right = true;
        }
    }

    private static String name(CreatureEntity creature) {
        return creature.species().name().toLowerCase(java.util.Locale.ROOT) + " " + creature.getUUID().toString().substring(0, 8);
    }

    private static void mark(String note) {
        var connection = Minecraft.getInstance().getConnection();
        String text = "auto:" + note;
        if (connection != null && connection.hasChannel(RecorderPayloads.Mark.TYPE))
            ClientPacketDistributor.sendToServer(new RecorderPayloads.Mark(text.length() > 64 ? text.substring(0, 64) : text));
    }

    private SessionAutopilot() {}
}
