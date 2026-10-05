package dev.nez.arksurvivalreturns.client.betterf3;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.client.DangerMapClient;
import dev.nez.arksurvivalreturns.client.MassClient;
import dev.nez.arksurvivalreturns.client.SessionRecorderClient;
import dev.nez.arksurvivalreturns.client.ThreadLoad;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.debug.DinoDebugSync;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.spawn.DangerBands;
import dev.nez.arksurvivalreturns.feature.spawn.DangerTier;
import dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations;
import dev.nez.arksurvivalreturns.registry.ModContent;
import me.cominixo.betterf3.config.ModConfigFile;
import me.cominixo.betterf3.modules.BaseModule;
import me.cominixo.betterf3.modules.EmptyModule;
import me.cominixo.betterf3.modules.MinecraftModule;
import me.cominixo.betterf3.utils.DebugLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Ark's block on the BetterF3 debug screen: danger zone, creatures drawn and loaded, the wildlife budget, thread
 * load, the creature aimed at, carried load, Ark level and shader pack. BetterF3 draws its own modules in place
 * of the vanilla debug entries, so Ark's figures have to be one of them. Only touched when BetterF3 is loaded.
 *
 * <p>BetterF3 closes its module base class to other packages. {@link MinecraftModule} is its plainest public
 * module and is extended for the constructor alone; its one line is replaced by Ark's. BetterF3 names a module
 * after its class, so this one is "ark" in its file and its language keys.
 */
public final class ArkModule extends MinecraftModule {
    private static final Set<EntityType<?>> CREATURES = new HashSet<>();
    private static final long REFRESH_MILLIS = 250;

    static {
        ModContent.CREATURES.values().forEach(type -> CREATURES.add(type.get()));
        CREATURES.add(ModContent.GUARDIAN_DRAGON.get());
        CREATURES.add(ModContent.GUARDIAN_GIGANOTOSAURUS.get());
    }

    private final DebugLine zone, creatures, wildlife, nearby, threads, target, mass, level, shaders;
    private long refreshed;

    /** Public and without arguments: BetterF3 makes its own instance for every place in a saved layout. */
    public ArkModule() {
        lines.clear();
        defaultNameColor = nameColor = TextColor.fromRgb(0x80FF20);
        defaultValueColor = valueColor = TextColor.fromRgb(0xFFD24A);
        zone = line("ark_zone", false);
        creatures = line("ark_creatures", true);
        wildlife = line("ark_wildlife", false);
        nearby = line("ark_nearby", false);
        threads = line("ark_threads", true);
        target = line("ark_target", false);
        mass = line("ark_mass", true);
        level = line("ark_level", true);
        shaders = line("ark_shaders", true);
    }

    /** Adds the module to BetterF3's lists; on the main thread, once BetterF3 is constructed. */
    public static void register() {
        var module = new ArkModule();
        BaseModule.allModules.add(module);
        // A saved layout was read before BetterF3 knew this module, which left a blank in its place: read it again.
        if (Files.exists(FMLPaths.CONFIGDIR.get().resolve("betterf3.toml"))) {
            ModConfigFile.load(ModConfigFile.FileType.TOML);
            return;
        }
        // No saved layout: the right-hand column, under the system figures and above the targeted block.
        int at = 0;
        while (at < BaseModule.modulesRight.size() && !(BaseModule.modulesRight.get(at) instanceof EmptyModule)) at++;
        BaseModule.modulesRight.add(at, module);
    }

    @Override public void update(Minecraft mc) {
        var player = mc.player;
        var world = mc.level;
        // BetterF3 asks every frame when its own caching is switched off.
        if (player == null || world == null || Util.getMillis() - refreshed < REFRESH_MILLIS) return;
        refreshed = Util.getMillis();

        var map = DangerMapClient.profile();
        if (map == null || map.bandWidth() <= 0) zone.active = false;
        else {
            int danger = DangerBands.level(player.getBlockX(), player.getBlockZ(), map.originX(), map.originZ(), map.bandWidth());
            zone.value(danger + " " + DangerTier.values()[danger - 1].id);
        }

        int loaded = 0, drawn = 0;
        for (Entity entity : world.entitiesForRendering()) if (entity instanceof CreatureEntity) loaded++;
        for (EntityRenderState state : mc.gameRenderer.getGameRenderState().levelRenderState.entityRenderStates)
            if (CREATURES.contains(state.entityType)) drawn++;
        creatures.value(drawn + " drawn / " + loaded + " loaded");

        // The budget runs on the server: only a single-player client shares its count.
        var census = mc.getSingleplayerServer() == null ? null : NaturalPopulations.census(player.getUUID());
        set(wildlife, census == null ? "" : census.loaded() + " loaded / cap " + census.cap());
        set(nearby, census == null ? "" : census.nearby() + (census.groups() ? " groups" : " animals") + " / target " + census.target());

        set(threads, ThreadLoad.summary());

        var aimed = DinoDebugSync.target(player);
        set(target, aimed == null ? "" : aimed.species().displayName + " level " + aimed.creatureLevel() + ", "
                + aimed.behavior().name().toLowerCase(Locale.ROOT) + ", " + Math.round(aimed.distanceTo(player)) + " m");

        set(mass, MassClient.summary());

        var progress = ArkLevels.get(player);
        level.value(progress.level() + (progress.level() >= Config.PLAYER_LEVEL_CAP.get() ? " (cap)"
                : ", " + progress.xp() + " / " + ArkLevels.xpForNextLevel(progress.level()) + " XP"));

        shaders.value(SessionRecorderClient.shaders());
    }

    private DebugLine line(String id, boolean inReducedDebug) {
        var line = new DebugLine(id);
        line.inReducedDebug = inReducedDebug;
        lines.add(line);
        return line;
    }

    /** A line without a value is left out rather than printed empty. */
    private static void set(DebugLine line, String value) {
        if (value.isEmpty()) line.active = false;
        else line.value(value);
    }
}
