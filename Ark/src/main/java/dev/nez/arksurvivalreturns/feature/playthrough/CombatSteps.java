package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The two measurements every stage ends with. A kill streak puts each species, smallest drawn body first, frozen in
 * front of the player, who strikes whenever the weapon's swing is full: hits, game ticks and damage until it dies or
 * the cap. A damage test roots each species in front of the player and lets it strike until the player has taken three
 * times their health (the blows are recorded and then cancelled, so nobody dies) or the window closes.
 */
public final class CombatSteps {
    /** Where the fights happen, from the run's origin: south of the workshop, clear of the Testing Ground's layout. */
    static final int LANE_X = 0, LANE_Z = 140;
    static final int KILL_CAP = 20 * 60, HIT_WINDOW = 20 * 30;
    static final float TAKEN_MULTIPLE = 3f;

    /** Land and amphibious species ordered by the size of their drawn body; the sea's need water and are left out. */
    public static List<Species> bySize(ServerLevel level) {
        var table = TestGroundBuilder.extents(level);
        var out = new ArrayList<Species>();
        for (var species : Species.values()) if (!species.aquatic()) out.add(species);
        out.sort(Comparator.comparingDouble(species -> TestGroundBuilder.extent(table, species).reach()));
        return out;
    }

    public static Step killStreak(String label, Species species, int creatureLevel) {
        return new Fight("kill " + species.id + " (" + label + ")", species, creatureLevel, true, label);
    }

    public static Step damageTest(String label, Species species, int creatureLevel) {
        return new Fight("hurt by " + species.id + " (" + label + ")", species, creatureLevel, false, label);
    }

    /** The first {@code limit} species by size, one step each, for either measurement. Resolved when the run reaches it. */
    public static Step all(String label, int creatureLevel, boolean kill, int limit) {
        return new Step() {
            List<Step> fights;
            int at;
            @Override public String name() { return (kill ? "kill streak " : "damage test ") + label; }
            @Override public int timeout() { return Integer.MAX_VALUE; }
            @Override public boolean tick(PlayRun run, int ticks) {
                if (fights == null) {
                    fights = new ArrayList<>();
                    for (var species : bySize(run.level()).subList(0, Math.min(limit, bySize(run.level()).size()))) fights.add(kill ? killStreak(label, species, creatureLevel) : damageTest(label, species, creatureLevel));
                }
                if (at >= fights.size()) return true;
                var fight = (Fight) fights.get(at);
                if (fight.tick(run, fight.ticks++) || fight.ticks > fight.timeout()) at++;
                return false;
            }
        };
    }

    static final class Fight implements Step {
        final String name, label;
        final Species species;
        final int creatureLevel;
        final boolean kill;
        int ticks;
        CreatureEntity creature;
        Vec3 spot;
        long started;
        int hits, blows;
        float dealt, raw, taken, healthBefore;
        long firstBlow = -1, lastBlow = -1;

        Fight(String name, Species species, int creatureLevel, boolean kill, String label) {
            this.name = name; this.species = species; this.creatureLevel = creatureLevel; this.kill = kill; this.label = label;
        }

        @Override public String name() { return name; }
        @Override public int timeout() { return (kill ? KILL_CAP : HIT_WINDOW) + 60; }

        @Override public boolean tick(PlayRun run, int ticks) {
            var level = run.level();
            var player = run.player();
            if (ticks == 0) {
                var body = TestGroundBuilder.extent(TestGroundBuilder.extents(level), species);
                spot = Vec3.atBottomCenterOf(run.at(LANE_X, 1, LANE_Z));
                creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.COMMAND);
                if (creature == null) return finish(run, "not_created");
                creature.initializeLevel(creatureLevel);
                creature.snapTo(spot.x, spot.y, spot.z, 0, 0);
                creature.yBodyRot = 0; creature.yHeadRot = 0;
                creature.setPersistenceRequired();
                creature.setNoAi(kill);
                if (!level.addFreshEntity(creature)) return finish(run, "refused");
                // In front of the snout: the body's front reach plus a step for the kill, just inside its bite for the test.
                double gap = body.front() + (kill ? 1.5 : 0.8);
                Verbs.tp(run, LANE_X, 0, LANE_Z + gap, 180, 10);
                healthBefore = creature.getHealth();
                started = level.getGameTime();
                player.setHealth(player.getMaxHealth());
                if (!kill) Playthrough.watch(this);
                return false;
            }
            if (creature.isRemoved() || creature.isDeadOrDying()) return finish(run, "dead");
            long elapsed = level.getGameTime() - started;
            if (kill) {
                if (elapsed > KILL_CAP) return finish(run, "cap");
                if (player.getAttackStrengthScale(0.5f) >= 1f) {
                    float before = creature.getHealth();
                    player.attack(creature);
                    player.swing(InteractionHand.MAIN_HAND, true);
                    hits++;
                    dealt += Math.max(0, before - creature.getHealth());
                }
                return false;
            }
            // Rooted: it turns and strikes but keeps its place; the player is struck back into reach.
            creature.setPos(spot.x, creature.getY(), spot.z);
            creature.getNavigation().stop();
            if (elapsed % 20 == 1) {
                creature.setTarget(player);
                if (blows == 0 && elapsed % 100 == 1) creature.hurtServer(level, level.damageSources().playerAttack(player), 0.01f);
            }
            if (taken >= TAKEN_MULTIPLE * player.getMaxHealth()) return finish(run, "taken");
            if (elapsed > HIT_WINDOW && (blows == 0 || level.getGameTime() - lastBlow > HIT_WINDOW / 2)) return finish(run, blows == 0 ? "no_blow" : "stopped");
            return false;
        }

        /** Called by the damage hooks while this test watches the player. */
        void incoming(float amount) { raw += amount; }

        void struck(PlayRun run, float amount) {
            blows++;
            taken += amount;
            long now = run.level().getGameTime();
            if (firstBlow < 0) firstBlow = now;
            lastBlow = now;
        }

        private boolean finish(PlayRun run, String end) {
            Playthrough.watch(null);
            var level = run.level();
            var player = run.player();
            var event = run.event(kill ? "kill_streak" : "damage_test");
            event.addProperty("label", label);
            event.addProperty("species", species.id);
            event.addProperty("creature_level", creatureLevel);
            event.addProperty("end", end);
            event.addProperty("ticks", level.getGameTime() - started);
            event.addProperty("weapon", Verbs.id(player.getMainHandItem().getItem()));
            event.addProperty("armor", player.getAttributeValue(Attributes.ARMOR));
            event.addProperty("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            event.addProperty("player_health", player.getMaxHealth());
            if (creature != null) {
                event.addProperty("creature_health", healthBefore);
                event.addProperty("health_left", Math.max(0, creature.getHealth()));
                event.addProperty("knocked_out", creature.torpor().restricted());
            }
            if (kill) {
                event.addProperty("hits", hits);
                event.addProperty("dealt", dealt);
                var weapon = player.getMainHandItem();
                if (weapon.isDamageableItem()) event.addProperty("durability_left", weapon.getMaxDamage() - weapon.getDamageValue());
            } else {
                event.addProperty("blows", blows);
                event.addProperty("raw", raw);
                event.addProperty("taken", taken);
                event.addProperty("first_blow", firstBlow < 0 ? -1 : firstBlow - started);
                event.addProperty("mean_gap", blows > 1 ? (double) (lastBlow - firstBlow) / (blows - 1) : -1);
            }
            run.write(event);
            if (creature != null && !creature.isRemoved()) creature.discard();
            for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(spot, spot).inflate(24))) item.discard();
            player.setHealth(player.getMaxHealth());
            return true;
        }
    }

    private CombatSteps() {}
}
