package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Regression fixtures for the failures measured in the first packet-driven forest session. */
final class WildlifeRegressionGameTests {
    private static void floor(GameTestHelper h) {
        for (int x = 16; x < 112; x++) for (int z = 16; z < 112; z++) {
            h.setBlock(x, 1, z, Blocks.STONE);
            h.setBlock(x, 2, z, Blocks.DIRT);
        }
    }
    private static CreatureEntity wild(GameTestHelper h, Species species, Vec3 relative) {
        var mob = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        mob.initializeLevel(1); mob.setPersistenceRequired(); mob.getRandom().setSeed(0L);
        mob.setPos(h.absoluteVec(relative)); mob.setYRot(0); mob.yBodyRot = 0;
        mob.wildlife().mind().restoreNeeds(0.1, 0.1, 0.1);
        mob.wildlife().mind().interruptSleep(1200);
        h.getLevel().addFreshEntity(mob);
        GameTestCleanup.onFinish(h, mob::discard);
        return mob;
    }
    private static void wall(GameTestHelper h, int z, net.minecraft.world.level.block.Block block) {
        for (int x = 56; x <= 72; x++) for (int y = 3; y <= 15; y++) h.setBlock(x, y, z, block);
    }
    static void packets(GameTestHelper h) {
        floor(h);
        var mob = wild(h, Species.PEGOMASTAX, new Vec3(64.5, 3, 64.5)); mob.setNoAi(true);
        wall(h, 66, Blocks.STONE);
        var world = h.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "wildlife-packet"), false);
        var player = new ServerPlayer(world.getServer(), world, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        // There is no client to negotiate mod payloads. Only outbound rendering/sync is suppressed;
        // incoming movement and client-tick-end packets use the unmodified server handlers.
        var listener = new ServerGamePacketListenerImpl(world.getServer(), connection, player, cookie) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                    io.netty.channel.ChannelFutureListener completion) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        Vec3 start = h.absoluteVec(new Vec3(64.5, 3, 68.5));
        player.absSnapTo(start.x, start.y, start.z);
        GameTestCleanup.onFinish(h, () -> { player.discard(); channel.finishAndReleaseAll(); });
        player.setOnGround(true); world.addNewPlayer(player); listener.markClientLoaded();
        listener.resetPosition();
        listener.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.position().add(0, 0, 0.2), true, false));
        h.assertTrue(player.position().distanceToSqr(new Vec3(player.xo, player.yo, player.zo)) == 0,
                "Fixture did not reproduce the movement packet overwriting previous position");
        h.assertTrue(player.getKnownMovement().lengthSqr() > 0.0004, "Movement packet did not retain its accepted delta");
        var walking = WildlifeSenses.detect(mob, player);
        h.assertFalse(walking.visible(), "Packet hearing fixture has a sight line through its wall");
        h.assertTrue(walking.strength() == 0.65, "Real packet movement was not heard through nearby cover");
        player.setShiftKeyDown(true);
        h.assertTrue(WildlifeSenses.detect(mob, player).strength() < 0.65, "Crouching retained walking earshot through cover");
        player.setShiftKeyDown(false);
        listener.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(h.absoluteVec(new Vec3(64.5, 3, 73.5)), true, false));
        h.assertTrue(WildlifeSenses.detect(mob, player).strength() < 0.65, "Walking was heard beyond its covered range");
        player.setSprinting(true);
        h.assertTrue(WildlifeSenses.detect(mob, player).strength() == 0.65, "Sprinting did not extend packet-driven hearing");
        listener.handleClientTickEnd(new ServerboundClientTickEndPacket());
        listener.handleClientTickEnd(new ServerboundClientTickEndPacket());
        h.assertTrue(player.getKnownMovement().lengthSqr() == 0, "A client tick without movement did not clear known movement");
        h.assertTrue(WildlifeSenses.detect(mob, player).strength() < 0.65, "A stopped player continued making movement noise");
        h.succeed();
    }
    static void cover(GameTestHelper h) {
        floor(h);
        var mob = wild(h, Species.CARNOTAURUS, new Vec3(64.993, 3, 57.5)); mob.setNoAi(true); mob.setOnGround(true);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(64.993, 3, 63.5))); h.getLevel().addFreshEntity(player);
        GameTestCleanup.onFinish(h, player::discard);
        for (int y = 3; y <= 15; y++) h.setBlock(64, y, 61, Blocks.SPRUCE_LOG);
        var center = h.getLevel().clip(new ClipContext(mob.getEyePosition(), player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        h.assertTrue(center.getType() == HitResult.Type.BLOCK, "Central ray did not clip the trunk's 7 mm corner");
        h.assertTrue(WildlifeSenses.detect(mob, player).visible(), "A trunk corner hid the exposed player from all sight lines");
        h.assertTrue(mob.hasLineOfSight(player), "Combat and sensing disagree about the exposed player");
        mob.wildlife().mind().resumeAs(BehaviorState.SLEEP); mob.setBehavior(BehaviorState.SLEEP);
        mob.wildlife().think();
        h.assertFalse(mob.behavior().sleeping(), "An exposed intruder within body reach did not wake the sleeper");
        wall(h, 60, Blocks.STONE);
        h.assertFalse(WildlifeSenses.detect(mob, player).visible(), "Alternate rays saw through a solid wall");
        h.assertFalse(mob.strike(player), "A strike began through a solid wall");
        wall(h, 60, Blocks.SPRUCE_LEAVES);
        h.assertFalse(WildlifeSenses.detect(mob, player).visible(), "Dense leaves stopped obstructing sight");
        h.succeed();
    }
    static void recovery(GameTestHelper h) {
        floor(h);
        var mob = wild(h, Species.PEGOMASTAX, new Vec3(64.5, 3, 64.5));
        Vec3 home = Vec3.atBottomCenterOf(mob.wildlife().home());
        mob.setOnGround(false);
        for (int i = 0; i < 8; i++) mob.wildlife().think();
        h.assertTrue(mob.wildlife().mind().recovery() == 0, "First airborne decisions abandoned ordinary roaming");
        mob.setOnGround(true);
        mob.wildlife().mind().abandonChase();
        mob.wildlife().think();
        h.assertTrue(mob.wildlife().mind().recovery() == 0 && mob.behavior() != BehaviorState.RETURN_HOME,
                "Arrival at the home block did not end recovery");
        // Repeat the refusal condition at an older age, as on a downhill hop in the real recording.
        mob.tickCount = 80; mob.setPos(home.add(0, 0.5, 12)); mob.setOnGround(false);
        mob.wildlife().mind().abandonChase();
        for (int i = 0; i < 8; i++) mob.wildlife().think();
        h.assertTrue(mob.wildlife().mind().recovery() == 120, "Later airborne home requests restarted recovery instead of letting it expire");
        mob.setPos(home.add(0, 0, 12)); mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        mob.wildlife().mind().abandonChase(); mob.wildlife().think();
        var noChunkLoads = NavigationGameTests.watchChunkLoads(h);
        boolean[] arrived = {false};
        h.onEachTick(() -> {
            if (mob.position().distanceToSqr(home) < 1 && mob.wildlife().mind().recovery() == 0
                    && mob.behavior() != BehaviorState.RETURN_HOME) arrived[0] = true;
        });
        h.runAfterDelay(240, () -> {
            h.assertTrue(arrived[0], "Actual navigation never completed return-home recovery: " + mob.position() + " " + mob.behavior());
            noChunkLoads.run();
            h.succeed();
        });
    }
    /** No goals compete with the requested real path; AI, navigation, steering and physics all tick normally. */
    private static final class Walker extends CreatureEntity {
        Walker(net.minecraft.server.level.ServerLevel world) { super(ModContent.CREATURES.get(Species.PEGOMASTAX).get(), world, Species.PEGOMASTAX); }
        @Override protected void registerGoals() {}
    }
    static void turning(GameTestHelper h) {
        floor(h);
        var mob = new Walker(h.getLevel()); mob.initializeLevel(1); mob.setPersistenceRequired();
        Vec3 start = h.absoluteVec(new Vec3(64.5, 3, 64.5)), goal = start.add(0, 0, -12);
        mob.setPos(start); mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        mob.setYRot(0); mob.yBodyRot = 0; mob.setAction(BehaviorAction.WALK);
        h.getLevel().addFreshEntity(mob); GameTestCleanup.onFinish(h, mob::discard);
        h.assertTrue(mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 0, mob.wanderModifier()), "Turn fixture has no real path");
        var noChunkLoads = NavigationGameTests.watchChunkLoads(h);
        int[] consecutive = {0}, longest = {0}; boolean[] pivoted = {false};
        h.onEachTick(() -> {
            if (mob.isPivoting()) {
                pivoted[0] = true; longest[0] = Math.max(longest[0], ++consecutive[0]);
                h.assertTrue(mob.action() == BehaviorAction.TURN, "A pivoting animal still advertised WALK");
            } else consecutive[0] = 0;
        });
        h.runAfterDelay(180, () -> {
            h.assertTrue(pivoted[0], "Reverse path did not exercise a pivot");
            h.assertTrue(longest[0] < 20, "A small animal spent a second turning on the spot: " + longest[0]);
            h.assertTrue(mob.position().distanceToSqr(goal) < 4, "Reverse path did not produce actual forward travel: " + mob.position());
            h.assertTrue(mob.action() == BehaviorAction.WALK, "TURN did not restore the requested gait after alignment");
            noChunkLoads.run();
            h.succeed();
        });
    }
    static void drinking(GameTestHelper h) {
        floor(h);
        // A nearer enclosed source is unusable; the single exposed source must be found and reached.
        h.setBlock(74, 2, 64, Blocks.WATER);
        for (int x = 72; x <= 76; x++) for (int z = 62; z <= 66; z++) for (int y = 3; y <= 9; y++)
            if (x == 72 || x == 76 || z == 62 || z == 66) h.setBlock(x, y, z, Blocks.STONE);
        h.setBlock(81, 2, 64, Blocks.WATER);
        var mob = wild(h, Species.PEGOMASTAX, new Vec3(64.5, 3, 64.5));
        mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        mob.wildlife().mind().restoreNeeds(0.1, 0.9, 0.1);
        var noChunkLoads = NavigationGameTests.watchChunkLoads(h);
        Vec3 start = mob.position(); boolean[] drank = {false};
        h.onEachTick(() -> {
            if (mob.behavior() == BehaviorState.DRINK) {
                drank[0] = true;
                h.assertTrue(mob.position().distanceToSqr(start) > 64, "Animal drank through the nearer enclosure");
            }
            h.assertTrue(mob.wildlife().mind().recovery() == 0, "Water search failures abandoned a chase that never existed");
        });
        h.runAfterDelay(400, () -> {
            h.assertTrue(drank[0] && mob.wildlife().mind().thirst() < 0.5,
                    "Actual wildlife never reached and used the exposed water: " + mob.position() + " " + mob.behavior()
                            + " thirst=" + mob.wildlife().mind().thirst());
            noChunkLoads.run();
            h.succeed();
        });
    }
    static void pursuit(GameTestHelper h) {
        floor(h);
        var mob = wild(h, Species.CARNOTAURUS, new Vec3(64.5, 3, 64.5)); mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024); player.setHealth(1024);
        player.setPos(h.absoluteVec(new Vec3(64.5, 3, 73.5))); h.getLevel().addFreshEntity(player);
        GameTestCleanup.onFinish(h, player::discard);
        Vec3 start = mob.position();
        h.runAfterDelay(260, () -> {
            h.assertTrue(mob.position().distanceToSqr(start) > 4, "A wide pursuer never moved toward its intruder");
            h.assertTrue(player.getHealth() < 1024, "Wide-body navigation stopped outside actual melee reach");
            h.succeed();
        });
    }
    private WildlifeRegressionGameTests() {}
}
