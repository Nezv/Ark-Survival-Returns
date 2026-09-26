package dev.nez.arksurvivalreturns.client.audio;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client audio: Presence Footsteps recordings for the local player's steps and AmbientSounds beds.
 *
 * <p>A footfall lands on every half swing of the walk animation, so the steps follow the legs (about
 * 3.7 a second walking, 4.2 sprinting) instead of a fixed short distance. The vanilla step sound is
 * dropped wherever a catalog profile covers the block (see LocalPlayerMixin); otherwise both would
 * play and every step would double.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkAudioEngine {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<SoundType, String> SOUND_TYPE_NAMES = discoverSoundTypes();
    /** Walk-animation distance between two footfalls: half a leg swing (HumanoidModel uses cos(pos * 0.6662)). */
    private static final float HALF_SWING = (float) (Math.PI / 0.6662D);
    private static Map<SoundEvent, String> stepSoundTypes;
    private static long lastFootfall = Long.MIN_VALUE;
    private static boolean leftFoot;
    private static int ambienceDelay;
    private static SoundInstance ambience;

    @SubscribeEvent public static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(ArkSurvivalReturns.id("audio_catalog"), AudioCatalog.INSTANCE);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) return;

        tickFootsteps(minecraft);
        if (ambienceDelay-- <= 0) tickAmbience(minecraft);
    }

    private static void tickFootsteps(Minecraft minecraft) {
        var player = minecraft.player;
        long footfall = (long) Math.floor(player.walkAnimation.position() / HALF_SWING);
        // Vanilla is silent in the air, riding, swimming, flying and sneaking; so are these steps.
        boolean silent = !player.onGround() || player.isPassenger() || player.isSwimming() || player.isSpectator()
                || player.getAbilities().flying || player.isDiscrete();
        long previous = lastFootfall;
        lastFootfall = footfall;
        // First tick, a reset animation (riding stops it) or a silent tick: wait for the next footfall.
        if (silent || previous == Long.MIN_VALUE || footfall <= previous) return;

        AudioCatalog.Footstep profile = footstep(AudioCatalog.INSTANCE.get(),
                SOUND_TYPE_NAMES.getOrDefault(player.getBlockStateOn().getSoundType(), "STONE"));
        if (profile == null) return;
        boolean running = player.isSprinting();
        Identifier sound = Identifier.tryParse(running ? profile.run() : profile.walk());
        if (sound == null) return;
        // Feet land a hand's width to either side of the walking line; walking is softer than running.
        leftFoot = !leftFoot;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD, side = leftFoot ? 0.12F : -0.12F;
        float volume = profile.volume() * (running ? 1F : 0.8F) * (0.85F + RANDOM.nextFloat() * 0.15F);
        float pitch = 1F + (RANDOM.nextFloat() * 2F - 1F) * profile.pitchVariance();
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS,
                volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR,
                player.getX() + Mth.cos(yaw) * side, player.getY(), player.getZ() + Mth.sin(yaw) * side, false));
    }

    private static AudioCatalog.Footstep footstep(AudioCatalog.Data catalog, String soundType) {
        return catalog.footsteps().get(catalog.soundTypes().getOrDefault(soundType, catalog.fallbackMaterial()));
    }

    /**
     * True when a vanilla step sound of the local player belongs to a block the Ark footsteps cover,
     * so LocalPlayerMixin drops it instead of doubling the step.
     */
    public static boolean replacesStep(SoundEvent sound) {
        if (sound == null) return false;
        if (stepSoundTypes == null) {
            var steps = new HashMap<SoundEvent, String>();
            SOUND_TYPE_NAMES.forEach((type, name) -> {
                try { steps.putIfAbsent(type.getStepSound(), name); }
                catch (RuntimeException unresolved) {} // A deferred modded type whose sound is not registered yet.
            });
            stepSoundTypes = steps;
        }
        String type = stepSoundTypes.get(sound);
        return type != null && footstep(AudioCatalog.INSTANCE.get(), type) != null;
    }

    private static void tickAmbience(Minecraft minecraft) {
        if (ambience != null && minecraft.getSoundManager().isActive(ambience)) {
            ambienceDelay = 40;
            return;
        }
        var level = minecraft.level;
        var player = minecraft.player;
        BlockPos pos = player.blockPosition();
        String dimension = level.dimension().identifier().getPath();
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        boolean night = level.getOverworldClockTime() % 24000L >= 12500L;
        boolean raining = level.isRainingAt(pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);

        var profile = AudioCatalog.INSTANCE.get().ambience().stream().filter(candidate ->
                (candidate.dimension() == null || candidate.dimension().equals(dimension))
                && (candidate.raining() == null || candidate.raining() == raining)
                && (candidate.night() == null || candidate.night() == night)
                && (candidate.underwater() == null || candidate.underwater() == player.isUnderWater())
                && (candidate.maximumSkyLight() == null || skyLight <= candidate.maximumSkyLight())
                && (candidate.biomeContains() == null || candidate.biomeContains().isEmpty()
                    || candidate.biomeContains().stream().anyMatch(part -> biome.contains(part.toLowerCase(Locale.ROOT))))
        ).max(java.util.Comparator.comparingInt(ArkAudioEngine::specificity)).orElse(null);
        if (profile == null) {
            ambienceDelay = 200;
            return;
        }
        if (profile.sounds().isEmpty()) return;
        Identifier sound = Identifier.tryParse(profile.sounds().get(RANDOM.nextInt(profile.sounds().size())));
        if (sound == null) return;
        ambience = new SimpleSoundInstance(sound, SoundSource.AMBIENT, profile.volume(), 1F, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, 0D, 0D, 0D, true);
        minecraft.getSoundManager().play(ambience);
        int range = Math.max(1, profile.maximumDelay() - profile.minimumDelay() + 1);
        ambienceDelay = profile.minimumDelay() + RANDOM.nextInt(range);
    }

    private static int specificity(AudioCatalog.Ambience profile) {
        int score = profile.underwater() == Boolean.TRUE ? 32 : profile.underwater() == Boolean.FALSE ? 1 : 0;
        if (profile.dimension() != null) score += 16;
        if (profile.raining() != null) score += 8;
        if (profile.biomeContains() != null && !profile.biomeContains().isEmpty()) score += 4;
        if (profile.maximumSkyLight() != null) score += 2;
        if (profile.night() != null) score += 1;
        return score;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    private static void reset() {
        lastFootfall = Long.MIN_VALUE;
        ambienceDelay = 0;
        ambience = null;
    }

    private static Map<SoundType, String> discoverSoundTypes() {
        Map<SoundType, String> result = new IdentityHashMap<>();
        for (Field field : SoundType.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != SoundType.class) continue;
            try { result.put((SoundType) field.get(null), field.getName()); }
            catch (IllegalAccessException ignored) {}
        }
        return result;
    }

    private ArkAudioEngine() {}
}
