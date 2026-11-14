package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.InventoryCrafting;
import dev.nez.arksurvivalreturns.feature.station.StorageCrateBlockEntity;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How the playthrough earns an item the way a survival player does, and nothing else: nothing is ever given.
 * {@link #obtain} looks at the live bags each time it is free and picks the next thing to do: break the block the item
 * comes from (on a spot of the yard that is put back after each break) with the right tool, earned first when the
 * block needs one (logs an axe, stone a pickaxe); hunt the creature whose carcass drops it (put frozen in the yard,
 * killed with the best weapon carried); put its raw form through the camp's forge, crusher, drying rack or
 * stonecutter; or craft it at its workstation once every input is in the bags.
 *
 * <p>Every station is itself earned and put down in the camp by the item's own right-click, and stays there; it is
 * then worked by clicks (an item in hand, or an empty hand to take out) and its screen's slots, the way a player
 * works it. Bags that fill up are emptied into Storage Crates the player earned, through the crate's screen. What
 * cannot be earned ends the goal as blocked, with the reason, so the report shows each gap in the economy.
 *
 * <p>Three things are supplied, and written down as such: the yard's blocks and hunted creatures (the "infinite
 * blocks"); a level the play so far did not reach ({@link #lift}): the run does not grind for experience, it
 * records how far short the earned XP fell and lifts the player to the gate; and a drop whose hunt this run has
 * already validated ({@link #VALIDATED_KILLS} measured kills of the species for that item): the rest comes at the
 * measured rate with the measured XP instead of repeating the same grind, so each kill is validated once and the
 * run's minutes go to what is not yet validated.
 */
public final class Gathering {
    sealed interface Source permits Breaks, Hunts, Processes, Cooks, Fills {}
    record Breaks(String block) implements Source {}
    record Hunts(Species species) implements Source {}
    /**
     * Station block: forge, crusher, drying rack, the Stone Fire's spit or stonecutter (which uses its own recipes).
     * {@code second} is the crusher's second slot for a two-ingredient recipe (gunpowder: coal and sulphur), else null.
     */
    record Processes(String station, String input, String second) implements Source {}
    /** A Cooking Pot meal: one of each input in its four slots, the pot standing on a lit Stone Fire. */
    record Cooks(List<String> inputs) implements Source {}
    /** A container filled at a water source the player stands over (the glass bottle's own use). */
    record Fills(String container) implements Source {}

    static final String NS = "arksurvivalreturns:";
    static final String FORGE = NS + "primitive_forge", CRUSHER = NS + "crusher", RACK = NS + "drying_rack", CRATE = NS + "storage_crate",
            FIRE = NS + "stone_fire", POT = NS + "cooking_pot", FIRE_STARTER = NS + "fire_starter", STONECUTTER = "minecraft:stonecutter",
            WATER_BOTTLE = "minecraft:water_bottle";
    static final Map<String, Source> SOURCES = new HashMap<>();
    static {
        breaks(NS + "rock", NS + "loose_rock");
        breaks("minecraft:stick", "minecraft:oak_leaves");   // the Prehistoric handle: leaves, before any plank
        breaks("minecraft:flint", "minecraft:gravel");
        breaks("minecraft:gravel", "minecraft:gravel");
        breaks(NS + "plant_fiber", "minecraft:short_grass");
        for (String berry : List.of("tintoberry", "amarberry", "azulberry", "narcoberry")) breaks(NS + berry, NS + berry + "_bush");
        breaks("minecraft:oak_log", "minecraft:oak_log");
        breaks("minecraft:cobblestone", "minecraft:stone");
        breaks("minecraft:clay_ball", "minecraft:clay");
        breaks("minecraft:sand", "minecraft:sand");
        breaks("minecraft:coal", "minecraft:coal_ore");
        breaks("minecraft:raw_iron", "minecraft:iron_ore");
        breaks("minecraft:raw_copper", "minecraft:copper_ore");
        breaks(NS + "raw_tin", NS + "tin_ore");
        breaks(NS + "sulphur", NS + "sulphur_cluster");
        breaks("minecraft:sugar_cane", "minecraft:sugar_cane");
        hunts(NS + "keratin", Species.TRICERATOPS);
        hunts("minecraft:leather", Species.PARASAUR);
        hunts("minecraft:bone", Species.PARASAUR);
        hunts(NS + "raw_herbivore_meat", Species.PARASAUR);
        hunts(NS + "raw_carnivore_meat", Species.DILOPHOSAUR);
        hunts("minecraft:feather", Species.ARCHAEOPTERYX);
        hunts(NS + "raw_bird_meat", Species.ARCHAEOPTERYX);
        processes(NS + "bronze_ingot", FORGE, NS + "bronze_blend");
        processes(NS + "tin_ingot", FORGE, NS + "raw_tin");
        processes("minecraft:iron_ingot", FORGE, "minecraft:raw_iron");
        processes("minecraft:copper_ingot", FORGE, "minecraft:raw_copper");
        processes("minecraft:glass", FORGE, "minecraft:sand");
        processes("minecraft:charcoal", FORGE, "minecraft:oak_log");
        // Gunpowder is the crusher's two-ingredient recipe: two coal in the hopper, two sulphur in the second slot, two out.
        SOURCES.put("minecraft:gunpowder", new Processes(CRUSHER, "minecraft:coal", NS + "sulphur"));
        processes("minecraft:bone_meal", CRUSHER, "minecraft:bone");
        processes("minecraft:sugar", CRUSHER, "minecraft:sugar_cane");
        processes("minecraft:red_dye", CRUSHER, "minecraft:poppy");
        processes("minecraft:yellow_dye", CRUSHER, "minecraft:dandelion");
        // The Stone Fire's spit, before a pot stands on it: each meat the hunts give, cooked.
        processes(NS + "cooked_herbivore_meat", FIRE, NS + "raw_herbivore_meat");
        processes(NS + "cooked_carnivore_meat", FIRE, NS + "raw_carnivore_meat");
        processes(NS + "cooked_bird_meat", FIRE, NS + "raw_bird_meat");
        // A berry dried on the rack is a ration.
        processes(NS + "dried_ration", RACK, NS + "tintoberry");
        // What else the wild grows and the benches ask for.
        breaks("minecraft:dandelion", "minecraft:dandelion");
        breaks("minecraft:cactus", "minecraft:cactus");
        breaks("minecraft:pumpkin", "minecraft:pumpkin");
        breaks("minecraft:brown_mushroom", "minecraft:brown_mushroom");
        breaks("minecraft:moss_block", "minecraft:moss_block");
        breaks("minecraft:mud", "minecraft:mud");
        breaks("minecraft:snowball", "minecraft:snow_block");
        breaks("minecraft:amethyst_shard", "minecraft:amethyst_cluster");
        breaks("minecraft:red_mushroom", "minecraft:red_mushroom");
        breaks("minecraft:dirt", "minecraft:dirt");
        breaks("minecraft:diorite", "minecraft:diorite");
        breaks("minecraft:pointed_dripstone", "minecraft:pointed_dripstone");
        processes("minecraft:brick", FORGE, "minecraft:clay_ball");
        processes("minecraft:smooth_stone_slab", STONECUTTER, "minecraft:smooth_stone");
        processes(NS + "dried_meat", RACK, NS + "raw_herbivore_meat");
        processes("minecraft:stone", FORGE, "minecraft:cobblestone");
        processes("minecraft:smooth_stone", FORGE, "minecraft:stone");
        processes("minecraft:stone_slab", STONECUTTER, "minecraft:stone");
        // Every wood the world grows, and the leaves the ghillie wants.
        for (String wood : List.of("spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"))
            breaks("minecraft:" + wood + "_log", "minecraft:" + wood + "_log");
        breaks("minecraft:bamboo", "minecraft:bamboo");
        breaks("minecraft:jungle_leaves", "minecraft:jungle_leaves");
        breaks("minecraft:poppy", "minecraft:poppy");
        breaks("minecraft:obsidian", "minecraft:obsidian");
        breaks("minecraft:raw_gold", "minecraft:gold_ore");
        breaks("minecraft:diamond", "minecraft:diamond_ore");
        breaks("minecraft:carrot", NS + "wild_carrots");
        processes("minecraft:gold_ingot", FORGE, "minecraft:raw_gold");
        breaks("minecraft:short_grass", "minecraft:short_grass");   // the tuft itself: only shears cut it (HELD)
        SOURCES.put(WATER_BOTTLE, new Fills("minecraft:glass_bottle"));
        breaks(NS + "amber", "minecraft:gravel");   // 1.5% of gravel and clay broken by a player (TrophyDrops)
        // Trophies, from the species whose carcass gives them (TrophyDrops.TABLE).
        hunts(NS + "sickle_claw", Species.VELOCIRAPTOR);
        hunts(NS + "scythe_claw", Species.THERIZINOSAURUS);
        hunts(NS + "fang", Species.SABERTOOTH);
        hunts(NS + "thick_pelt", Species.MAMMOTH);
        hunts(NS + "giant_antler", Species.MEGALOCERUS);
        hunts(NS + "wing_membrane", Species.QUETZAL);
        hunts(NS + "quetzal_feather", Species.QUETZAL);
        hunts(NS + "croc_scute", Species.DEINOSUCHUS);
        hunts(NS + "marine_flipper", Species.MOSASAURUS);
        hunts(NS + "megalodon_tooth", Species.MEGALODON);
        hunts(NS + "unicorn_horn", Species.UNICORN);
        hunts(NS + "venom_fang", Species.TITANOBOA);
        hunts(NS + "tyrant_tooth", Species.TYRANNOSAURUS);
        hunts(NS + "argentavis_talon", Species.ARGENTAVIS);
        SOURCES.put(NS + "hearty_stew", new Cooks(List.of(NS + "dried_meat", NS + "dried_meat", NS + "raw_herbivore_meat", "minecraft:carrot")));
        SOURCES.put(NS + "trail_mix", new Cooks(List.of(NS + "dried_meat", NS + "dried_meat", NS + "tintoberry", NS + "tintoberry")));
    }
    private static void breaks(String item, String block) { SOURCES.put(item, new Breaks(block)); }
    private static void hunts(String item, Species species) { SOURCES.put(item, new Hunts(species)); }
    private static void processes(String item, String station, String input) { SOURCES.put(item, new Processes(station, input, null)); }
    /** Best first: the weapon taken in hand to hunt. */
    static final String[] WEAPONS = {"minecraft:diamond_sword", NS + "bronze_longsword", NS + "bronze_hammer", NS + "keratin_spear",
            "minecraft:golden_sword", NS + "rock_sword", NS + "flint_knife", NS + "stone_hatchet"};
    /** Each kind of tool by tier, lowest first: the first one not yet carried is earned for a block that needs it. */
    static final Map<TagKey<Block>, List<String>> TOOLS = Map.of(
            BlockTags.MINEABLE_WITH_AXE, List.of(NS + "stone_hatchet", NS + "bronze_axe", "minecraft:diamond_axe"),
            BlockTags.MINEABLE_WITH_PICKAXE, List.of(NS + "rock_pickaxe", NS + "bronze_pickaxe", "minecraft:diamond_pickaxe"),
            BlockTags.MINEABLE_WITH_SHOVEL, List.of(NS + "rock_shovel", NS + "bronze_shovel", "minecraft:diamond_shovel"));
    /** Items whose block gives them only to this tool in hand (the grass tuft to shears). */
    static final Map<String, String> HELD = Map.of("minecraft:short_grass", "minecraft:shears");
    /** Kept in the bags when they are emptied into crates: the tools and weapons the run works with. */
    static final Set<String> KEEP = Set.of(NS + "bronze_longsword", NS + "bronze_hammer", NS + "keratin_spear", NS + "rock_sword",
            NS + "flint_knife", NS + "stone_hatchet", NS + "bronze_axe", NS + "bronze_pickaxe", NS + "bronze_shovel",
            NS + "rock_pickaxe", NS + "rock_shovel", "minecraft:shears", "minecraft:golden_sword", "minecraft:diamond_sword",
            "minecraft:diamond_pickaxe", "minecraft:diamond_axe", "minecraft:diamond_shovel", "minecraft:bow", NS + "fire_starter");
    /** What the forge burns, best first: none of them smelts, so a click puts it in the fuel slot (a log goes in as input). */
    static final List<String> FORGE_FUELS = List.of("minecraft:coal", "minecraft:charcoal", "minecraft:oak_planks");
    private static final int MAX_DEPTH = 12, STUCK = 2, FREE_SLOTS = 6;
    /** The yard (blocks broken, creatures hunted), the camp (stations) and the crates, from the run's origin. */
    static final int YARD_X = 30, YARD_Z = 140, CAMP_X = 40, CAMP_Z = 160, CRATE_X = 20, CRATE_Z = 160;

    // ------------------------------------------------------------------ obtain

    /** What could not be earned: ends the goal, written down as blocked. */
    record Blocked(String item, int count, String why) implements Step {
        @Override public String name() { return "blocked " + item + " " + count; }
        @Override public boolean tick(PlayRun run, int ticks) { return true; }
    }

    /** Have at least {@code count} of the item in the bags, earned. */
    public static Step obtain(String item, int count) { return obtain(item, count, true); }

    static Step obtain(String item, int count, boolean mayStash) {
        return new Step() {
            Step current;
            int currentTicks, repeats;
            String last;
            @Override public String name() { return "obtain " + item + " x" + count; }
            @Override public int timeout() { return 20 * 60 * 30; }
            @Override public boolean tick(PlayRun run, int ticks) {
                if (current != null) {
                    run.safe = false;
                    if (Step.drive(run, current, currentTicks++)) current = null;
                    return false;
                }
                run.safe = true;
                var player = run.player();
                pullDrops(run, player.blockPosition(), 6);
                if (mayStash && freeSlots(player) < FREE_SLOTS && !run.noCrate) {
                    var stash = stash(run, player);
                    if (stash != null) { current = stash; currentTicks = 0; return false; }
                }
                var next = next(run, player, item, count, 0);
                if (next == null) {
                    var event = run.event("obtained");
                    event.addProperty("item", item);
                    event.addProperty("count", count);
                    event.addProperty("ticks", ticks);
                    run.write(event);
                    return true;
                }
                if (freeSlots(player) == 0 && run.noCrate) return blocked(run, item, item, count, "bags_full");
                repeats = next.name().equals(last) ? repeats + 1 : 0;
                last = next.name();
                if (next instanceof Blocked blocked) return blocked(run, item, blocked.item(), blocked.count(), blocked.why());
                // The same action again without the bags getting there: the source does not give it.
                if (repeats >= STUCK) {
                    var words = next.name().split(" ");
                    return blocked(run, item, words.length > 1 ? words[1] : next.name(), 0, "no_progress: " + next.name());
                }
                current = next;
                currentTicks = 0;
                return false;
            }
            @Override public void abandon(PlayRun run) { if (current != null) current.abandon(run); }
        };
    }

    private static boolean blocked(PlayRun run, String goal, String item, int count, String why) {
        var event = run.event("blocked");
        event.addProperty("goal", goal);
        event.addProperty("item", item);
        event.addProperty("count", count);
        event.addProperty("why", why);
        run.write(event);
        return true;
    }

    /** The first thing to do towards {@code count} of {@code item}, or null when the bags already hold it. */
    static Step next(PlayRun run, ServerPlayer player, String item, int count, int depth) {
        int missing = count - count(player, item);
        if (missing <= 0) return null;
        if (depth > MAX_DEPTH) return new Blocked(item, missing, "too_deep");
        if (inCrates(run, item) > 0) return withdraw(item, missing);
        var source = SOURCES.get(item);
        if (source instanceof Breaks breaks) {
            var state = ripe(BuiltInRegistries.BLOCK.getValue(Identifier.parse(breaks.block())));
            String held = HELD.get(item);
            if (held != null && count(player, held) == 0) return next(run, player, held, 1, depth + 1);
            if (held == null && !canHarvest(player, state)) {
                String tool = toolFor(player, state);
                if (tool == null || tool.equals(item)) return new Blocked(item, missing, "no_tool_for " + breaks.block());
                return next(run, player, tool, 1, depth + 1);
            }
            return harvest(item, missing, breaks.block());
        }
        if (source instanceof Hunts hunts) {
            // Armed first: a hunt with bare hands or a broken sword is hundreds of hits, not a measurement.
            if (!carriesWeapon(player)) {
                var arm = next(run, player, NS + "rock_sword", 1, depth + 1);
                if (arm != null) return arm;
            }
            return hunt(item, missing, hunts.species());
        }
        if (source instanceof Fills fills) {
            var bottles = next(run, player, fills.container(), missing, depth + 1);
            return bottles != null ? bottles : fill(item, missing, fills.container());
        }
        if (source instanceof Processes process) {
            var station = station(run, player, process.station(), depth);
            if (station != null) return station;
            var input = next(run, player, process.input(), missing, depth + 1);
            if (input != null) return input;
            if (process.second() != null) {
                var second = next(run, player, process.second(), missing, depth + 1);
                if (second != null) return second;
            }
            if (process.station().equals(FORGE)) {
                var fuel = fuel(run, player, missing, depth);
                if (fuel != null) return fuel;
            }
            if (process.station().equals(FIRE)) {
                for (var need : Map.of(FIRE_STARTER, 1, "minecraft:oak_log", 2).entrySet()) {
                    var step = next(run, player, need.getKey(), need.getValue(), depth + 1);
                    if (step != null) return step;
                }
            }
            return process(item, missing, process.station(), process.input(), process.second());
        }
        if (source instanceof Cooks cooks) {
            for (String station : List.of(FIRE, POT)) {
                var step = station(run, player, station, depth);
                if (step != null) return step;
            }
            var needed = new java.util.LinkedHashMap<String, Integer>();
            for (String input : cooks.inputs()) needed.merge(input, missing, Integer::sum);
            needed.merge(FIRE_STARTER, 1, Math::max);
            needed.merge("minecraft:oak_log", 2, Math::max);
            for (var entry : needed.entrySet()) {
                var step = next(run, player, entry.getKey(), entry.getValue(), depth + 1);
                if (step != null) return step;
            }
            return cook(item, missing, cooks.inputs());
        }
        var found = Verbs.find(item);
        if (found != null) {
            int level = found.craft().level();
            if (Config.WORKSTATION_LEVEL_GATE.get() && !ArkLevels.requires(player, level)) return lift(level);
            if (!InventoryCrafting.STATION.equals(found.station().station())) {
                String bench = Verbs.benchBlock(found.station().station());
                if (bench == null) return new Blocked(item, missing, "no_bench_block " + found.station().station());
                if (bench.equals(item)) return new Blocked(item, missing, "made_at_itself");
                var station = station(run, player, bench, depth);
                if (station != null) return station;
            }
            int each = Math.max(1, found.craft().variant().count());
            int times = (missing + each - 1) / each;
            for (var cost : found.craft().variant().cost().entrySet()) {
                String needed = resolve(player, cost.getKey());
                if (needed == null) return new Blocked(item, missing, "unknown_cost " + cost.getKey());
                var step = next(run, player, needed, cost.getValue() * times, depth + 1);
                if (step != null) return step;
            }
            return Verbs.craft(item, times);
        }
        return new Blocked(item, missing, "no_source");
    }

    /**
     * Fuel for {@code missing} smelts in the forge: null when the bags already burn long enough, else the step towards
     * planks (a log clicked on the forge goes in as the input, so a player fuels it with planks, charcoal or coal).
     */
    static Step fuel(PlayRun run, ServerPlayer player, int missing, int depth) {
        var values = run.level().fuelValues();
        long wanted = (long) Math.ceil(missing * Math.max(20, 200 * Config.PRIMITIVE_FORGE_SPEED.get())), carried = 0;
        for (String fuel : FORGE_FUELS) carried += (long) count(player, fuel) * new ItemStack(Verbs.item(fuel)).getBurnTime(RecipeType.SMELTING, values);
        if (carried >= wanted) return null;
        int plank = Math.max(1, new ItemStack(Verbs.item("minecraft:oak_planks")).getBurnTime(RecipeType.SMELTING, values));
        int planks = (int) Math.ceil((wanted - carried) / (double) plank) + count(player, "minecraft:oak_planks");
        return next(run, player, "minecraft:oak_planks", planks, depth + 1);
    }

    /**
     * A level gate the play so far did not reach: the run does not grind for it. The XP still missing is written down
     * (how far the earned XP fell short of the gate is the curve's measure) and the player is lifted to the gate.
     */
    static Step lift(int level) {
        return Step.of("lift level " + level, run -> {
            var player = run.player();
            long missing = ArkLevels.xpToReach(player, level);
            var event = run.event("level_lift");
            event.addProperty("wanted_level", level);
            event.addProperty("from_level", ArkLevels.get(player).level());
            event.addProperty("earned_xp", run.earnedXp(player));
            event.addProperty("supplied_xp", missing);
            run.suppliedXp += missing;
            ArkLevels.addXp(player, missing, ArkLevels.Source.COMMAND);
            event.addProperty("to_level", ArkLevels.get(player).level());
            run.write(event);
        });
    }

    /** Null when the station stands in the camp; else the step towards it (earn its item, or put it down). */
    static Step station(PlayRun run, ServerPlayer player, String block, int depth) {
        var pos = run.camp.get(block);
        if (pos != null && BuiltInRegistries.BLOCK.getKey(run.level().getBlockState(pos).getBlock()).toString().equals(block)) return null;
        String item = Verbs.id(BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).asItem());
        if (count(player, item) > 0) return place(block, item);
        return next(run, player, item, 1, depth + 1);
    }

    /** Puts a station down in the camp by its item's own right-click; it stays there for the rest of the run. */
    static Step place(String block, String item) {
        return Step.of("place " + block, run -> {
            var fire = run.camp.get(FIRE);
            int i = run.camp.size();
            // The pot cooks only standing on the fire: it is put on the fire's top.
            var pos = block.equals(POT) && fire != null ? fire.above() : run.at(CAMP_X + (i % 8) * 3, 1, CAMP_Z + (i / 8) * 3);
            boolean floored = floor(run, pos);
            boolean placed = Verbs.placeItem(run, block, item, pos);
            if (placed) run.camp.put(block, pos);
            var event = run.event("placed");
            if (floored) event.addProperty("supplied", "floor");
            event.addProperty("block", block);
            event.addProperty("item", item);
            event.addProperty("at", pos.subtract(run.origin).toShortString());
            event.addProperty("result", placed ? "placed" : "refused");
            if (!placed) event.addProperty("there", BuiltInRegistries.BLOCK.getKey(run.level().getBlockState(pos).getBlock()).toString());
            run.write(event);
        });
    }

    /** A world with no ground there (the GameTest's void) is given a floor block to place on; true when it was. */
    static boolean floor(PlayRun run, BlockPos pos) {
        if (!run.level().getBlockState(pos.below()).isAir()) return false;
        run.level().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        return true;
    }

    /** The meal a hungry player with empty bags goes and earns: dried meat once a rack stands in the camp, else berries. */
    static String provision(PlayRun run) { return run.camp.containsKey(RACK) ? NS + "dried_meat" : NS + "tintoberry"; }

    /** Stands the player beside a camp station, facing it, well inside its reach. */
    static void standAt(PlayRun run, BlockPos pos) {
        Verbs.tp(run, pos.getX() - run.origin.getX(), pos.getY() - run.origin.getY() - 1, pos.getZ() - run.origin.getZ() + 1.6, 180, 35);
    }

    /** An item for a cost key: the item itself, or for a tag the member held, else one gathered, else one crafted. */
    static String resolve(ServerPlayer player, String key) {
        if (!key.startsWith("#")) return BuiltInRegistries.ITEM.containsKey(Identifier.parse(key)) ? key : null;
        var tag = TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1)));
        // What the bags hold, else what the world gives, else what can be crafted: never a crafted form of a gathered
        // thing (any log is an oak log, not dark oak wood made from logs the yard does not grow).
        String held = null, gathered = null, crafted = null, first = null;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            String id = Verbs.id(holder.value());
            if (first == null) first = id;
            if (held == null && count(player, id) > 0) held = id;
            if (gathered == null && SOURCES.containsKey(id)) gathered = id;
            if (crafted == null && Verbs.find(id) != null) crafted = id;
        }
        return held != null ? held : gathered != null ? gathered : crafted != null ? crafted : first;
    }

    /** How many the bags hold, matched the way a bench matches its costs (so a water bottle is a potion of water). */
    static int count(ServerPlayer player, String item) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (dev.nez.arksurvivalreturns.feature.station.WorkstationInventory.matches(inventory.getItem(i), item)) total += inventory.getItem(i).getCount();
        return total;
    }

    static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    // ------------------------------------------------------------------ tools

    /** Whether the best tool carried gets this block's drops (the game's own check, Ark's log rule included). */
    static boolean canHarvest(ServerPlayer player, BlockState state) {
        bestTool(player, state);
        return player.hasCorrectToolForDrops(state);
    }

    /** The next tool to earn for a block the carried ones cannot harvest: the lowest tier not carried, or null. */
    static String toolFor(ServerPlayer player, BlockState state) {
        for (var entry : TOOLS.entrySet())
            if (state.is(entry.getKey()))
                for (String tool : entry.getValue()) if (count(player, tool) == 0) return tool;
        return null;
    }

    /** A block in its ripest state: a bush or crop at its highest age, so it drops what a ripe one does. */
    static BlockState ripe(Block block) {
        var state = block.defaultBlockState();
        for (var property : state.getProperties())
            if (property instanceof IntegerProperty age && property.getName().equals("age"))
                state = state.setValue(age, age.getPossibleValues().stream().max(Integer::compare).orElse(0));
        return state;
    }

    /** The bag item that breaks this block fastest, picked on the hotbar. */
    static String bestTool(ServerPlayer player, BlockState state) {
        var inventory = player.getInventory();
        float speed = player.getMainHandItem().getDestroySpeed(state);
        ItemStack best = null;
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (stack.getDestroySpeed(state) > speed) { speed = stack.getDestroySpeed(state); best = stack; }
        }
        if (best != null) Verbs.select(player, best.getItem());
        return Verbs.id(player.getMainHandItem().getItem());
    }

    /** The best weapon in the bags, picked on the hotbar. */
    static String bestWeapon(ServerPlayer player) {
        for (String weapon : WEAPONS) if (Verbs.select(player, weapon)) return weapon;
        return Verbs.id(player.getMainHandItem().getItem());
    }

    /** Whether any of the run's weapons is still in the bags (they break: a hunt re-arms before it starts). */
    static boolean carriesWeapon(ServerPlayer player) {
        for (String weapon : WEAPONS) if (count(player, weapon) > 0) return true;
        return false;
    }

    // ------------------------------------------------------------------ the actions

    /** Breaks the block the item comes from on one spot, put back after each break, until the bags hold enough. */
    static Step harvest(String item, int missing, String blockId) {
        return new Step() {
            int target, tries, cap;
            float progress;
            String tool = "";
            @Override public String name() { return "harvest " + item + " " + missing; }
            @Override public int timeout() { return 20 * 120; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var pos = run.at(YARD_X, 1, YARD_Z);
                var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
                if (ticks == 0) {
                    target = count(player, item) + missing;
                    cap = Math.max(64, missing * 100);   // rare drops (amber: 1.5%) need their tries
                    Verbs.tp(run, YARD_X + 1.5, 0, YARD_Z, 90, 30);
                    tool = HELD.containsKey(item) ? (Verbs.select(player, HELD.get(item)) ? HELD.get(item) : "") : bestTool(player, ripe(block));
                    return false;
                }
                pullDrops(run, pos, 4);
                if (count(player, item) >= target || tries >= cap) {
                    if (count(player, item) < target && ticks < tries + 12) return false;
                    return finish(run);
                }
                var soil = block == Blocks.SUGAR_CANE ? Blocks.SAND : Blocks.GRASS_BLOCK;
                run.level().setBlockAndUpdate(pos.below(), soil.defaultBlockState());
                if (block == Blocks.SUGAR_CANE) run.level().setBlockAndUpdate(pos.below().east(), Blocks.WATER.defaultBlockState());
                run.level().setBlockAndUpdate(pos, ripe(block));
                progress = run.level().getBlockState(pos).getDestroyProgress(player, run.level(), pos);
                player.gameMode.destroyBlock(pos);
                tries++;
                return false;
            }
            private boolean finish(PlayRun run) {
                var player = run.player();
                var event = run.event("harvest");
                event.addProperty("item", item);
                event.addProperty("block", blockId);
                event.addProperty("wanted", missing);
                event.addProperty("tries", tries);
                event.addProperty("tool", tool);
                event.addProperty("hand_ticks", progress <= 0 ? -1 : (int) Math.ceil(1 / progress));
                event.addProperty("short", Math.max(0, target - count(player, item)));
                event.addProperty("supplied", "yard block");
                run.write(event);
                return true;
            }
            @Override public void abandon(PlayRun run) { finish(run); }
        };
    }

    /** Kills of one species-and-item pair after which its drop rate counts as measured ({@link #hunt}). */
    static final int VALIDATED_KILLS = 3;

    /**
     * Hunts the species, frozen in the yard, with the best weapon carried, until its carcasses gave enough. Each hunt
     * records what the carcasses gave and what the kills earned; once a pair of species and item has
     * {@link #VALIDATED_KILLS} measured kills behind it in this run, a later need is filled at the measured rate with
     * the matching XP instead of grinding the same validated kill again, and written down as supplied.
     */
    static Step hunt(String item, int missing, Species species) {
        return new Killing("hunt " + item + " " + missing, "hunt", species, 1, 8) {
            final String key = species.id + "|" + item;
            int target, start;
            @Override void begin(PlayRun run) { start = count(run.player(), item); target = start + missing; }
            @Override boolean enough(PlayRun run) { return count(run.player(), item) >= target; }
            @Override boolean needsWeapon() { return true; }
            @Override boolean filled(PlayRun run) {
                var player = run.player();
                int missingNow = target - count(player, item);
                if (missingNow <= 0) return false;
                int[] pair = run.huntDrops.get(key);
                if (pair == null || pair[0] < VALIDATED_KILLS || pair[1] <= 0) return false;
                double rate = pair[1] / (double) pair[0];
                int equivalent = (int) Math.ceil(missingNow / rate);
                long[] xp = run.hunted.get(species.id);
                long grant = xp != null && xp[0] > 0 ? Math.round(equivalent * (xp[1] / (double) xp[0])) : 0;
                give(player, item, missingNow);
                if (grant > 0) {
                    run.suppliedXp += grant;
                    ArkLevels.addXp(player, grant, ArkLevels.Source.COMMAND);
                }
                var event = run.event("hunt_fill");
                event.addProperty("species", species.id);
                event.addProperty("item", item);
                event.addProperty("count", missingNow);
                event.addProperty("rate", Math.round(rate * 100) / 100.0);
                event.addProperty("kills_equivalent", equivalent);
                event.addProperty("xp", grant);
                event.addProperty("supplied", "validated carcass");
                run.write(event);
                return true;
            }
            @Override void end(PlayRun run, JsonObject event) {
                event.addProperty("item", item);
                event.addProperty("wanted", missing);
                event.addProperty("short", Math.max(0, target - count(run.player(), item)));
                if (kills <= 0) return;
                var pair = run.huntDrops.computeIfAbsent(key, ignored -> new int[2]);
                pair[0] += kills;
                pair[1] += Math.max(0, count(run.player(), item) - start);
                var xp = run.hunted.computeIfAbsent(species.id, ignored -> new long[2]);
                xp[0] += kills;
                xp[1] += Math.max(0, xpEarned);
            }
        };
    }

    /** Fills the bags with a validated item, stack by stack; what does not fit lands at the player's feet. */
    static void give(ServerPlayer player, String item, int count) {
        var kind = Verbs.item(item);
        int left = count;
        while (left > 0) {
            int portion = Math.min(new ItemStack(kind).getMaxStackSize(), left);
            var stack = new ItemStack(kind, portion);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            left -= portion;
        }
    }

    /** Kills one frozen creature after another in the yard, picking up what they drop, until enough or the cap. */
    abstract static class Killing implements Step {
        final String name, type;
        final Species species;
        final int creatureLevel, cap;
        int kills, hits;
        long started, xpBefore, xpEarned;
        CreatureEntity creature;
        String weapon = "";

        Killing(String name, String type, Species species, int creatureLevel, int cap) {
            this.name = name; this.type = type; this.species = species; this.creatureLevel = creatureLevel; this.cap = cap;
        }

        abstract void begin(PlayRun run);
        abstract boolean enough(PlayRun run);
        abstract void end(PlayRun run, JsonObject event);
        /** True when the rest of the need was filled from validated carcasses and the hunt may end (the hunts only). */
        boolean filled(PlayRun run) { return false; }
        /** Hunts need a real weapon; a measurement wields its own and is never cut short here. */
        boolean needsWeapon() { return false; }

        @Override public String name() { return name; }
        @Override public int timeout() { return 20 * 60 * 15; }
        @Override public boolean tick(PlayRun run, int ticks) {
            var player = run.player();
            var level = run.level();
            var spot = run.at(YARD_X, 1, YARD_Z + 20);
            if (ticks == 0) { begin(run); started = level.getGameTime(); weapon = bestWeapon(player); xpBefore = ArkLevels.totalXp(ArkLevels.get(player)); }
            pullDrops(run, spot, 16);
            if (creature == null || creature.isRemoved()) {
                if (enough(run) || kills >= cap) return finish(run, kills >= cap && !enough(run) ? "cap" : "enough");
                if (filled(run)) return finish(run, "validated_fill");
                // The weapon is picked again for every carcass: a sword that broke falls to the next one carried,
                // and a hunt left bare-handed ends to re-arm instead of punching with what is in the hand.
                weapon = bestWeapon(player);
                if (needsWeapon() && !List.of(WEAPONS).contains(weapon)) return finish(run, "no_weapon");
                creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.COMMAND);
                if (creature == null) return finish(run, "not_created");
                creature.initializeLevel(creatureLevel);
                creature.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
                creature.setNoAi(true);
                level.addFreshEntity(creature);
                var body = TestGroundBuilder.extent(TestGroundBuilder.extents(level), species);
                Verbs.tp(run, YARD_X, 0, YARD_Z + 20 + body.front() + 1.5, 180, 10);
                return false;
            }
            if (creature.isDeadOrDying()) {
                if (creature.deathTime >= 18) { kills++; creature = null; }
                return false;
            }
            if (player.getAttackStrengthScale(0.5f) >= 1f) { player.attack(creature); player.swing(InteractionHand.MAIN_HAND, true); hits++; }
            return false;
        }

        private boolean finish(PlayRun run, String why) {
            var player = run.player();
            xpEarned = ArkLevels.totalXp(ArkLevels.get(player)) - xpBefore;
            var event = run.event(type);
            event.addProperty("species", species.id);
            event.addProperty("creature_level", creatureLevel);
            event.addProperty("kills", kills);
            event.addProperty("hits", hits);
            event.addProperty("weapon", weapon);
            event.addProperty("end", why);
            event.addProperty("ticks", run.level().getGameTime() - started);
            event.addProperty("xp", xpEarned);
            event.addProperty("supplied", "yard creature");
            end(run, event);
            run.write(event);
            return true;
        }

        /** A hunt that ran out of time still says how far it got, and takes its creature away. */
        @Override public void abandon(PlayRun run) {
            if (creature != null && !creature.isRemoved()) creature.discard();
            creature = null;
            finish(run, "timeout");
        }
    }

    /**
     * Puts the raw form through the camp's station by clicks, the way a player works it: the input clicked in, fuel
     * clicked into the forge, the output taken with an empty hand once there is some, what is left of the input taken
     * back at the end. The stonecutter is worked through its screen.
     */
    static Step process(String item, int missing, String station, String input, String second) {
        return new Step() {
            int start, clicks;
            long started;
            @Override public String name() { return "process " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                var pos = run.camp.get(station);
                if (pos == null) return finish(run, "no_station");
                if (ticks == 0) { standAt(run, pos); started = level.getGameTime(); start = count(player, item); return false; }
                int made = count(player, item) - start;
                if (station.equals(STONECUTTER)) return cutOne(run, pos) ? (count(player, item) - start >= missing && finish(run, "made")) : finish(run, "short_of " + input);
                if (ticks % 10 != 0) return false;
                switch (level.getBlockEntity(pos)) {
                    case PrimitiveForgeBlockEntity forge -> {
                        if (made >= missing) {
                            // Done: an empty-hand click takes the output, then what is left of the input, back.
                            if (forge.output().isEmpty() && forge.input().isEmpty()) return finish(run, "made");
                            Verbs.use(run, pos, null); clicks++;
                            return false;
                        }
                        if (!forge.output().isEmpty()) { Verbs.use(run, pos, null); clicks++; }
                        // Something else left in the input (an earlier smelt cut short) is taken out first.
                        else if (!forge.input().isEmpty() && !forge.input().is(Verbs.item(input))) { Verbs.use(run, pos, null); clicks++; }
                        else if (forge.input().isEmpty()) {
                            if (count(player, input) == 0) return finish(run, "short_of " + input);
                            Verbs.use(run, pos, input); clicks++;
                        }
                        if (forge.fuel().isEmpty() && !forge.isBurning() && !forge.input().isEmpty()) {
                            String fuel = null;
                            for (String candidate : FORGE_FUELS) if (count(player, candidate) > 0) { fuel = candidate; break; }
                            if (fuel == null) return finish(run, "no_fuel");
                            Verbs.use(run, pos, fuel); clicks++;
                        }
                    }
                    case CrusherBlockEntity crusher -> {
                        if (!crusher.getItem(CrusherBlockEntity.OUTPUT).isEmpty()) { Verbs.use(run, pos, null); clicks++; }
                        else if (made >= missing) return finish(run, "made");
                        else if (crusher.getItem(CrusherBlockEntity.INPUT).isEmpty()) {
                            if (count(player, input) == 0) return finish(run, "short_of " + input);
                            Verbs.use(run, pos, input); clicks++;
                        }
                        else if (second != null && crusher.getItem(CrusherBlockEntity.INPUT_B).isEmpty()) {
                            if (count(player, second) == 0) return finish(run, "short_of " + second);
                            Verbs.use(run, pos, second); clicks++;
                        }
                    }
                    case StoneFireBlockEntity fire -> {
                        // The spit: fed and lit like any fire, raw food clicked on, the cooked taken with an empty hand.
                        if (fire.getBlockState().getValue(dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock.POT)) return finish(run, "pot_on_fire");
                        var side = new Vec3(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 1.0);
                        if (!fire.cooked().isEmpty()) { Verbs.use(run, pos, Direction.SOUTH, side, null); clicks++; }
                        else if (made >= missing) return finish(run, "made");
                        else if (fire.burnTicks() < 200 && count(player, "minecraft:oak_log") > 0) { Verbs.use(run, pos, Direction.SOUTH, side, "minecraft:oak_log"); clicks++; }
                        else if (!fire.isLit()) {
                            if (count(player, FIRE_STARTER) == 0) return finish(run, "no_fire_starter");
                            Verbs.use(run, pos, Direction.SOUTH, side, FIRE_STARTER);
                            if (++clicks > 200) return finish(run, "not_lit");
                        }
                        else if (fire.spit().isEmpty()) {
                            if (count(player, input) == 0) return finish(run, "short_of " + input);
                            Verbs.use(run, pos, Direction.SOUTH, side, input); clicks++;
                        }
                    }
                    case DryingRackBlockEntity rack -> {
                        if (!rack.output().isEmpty()) { Verbs.use(run, pos, null); clicks++; }
                        else if (made >= missing) return finish(run, "made");
                        else if (rack.input().isEmpty()) {
                            if (count(player, input) == 0) return finish(run, "short_of " + input);
                            Verbs.use(run, pos, input); clicks++;
                        }
                    }
                    case null, default -> { return finish(run, "no_station"); }
                }
                return false;
            }
            private boolean finish(PlayRun run, String end) {
                run.player().closeContainer();
                var event = run.event("process");
                event.addProperty("item", item);
                event.addProperty("station", station);
                var pos = run.camp.get(station);
                if (pos != null) event.addProperty("at", pos.subtract(run.origin).toShortString());
                event.addProperty("input", input);
                event.addProperty("wanted", missing);
                event.addProperty("made", count(run.player(), item) - start);
                event.addProperty("clicks", clicks);
                event.addProperty("end", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                run.write(event);
                return true;
            }
            /** One cut through the stonecutter's screen: an input in its slot, the recipe button, the result shift-clicked out. */
            private boolean cutOne(PlayRun run, BlockPos pos) {
                var player = run.player();
                if (!(player.containerMenu instanceof StonecutterMenu) && !Verbs.openAt(run, pos)) return false;
                if (!(player.containerMenu instanceof StonecutterMenu cutter)) return false;
                if (cutter.getSlot(0).getItem().isEmpty()) {
                    int from = Verbs.slotWith(player, player.getInventory(), stack -> stack.is(Verbs.item(input)));
                    if (from < 0) return false;
                    Verbs.placeOne(player, from, 0);
                }
                var entries = cutter.getVisibleRecipes().entries();
                var single = new SingleRecipeInput(cutter.getSlot(0).getItem());
                for (int i = 0; i < entries.size(); i++) {
                    var recipe = entries.get(i).recipe().recipe();
                    if (recipe.isPresent() && recipe.get().value().assemble(single).is(Verbs.item(item))) {
                        if (cutter.getSelectedRecipeIndex() != i) cutter.clickMenuButton(player, i);
                        Verbs.quickMove(player, 1);
                        clicks++;
                        return true;
                    }
                }
                return false;
            }
            @Override public void abandon(PlayRun run) { finish(run, "timeout"); }
        };
    }

    /** Fills bottles at a water source in the yard, standing over it and using the bottle the way a right-click does. */
    static Step fill(String item, int missing, String container) {
        return new Step() {
            int target, uses;
            @Override public String name() { return "fill " + item + " " + missing; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var water = run.at(YARD_X - 4, 0, YARD_Z);
                if (ticks == 0) {
                    target = count(player, item) + missing;
                    run.level().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                    Verbs.tp(run, YARD_X - 4, 0.2, YARD_Z, 0, 90);
                    return false;
                }
                if (count(player, item) >= target || uses >= missing * 3) {
                    var event = run.event("fill");
                    event.addProperty("item", item);
                    event.addProperty("wanted", missing);
                    event.addProperty("uses", uses);
                    event.addProperty("short", Math.max(0, target - count(player, item)));
                    run.write(event);
                    return true;
                }
                if (ticks % 5 != 0) return false;
                if (Verbs.useItem(player, container) != net.minecraft.world.InteractionResult.FAIL) uses++;
                else return false;
                return false;
            }
        };
    }

    /**
     * Cooks a meal the way a player does: logs clicked onto the fire, the fire lit by the Fire Starter's click (which
     * can fail and is tried again), the pot's screen opened, one of each input put in its four slots, the meal
     * shift-clicked out once done.
     */
    static Step cook(String item, int missing, List<String> inputs) {
        return new Step() {
            int start, strikes;
            long started;
            @Override public String name() { return "cook " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                var fire = run.camp.get(FIRE);
                if (fire == null || !(level.getBlockEntity(fire) instanceof StoneFireBlockEntity hearth)
                        || !(level.getBlockEntity(fire.above()) instanceof CookingPotBlockEntity pot)) return finish(run, "no_station");
                if (ticks == 0) { standAt(run, fire); started = level.getGameTime(); start = count(player, item); return false; }
                if (count(player, item) - start >= missing) return finish(run, "made");
                if (ticks % 10 != 0) return false;
                var side = new Vec3(fire.getX() + 0.5, fire.getY() + 0.2, fire.getZ() + 1.0);
                if (hearth.burnTicks() < 400 && count(player, "minecraft:oak_log") > 0) Verbs.use(run, fire, Direction.SOUTH, side, "minecraft:oak_log");
                if (!hearth.isLit()) {
                    if (count(player, FIRE_STARTER) == 0) return finish(run, "no_fire_starter");
                    Verbs.use(run, fire, Direction.SOUTH, side, FIRE_STARTER);
                    if (++strikes > 60) return finish(run, "not_lit");
                    return false;
                }
                if (!(player.containerMenu instanceof dev.nez.arksurvivalreturns.feature.kitchen.CookingPotMenu) && !Verbs.openAt(run, fire.above())) return finish(run, "no_menu");
                int output = Verbs.slotFor(player, pot, CookingPotBlockEntity.OUTPUT_SLOT);
                if (output >= 0 && player.containerMenu.getSlot(output).hasItem()) { Verbs.quickMove(player, output); return false; }
                for (int slot = 0; slot < inputs.size(); slot++) {
                    if (!pot.getItem(slot).isEmpty()) continue;
                    String wanted = inputs.get(slot);
                    int from = Verbs.slotWith(player, player.getInventory(), stack -> stack.is(Verbs.item(wanted)));
                    if (from < 0) return finish(run, count(player, item) > start ? "made" : "short_of " + wanted);
                    Verbs.placeOne(player, from, Verbs.slotFor(player, pot, slot));
                }
                return false;
            }
            private boolean finish(PlayRun run, String end) {
                run.player().closeContainer();
                var event = run.event("cook");
                event.addProperty("item", item);
                var fire = run.camp.get(FIRE);
                if (fire != null) event.addProperty("at", fire.above().subtract(run.origin).toShortString());
                event.addProperty("wanted", missing);
                event.addProperty("made", count(run.player(), item) - start);
                event.addProperty("strikes", strikes);
                event.addProperty("end", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                run.write(event);
                return true;
            }
            @Override public void abandon(PlayRun run) { finish(run, "timeout"); }
        };
    }

    // ------------------------------------------------------------------ storage

    /**
     * Empties the bags into the player's crates through the crate's screen, keeping the working tools and weapons;
     * puts a crate down from the bags when none has room, or earns one. Null when there is no way to store anything.
     */
    static Step stash(PlayRun run, ServerPlayer player) {
        for (var pos : run.crates)
            if (run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate && hasRoom(crate))
                return Step.of("stash", run2 -> deposit(run2, run2.player(), pos));
        if (count(player, CRATE) > 0) return Step.of("place crate", run2 -> {
            var pos = run2.at(CRATE_X - run2.crates.size() * 2, 1, CRATE_Z);
            boolean placed = Verbs.placeItem(run2, CRATE, CRATE, pos);
            if (placed) run2.crates.add(pos);
            var event = run2.event("placed");
            event.addProperty("block", CRATE);
            event.addProperty("item", CRATE);
            event.addProperty("at", pos.subtract(run2.origin).toShortString());
            event.addProperty("result", placed ? "placed" : "refused");
            run2.write(event);
            if (!placed) run2.noCrate = true;
        });
        if (Verbs.find(CRATE) == null) { noCrate(run, "no_recipe"); return null; }
        // One try: a crate that cannot be earned stops the stashing (the goal goes on with full bags, then blocks).
        var earn = obtain(CRATE, 1, false);
        return new Step() {
            @Override public String name() { return "earn crate"; }
            @Override public int timeout() { return earn.timeout(); }
            @Override public boolean tick(PlayRun run2, int ticks) {
                if (!earn.tick(run2, ticks)) return false;
                if (count(run2.player(), CRATE) == 0) noCrate(run2, "not_earned");
                return true;
            }
            @Override public void abandon(PlayRun run2) { earn.abandon(run2); noCrate(run2, "timeout"); }
        };
    }

    private static void noCrate(PlayRun run, String why) {
        if (run.noCrate) return;
        run.noCrate = true;
        var event = run.event("no_crate");
        event.addProperty("why", why);
        run.write(event);
    }

    static int inCrates(PlayRun run, String item) {
        var wanted = Verbs.item(item);
        int total = 0;
        for (var pos : run.crates)
            if (run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate)
                for (int i = 0; i < crate.getContainerSize(); i++) if (crate.getItem(i).is(wanted)) total += crate.getItem(i).getCount();
        return total;
    }

    /** Takes what is needed back out of the player's crates, shift-clicking stacks out of each crate's screen. */
    static Step withdraw(String item, int missing) {
        return Step.of("withdraw " + item + " " + missing, run -> {
            var player = run.player();
            var wanted = Verbs.item(item);
            int start = count(player, item);
            for (var pos : run.crates) {
                if (count(player, item) - start >= missing) break;
                if (!(run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate) || !Verbs.openAt(run, pos)) continue;
                for (int i = 0; i < crate.getContainerSize() && count(player, item) - start < missing; i++)
                    if (crate.getItem(i).is(wanted)) Verbs.quickMove(player, Verbs.slotFor(player, crate, i));
                player.closeContainer();
            }
            var event = run.event("withdrawn");
            event.addProperty("item", item);
            event.addProperty("wanted", missing);
            event.addProperty("got", count(player, item) - start);
            run.write(event);
        });
    }

    private static boolean hasRoom(StorageCrateBlockEntity crate) {
        for (int i = 0; i < crate.getContainerSize(); i++) if (crate.getItem(i).isEmpty()) return true;
        return false;
    }

    private static void deposit(PlayRun run, ServerPlayer player, BlockPos pos) {
        if (!(run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity) || !Verbs.openAt(run, pos)) return;
        var inventory = player.getInventory();
        int moved = 0;
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty() || i == inventory.getSelectedSlot() || KEEP.contains(Verbs.id(stack.getItem())) || Verbs.isMeal(stack)) continue;
            Verbs.quickMove(player, Verbs.slotFor(player, inventory, i));
            if (inventory.getItem(i).isEmpty()) moved++;
        }
        player.closeContainer();
        var event = run.event("stashed");
        event.addProperty("stacks", moved);
        event.addProperty("crate", run.crates.indexOf(pos));
        run.write(event);
    }

    /** Drops lying around a spot are walked to the player, so the game's own pickup gives them. */
    static void pullDrops(PlayRun run, BlockPos around, double radius) {
        var player = run.player();
        for (var item : run.level().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(radius))) {
            item.setNoPickUpDelay();
            item.setPos(player.position());
            item.playerTouch(player);   // the game's own pickup, at once
        }
    }

    private Gathering() {}
}
