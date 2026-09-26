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
 * population and, for every regional large species legal at this danger, a sample of placement points
 * classified with the exact {@link SpawnRules.Placement} funnel the spawner uses.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WildlifeCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arkwildlife")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> report(context.getSource(), 64))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 256))
                        .executes(context -> report(context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius")))));
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
                + " naturalSpawns=" + Config.NATURAL_SPAWNS.get() + " budget=" + Config.POPULATION_BUDGET.get());
        int wildCount = 0;
        var bySpecies = new java.util.TreeMap<String, Integer>();
        var byTier = new EnumMap<dev.nez.arksurvivalreturns.feature.behavior.BehaviorTier, Integer>(
                dev.nez.arksurvivalreturns.feature.behavior.BehaviorTier.class);
        for (var entity : level.getAllEntities())
            if (entity instanceof CreatureEntity creature && creature.isAlive() && creature.isNaturalWildlife()) {
                byTier.merge(creature.behaviorTier(), 1, Integer::sum);
                if (creature.distanceToSqr(player) > (double)radius * radius) continue;
                wildCount++;
                bySpecies.merge(creature.species().id, 1, Integer::sum);
            }
        send(source, "wilds within " + radius + "=" + wildCount + "/" + Config.POPULATION_TARGET.get() + " " + bySpecies);
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
