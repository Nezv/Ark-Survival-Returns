package dev.nez.arksurvivalreturns.client.accessory;

import java.util.Map;
import java.util.WeakHashMap;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryContent;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryNetwork;
import dev.nez.arksurvivalreturns.feature.accessory.Worn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client half of the accessories: renderer registration and the movement the local player owns (gliding,
 * the Quetzal air jump, diving with stones, squinting through snow goggles). The server re-checks and
 * forgives falls; see AccessoryEffects.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class AccessoryClient {
    /** Glide physics: sink rate and cruise speed, both steeper when looking down. */
    static final double SINK = 0.08, SINK_DIVE = 0.22, CRUISE = 0.42, CRUISE_DIVE = 0.38;
    private static final Map<Entity, float[]> SPREAD = new WeakHashMap<>();
    private static boolean gliding;
    private static boolean jumpHeld;

    @SubscribeEvent static void setup(FMLClientSetupEvent event) {
        if (Worn.CURIOS) event.enqueueWork(CuriosAccessoryRenderer::registerAll);
    }

    @SubscribeEvent static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isAlive()) return;
        var input = player.input.keyPresses;
        boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying();
        // Quetzal Mantle: a second press of jump in the air.
        if (input.jump() && !jumpHeld && airborne && !player.onClimbable() && Worn.has(player, Accessory.QUETZAL_MANTLE)) {
            Worn.State state = Worn.state(player);
            if (!state.airJumpUsed) {
                state.airJumpUsed = true;
                Vec3 v = player.getDeltaMovement();
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(v.x * 0.6 + look.x * 0.25, 0.55, v.z * 0.6 + look.z * 0.25);
                player.resetFallDistance();
                ClientPacketDistributor.sendToServer(new AccessoryNetwork.AirJump());
            }
        }
        if (player.onGround() || player.isInWater() || player.onClimbable()) Worn.state(player).airJumpUsed = false;
        jumpHeld = input.jump();
        // Membrane Glider: hold jump while falling.
        boolean glide = airborne && input.jump() && Worn.has(player, Accessory.MEMBRANE_GLIDER)
                && (gliding || player.getDeltaMovement().y < -0.25);
        gliding = glide;
        if (glide) {
            Vec3 v = player.getDeltaMovement();
            Vec3 look = player.getLookAngle();
            double dive = Math.max(0, -look.y);
            Vec3 heading = new Vec3(look.x, 0, look.z);
            heading = heading.lengthSqr() < 1e-4 ? Vec3.ZERO : heading.normalize();
            double cruise = CRUISE + CRUISE_DIVE * dive;
            double sink = SINK + SINK_DIVE * dive;
            Vec3 flat = new Vec3(v.x, 0, v.z).lerp(heading.scale(cruise), 0.1);
            double vy = v.y < -sink ? Mth.lerp(0.35, v.y, -sink) : v.y;
            player.setDeltaMovement(flat.x, vy, flat.z);
            player.resetFallDistance();
        }
        // Diver's Stones: sneak to sink like a stone.
        if (player.isInWater() && input.shift() && Worn.has(player, Accessory.DIVER_STONES)) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(-0.4, v.y - 0.05), v.z);
        }
    }

    /** Bone Snow Goggles: sneaking narrows the view like a spyglass. */
    @SubscribeEvent static void fov(ComputeFovModifierEvent event) {
        if (event.getPlayer().isCrouching() && Worn.has(event.getPlayer(), Accessory.BONE_SNOW_GOGGLES)) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.6f);
        }
    }

    /** Wing spread for the renderer, eased toward the synced glide state of any player. */
    static float spread(Entity entity) {
        boolean target = entity == Minecraft.getInstance().player ? gliding : entity.getData(AccessoryContent.GLIDING);
        float[] value = SPREAD.computeIfAbsent(entity, e -> new float[]{0, 0});
        float now = (float) (System.nanoTime() / 1.0e9 % 100000);
        float dt = value[1] == 0 ? 0.05f : Math.max(0, Math.min(0.1f, now - value[1]));
        value[1] = now;
        value[0] = Mth.clamp(value[0] + (target ? 1 : -1) * dt * 4.0f, 0, 1);
        return value[0];
    }

    private AccessoryClient() {}
}
