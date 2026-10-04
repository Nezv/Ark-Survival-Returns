package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * What an animal does about a blow. The reported failures: a Giganotosaurus killed by three Triceratops without
 * an answer, and animals struck by a creative player that neither ran nor fought.
 */
final class ReactionGameTests {
    private static void floor(GameTestHelper h) {
        for (int x = 40; x < 90; x++) for (int z = 40; z < 90; z++) {
            h.setBlock(x, 1, z, Blocks.STONE);
            h.setBlock(x, 2, z, Blocks.DIRT);
        }
    }
    private static CreatureEntity wild(GameTestHelper h, Species species, Vec3 relative) {
        var mob = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        mob.initializeLevel(1); mob.setPersistenceRequired(); mob.getRandom().setSeed(0L);
        mob.setPos(h.absoluteVec(relative)); mob.setYRot(0); mob.yBodyRot = 0;
        mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
        mob.wildlife().mind().restoreNeeds(0.1, 0.1, 0.1);
        h.getLevel().addFreshEntity(mob);
        GameTestCleanup.onFinish(h, mob::discard);
        return mob;
    }
    private static WildlifeMind.Observation seen(boolean attacked, boolean intimidating, boolean far, double health) {
        return new WildlifeMind.Observation(1, true, false, false, attacked, intimidating, far, false, false, false, health, true);
    }
    private static WildlifeMind.Observation heard() {
        return new WildlifeMind.Observation(0.65, false, false, false, false, false, false, false, false, false, 1, false);
    }
    private static WildlifeMind.Routine day(boolean cornered) {
        return new WildlifeMind.Routine(true, false, false, true, false, false, cornered, 200, 1);
    }

    /** The decision rules alone, without a world. */
    static void rules(GameTestHelper h) {
        // Under blows an animal neither walks home nor gives the fight up.
        var hunter = new WildlifeMind(true, false, false);
        hunter.step(seen(true, false, true, 1), 10, day(false));
        for (int tick = 10; tick <= 600; tick += 10) {
            var state = hunter.step(seen(tick % 100 == 0, false, true, 1), 10, day(false));
            h.assertTrue(state == BehaviorState.DEFEND, "A hunter struck every five seconds left the fight after " + tick + " ticks: " + state);
        }
        // Left alone and far from home, it goes back once the blow is off its mind.
        BehaviorState after = null;
        for (int tick = 0; tick < 300; tick += 10) after = hunter.step(seen(false, false, true, 1), 10, day(false));
        h.assertTrue(after == BehaviorState.RETURN_HOME, "A hunter far from home never went back: " + after);

        // A flight lasts: with its back to the threat a runner only hears it, and still runs.
        var runner = new WildlifeMind(true, false, false);
        for (int i = 0; i < 4; i++) runner.step(seen(false, true, false, 1), 10, day(false));
        h.assertTrue(runner.state() == BehaviorState.FLEE, "A hunter facing a bigger one did not run: " + runner.state());
        for (int i = 0; i < 10; i++)
            h.assertTrue(runner.step(heard(), 10, day(false)) == BehaviorState.FLEE, "A runner turned back to look at what it ran from: " + runner.state());
        // Nothing sensed any more: the flight ends.
        for (int i = 0; i < 12; i++) {
            var state = runner.step(new WildlifeMind.Observation(0, false, false, false, false, false, false, false, false, false, 1, false), 10, day(false));
            // Found by the recorded trials: a Carnotaurus that had run from a Giganotosaurus walked back to look.
            h.assertTrue(state != BehaviorState.INVESTIGATE, "A runner walked back to what it had just run from");
        }
        h.assertTrue(runner.state() != BehaviorState.FLEE, "A flight never ended");

        // Cornered: whatever can bite turns and fights, a timid animal keeps trying to get away.
        var trapped = new WildlifeMind(true, false, false);
        for (int i = 0; i < 4; i++) trapped.step(seen(false, true, false, 1), 10, day(false));
        h.assertTrue(trapped.step(seen(true, true, false, 1), 10, day(true)) == BehaviorState.DEFEND
                && trapped.reason() == WildlifeMind.Reason.CORNERED, "A cornered hunter did not turn: " + trapped.state());
        var timid = new WildlifeMind(false, true, false);
        for (int i = 0; i < 4; i++) timid.step(seen(true, false, false, 1), 10, day(true));
        h.assertTrue(timid.state() == BehaviorState.FLEE, "A timid animal stood its ground: " + timid.state());
        h.succeed();
    }

    /** A blow from a creative player is answered like any other: the timid run, the others turn on it. */
    static void creativeHit(GameTestHelper h) {
        floor(h);
        var world = h.getLevel();
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.setPos(h.absoluteVec(new Vec3(64.5, 3, 70.5))); world.addFreshEntity(player);
        GameTestCleanup.onFinish(h, player::discard);
        var parasaur = wild(h, Species.PARASAUR, new Vec3(60.5, 3, 64.5));
        var trike = wild(h, Species.TRICERATOPS, new Vec3(68.5, 3, 64.5));
        // Both face away from the player: the blow itself tells them where it came from.
        parasaur.setYRot(180); parasaur.yBodyRot = 180; trike.setYRot(180); trike.yBodyRot = 180;
        h.runAfterDelay(20, () -> {
            for (int i = 0; i < 4; i++) { parasaur.wildlife().think(); trike.wildlife().think(); }
            h.assertFalse(parasaur.behavior().alarm() || trike.behavior().alarm(), "A creative player standing by alarmed wildlife");
            parasaur.hurtServer(world, world.damageSources().playerAttack(player), 1);
            trike.hurtServer(world, world.damageSources().playerAttack(player), 1);
            for (int i = 0; i < 3; i++) { parasaur.wildlife().think(); trike.wildlife().think(); }
            h.assertTrue(parasaur.behavior() == BehaviorState.FLEE, "A Parasaur struck by a creative player did not run: " + parasaur.behavior());
            h.assertTrue(trike.behavior() == BehaviorState.DEFEND, "A Triceratops struck by a creative player did not turn on it: " + trike.behavior());
        });
        // Vanilla never stores a creative player as a mob's target, so the answer is read off the ground they cover.
        Vec3 parasaurStart = parasaur.position();
        h.runAfterDelay(180, () -> {
            h.assertTrue(parasaur.position().distanceTo(parasaurStart) > 8, "The struck Parasaur ran " + parasaur.position().distanceTo(parasaurStart) + " blocks");
            h.assertTrue(dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.bodyDistance(trike, player) < 4,
                    "The struck Triceratops did not come for the creative player: " + trike.distanceTo(player) + " blocks off");
        });
        // The blow wears off; a creative player is nothing to wildlife again.
        h.runAfterDelay(360, () -> {
            h.assertFalse(trike.behavior().combat(), "A Triceratops went on fighting a creative player long after the blow: " + trike.behavior());
            h.succeed();
        });
    }

    /**
     * Found by the recorded trials: one Parasaur lifting its head at a player its herd would only watch made the
     * whole herd bolt. A mate's glance turns heads; a mate struck sets the herd running.
     */
    static void glance(GameTestHelper h) {
        floor(h);
        var world = h.getLevel();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(64.5, 3, 84.5))); world.addFreshEntity(player);
        GameTestCleanup.onFinish(h, player::discard);
        var pack = UUID.randomUUID();
        var herd = new CreatureEntity[3];
        for (int i = 0; i < 3; i++) {
            herd[i] = wild(h, Species.PARASAUR, new Vec3(60.5 + i * 4, 3, 64.5));
            herd[i].joinPack(pack);
        }
        h.runAfterDelay(20, () -> {
            boolean watched = false;
            for (int pass = 0; pass < 14; pass++) for (var mob : herd) {
                mob.wildlife().think();
                watched |= mob.behavior() == BehaviorState.ALERT;
                h.assertTrue(mob.behavior() != BehaviorState.FLEE, "A herd bolted from a player twenty blocks off that each of them alone only watches");
            }
            h.assertTrue(watched, "No Parasaur looked up at a player twenty blocks off");
            herd[0].hurtServer(world, world.damageSources().playerAttack(player), 1);
            for (int pass = 0; pass < 8; pass++) for (var mob : herd) mob.wildlife().think();
            for (var mob : herd) h.assertTrue(mob.behavior() == BehaviorState.FLEE, "A Parasaur stayed when its herd mate was struck: " + mob.behavior());
            h.succeed();
        });
    }

    /** The reported kill: a Giganotosaurus among three Triceratops answers their blows, and gives ground only when hurt. */
    static void mobbed(GameTestHelper h) {
        floor(h);
        var world = h.getLevel();
        var giga = wild(h, Species.GIGANOTOSAURUS, new Vec3(64.5, 3, 64.5));
        var pack = UUID.randomUUID();
        var trikes = new CreatureEntity[3];
        for (int i = 0; i < 3; i++) {
            trikes[i] = wild(h, Species.TRICERATOPS, new Vec3(58.5 + i * 6, 3, 70.5));
            trikes[i].joinPack(pack); trikes[i].setNoAi(true);
            // A herd that outlasts the test: a kill would send the hunter to its meal.
            trikes[i].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);
            trikes[i].setHealth(1024);
        }
        h.runAfterDelay(20, () -> {
            giga.hurtServer(world, world.damageSources().mobAttack(trikes[1]), 8);
            for (int i = 0; i < 3; i++) giga.wildlife().think();
            h.assertTrue(giga.behavior() == BehaviorState.DEFEND && giga.getTarget() instanceof CreatureEntity c && c.species() == Species.TRICERATOPS,
                    "A Giganotosaurus struck by a Triceratops herd did not fight back: " + giga.behavior());
        });
        h.runAfterDelay(160, () -> {
            float left = 0;
            for (var trike : trikes) left += trike.getHealth();
            h.assertTrue(left < trikes[0].getMaxHealth() * 3, "A fighting Giganotosaurus never landed a bite on the herd");
            // Badly hurt among a herd that stands together, it gives ground.
            giga.setHealth(giga.getMaxHealth() * 0.4f);
            giga.invulnerableTime = 0;
            giga.hurtServer(world, world.damageSources().mobAttack(trikes[1]), 2);
            for (int i = 0; i < 8; i++) giga.wildlife().think();
            h.assertTrue(giga.behavior() == BehaviorState.FLEE, "A badly hurt Giganotosaurus stayed among the herd: " + giga.behavior());
        });
        // Struck again on the run: it cannot shake them, so it turns.
        h.runAfterDelay(170, () -> {
            giga.invulnerableTime = 0;
            giga.hurtServer(world, world.damageSources().mobAttack(trikes[1]), 8);
            giga.wildlife().think();
            h.assertTrue(giga.behavior() == BehaviorState.DEFEND && giga.wildlife().mind().reason() == WildlifeMind.Reason.CORNERED,
                    "A Giganotosaurus struck on the run did not turn: " + giga.behavior() + " " + giga.wildlife().mind().reason());
            h.succeed();
        });
    }

    private ReactionGameTests() {}
}
