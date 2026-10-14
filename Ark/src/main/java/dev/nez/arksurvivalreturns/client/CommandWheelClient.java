package dev.nez.arksurvivalreturns.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.companion.CompanionWhistle;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.Nullable;

/**
 * The command wheel's key (F22, Z by default): held or tapped, it opens the wheel for the tame under the crosshair
 * within whistle range, or for every own tame in range when none is aimed at.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class CommandWheelClient {
    static final KeyMapping OPEN_WHEEL = new KeyMapping("key.arksurvivalreturns.command_wheel",
            InputConstants.Type.KEYSYM, InputConstants.KEY_Z, JournalClient.CATEGORY);

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_WHEEL);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (OPEN_WHEEL.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.screen == null && !mc.player.isSpectator()) {
                mc.setScreen(new CommandWheelScreen(aimedTame(mc)));
            }
        }
    }

    /** The tame the player looks at, walls stopping the look, within whistle range; never the player's own mount. */
    private static @Nullable CreatureEntity aimedTame(Minecraft mc) {
        return aimedCreature(mc, CompanionWhistle.range(), CreatureEntity::isTamed);
    }

    /** The living creature the player looks at within range, walls stopping the look; never the player's own mount. */
    static @Nullable CreatureEntity aimedCreature(Minecraft mc, double range, java.util.function.Predicate<CreatureEntity> filter) {
        var player = mc.player;
        if (player == null || mc.level == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        var blocks = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blocks.getType() != HitResult.Type.MISS) end = blocks.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(range)).inflate(1),
                entity -> entity instanceof CreatureEntity creature && creature.isAlive() && filter.test(creature)
                        && entity != player.getVehicle(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof CreatureEntity creature ? creature : null;
    }

    private CommandWheelClient() {}
}
