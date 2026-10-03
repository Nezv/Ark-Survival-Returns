package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.EnumMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator diagnostic for natural spawning: why apex species do or do not appear around the caller.
 *
 * <p>Read-only and gamemaster-only. It reports the spatial danger band, the biome, the local wild
 * population, the budget's target and, under the ledger model, the region's predator-prey state, and for
 * every regional large species legal at this danger, a sample of placement points classified with the exact
 * {@link SpawnRules.Placement} funnel the spawner uses.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WildlifeCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arkwildlife")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> report(context.getSource(), 64))
                .then(Commands.literal("biome")
                        .executes(context -> biomeReport(context.getSource(), 256))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, BiomePatchSurvey.MAX_RADIUS))
                                .executes(context -> biomeReport(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 256))
                        .executes(context -> report(context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static int biomeReport(CommandSourceStack source, int radius) {
        Player player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player: the survey is anchored to your position."));
            return 0;
        }
        var result = SurfaceBiomes.survey(source.getLevel(), player.getBlockX(), player.getBlockZ(), radius);
        if (result.isEmpty()) {
            source.sendFailure(Component.literal("No loaded surface available here."));
            return 0;
        }
        var patch = result.get();
        var profile = patch.profile();
        send(source, "surface biome=" + profile.biomeId() + " type=" + profile.type().id()
                + " climate=" + profile.climate() + " moisture=" + profile.moisture()
                + " mountainous=" + profile.mountainous() + " snowy=" + profile.snowy());
        send(source, String.format(java.util.Locale.ROOT,
                "connected patch: observed ~%d blocks squared (%d sampled cells), span ~%dx%d, equivalent diameter ~%.0f blocks",
                patch.areaBlocks(), patch.cells(), patch.spanX(), patch.spanZ(), patch.equivalentDiameter()));
        send(source, "surface altitude Y=" + patch.minY() + ".." + patch.maxY()
                + "; " + (patch.enclosed() ? "enclosed at sampling resolution" : "INCOMPLETE; full size unknown")
                + "; unavailable columns=" + patch.unavailableColumns() + " range limit=" + patch.rangeLimited()
                + " sample limit=" + patch.budgetLimited());
        send(source, "Grid=" + BiomePatchSurvey.STEP + " blocks, square search radius=" + radius
                + ", sampled=" + patch.sampledColumns() + "/" + BiomePatchSurvey.MAX_SAMPLES
                + ". Thin boundaries can fall between samples. Population rules are not changed by this survey.");
        return 1;
    }

    private static int report(CommandSourceStack source, int radius) {
        Player player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player: the report is anchored to your position."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        var pos = player.blockPosition();
        var data = ProgressionData.get(level);
        int danger = data.levelAt(pos);
        var biome = level.getBiome(pos);
        send(source, "position=" + pos.toShortString() + " danger=" + danger
                + " bandOrigin=" + data.originX() + "," + data.originZ() + " bandWidth=" + data.bandWidth());
        send(source, "biome=" + biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?")
                + " naturalSpawns=" + Config.NATURAL_SPAWNS.get() + " budget=" + Config.POPULATION_BUDGET.get()
                + " model=" + Config.POPULATION_MODEL.get());
        int wildCount = 0;
        var bySpecies = new java.util.TreeMap<String, Integer>();
        var byTier = new EnumMap<dev.nez.arksurvivalreturns.feature.behavior.BehaviorTier, Integer>(
                dev.nez.arksurvivalreturns.feature.behavior.BehaviorTier.class);
        for (var entity : level.getAllEntities())
            if (entity instanceof CreatureEntity creature && creature.isAlive() && creature.isNaturalWildlife()) {
                byTier.merge(creature.behaviorTier(), 1, Integer::sum);
                double dx = creature.getX() - player.getX(), dz = creature.getZ() - player.getZ();
                if (dx * dx + dz * dz > (double) radius * radius) continue;
                wildCount++;
                bySpecies.merge(creature.species().id, 1, Integer::sum);
            }
        int budgetRadius = Config.POPULATION_RADIUS.get();
        send(source, "wilds within " + radius + " (horizontal)=" + wildCount + " " + bySpecies);
        send(source, "budget keeps " + NaturalPopulations.targetFor(level, player) + " within " + budgetRadius
                + ", dimension cap " + NaturalPopulations.globalCap(level.players().size()));
        if (NaturalPopulations.ledger()) {
            var state = RegionalLedger.get(level).at(level, pos.getX(), pos.getZ());
            send(source, String.format(java.util.Locale.ROOT,
                    "ledger region %d,%d: prey %.2f predators %.2f (1 = balance), abundance around you %.2f, %s every %.1f days",
                    RegionalLedger.regionOf(pos.getX()), RegionalLedger.regionOf(pos.getZ()), state.prey(), state.predators(),
                    NaturalPopulations.abundanceAround(level, player, budgetRadius),
                    Config.LEDGER_CYCLES.get() ? "cycles" : "swings back to balance", Config.LEDGER_CYCLE_DAYS.get()));
        }
        // Behaviour cost scales with these: full-detail creatures sense and path, ambient ones only wander.
        send(source, "loaded wilds by tier " + byTier + " (tiers " + (Config.BEHAVIOR_TIERS.get() ? "on" : "off")
                + ", radii " + Config.TIER_FULL_RADIUS.get() + "/" + Config.TIER_AMBIENT_RADIUS.get() + "/"
                + Config.TIER_DORMANT_RADIUS.get() + ")");
        for (var species : Species.values()) {
            if (!NaturalPopulations.isRegionalLarge(species) || danger < species.minimumDanger()) continue;
            send(source, species.id + " minDanger=" + species.minimumDanger()
                    + (biome.is(species.biomes) ? " habitat" : " outside its habitat")
                    + " weight=" + species.weight + " " + probe(level, player, species, radius));
        }
        return 1;
    }

    /** Samples placement points around the player and tallies the first refusal reason of each. */
    private static String probe(ServerLevel level, Player player, Species species, int radius) {
        var type = ModContent.CREATURES.get(species).get();
        int minDistance = Config.POPULATION_MIN_DISTANCE.get();
        var reasons = new EnumMap<SpawnRules.Placement, Integer>(SpawnRules.Placement.class);
        int sampled = 0, ok = 0, noSurface = 0;
        var random = level.getRandom();
        for (int i = 0; i < 24; i++) {
            int x = player.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = player.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            if (player.distanceToSqr(x + 0.5, player.getY(), z + 0.5) < (double)minDistance * minDistance) continue;
            var surface = SpawnRules.surface(level, x, z);
            sampled++;
            if (surface == null) { noSurface++; continue; }
            var placement = SpawnRules.placement(type, level, surface);
            if (placement == SpawnRules.Placement.OK) ok++;
            else reasons.merge(placement, 1, Integer::sum);
        }
        var text = new StringBuilder("samples=").append(sampled).append(" ok=").append(ok);
        if (noSurface > 0) text.append(" no_surface=").append(noSurface);
        reasons.forEach((reason, count) -> text.append(' ')
                .append(reason.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(count));
        return text.toString();
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private WildlifeCommand() {}
}
