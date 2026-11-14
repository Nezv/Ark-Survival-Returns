package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The measurements. A kill streak puts each species, smallest drawn body first, frozen in front of the player, who
 * strikes whenever the weapon's swing is full; a damage test roots each species in front of the player and lets it
 * strike (the blows are recorded and then cancelled, so nobody dies). Neither waits for the whole thing: a kill
 * streak swings for {@link #KILL_SAMPLE} ticks and a damage test takes {@link #SAMPLE_BLOWS} blows, then each
 * projects what is left (hits and seconds to kill, blows and seconds to take three times the player's health) from
 * the measured rate, and says so ({@code end: projected}). Sea species are measured in a pool, the player at its
 * surface. {@link #fights} sets creatures on each other.
 */
public final class CombatSteps {
    /** Where the fights happen, from the run's origin: south of the camp; the pool east of the yard; the fight field far south. */
    static final int LANE_X = 0, LANE_Z = 140, POOL_X = 90, POOL_Z = 140, FIELD_X = 0, FIELD_Z = 320;
    /** Ticks of swinging before a kill is projected; ticks a creature may take for its first blow; blows sampled. */
    static final int KILL_SAMPLE = 20 * 8, FIRST_BLOW = 20 * 6, HIT_WINDOW = 20 * 15, SAMPLE_BLOWS = 3, FIGHT_CAP = 20 * 25;
    static final float TAKEN_MULTIPLE = 3f;

    /** Every species by the size of its drawn body; the sea's are measured in the pool. */
    public static List<Species> bySize(ServerLevel level) {
        var table = TestGroundBuilder.extents(level);
        var out = new ArrayList<>(List.of(Species.values()));
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
        return Sweeps.sequence((kill ? "kill streak " : "damage test ") + label, run -> {
            var steps = new ArrayList<Step>();
            var species = bySize(run.level());
            for (var one : species.subList(0, Math.min(limit, species.size())))
                steps.add(kill ? killStreak(label, one, creatureLevel) : damageTest(label, one, creatureLevel));
            return steps;
        });
    }

    // ------------------------------------------------------------------ where

    /**
     * Where a creature of this species stands for a measurement: on the lane, or for a sea species in the pool, which is
     * dug and filled as large as the biggest body so far needs (water only added, never taken away).
     */
    static Vec3 spot(PlayRun run, Species species, TestGroundBuilder.Extent body) {
        if (!species.aquatic()) return Vec3.atBottomCenterOf(run.at(LANE_X, 1, LANE_Z));
        var level = run.level();
        var centre = run.at(POOL_X, 0, POOL_Z);
        int radius = (int) Math.ceil(Math.min(26, body.reach() + 3)), depth = (int) Math.ceil(Math.min(16, body.height() + 3));
        var water = Blocks.WATER.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                for (int dy = -depth + 1; dy <= 0; dy++) {
                    var pos = centre.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(Blocks.WATER)) level.setBlock(pos, water, 2);
                }
        double top = centre.getY() + 1 - body.height() - 0.3;
        return new Vec3(centre.getX() + 0.5, Math.max(centre.getY() - depth + 1, top), centre.getZ() + 0.5);
    }

    /** The player's stand in front of the snout: on the lane, or floating at the pool's surface (head out, breathing). */
    static Vec3 stand(PlayRun run, Species species, Vec3 spot, double gap) {
        double y = species.aquatic() ? run.origin.getY() + 0.4 : spot.y;
        return new Vec3(spot.x, y, spot.z + gap);
    }

    static void hold(PlayRun run, Vec3 stand, Vec3 look) {
        var player = run.player();
        double dx = look.x - stand.x, dz = look.z - stand.z, dy = look.y - (stand.y + player.getEyeHeight());
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        if (player.position().distanceToSqr(stand) > 0.06 || Math.abs(player.getYRot() - yaw) > 1)
            player.teleportTo(run.level(), stand.x, stand.y, stand.z, java.util.Set.of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.setAirSupply(player.getMaxAirSupply());
    }

    static CreatureEntity spawn(PlayRun run, Species species, int creatureLevel, Vec3 at, boolean ai) {
        var level = run.level();
        var creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.COMMAND);
        if (creature == null) return null;
        creature.initializeLevel(creatureLevel);
        creature.snapTo(at.x, at.y, at.z, 0, 0);
        creature.yBodyRot = 0; creature.yHeadRot = 0;
        creature.setPersistenceRequired();
        creature.setNoAi(!ai);
        return level.addFreshEntity(creature) ? creature : null;
    }

    static void clear(PlayRun run, CreatureEntity creature, Vec3 around) {
        if (creature != null && !creature.isRemoved()) creature.discard();
        for (var item : run.level().getEntitiesOfClass(ItemEntity.class, new AABB(around, around).inflate(32))) item.discard();
        for (var arrow : run.level().getEntitiesOfClass(AbstractArrow.class, new AABB(around, around).inflate(32))) arrow.discard();
    }

    // ------------------------------------------------------------------ kill streak and damage test

    static final class Fight implements Step {
        final String name, label;
        final Species species;
        final int creatureLevel;
        final boolean kill;
        CreatureEntity creature;
        Vec3 spot, stand;
        long started, xpBefore;
        int hits, blows;
        float dealt, raw, taken, healthBefore;
        long firstBlow = -1, lastBlow = -1;
        boolean done;

        Fight(String name, Species species, int creatureLevel, boolean kill, String label) {
            this.name = name; this.species = species; this.creatureLevel = creatureLevel; this.kill = kill; this.label = label;
        }

        @Override public String name() { return name; }
        @Override public int timeout() { return (kill ? KILL_SAMPLE : HIT_WINDOW) + 100; }

        @Override public boolean tick(PlayRun run, int ticks) {
            var level = run.level();
            var player = run.player();
            if (ticks == 0) {
                var body = TestGroundBuilder.extent(TestGroundBuilder.extents(level), species);
                spot = spot(run, species, body);
                creature = spawn(run, species, creatureLevel, spot, !kill);
                if (creature == null) return finish(run, "not_created");
                // In front of the snout: the body's front reach plus a step for the kill, just inside its bite for the test.
                stand = stand(run, species, spot, body.front() + (kill ? 1.5 : 0.8));
                hold(run, stand, spot.add(0, body.height() / 2, 0));
                healthBefore = creature.getHealth();
                started = level.getGameTime();
                xpBefore = ArkLevels.totalXp(ArkLevels.get(player));
                player.setHealth(player.getMaxHealth());
                run.busy = true;
                if (!kill) Playthrough.watch(this);
                return false;
            }
            if (creature.isRemoved() || creature.isDeadOrDying()) return finish(run, "dead");
            hold(run, stand, creature.position().add(0, creature.getBbHeight() / 2, 0));
            long elapsed = level.getGameTime() - started;
            if (kill) {
                if (elapsed > KILL_SAMPLE) return finish(run, hits == 0 ? "no_hit" : "projected");
                if (player.getAttackStrengthScale(0.5f) >= 1f) {
                    float before = creature.getHealth();
                    player.attack(creature);
                    player.swing(InteractionHand.MAIN_HAND, true);
                    hits++;
                    dealt += Math.max(0, before - creature.getHealth());
                }
                return false;
            }
            // Rooted: it turns and strikes but keeps its place; the player is held in front of it, knockback undone.
            creature.setPos(spot.x, creature.getY(), spot.z);
            creature.getNavigation().stop();
            if (elapsed % 20 == 1) {
                creature.setTarget(player);
                if (blows == 0 && elapsed % 100 == 1) creature.hurtServer(level, level.damageSources().playerAttack(player), 0.01f);
            }
            if (taken >= TAKEN_MULTIPLE * player.getMaxHealth()) return finish(run, "taken");
            if (blows >= SAMPLE_BLOWS) return finish(run, "projected");
            if (blows == 0 && elapsed > FIRST_BLOW) return finish(run, "no_blow");
            if (elapsed > HIT_WINDOW) return finish(run, "window");
            return false;
        }

        /** Called by the damage hooks for a blow of this test's creature on the player. */
        void incoming(float amount) { raw += amount; }

        void struck(PlayRun run, float amount) {
            blows++;
            taken += amount;
            long now = run.level().getGameTime();
            if (firstBlow < 0) firstBlow = now;
            lastBlow = now;
        }

        boolean from(net.minecraft.world.entity.Entity attacker) { return creature != null && attacker == creature; }

        @Override public void abandon(PlayRun run) { finish(run, "timeout"); }

        private boolean finish(PlayRun run, String end) {
            if (done) return true;
            done = true;
            Playthrough.watch(null);
            run.busy = false;
            var level = run.level();
            var player = run.player();
            var event = run.event(kill ? "kill_streak" : "damage_test");
            long ticks = level.getGameTime() - started;
            event.addProperty("label", label);
            event.addProperty("species", species.id);
            event.addProperty("carnivore", species.predator);
            event.addProperty("creature_level", creatureLevel);
            event.addProperty("end", end);
            event.addProperty("ticks", ticks);
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
                event.addProperty("xp", ArkLevels.totalXp(ArkLevels.get(player)) - xpBefore);
                if (end.equals("dead")) { event.addProperty("hits_to_kill", hits); event.addProperty("seconds_to_kill", ticks / 20.0); }
                else if (hits > 0 && dealt > 0) {
                    // What is left, at the rate measured: the hits and the seconds the whole kill would take.
                    float left = Math.max(0, creature == null ? 0 : creature.getHealth());
                    int total = hits + (int) Math.ceil(left / (dealt / hits));
                    event.addProperty("hits_to_kill", total);
                    event.addProperty("seconds_to_kill", ticks / 20.0 * total / hits);
                }
                var weapon = player.getMainHandItem();
                if (weapon.isDamageableItem()) event.addProperty("durability_left", weapon.getMaxDamage() - weapon.getDamageValue());
            } else {
                event.addProperty("blows", blows);
                event.addProperty("raw", raw);
                event.addProperty("taken", taken);
                event.addProperty("first_blow", firstBlow < 0 ? -1 : firstBlow - started);
                double gap = blows > 1 ? (double) (lastBlow - firstBlow) / (blows - 1) : -1;
                event.addProperty("mean_gap", gap);
                if (blows > 0 && taken > 0) {
                    int total = (int) Math.ceil(TAKEN_MULTIPLE * player.getMaxHealth() / (taken / blows));
                    event.addProperty("blows_to_three_lives", total);
                    if (gap > 0) event.addProperty("seconds_to_three_lives", ((firstBlow - started) + gap * (total - 1)) / 20.0);
                }
            }
            run.write(event);
            clear(run, creature, spot == null ? Vec3.atCenterOf(run.origin) : spot);
            player.setHealth(player.getMaxHealth());
            return true;
        }
    }

    // ------------------------------------------------------------------ ranged

    /**
     * A ranged weapon tried the way a player shoots it: the ammunition put in the off hand (the inventory screen's
     * off-hand swap), the weapon drawn by holding its use and loosed by letting go (a crossbow charged, then fired by a
     * second use), aimed at a frozen creature ten blocks off. Hits, damage and the torpor it gave, then projected.
     */
    public static Step shoot(String label, String weapon, String ammo, Species species, int creatureLevel) {
        return new Step() {
            CreatureEntity creature;
            Vec3 spot, stand;
            long started;
            int shots, drawn, timer;
            float healthBefore;
            boolean done, held, wasCharged;
            @Override public String name() { return "shoot " + species.id + " with " + weapon + " and " + ammo; }
            @Override public int timeout() { return KILL_SAMPLE * 2 + 100; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                if (ticks == 0) {
                    var body = TestGroundBuilder.extent(TestGroundBuilder.extents(level), species);
                    spot = spot(run, species, body);
                    creature = spawn(run, species, creatureLevel, spot, false);
                    if (creature == null) return finish(run, "not_created");
                    stand = stand(run, species, spot, body.front() + 10);
                    healthBefore = creature.getHealth();
                    started = level.getGameTime();
                    run.busy = true;
                    if (!offHand(player, ammo)) return finish(run, "no_ammo");
                    if (!Verbs.select(player, weapon)) return finish(run, "no_weapon");
                    return false;
                }
                if (creature.isRemoved() || creature.isDeadOrDying()) return finish(run, "dead");
                hold(run, stand, creature.position().add(0, creature.getBbHeight() * 0.6, 0));
                if (level.getGameTime() - started > KILL_SAMPLE * 2) return finish(run, shots == 0 ? "no_shot" : "projected");
                var weaponStack = player.getMainHandItem();
                boolean crossbow = weaponStack.getItem() instanceof CrossbowItem;
                timer++;
                if (!held) {
                    if (timer < 4) return false;   // the key stays up long enough for the client to see it
                    if (player.getOffhandItem().isEmpty() && !(crossbow && CrossbowItem.isCharged(weaponStack)) && !offHand(player, ammo)) return finish(run, "out_of_ammo");
                    // The use key goes down: a bow starts to draw, a crossbow to charge, a charged crossbow fires.
                    wasCharged = crossbow && CrossbowItem.isCharged(weaponStack);
                    Verbs.useKey(run, true);
                    held = true;
                    drawn = timer = 0;
                    return false;
                }
                boolean using = player.isUsingItem(), release;
                if (wasCharged) release = !CrossbowItem.isCharged(weaponStack) || timer > 40;
                else if (crossbow) release = using && ++drawn >= CrossbowItem.getChargeDuration(weaponStack, player) + 3 || timer > 120;
                else release = using && ++drawn >= 22 || timer > 80;
                if (release) {
                    // Let go: the bow looses its arrow; the crossbow keeps its charge for the next press, or has just fired.
                    if (wasCharged ? !CrossbowItem.isCharged(weaponStack) : !crossbow && using && drawn >= 22) shots++;
                    Verbs.useKey(run, false);
                    held = false;
                    timer = 0;
                }
                return false;
            }
            @Override public void abandon(PlayRun run) { finish(run, "timeout"); }
            private boolean finish(PlayRun run, String end) {
                if (done) return true;
                done = true;
                run.busy = false;
                var player = run.player();
                if (held) Verbs.useKey(run, false);
                player.stopUsingItem();
                long ticks = run.level().getGameTime() - started;
                var event = run.event("ranged");
                event.addProperty("label", label);
                event.addProperty("weapon", weapon);
                event.addProperty("ammo", ammo);
                event.addProperty("species", species.id);
                event.addProperty("creature_level", creatureLevel);
                event.addProperty("end", end);
                event.addProperty("shots", shots);
                event.addProperty("ticks", ticks);
                if (creature != null) {
                    float dealt = healthBefore - Math.max(0, creature.getHealth());
                    event.addProperty("creature_health", healthBefore);
                    event.addProperty("dealt", dealt);
                    event.addProperty("torpor", creature.torpor().torpor());
                    event.addProperty("torpor_max", creature.torpor().maximum());
                    event.addProperty("knocked_out", creature.torpor().restricted());
                    if (end.equals("dead")) event.addProperty("shots_to_kill", shots);
                    else if (shots > 0 && dealt > 0) event.addProperty("shots_to_kill", (int) Math.ceil(healthBefore / (dealt / shots)));
                }
                run.write(event);
                clear(run, creature, spot == null ? Vec3.atCenterOf(run.origin) : spot);
                return true;
            }
        };
    }

    /** The ammunition swapped into the off hand from the bags, the inventory screen's off-hand key. */
    static boolean offHand(net.minecraft.server.level.ServerPlayer player, String ammo) {
        var wanted = Verbs.item(ammo);
        if (player.getOffhandItem().is(wanted)) return true;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++)
            if (inventory.getItem(slot).is(wanted)) {
                player.closeContainer();
                int menu = slot < 9 ? 36 + slot : slot;
                player.inventoryMenu.clicked(menu, 40, ContainerInput.SWAP, player);
                player.inventoryMenu.broadcastChanges();
                return player.getOffhandItem().is(wanted);
            }
        return false;
    }

    // ------------------------------------------------------------------ creature against creature

    /**
     * Every species set on the next bigger one, land on the field and sea in the pool, at one level: each pair kept at
     * each other until one dies or the cap (then the one with more of its health left wins on points). The player
     * watches from the air, out of the way.
     */
    public static Step fights(int creatureLevel) {
        return Sweeps.sequence("fights", run -> {
            var steps = new ArrayList<Step>();
            var land = new ArrayList<Species>();
            var sea = new ArrayList<Species>();
            for (var species : bySize(run.level())) (species.aquatic() ? sea : land).add(species);
            for (var ladder : List.of(land, sea))
                for (int i = 0; i + 1 < ladder.size(); i++) steps.add(fight(ladder.get(i), ladder.get(i + 1), creatureLevel));
            return steps;
        });
    }

    static Step fight(Species one, Species two, int creatureLevel) {
        return new Step() {
            CreatureEntity a, b;
            float healthA, healthB;
            long started;
            Vec3 centre;
            boolean done;
            @Override public String name() { return "fight " + one.id + " v " + two.id; }
            @Override public int timeout() { return FIGHT_CAP + 100; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var level = run.level();
                if (ticks == 0) {
                    var table = TestGroundBuilder.extents(level);
                    var bodyA = TestGroundBuilder.extent(table, one);
                    var bodyB = TestGroundBuilder.extent(table, two);
                    centre = one.aquatic() ? spot(run, two, bodyB) : Vec3.atBottomCenterOf(run.at(FIELD_X, 1, FIELD_Z));
                    double apart = (bodyA.front() + bodyB.front()) / 2 + 2;
                    a = spawn(run, one, creatureLevel, centre.add(0, 0, -apart), true);
                    b = spawn(run, two, creatureLevel, centre.add(0, 0, apart), true);
                    if (a == null || b == null) return finish(run, "not_created");
                    healthA = a.getHealth();
                    healthB = b.getHealth();
                    started = level.getGameTime();
                    var o = run.origin;
                    var player = run.player();
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, centre.x + 30, o.getY() + 18, centre.z, java.util.Set.of(), 90, 30, true);
                    return false;
                }
                if (!a.isAlive() || !b.isAlive()) return finish(run, "dead");
                if (level.getGameTime() - started > FIGHT_CAP) return finish(run, "cap");
                if (ticks % 10 == 0) {
                    a.setLastHurtByMob(b); b.setLastHurtByMob(a);
                    a.setTarget(b); b.setTarget(a);
                    if (a.distanceTo(b) > (a.getBbWidth() + b.getBbWidth()) / 2 + 3) {
                        a.getNavigation().moveTo(b, 1.2);
                        b.getNavigation().moveTo(a, 1.2);
                    }
                }
                return false;
            }
            @Override public void abandon(PlayRun run) { finish(run, "timeout"); }
            private boolean finish(PlayRun run, String end) {
                if (done) return true;
                done = true;
                var event = run.event("fight");
                event.addProperty("a", one.id);
                event.addProperty("b", two.id);
                event.addProperty("creature_level", creatureLevel);
                event.addProperty("end", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                if (a != null && b != null) {
                    float leftA = a.isAlive() ? a.getHealth() / healthA : 0, leftB = b.isAlive() ? b.getHealth() / healthB : 0;
                    event.addProperty("a_left", leftA);
                    event.addProperty("b_left", leftB);
                    event.addProperty("a_dealt", healthB - Math.max(0, b.getHealth()));
                    event.addProperty("b_dealt", healthA - Math.max(0, a.getHealth()));
                    event.addProperty("winner", leftA == leftB ? "" : leftA > leftB ? one.id : two.id);
                }
                run.write(event);
                clear(run, a, centre == null ? Vec3.atCenterOf(run.origin) : centre);
                clear(run, b, centre == null ? Vec3.atCenterOf(run.origin) : centre);
                return true;
            }
        };
    }

    private CombatSteps() {}
}
