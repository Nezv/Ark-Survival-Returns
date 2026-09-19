package dev.nez.arksurvivalreturns.client.audio;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
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
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkAudioEngine {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<SoundType, String> SOUND_TYPE_NAMES = discoverSoundTypes();
    private static double lastX;
    private static double lastZ;
    private static double stride;
    private static boolean positioned;
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
        if (!positioned) {
            lastX = player.getX();
            lastZ = player.getZ();
            positioned = true;
            return;
        }
        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        lastX = player.getX();
        lastZ = player.getZ();
        if (!player.onGround() || player.isPassenger() || player.isSwimming()) {
            stride = 0D;
            return;
        }
        stride += Math.sqrt(dx * dx + dz * dz);
        boolean running = player.isSprinting();
        double interval = running ? 0.58D : 0.78D;
        if (stride < interval) return;
        stride %= interval;

        var catalog = AudioCatalog.INSTANCE.get();
        String soundType = SOUND_TYPE_NAMES.getOrDefault(player.getBlockStateOn().getSoundType(), "STONE");
        String material = catalog.soundTypes().getOrDefault(soundType, catalog.fallbackMaterial());
        AudioCatalog.Footstep profile = catalog.footsteps().get(material);
        if (profile == null) return;
        Identifier sound = Identifier.tryParse(running ? profile.run() : profile.walk());
        if (sound == null) return;
        float pitch = 1F + (RANDOM.nextFloat() * 2F - 1F) * profile.pitchVariance();
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS,
                profile.volume(), pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR,
                player.getX(), player.getY(), player.getZ(), false));
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
        positioned = false;
        stride = 0D;
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
