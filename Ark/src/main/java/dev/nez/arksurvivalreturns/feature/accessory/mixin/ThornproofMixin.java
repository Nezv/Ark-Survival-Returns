package dev.nez.arksurvivalreturns.feature.accessory.mixin;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import dev.nez.arksurvivalreturns.feature.accessory.Worn;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thornproof Leg Wraps: thorny bushes (block tag arksurvivalreturns:thorny) no longer hold the wearer. */
@Mixin(Entity.class)
public abstract class ThornproofMixin {
    @Unique
    private static final TagKey<Block> ARK$THORNY = TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id("thorny"));

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void ark$pushThrough(BlockState state, Vec3 speedMultiplier, CallbackInfo ci) {
        if ((Object) this instanceof Player player && state.is(ARK$THORNY) && Worn.has(player, Accessory.THORNPROOF_WRAPS)) {
            ci.cancel();
        }
    }
}
