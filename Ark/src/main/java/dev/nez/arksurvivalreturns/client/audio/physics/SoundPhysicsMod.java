package dev.nez.arksurvivalreturns.client.audio.physics;

import dev.nez.arksurvivalreturns.client.audio.physics.config.SoundPhysicsConfig;
import net.neoforged.fml.ModList;

/** Ark-owned bootstrap for the embedded GPL sound-physics core. */
public final class SoundPhysicsMod {
    public static final String MODID = "arksurvivalreturns";
    public static final SoundPhysicsConfig CONFIG = new SoundPhysicsConfig();
    private static final String EXTERNAL_MOD_ID = "sound_physics_remastered";

    private SoundPhysicsMod() {}

    /**
     * Lets the standalone mod take ownership when it is installed. Running both
     * engines would apply reverb, occlusion and attenuation twice.
     */
    public static boolean isEnabled() {
        return !ModList.get().isLoaded(EXTERNAL_MOD_ID) && CONFIG.enabled.get();
    }

    public static void initClient() {
        CONFIG.reloadClient();
        if (ModList.get().isLoaded(EXTERNAL_MOD_ID)) {
            Loggers.log("Standalone Sound Physics Remastered detected; disabling Ark's embedded physics engine");
        }
    }
}
