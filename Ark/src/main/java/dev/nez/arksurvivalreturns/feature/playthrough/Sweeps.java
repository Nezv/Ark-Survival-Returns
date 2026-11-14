package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.companion.CompanionCommand;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.companion.CompanionWhistle;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.taming.TamingMethod;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ProjectileWeaponItem;
import dev.nez.arksurvivalreturns.feature.drop.LootCrateBlockEntity;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import dev.nez.arksurvivalreturns.feature.station.StationBlock;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import dev.nez.arksurvivalreturns.feature.tech.TechTree;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlock;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The sweeps after the stages, so that at the end of a run every recipe, block, screen, armour set, weapon and supply
 * drop tier has been through the game once: what each gave is in the events file, nothing is judged here.
 */
public final class Sweeps {
    static final String NS = ArkSurvivalReturns.MOD_ID;
    /** The wall of blocks, from the run's origin: west of the fight lane. */
    static final int WALL_X = -70, WALL_Z = 120, WALL_ROW = 16;

    /** Steps planned when the run reaches them, from the world as it is then; ticked one after another. */
    public static Step sequence(String name, Function<PlayRun, List<Step>> plan) {
        return new Step() {
            List<Step> steps;
            int at, ticks;
            boolean between = true;
            @Override public String name() { return name; }
            @Override public int timeout() { return Integer.MAX_VALUE; }
            @Override public boolean tick(PlayRun run, int outer) {
                if (steps == null) steps = plan.apply(run);
                if (at >= steps.size()) return true;
                // One idle tick between steps: the run may eat here, never in the middle of one.
                if (between) { between = false; run.safe = true; return false; }
                run.safe = false;
                if (Step.drive(run, steps.get(at), ticks++)) { at++; ticks = 0; between = true; }
                return false;
            }
            @Override public void abandon(PlayRun run) { if (steps != null && at < steps.size()) steps.get(at).abandon(run); }
        };
    }

    // ------------------------------------------------------------------ recipes

    /**
     * Every Ark item a workstation makes, and the Armoury's gear, earned once through the economy like any other goal;
     * the crates keep them. Vanilla's decoration blocks are left out: the recipe tree is Ark's.
     */
    public static Step recipes() { return recipes(false); }

    /** {@code all}: every item any bench makes, vanilla's too (the whole recipe tree; what has no way in ends blocked). */
    public static Step recipes(boolean all) {
        return sequence("recipe sweep", run -> {
            var steps = new ArrayList<Step>();
            var items = new java.util.LinkedHashSet<String>();
            for (var station : WorkstationCatalog.all().values())
                for (var craft : station.crafts())
                    if (!craft.variant().apply() && BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(craft.variant().item()))
                            && (all || craft.variant().item().startsWith(NS + ":") || station.station().endsWith(":armoury"))) items.add(craft.variant().item());
            for (String item : items) steps.add(Gathering.obtain(item, 1));
            return steps;
        });
    }

    // ------------------------------------------------------------------ blocks

    static List<Block> arkBlocks() {
        var out = new ArrayList<Block>();
        for (var block : BuiltInRegistries.BLOCK)
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(NS) && block.asItem() != net.minecraft.world.item.Items.AIR) out.add(block);
        return out;
    }

    /**
     * Every Ark block put on a wall, pictured from above and from the front, then broken with the bronze tools earned
     * in the stages. The wall is the test's, not the player's: its drops are written down and taken out of the bags.
     */
    public static Step blocks() {
        return sequence("block sweep", run -> {
            var steps = new ArrayList<Step>();
            var blocks = arkBlocks();
            for (String tool : List.of("bronze_pickaxe", "bronze_axe", "bronze_shovel")) steps.add(Gathering.obtain(NS + ":" + tool, 1));
            steps.add(Step.of("place the wall", run2 -> {
                for (int i = 0; i < blocks.size(); i++) {
                    var pos = wall(run2, i);
                    run2.level().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                    run2.level().setBlockAndUpdate(pos, blocks.get(i).defaultBlockState());
                }
            }));
            steps.add(Verbs.waitTicks(20));
            steps.add(Step.of("check the wall", run2 -> {
                var fell = new JsonArray();
                for (int i = 0; i < blocks.size(); i++) if (!run2.level().getBlockState(wall(run2, i)).is(blocks.get(i))) fell.add(Verbs.id(blocks.get(i)));
                var event = run2.event("block_wall");
                event.addProperty("blocks", blocks.size());
                event.add("did_not_stay", fell);
                event.addProperty("supplied", "test wall");
                run2.write(event);
            }));
            int rows = (blocks.size() + WALL_ROW - 1) / WALL_ROW;
            // From above the player flies (a survival player sent up without it fell to their death), then lands.
            steps.add(Verbs.hover(WALL_X + WALL_ROW * 1.5, 30, WALL_Z + rows * 1.5, 180, 90));
            steps.add(Verbs.shot("blocks_above", 60));
            steps.add(Verbs.land(WALL_X + WALL_ROW * 1.5, WALL_Z - 10));
            steps.add(Verbs.teleport(WALL_X + WALL_ROW * 1.5, 0, WALL_Z - 10, 0, 15));
            steps.add(Verbs.shot("blocks_front", 60));
            for (int i = 0; i < blocks.size(); i++) {
                int index = i;
                steps.add(new Step() {
                    Map<String, Integer> before;
                    @Override public String name() { return "break " + Verbs.id(blocks.get(index)); }
                    @Override public boolean tick(PlayRun run2, int ticks) {
                        var pos = wall(run2, index);
                        var player = run2.player();
                        if (ticks == 0) {
                            Verbs.tp(run2, pos.getX() - run2.origin.getX(), 0, pos.getZ() - run2.origin.getZ() - 2, 0, 30);
                            before = Verbs.counts(player);
                            var state = run2.level().getBlockState(pos);
                            Gathering.bestTool(player, state);
                            var event = run2.event("block");
                            event.addProperty("block", Verbs.id(blocks.get(index)));
                            event.addProperty("present", state.is(blocks.get(index)));
                            event.addProperty("broken", !state.isAir() && player.gameMode.destroyBlock(pos));
                            event.addProperty("tool", Verbs.id(player.getMainHandItem().getItem()));
                            run2.write(event);
                            return false;
                        }
                        Gathering.pullDrops(run2, pos, 2);
                        if (ticks < 8) return false;
                        var event = run2.event("block_drops");
                        event.addProperty("block", Verbs.id(blocks.get(index)));
                        var gained = Verbs.difference(before, Verbs.counts(player));
                        event.add("gained", gained);
                        run2.write(event);
                        // The wall was put there by the test, not earned: what it dropped leaves the bags again.
                        for (var entry : gained.entrySet()) if (entry.getValue().getAsInt() > 0)
                            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(Verbs.item(entry.getKey())), entry.getValue().getAsInt(), player.inventoryMenu.getCraftSlots());
                        return true;
                    }
                });
            }
            return steps;
        });
    }

    static BlockPos wall(PlayRun run, int i) { return run.at(WALL_X + (i % WALL_ROW) * 3, 1, WALL_Z + (i / WALL_ROW) * 3); }

    // ------------------------------------------------------------------ screens

    /** Every Ark block with a screen opened by a click and pictured, then the screens only the client opens. */
    public static Step screens() {
        return sequence("screen sweep", run -> {
            var steps = new ArrayList<Step>();
            for (var block : arkBlocks()) {
                if (!opens(run, block)) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
                steps.add(Step.of("open " + id, run2 -> {
                    var player = run2.player();
                    player.closeContainer();
                    var pos = run2.at(-20, 1, 100);
                    Verbs.tp(run2, -20, 0, 102.5, 180, 25);
                    run2.level().setBlockAndUpdate(pos, block.defaultBlockState());
                    if (run2.level().getBlockEntity(pos) instanceof SpecimenPostBlockEntity post) post.configure(Species.PARASAUR, 0, 1, SpecimenPostBlockEntity.Mode.FROZEN, 50, false);
                    run2.level().getBlockState(pos).useWithoutItem(run2.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                    var event = run2.event("screen_open");
                    event.addProperty("block", id);
                    event.addProperty("menu", player.containerMenu == player.inventoryMenu ? "" : player.containerMenu.getClass().getSimpleName());
                    run2.write(event);
                }));
                steps.add(Verbs.shot("screen_" + id, 15));
                steps.add(Step.of("close " + id, run2 -> { run2.player().closeContainer(); run2.level().removeBlock(run2.at(-20, 1, 100), false); }));
                steps.add(Verbs.client("screen:close", 2));
            }
            for (String screen : List.of("inventory", "tech")) {
                steps.add(Verbs.client("screen:" + screen, 5));
                steps.add(Verbs.shot("screen_" + screen, 15));
                steps.add(Verbs.client("screen:close", 2));
            }
            return steps;
        });
    }

    private static boolean opens(PlayRun run, Block block) {
        if (block instanceof StationBlock || block instanceof SpecimenPostBlock) return true;
        if (!(block instanceof EntityBlock entity)) return false;
        try { return entity.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof MenuProvider; }
        catch (RuntimeException e) { return false; }
    }

    // ------------------------------------------------------------------ armour and weapons

    /** Armour sets by name (keratin, bronze, ... and hide), each piece in its slot. */
    static Map<String, List<String>> armourSets() {
        var sets = new LinkedHashMap<String, List<String>>();
        for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            boolean hide = key.getNamespace().equals("minecraft") && key.getPath().startsWith("leather_") && !key.getPath().contains("horse");
            if (!key.getNamespace().equals(NS) && !hide) continue;
            var equippable = new ItemStack(item).get(DataComponents.EQUIPPABLE);
            if (equippable == null) continue;
            var slot = equippable.slot();
            if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) continue;
            String path = key.getPath();
            int cut = path.lastIndexOf('_');
            sets.computeIfAbsent(cut > 0 ? path.substring(0, cut) : path, k -> new ArrayList<>()).add(key.toString());
        }
        return sets;
    }

    /** Each set earned, worn and pictured from the front in third person; its armour points written down. */
    public static Step armour() {
        return sequence("armour sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.teleport(-40, 0, 100, 180, 0));
            steps.add(Verbs.client("camera:third", 5));
            for (var set : armourSets().entrySet()) {
                for (String piece : set.getValue()) steps.add(Gathering.obtain(piece, 1));
                steps.add(Verbs.unequip());
                steps.add(Verbs.equip(set.getValue().toArray(String[]::new)));
                steps.add(Verbs.shot("armour_" + set.getKey(), 30));
            }
            steps.add(Verbs.unequip());
            steps.add(Verbs.client("camera:first", 5));
            return steps;
        });
    }

    /** Ark items, and the Armoury's, that hit harder than a fist. */
    static List<String> weapons(PlayRun run) {
        var out = new ArrayList<String>();
        var player = run.player();
        var held = player.getMainHandItem().copy();
        var armoury = armoury();
        for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(NS) && !armoury.contains(key.toString())) continue;
            if (item instanceof ProjectileWeaponItem) continue;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            if (player.getAttributeValue(Attributes.ATTACK_DAMAGE) > 1.5) out.add(key.toString());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return out;
    }

    static java.util.Set<String> armoury() {
        var out = new java.util.HashSet<String>();
        for (var station : WorkstationCatalog.all().values())
            if (station.station().endsWith(":armoury")) for (var craft : station.crafts()) out.add(craft.variant().item());
        return out;
    }

    /** The Armoury's bows and crossbows, and its arrows. */
    static final List<String> RANGED = List.of("minecraft:bow", "minecraft:crossbow");
    static final List<String> AMMO = List.of("minecraft:arrow", NS + ":tranquilizer_arrow", NS + ":improved_tranquilizer_arrow", NS + ":explosive_arrow");

    /**
     * Every melee weapon earned and swung at the same frozen Triceratops (one row of time to kill each), then every bow
     * and crossbow earned and shot with each arrow: damage and torpor per shot.
     */
    public static Step weapons() {
        return sequence("weapon sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.unequip());
            for (String weapon : weapons(run)) {
                steps.add(Gathering.obtain(weapon, 1));
                steps.add(Verbs.wield(weapon));
                steps.add(CombatSteps.killStreak("weapon sweep " + weapon.substring(weapon.indexOf(':') + 1), Species.TRICERATOPS, 1));
            }
            for (String bow : RANGED) {
                steps.add(Gathering.obtain(bow, 1));
                for (String ammo : AMMO) {
                    steps.add(Gathering.obtain(ammo, 16));
                    steps.add(CombatSteps.shoot("weapon sweep", bow, ammo, Species.TRICERATOPS, 1));
                }
            }
            return steps;
        });
    }

    // ------------------------------------------------------------------ supply drops

    /** One real drop of each tier called down on the player: its fall, its crate opened and pictured, its contents. */
    public static Step drops() {
        return sequence("drop sweep", run -> {
            var steps = new ArrayList<Step>();
            for (var tier : SupplyTier.values()) {
                steps.add(Verbs.teleport(0, 0, 200 + tier.ordinal() * 40, 180, 0));
                steps.add(Verbs.command("execute as " + run.player().getScoreboardName() + " at @s run arkdrop " + tier.id));
                steps.add(new Step() {
                    long called;
                    @Override public String name() { return "drop " + tier.id; }
                    @Override public int timeout() { return 20 * 60 * 4; }
                    @Override public boolean tick(PlayRun run2, int ticks) {
                        if (ticks == 0) { called = run2.level().getGameTime(); return false; }
                        if (ticks % 20 != 0) return false;
                        var crate = crate(run2);
                        if (crate == null) return false;
                        var pos = crate.getBlockPos();
                        var player = run2.player();
                        Verbs.tp(run2, pos.getX() - run2.origin.getX(), pos.getY() - run2.origin.getY() - 1, pos.getZ() - run2.origin.getZ() + 2.5, 180, 30);
                        run2.level().getBlockState(pos).useWithoutItem(run2.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                        var items = new LinkedHashMap<String, Integer>();
                        for (int i = 0; i < crate.getContainerSize(); i++)
                            if (!crate.getItem(i).isEmpty()) items.merge(Verbs.id(crate.getItem(i).getItem()), crate.getItem(i).getCount(), Integer::sum);
                        var event = run2.event("drop");
                        event.addProperty("tier", tier.id);
                        event.addProperty("landed_after", run2.level().getGameTime() - called);
                        event.addProperty("crate_tier", crate.tier().id);
                        event.addProperty("menu", player.containerMenu.getClass().getSimpleName());
                        event.add("items", Verbs.json(items));
                        run2.write(event);
                        return true;
                    }
                });
                steps.add(Verbs.shot("drop_" + tier.id, 20));
                steps.add(Step.of("clear drop " + tier.id, run2 -> {
                    run2.player().closeContainer();
                    var crate = crate(run2);
                    if (crate != null) { crate.clearContent(); run2.level().removeBlock(crate.getBlockPos(), false); }
                }));
            }
            return steps;
        });
    }

    static LootCrateBlockEntity crate(PlayRun run) {
        var centre = run.player().blockPosition();
        LootCrateBlockEntity best = null;
        for (int cx = (centre.getX() >> 4) - 6; cx <= (centre.getX() >> 4) + 6; cx++)
            for (int cz = (centre.getZ() >> 4) - 6; cz <= (centre.getZ() >> 4) + 6; cz++) {
                var chunk = run.level().getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values())
                    if (entity instanceof LootCrateBlockEntity crate && (best == null || crate.getBlockPos().distSqr(centre) < best.getBlockPos().distSqr(centre))) best = crate;
            }
        return best;
    }

    // ------------------------------------------------------------------ taming and the tech tree

    /**
     * A wild creature tamed the way a player tames it: a knock-out species sedated by swinging the player's own
     * Narcotics (each blow a dose), then fed by hand while it sleeps and dosed by hand when it starts to wake; a passive
     * one fed by hand while awake. It is rooted in place, out of its reach, so the player is never struck.
     */
    public static Step tame(Species species, int creatureLevel, String food, String sedative) { return tame(species, creatureLevel, food, sedative, false); }

    /**
     * A tame with what it takes earned first: food for the meals, and for a knock-out species twice the doses its torpor
     * holds (to put it under and keep it there), worked out from the species' profile and the sedative's own potency.
     */
    public static Step tamed(Species species, int creatureLevel, String food, String sedative, boolean keep) {
        return sequence("tamed " + species.id, run -> {
            var steps = new ArrayList<Step>();
            var profile = dev.nez.arksurvivalreturns.feature.taming.CreatureProfileRegistry.of(species);
            if (profile.method() == TamingMethod.KNOCKOUT) {
                double dose = TamingService.sedativePotency(new ItemStack(Verbs.item(sedative))) * profile.sedativeResistance();
                int doses = dose <= 0 ? 16 : (int) Math.ceil(profile.size().maxTorpor() / dose) * 2 + 4;
                steps.add(Gathering.obtain(sedative, Math.min(doses, 128)));
            }
            steps.add(Gathering.obtain(food, 24));
            steps.add(tame(species, creatureLevel, food, sedative, keep));
            return steps;
        });
    }

    /** {@code keep}: the tamed creature stays as the run's companion ({@link PlayRun#companion}) for {@link #companion}. */
    public static Step tame(Species species, int creatureLevel, String food, String sedative, boolean keep) {
        return new Step() {
            CreatureEntity creature;
            Vec3 spot;
            long started, xpBefore;
            int blows, doses, offers;
            boolean done;
            @Override public String name() { return "tame " + species.id; }
            @Override public int timeout() { return 20 * 60 * 10; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                if (ticks == 0) {
                    var body = TestGroundBuilder.extent(TestGroundBuilder.extents(level), species);
                    spot = Vec3.atBottomCenterOf(run.at(-40, 1, 140));
                    creature = CombatSteps.spawn(run, species, creatureLevel, spot, true);
                    if (creature == null) return finish(run, "not_created");
                    Verbs.tp(run, -40, 0, 140 + body.front() + 4, 180, 10);
                    started = level.getGameTime();
                    xpBefore = ArkLevels.totalXp(ArkLevels.get(player));
                    return false;
                }
                if (!creature.isAlive()) return finish(run, "died");
                var state = TamingService.of(creature);
                if (state.tamed()) return finish(run, "tamed");
                creature.setPos(spot.x, creature.getY(), spot.z);
                creature.getNavigation().stop();
                boolean knockout = creature.profile().method() == TamingMethod.KNOCKOUT;
                var torpor = TorporService.of(creature);
                if (knockout && !torpor.restricted()) {
                    if (player.getAttackStrengthScale(0.5f) < 1f) return false;
                    if (!Verbs.select(player, sedative)) return finish(run, "no_sedative");
                    player.attack(creature);
                    player.swing(InteractionHand.MAIN_HAND, true);
                    blows++;
                    return false;
                }
                if (ticks % 20 != 0) return false;
                boolean dose = knockout && torpor.ratio() < 0.5;
                String held = dose ? sedative : food;
                if (!Verbs.select(player, held)) return finish(run, dose ? "no_sedative" : "no_food");
                player.interactOn(creature, InteractionHand.MAIN_HAND, creature.position());
                if (dose) doses++; else offers++;
                return false;
            }
            @Override public void abandon(PlayRun run) { finish(run, "timeout"); }
            private boolean finish(PlayRun run, String end) {
                if (done) return true;
                done = true;
                var event = run.event("tame");
                event.addProperty("species", species.id);
                event.addProperty("creature_level", creatureLevel);
                event.addProperty("end", end);
                event.addProperty("status", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                event.addProperty("sedating_blows", blows);
                event.addProperty("doses", doses);
                event.addProperty("food_offers", offers);
                if (creature != null) {
                    var state = TamingService.of(creature);
                    event.addProperty("method", creature.profile().method().name());
                    event.addProperty("meals", state.meals());
                    event.addProperty("progress", state.progress());
                }
                event.addProperty("xp", ArkLevels.totalXp(ArkLevels.get(run.player())) - xpBefore);
                run.write(event);
                if (keep && end.equals("tamed")) run.companion = creature;
                else if (creature != null && !creature.isRemoved()) creature.discard();
                return true;
            }
        };
    }

    // ------------------------------------------------------------------ a tame's life, the camp's small things

    /**
     * What a tamed worker does for its owner, each tried once and written down: the WORK order whistled (it forages
     * ripe bushes put beside it), a wild Dilophosaur set on it (it fights back; the kill is the tame's), a Trough the
     * player earned, placed and filled by a click (it eats when hungry enough). The companion is let go at the end.
     */
    public static Step companion() {
        return sequence("companion", run -> {
            var steps = new ArrayList<Step>();
            if (run.companion == null || !run.companion.isAlive()) {
                steps.add(Step.of("no companion", run2 -> { var event = run2.event("companion"); event.addProperty("what", "none"); event.addProperty("end", "no_tame"); run2.write(event); }));
                return steps;
            }
            var tame = run.companion;
            var home = tame.blockPosition();
            steps.add(new Step() {
                BlockPos bush;
                @Override public String name() { return "companion work"; }
                @Override public int timeout() { return 20 * 60; }
                @Override public boolean tick(PlayRun run2, int ticks) {
                    var level = run2.level();
                    if (ticks == 0) {
                        bush = home.offset(3, 0, 0);
                        var ripe = Gathering.ripe(BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(NS + ":tintoberry_bush")));
                        for (int i = 0; i < 3; i++) level.setBlockAndUpdate(bush.offset(0, 0, i - 1), ripe);
                        Verbs.tp(run2, home.getX() - run2.origin.getX(), 0, home.getZ() - run2.origin.getZ() + 6, 180, 10);
                        CompanionWhistle.whistleAll(run2.player(), CompanionCommand.WORK);
                        return false;
                    }
                    boolean worked = false;
                    for (int i = 0; i < 3; i++) worked |= !level.getBlockState(bush.offset(0, 0, i - 1)).equals(
                            Gathering.ripe(BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(NS + ":tintoberry_bush"))));
                    if (!worked && ticks < 20 * 50) return false;
                    return done(run2, worked ? "worked" : "no_work", ticks);
                }
                @Override public void abandon(PlayRun run2) { done(run2, "timeout", timeout()); }
                private boolean done(PlayRun run2, String end, int ticks) {
                    var event = run2.event("companion");
                    event.addProperty("what", "work");
                    event.addProperty("species", tame.species().id);
                    event.addProperty("end", end);
                    event.addProperty("ticks", ticks);
                    event.addProperty("order", CompanionService.of(tame).order().name());
                    event.addProperty("supplied", "berry bushes");
                    run2.write(event);
                    for (int i = 0; i < 3; i++) run2.level().removeBlock(bush.offset(0, 0, i - 1), false);
                    CompanionWhistle.whistleAll(run2.player(), CompanionCommand.STAY);
                    return true;
                }
            });
            steps.add(new Step() {
                CreatureEntity wild;
                @Override public String name() { return "companion kill"; }
                @Override public int timeout() { return 20 * 90; }
                @Override public boolean tick(PlayRun run2, int ticks) {
                    if (ticks == 0) {
                        wild = CombatSteps.spawn(run2, Species.DILOPHOSAUR, 1, tame.position().add(4, 0, 0), true);
                        return wild == null && done(run2, "not_created", 0);
                    }
                    if (!tame.isAlive()) return done(run2, "tame_died", ticks);
                    if (!wild.isAlive()) return done(run2, "killed", ticks);
                    if (ticks % 20 == 0) { wild.setTarget(tame); wild.getNavigation().moveTo(tame, 1.2); }
                    return ticks > 20 * 80 && done(run2, "no_kill", ticks);
                }
                @Override public void abandon(PlayRun run2) { done(run2, "timeout", timeout()); }
                private boolean done(PlayRun run2, String end, int ticks) {
                    var event = run2.event("companion");
                    event.addProperty("what", "kill");
                    event.addProperty("species", tame.species().id);
                    event.addProperty("end", end);
                    event.addProperty("ticks", ticks);
                    event.addProperty("tame_health", tame.getHealth() / tame.getMaxHealth());
                    event.addProperty("supplied", "wild dilophosaur");
                    run2.write(event);
                    if (wild != null && !wild.isRemoved()) wild.discard();
                    return true;
                }
            });
            String trough = NS + ":trough";
            steps.add(Gathering.obtain(trough, 1));
            steps.add(Gathering.obtain(NS + ":tintoberry", 16));
            steps.add(new Step() {
                BlockPos pos;
                int stocked;
                @Override public String name() { return "companion trough"; }
                @Override public int timeout() { return 20 * 100; }
                @Override public boolean tick(PlayRun run2, int ticks) {
                    var level = run2.level();
                    if (ticks == 0) {
                        pos = tame.blockPosition().offset(-3, 0, 0);
                        Gathering.floor(run2, pos);
                        if (!Verbs.placeItem(run2, trough, trough, pos)) return done(run2, "not_placed", 0);
                        Verbs.use(run2, pos, NS + ":tintoberry");
                        stocked = level.getBlockEntity(pos) instanceof TroughBlockEntity filled ? filled.stored().getCount() : 0;
                        return stocked == 0 && done(run2, "not_filled", 0);
                    }
                    if (!(level.getBlockEntity(pos) instanceof TroughBlockEntity filled)) return done(run2, "gone", ticks);
                    if (filled.stored().getCount() < stocked) return done(run2, "fed", ticks);
                    return ticks > 20 * 90 && done(run2, "not_hungry", ticks);
                }
                @Override public void abandon(PlayRun run2) { done(run2, "timeout", timeout()); }
                private boolean done(PlayRun run2, String end, int ticks) {
                    var event = run2.event("companion");
                    event.addProperty("what", "trough");
                    event.addProperty("species", tame.species().id);
                    event.addProperty("end", end);
                    event.addProperty("ticks", ticks);
                    event.addProperty("hunger", TamingService.of(tame).hunger());
                    event.addProperty("fed_at_hunger", dev.nez.arksurvivalreturns.Config.FARM_TROUGH_HUNGER.get());
                    run2.write(event);
                    return true;
                }
            });
            steps.add(Step.of("release companion", run2 -> {
                if (run2.companion != null && !run2.companion.isRemoved()) run2.companion.discard();
                run2.companion = null;
            }));
            return steps;
        });
    }

    /** A stick clicked on the lit camp fire: a torch (the light_torch event). */
    public static Step torch() {
        return sequence("torch", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Gathering.obtain("minecraft:stick", 1));
            steps.add(Step.of("light a torch", run2 -> {
                var player = run2.player();
                var fire = run2.camp.get(Gathering.FIRE);
                var event = run2.event("torch");
                int before = Gathering.count(player, "minecraft:torch");
                if (fire == null) event.addProperty("end", "no_fire");
                else {
                    Gathering.standAt(run2, fire);
                    Verbs.use(run2, fire, Direction.SOUTH, new Vec3(fire.getX() + 0.5, fire.getY() + 0.2, fire.getZ() + 1.0), "minecraft:stick");
                    boolean lit = run2.level().getBlockEntity(fire) instanceof dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlockEntity hearth && hearth.isLit();
                    event.addProperty("end", Gathering.count(player, "minecraft:torch") > before ? "torch" : lit ? "no_torch" : "fire_out");
                }
                run2.write(event);
            }));
            return steps;
        });
    }

    /**
     * A night in an earned bed: the bed put down by its item, dusk called (the Testing Ground's clock stands still), the
     * bed clicked, the player left to sleep until the game wakes them, then noon again.
     */
    public static Step sleep(String bed) {
        return sequence("sleep " + bed, run -> {
            var steps = new ArrayList<Step>();
            steps.add(Gathering.obtain(bed, 1));
            steps.add(new Step() {
                BlockPos pos;
                boolean slept;
                @Override public String name() { return "sleep in " + bed; }
                @Override public int timeout() { return 20 * 30; }
                @Override public boolean tick(PlayRun run2, int ticks) {
                    var player = run2.player();
                    if (ticks == 0) {
                        pos = run2.at(Gathering.CAMP_X - 6, 1, Gathering.CAMP_Z + (bed.endsWith("bedroll") ? 4 : 0));
                        Gathering.floor(run2, pos);
                        if (!Verbs.placeItem(run2, bed, bed, pos)) return done(run2, "not_placed", 0);
                        run2.server.getCommands().performPrefixedCommand(run2.server.createCommandSourceStack().withSuppressedOutput(), "time set 14000");
                        return false;
                    }
                    if (ticks == 5) { Gathering.standAt(run2, pos); Verbs.use(run2, pos, null); return false; }
                    if (ticks < 10) return false;
                    if (player.isSleeping()) { slept = true; return false; }
                    return done(run2, slept ? "woke" : ticks > 40 ? "refused" : null, ticks);
                }
                @Override public void abandon(PlayRun run2) { if (run2.player().isSleeping()) run2.player().stopSleepInBed(true, true); done(run2, slept ? "still_asleep" : "timeout", timeout()); }
                private boolean done(PlayRun run2, String end, int ticks) {
                    if (end == null) return false;
                    var event = run2.event("sleep");
                    event.addProperty("bed", bed);
                    event.addProperty("end", end);
                    event.addProperty("ticks", ticks);
                    var respawn = run2.player().getRespawnConfig();
                    event.addProperty("respawn_set", respawn != null);
                    run2.write(event);
                    run2.server.getCommands().performPrefixedCommand(run2.server.createCommandSourceStack().withSuppressedOutput(), "time set 6000");
                    return true;
                }
            });
            return steps;
        });
    }

    /** Which tech nodes the player's tribe has completed so far, and which not. */
    public static Step tech(String label) {
        return Step.of("tech " + label, run -> {
            var tree = TechTree.current();
            var event = run.event("tech");
            event.addProperty("label", label);
            if (tree == null) { event.addProperty("tree", false); run.write(event); return; }
            var progress = TechProgressData.get(run.level()).progress(TechService.tribeOf(run.player()));
            var done = new JsonArray();
            var open = new JsonArray();
            var future = new JsonArray();
            for (var node : tree.nodes()) {
                (progress.completed(node.id()) ? done : open).add(node.age() + "/" + node.id());
                if (node.trigger() instanceof dev.nez.arksurvivalreturns.feature.tech.TechTrigger.Future) future.add(node.age() + "/" + node.id());
            }
            event.add("completed", done);
            event.add("open", open);
            event.add("future", future);
            run.write(event);
        });
    }

    private Sweeps() {}
}
