package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.List;
import java.util.Set;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in experiment ({@code scenario=encounter}): one wild creature at a time appears at a set distance from a
 * standing survival observer on open ground, and the recording shows what it makes of the observer. Predators
 * come by night (when they hunt), herbivores by day. The observer is healed every tick, so a strike lands and
 * the run goes on; natural spawning is off and every other mob near the observer is removed before each trial,
 * so each trial is one creature and one person. Every intervention is an event in the recording.
 */
final class EncounterScenario {
    /** Open ground; a line of oaks five blocks apart (gaps a person sees through, trunks a giant cannot pass); a pond. */
    private enum Ground { OPEN, TREES, POND }
    private record Trial(String name, Species species, int distance, int dayTime, int seconds, Ground ground) {}
    private static final List<Trial> TRIALS = List.of(
            new Trial("giga_30", Species.GIGANOTOSAURUS, 30, 15000, 45, Ground.OPEN),
            new Trial("giga_60", Species.GIGANOTOSAURUS, 60, 15000, 45, Ground.OPEN),
            new Trial("giga_90", Species.GIGANOTOSAURUS, 90, 15000, 45, Ground.OPEN),
            new Trial("giga_trees_30", Species.GIGANOTOSAURUS, 30, 15000, 45, Ground.TREES),
            new Trial("parasaur_16", Species.PARASAUR, 16, 6000, 30, Ground.OPEN),
            new Trial("pegomastax_10", Species.PEGOMASTAX, 10, 6000, 30, Ground.OPEN),
            new Trial("lystrosaurus_pond_20", Species.LYSTROSAURUS, 20, 6000, 45, Ground.POND));
    private static final int PAUSE_TICKS = 60, CLEAR_RADIUS = 160, AFTER_STRIKE_TICKS = 60, TREE_LINE = 8, POND_BEYOND = 10;

    private Vec3 origin;
    private CreatureEntity creature;
    private Trial current;
    private int index, started, nextTrial, struckAt = -1;
    private boolean drank, finished, spawnRule = true;

    static Row settings() {
        var trials = new java.util.ArrayList<Row>();
        for (var trial : TRIALS)
            trials.add(new Row("trial").put("name", trial.name()).put("species", trial.species().id).put("distance", trial.distance())
                    .put("day_time", trial.dayTime()).put("seconds", trial.seconds()).put("ground", trial.ground().name()));
        return new Row("scenario").put("name", "encounter").put("version", 1).list("trials", trials)
                .put("observer", "standing_survival_healed_every_tick").put("spawn_mobs", false)
                .put("cleared_radius", CLEAR_RADIUS).put("creature", "command_spawn_level_1_persistent_normal_wild_AI_facing_observer");
    }

    void tick(Session session) {
        if (finished || !(session.subject() instanceof ServerPlayer player)) return;
        var world = player.level();
        if (origin == null) {
            origin = player.position();
            spawnRule = world.getGameRules().get(GameRules.SPAWN_MOBS);
            world.getGameRules().set(GameRules.SPAWN_MOBS, false, world.getServer());
            if (player.gameMode() != GameType.SURVIVAL) player.setGameMode(GameType.SURVIVAL);
            log(session, "setup", null, new Row("setup").xyz("origin", origin.x, origin.y, origin.z)
                    .put("spawn_mobs_was", spawnRule).put("cleared", clear(world, player)));
            nextTrial = session.tick() + PAUSE_TICKS;
        }
        // A strike lands for real (the damage is recorded) and the observer is whole again on the next tick.
        if (creature != null && struckAt < 0 && player.getLastHurtByMob() == creature && player.getLastHurtByMobTimestamp() > started)
            struckAt = session.tick();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        if (creature != null) {
            drank |= creature.behavior() == BehaviorState.DRINK;
            int age = session.tick() - started;
            if (!creature.isAlive() || creature.isRemoved()) finish(session, "lost");
            else if (struckAt >= 0 && session.tick() - struckAt >= AFTER_STRIKE_TICKS) finish(session, "struck");
            else if (drank && creature.wildlife().mind().thirst() <= 0.2) finish(session, "quenched");
            else if (age >= current.seconds() * 20) finish(session, "timeout");
            return;
        }
        if (session.tick() < nextTrial) return;
        if (index == TRIALS.size()) {
            finished = true;
            world.getGameRules().set(GameRules.SPAWN_MOBS, spawnRule, world.getServer());
            log(session, "complete", null, new Row("result").put("trials", index));
            session.stop("scenario_complete");
            return;
        }
        begin(session, world, player, TRIALS.get(index++));
    }

    private void begin(Session session, ServerLevel world, ServerPlayer player, Trial trial) {
        current = trial;
        // Each trial faces its own bearing, away from the ground earlier trials changed.
        double bearing = Math.toRadians(index * 45.0);
        Vec3 heading = new Vec3(Math.sin(bearing), 0, Math.cos(bearing));
        player.teleportTo(world, origin.x, origin.y, origin.z, Set.of(), player.getYRot(), player.getXRot(), true);
        var clock = world.dimensionType().defaultClock();
        clock.ifPresent(c -> world.clockManager().setTotalTicks(c, world.getDefaultClockTime() - Math.floorMod(world.getDefaultClockTime(), 24000L) + 24000L + trial.dayTime()));
        int cleared = clear(world, player);
        var ground = new Row("ground").put("kind", trial.ground().name());
        if (trial.ground() == Ground.TREES) ground.put("trees", treeLine(world, origin.add(heading.scale(TREE_LINE)), heading));
        Vec3 spot = origin.add(heading.scale(trial.distance()));
        if (trial.ground() == Ground.POND) {
            var pond = BlockPos.containing(origin.add(heading.scale(trial.distance() + POND_BEYOND)));
            ground.block("pond", pond.getX(), pond.getY(), pond.getZ()).put("water", pond(world, pond));
        }
        var surface = SpawnRules.surface(world, Mth.floor(spot.x), Mth.floor(spot.z));
        var mob = ModContent.CREATURES.get(trial.species()).get().create(world, EntitySpawnReason.COMMAND);
        if (surface == null || mob == null) {
            log(session, "setup_failed", null, new Row("trial").put("trial", trial.name()).put("why", surface == null ? "no_ground" : "entity_create"));
            nextTrial = session.tick() + PAUSE_TICKS;
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(surface);
        // Facing the observer, except the thirsty one, which faces its pond.
        Vec3 look = trial.ground() == Ground.POND ? heading : origin.subtract(at);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        mob.initializeLevel(1);
        mob.setPos(at); mob.setYRot(yaw); mob.yBodyRot = yaw; mob.yHeadRot = yaw;
        mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        mob.setPersistenceRequired();
        boolean thirsty = trial.ground() == Ground.POND;
        mob.wildlife().mind().restoreNeeds(mob.species().predator ? 0.7 : 0.2, thirsty ? 0.95 : 0.2, 0.1);
        session.note(mob, "encounter_trial_" + trial.name());
        if (!world.addFreshEntity(mob)) {
            log(session, "setup_failed", null, new Row("trial").put("trial", trial.name()).put("why", "entity_add_refused"));
            nextTrial = session.tick() + PAUSE_TICKS;
            return;
        }
        creature = mob; started = session.tick(); struckAt = -1; drank = false;
        log(session, "spawn", mob, new Row("trial").put("trial", trial.name()).put("species", trial.species().id)
                .put("distance", trial.distance()).put("gap", dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.bodyDistance(mob, player))
                .put("day_time", trial.dayTime()).put("seconds", trial.seconds()).put("cleared", cleared).row("ground", ground)
                .xyz("at", at.x, at.y, at.z).put("yaw", yaw).put("width", mob.getBbWidth()).put("height", mob.getBbHeight()));
        session.mark(player, "test:encounter " + trial.name());
    }

    private void finish(Session session, String result) {
        var observer = session.subject();
        var row = new Row("result").put("trial", current.name()).put("seconds", (session.tick() - started) / 20.0)
                .put("state", creature.behavior().name()).xyz("position", creature.getX(), creature.getY(), creature.getZ());
        if (observer != null) row.put("gap", dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.bodyDistance(creature, observer));
        if (struckAt >= 0) row.put("struck_after_s", (struckAt - started) / 20.0);
        if (current.ground() == Ground.POND) row.flag("drank", drank).put("thirst", creature.wildlife().mind().thirst());
        log(session, result, creature, row);
        session.mark(observer, "test:encounter " + current.name() + " " + result);
        session.note(creature, "encounter_cleanup");
        creature.discard();
        creature = null;
        nextTrial = session.tick() + PAUSE_TICKS;
    }

    void close(Session session, String reason) {
        if (creature != null) finish(session, reason);
        if (!finished && session.subject() instanceof ServerPlayer player)
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, spawnRule, player.level().getServer());
        finished = true;
    }

    /** Every mob but the observer's own pets near the observer goes, so a trial is one creature and one person. */
    private static int clear(ServerLevel world, ServerPlayer player) {
        var mobs = world.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(CLEAR_RADIUS),
                mob -> !(mob instanceof CreatureEntity c && c.isTamed()));
        mobs.forEach(Mob::discard);
        return mobs.size();
    }

    /** Oaks across the heading, five blocks apart: a person is seen between the crowns, a giant has to go through. */
    private static int treeLine(ServerLevel world, Vec3 center, Vec3 heading) {
        var oak = world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(TreeFeatures.OAK);
        if (oak.isEmpty()) return 0;
        Vec3 across = new Vec3(-heading.z, 0, heading.x);
        int planted = 0;
        for (int k = -6; k <= 6; k++) {
            Vec3 point = center.add(across.scale(k * 5));
            var ground = SpawnRules.surface(world, Mth.floor(point.x), Mth.floor(point.z));
            if (ground != null && oak.get().value().place(world, world.getChunkSource().getGenerator(), world.getRandom(), ground)) planted++;
        }
        return planted;
    }

    /** A three by three pond in the ground, one block deep. */
    private static int pond(ServerLevel world, BlockPos center) {
        var surface = SpawnRules.surface(world, center.getX(), center.getZ());
        if (surface == null) return 0;
        int filled = 0;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (world.setBlockAndUpdate(surface.offset(x, -1, z), Blocks.WATER.defaultBlockState())) filled++;
        return filled;
    }

    private static void log(Session session, String phase, CreatureEntity mob, Row details) {
        var text = new StringBuilder(); RowJson.line(text, details);
        session.timed(new Row("ev").put("ev", "test_encounter").put("r", phase).put("e", mob == null ? -1 : session.sid(mob))
                .put("note", text.toString()));
        ArkSurvivalReturns.LOGGER.info("Encounter test {}: {}", phase, text);
    }
}
