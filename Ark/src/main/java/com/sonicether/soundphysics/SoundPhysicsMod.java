package com.sonicether.soundphysics;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

/** Ark-owned bootstrap for the embedded GPL sound-physics core. */
public final class SoundPhysicsMod {
    public static final String MODID = "arksurvivalreturns";
    public static final SoundPhysicsConfig CONFIG = new SoundPhysicsConfig();
    private SoundPhysicsMod() {}
    public static void initClient() { CONFIG.reloadClient(); }
}
