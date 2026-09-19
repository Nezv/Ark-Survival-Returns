package dev.nez.arksurvivalreturns.client.audio.physics.config;

import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysics;

/** Dependency-free configuration surface for the embedded sound-physics engine. */
public final class SoundPhysicsConfig {
    public static final class Value<T> {
        private volatile T value;
        public Value(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
    public final Value<Boolean> enabled = value(true);
    public final Value<Float> attenuationFactor = value(1F);
    public final Value<Float> reverbAttenuationDistance = value(0F);
    public final Value<Float> reverbGain = value(1F);
    public final Value<Float> reverbBrightness = value(1F);
    public final Value<Float> reverbDistance = value(1.5F);
    public final Value<Float> blockAbsorption = value(1F);
    public final Value<Float> occlusionVariation = value(0.35F);
    public final Value<Float> defaultBlockReflectivity = value(0.5F);
    public final Value<Float> defaultBlockOcclusionFactor = value(1F);
    public final Value<Float> soundDistanceAllowance = value(4F);
    public final Value<Float> airAbsorption = value(1F);
    public final Value<Float> underwaterFilter = value(0.9F);
    public final Value<Boolean> evaluateAmbientSounds = value(false);
    public final Value<Integer> environmentEvaluationRayCount = value(24);
    public final Value<Integer> environmentEvaluationRayBounces = value(4);
    public final Value<Float> nonFullBlockOcclusionFactor = value(0.25F);
    public final Value<Integer> maxOcclusionRays = value(16);
    public final Value<Float> maxOcclusion = value(64F);
    public final Value<Boolean> strictOcclusion = value(false);
    public final Value<Boolean> soundDirectionEvaluation = value(true);
    public final Value<Boolean> redirectNonOccludedSounds = value(true);
    public final Value<Boolean> updateMovingSounds = value(false);
    public final Value<Integer> soundUpdateInterval = value(5);
    public final Value<Double> maxSoundProcessingDistance = value(256D);
    public final Value<Boolean> unsafeLevelAccess = value(false);
    public final Value<Integer> levelCloneRange = value(4);
    public final Value<Integer> levelCloneMaxRetainTicks = value(20);
    public final Value<Integer> levelCloneMaxRetainBlockDistance = value(16);
    public final Value<Boolean> debugLogging = value(false);
    public final Value<Boolean> occlusionLogging = value(false);
    public final Value<Boolean> environmentLogging = value(false);
    public final Value<Boolean> performanceLogging = value(false);
    public final Value<Boolean> renderSoundBounces = value(false);
    public final Value<Boolean> renderOcclusion = value(false);
    private static <T> Value<T> value(T initial) { return new Value<>(initial); }
    public void reloadClient() { SoundPhysics.syncReverbParams(); }
}
