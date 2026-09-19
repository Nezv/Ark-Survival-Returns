package dev.nez.arksurvivalreturns.client.audio.physics.mixin;

import com.mojang.blaze3d.audio.Channel;
import dev.nez.arksurvivalreturns.client.audio.physics.Loggers;
import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysics;
import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysicsMod;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class SourceMixin {

    @Shadow
    @Final
    private int source;

    @Unique
    private Vec3 pos;

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void setSelfPosition(Vec3 poss, CallbackInfo ci) {
        this.pos = poss;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void play(CallbackInfo ci) {
        if (pos == null || !SoundPhysicsMod.isEnabled()) {
            return;
        }
        SoundPhysics.onPlaySound(pos.x, pos.y, pos.z, source);
        Loggers.logALError("Sound play injector");
    }

    @ModifyVariable(method = "linearAttenuation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float injected(float attenuation) {
        if (!SoundPhysicsMod.isEnabled()) {
            return attenuation;
        }
        return attenuation / SoundPhysicsMod.CONFIG.attenuationFactor.get();
    }

    @Inject(method = "linearAttenuation", at = @At("RETURN"))
    private void linearAttenuation2(float attenuation, CallbackInfo ci) {
        if (!SoundPhysicsMod.isEnabled()) {
            return;
        }
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, attenuation / 2F);
    }

}
