package dev.nez.arksurvivalreturns.feature.theme;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import net.neoforged.neoforge.event.village.VillageSiegeEvent;

/**
 * Closes every survival route that could create a removed creature.
 *
 * <p>Natural spawning, structure spawning, spawners, mob eggs, conversions and entity loading
 * all funnel through these hooks, so a world that already contains the creatures is cleaned up
 * as its chunks load instead of corrupting any saved data. Administrator commands are left
 * available: an explicit {@code /summon} still creates the entity type.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class HostileGuard {
    /** Placement rules run before any spawn attempt is turned into an entity. */
    @SubscribeEvent
    public static void spawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!Config.THEME_MONSTERS.get()) return;
        if (ThemePolicy.removed(event.getEntityType())) event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** Position checks also cover block and structure spawners, which skip placement rules. */
    @SubscribeEvent
    public static void spawnPosition(MobSpawnEvent.PositionCheck event) {
        if (!Config.THEME_MONSTERS.get()) return;
        if (ThemePolicy.removed(event.getEntity().getType())) event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    @SubscribeEvent
    public static void finalizeSpawn(FinalizeSpawnEvent event) {
        if (!Config.THEME_MONSTERS.get()) return;
        if (ThemePolicy.removed(event.getEntity().getType())) event.setCanceled(true);
    }

    /** Loaded, mounted, bred and command-spawned creatures are removed when they enter a level. */
    @SubscribeEvent
    public static void joinLevel(EntityJoinLevelEvent event) {
        if (!Config.THEME_MONSTERS.get() || event.getLevel().isClientSide()) return;
        if (ThemePolicy.removed(event.getEntity().getType())) event.setCanceled(true);
    }

    /** Zombie villagers, villagers, hoglins and every other conversion are refused. */
    @SubscribeEvent
    public static void conversion(LivingConversionEvent.Pre event) {
        if (!Config.THEME_MONSTERS.get()) return;
        if (ThemePolicy.removed(event.getEntity().getType()) || ThemePolicy.removed(event.getOutcome())) {
            event.setCanceled(true);
        }
    }

    /** Patrols and phantom flybys are removed even when their game rule is switched back on. */
    @SubscribeEvent
    public static void customSpawners(ModifyCustomSpawnersEvent event) {
        if (!Config.THEME_MONSTERS.get()) return;
        event.getCustomSpawners().removeIf(spawner ->
                spawner instanceof net.minecraft.world.level.levelgen.PatrolSpawner
                        || spawner instanceof net.minecraft.world.level.levelgen.PhantomSpawner);
    }

    @SubscribeEvent
    public static void villageSiege(VillageSiegeEvent event) {
        if (!Config.THEME_MONSTERS.get()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void phantoms(PlayerSpawnPhantomsEvent event) {
        if (!Config.THEME_MONSTERS.get()) return;
        event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
    }

    /** Hides the spawn eggs of removed creatures from the creative listings. */
    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Tab contents are built on the client, where the server config is not readable, so this
        // listing follows the removal list unconditionally, like the generated data removals.
        event.removeIf(stack -> {
            EntityType<?> type = SpawnEggItem.getType(stack);
            return type != null && ThemePolicy.removed(type);
        }, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    private HostileGuard() {}
}
