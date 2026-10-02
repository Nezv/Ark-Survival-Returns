package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.aquatic.Water;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in experiment controller, separate from the observational recorder and production wildlife AI. */
final class WaterTestScenario {
    private static final Species[] SPECIES = {Species.PEGOMASTAX, Species.LYSTROSAURUS, Species.PARASAUR};
    private static final int[] DISTANCES = {12, 20};
    private static final int TRIAL_TICKS = 1200, SEARCH_RADIUS = 96;
    private record Site(BlockPos water, BlockPos bank, Direction inland) {}
    private record Placement(CreatureEntity mob, BlockPos start, int pathNodes) {}
    private final List<BlockPos> offsets = offsets();
    private final ArrayDeque<Site> candidates = new ArrayDeque<>();
    private final TreeMap<String, Integer> rejections = new TreeMap<>();
    private Site site;
    private CreatureEntity animal;
    private BlockPos origin;
    private int scan, trial, started, nextTrial;
    private int nextProgress;
    private boolean drank, finished;

    static Row settings() {
        return new Row("scenario").put("name", "water").put("version", 3).put("trials", 6).put("timeout_game_s", 60)
                .ints("bank_distances", DISTANCES).put("stop", "after_all_trials")
                .put("site_selection", "reachable_bank_per_trial")
                .put("initial_hunger", 0.1).put("initial_thirst", 0.95).put("initial_fatigue", 0.05)
                .put("observer", "creative_flying_above_bank").put("terrain", "existing_loaded_shoreline")
                .put("path_probe", "canReach_required_not_assigned").put("animal", "command_spawn_persistent_normal_wild_AI");
    }

    void tick(Session session) {
        if (finished || !(session.subject() instanceof ServerPlayer player)) return;
        var world = player.level();
        if (origin == null) {
            origin = player.blockPosition();
            log(session, "search", null, new Row("setup").block("origin", origin.getX(), origin.getY(), origin.getZ())
                    .put("radius", SEARCH_RADIUS));
            nextProgress = session.tick() + 400;
        }
        if (site == null) {
            if (session.tick() >= nextProgress) {
                var counts = new Row("rejections"); rejections.forEach(counts::put);
                log(session, "search_progress", null, new Row("search").put("trial", trial + 1)
                        .put("columns", scan).row("rejections", counts));
                nextProgress = session.tick() + 400;
            }
            if (!candidates.isEmpty()) {
                var candidate = candidates.removeFirst();
                // At most one path probe per tick, separate from AI requests and never assigned to a mob.
                if (placement(world, candidate, SPECIES[trial / 2], DISTANCES[trial % 2]) != null) {
                    site = candidate;
                    var bank = site.bank(); var water = site.water();
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(world, bank.getX() + 0.5, bank.getY() + 12, bank.getZ() + 0.5,
                            Set.of(), 0, 65, true);
                    log(session, "site", null, new Row("site").block("water", water.getX(), water.getY(), water.getZ())
                            .block("bank", bank.getX(), bank.getY(), bank.getZ()).put("inland", site.inland().name())
                            .put("biome", world.getBiome(bank).unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"))
                            .put("observer_mode", "creative_flying").put("terrain_edited", false));
                    nextTrial = session.tick() + 100;
                }
                return;
            }
            // Only loaded columns, bounded work; no locate command, generated terrain or chunk tickets.
            for (int i = 0; i < 256 && scan < offsets.size(); i++) {
                var offset = offsets.get(scan++);
                var water = Water.surfaceWater(world, origin.getX() + offset.getX(), origin.getZ() + offset.getZ());
                if (water == null) continue;
                for (var direction : Direction.Plane.HORIZONTAL) for (int edge = 1; edge <= 3; edge++) {
                    var column = water.relative(direction, edge);
                    var bank = SpawnRules.surface(world, column.getX(), column.getZ());
                    if (bank == null || bank.getY() < water.getY() + 1 || bank.getY() > water.getY() + 2) continue;
                    candidates.addLast(new Site(water, bank, direction));
                }
                if (!candidates.isEmpty()) return;
            }
            if (scan == offsets.size()) {
                log(session, "not_exercised", null, new Row("result").put("trial", trial + 1)
                        .put("why", "no_loaded_shore_with_reachable_trial_route"));
                finished = true;
            }
            return;
        }
        if (animal != null) {
            drank |= animal.behavior() == BehaviorState.DRINK;
            if (!animal.isAlive() || animal.isRemoved()) finish(session, "lost");
            else if (drank && animal.wildlife().mind().thirst() <= 0.2) finish(session, "quenched");
            else if (session.tick() - started >= TRIAL_TICKS) finish(session, "timeout");
        } else if (session.tick() >= nextTrial) {
            if (trial == 6) {
                finished = true; log(session, "complete", null, new Row("result").put("trials", trial));
                session.stop("scenario_complete"); return;
            }
            var species = SPECIES[trial / 2];
            int distance = DISTANCES[trial % 2];
            var placement = placement(world, site, species, distance);
            if (placement == null) {
                log(session, "search", null, new Row("trial").put("trial", trial + 1).put("species", species.id)
                        .put("distance", distance).put("why", "find_reachable_route_for_next_trial"));
                site = null; scan = 0; candidates.clear();
                return;
            }
            trial++;
            animal = placement.mob();
            animal.setPersistenceRequired();
            animal.wildlife().mind().restoreNeeds(0.1, 0.95, 0.05);
            started = session.tick(); drank = false;
            session.note(animal, "controlled_water_trial_" + trial);
            if (!world.addFreshEntity(animal)) {
                log(session, "setup_failed", null, new Row("trial").put("trial", trial).put("why", "entity_add_refused"));
                animal = null; nextTrial = session.tick() + 100; return;
            }
            log(session, "spawn", animal, new Row("trial").put("trial", trial).put("species", species.id)
                    .put("distance", distance).block("start", placement.start().getX(), placement.start().getY(), placement.start().getZ())
                    .block("bank", site.bank().getX(), site.bank().getY(), site.bank().getZ())
                    .block("water", site.water().getX(), site.water().getY(), site.water().getZ())
                    .put("water_height_difference", placement.start().getY() - site.water().getY())
                    .put("path_reachable", true).put("path_nodes", placement.pathNodes()).put("initial_thirst", 0.95));
            session.mark(player, "test:water " + trial + " " + species.id + " " + distance + " blocks");
        }
    }

    private void finish(Session session, String result) {
        log(session, result, animal, new Row("result").put("trial", trial).put("drank", drank)
                .put("thirst", animal.wildlife().mind().thirst()).put("elapsed_game_s", (session.tick() - started) / 20.0)
                .put("state", animal.behavior().name()).xyz("position", animal.getX(), animal.getY(), animal.getZ()));
        session.mark(session.subject(), "test:water " + trial + " " + result);
        session.note(animal, "controlled_water_cleanup");
        animal.discard(); animal = null;
        nextTrial = session.tick() + 100;
    }

    void close(Session session, String reason) {
        if (animal != null) finish(session, reason);
        finished = true;
    }

    private Placement rejected(String reason) { rejections.merge(reason, 1, Integer::sum); return null; }

    private Placement placement(ServerLevel world, Site site, Species species, int distance) {
        var column = site.bank().relative(site.inland(), distance);
        var start = SpawnRules.surface(world, column.getX(), column.getZ());
        // Reachability, rather than the AI's own search height filter, admits the trial. A reachable
        // downhill bank the animal fails to search for is useful evidence, not a setup rejection.
        if (start == null) return rejected("no_dry_start");
        var box = SpawnRules.bounds(species, start);
        var bankBox = SpawnRules.bounds(species, site.bank());
        if (!SpawnRules.loaded(world, box.minmax(bankBox).inflate(40))) return rejected("unloaded_surroundings");
        if (!world.getWorldBorder().isWithinBounds(box)) return rejected("border");
        if (!world.noCollision(null, box, true) || !world.noCollision(null, bankBox, true)) return rejected("body_clearance");
        if (!world.getEntitiesOfClass(CreatureEntity.class, box.minmax(bankBox).inflate(40),
                e -> e.isAlive() && e.species().predator && !e.isTamed()).isEmpty()) return rejected("nearby_predator");
        var mob = ModContent.CREATURES.get(species).get().create(world, EntitySpawnReason.COMMAND);
        if (mob == null) return rejected("entity_create");
        mob.initializeLevel(1); mob.setPos(Vec3.atBottomCenterOf(start));
        mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        // Independently prove the chosen bank is within physical drinking reach of this water block.
        var bank = Vec3.atBottomCenterOf(site.bank());
        var water = Vec3.atCenterOf(site.water()).add(0, 0.4, 0);
        int reach = 2 + (int) Math.ceil(mob.getBbWidth() / 2);
        if (Math.abs(site.water().getX() - site.bank().getX()) > reach
                || Math.abs(site.water().getZ() - site.bank().getZ()) > reach
                || world.clip(new ClipContext(bank.add(0, 0.8, 0), water,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() != HitResult.Type.MISS) return rejected("drinking_reach");
        var path = mob.getNavigation().createPath(site.bank(), 0);
        return path != null && path.canReach() ? new Placement(mob, start, path.getNodeCount()) : rejected("path_unreachable");
    }

    private static void log(Session session, String phase, CreatureEntity mob, Row details) {
        var text = new StringBuilder(); RowJson.line(text, details);
        session.timed(new Row("ev").put("ev", "test_water").put("r", phase).put("e", mob == null ? -1 : session.sid(mob))
                .put("note", text.toString()));
        ArkSurvivalReturns.LOGGER.info("Water test {}: {}", phase, text);
    }

    private static List<BlockPos> offsets() {
        var points = new ArrayList<BlockPos>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++)
            if (x * x + z * z <= SEARCH_RADIUS * SEARCH_RADIUS) points.add(new BlockPos(x, 0, z));
        points.sort(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getZ() * p.getZ()));
        return List.copyOf(points);
    }
}
