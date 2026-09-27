package dev.nez.arksurvivalreturns.client.audio.physics.mixin;

import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysicsMod;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SoundEvent.class)
public class SoundEventMixin {

    @ModifyConstant(method = "getRange", constant = @Constant(floatValue = 16F), expect = 2)
    private float allowance1(float value) {
        if (!SoundPhysicsMod.isEnabled()) {
            return value;
        }
        // The server config decides (it is synced, so client and server agree); before a world loads, the default.
        float multiplier = dev.nez.arksurvivalreturns.Config.SPEC.isLoaded()
                ? (float) (double) dev.nez.arksurvivalreturns.Config.SOUND_RANGE_MULTIPLIER.get()
                : SoundPhysicsMod.CONFIG.soundDistanceAllowance.get();
        return value * multiplier;
    }

}
