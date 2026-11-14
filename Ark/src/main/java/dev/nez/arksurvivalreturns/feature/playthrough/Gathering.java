package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How the playthrough earns an item the way a survival player does, and nothing else: nothing is ever given and no
 * level is ever set. {@link #obtain} looks at the live bags each time it is free and picks the next thing to do:
 * break the block the item comes from (on a spot of the yard that is put back after each break) with the right tool,
 * earned first when the block needs one (logs an axe, stone a pickaxe); hunt the creature whose carcass drops it (put
 * frozen in the yard, killed with the best weapon carried); put its raw form through the camp's forge, crusher or
 * drying rack; or craft it at its workstation once every input is in the bags.
 *
 * <p>Every station is itself earned and put down in the camp from the bags, and stays there. A level gate is met by
 * hunting for experience. Bags that fill up are emptied into Storage Crates the player earned. What cannot be earned
 * ends the goal as blocked, with the reason, so the report shows each gap in the economy.
 */
public final class Gathering {
    sealed interface Source permits Breaks, Hunts, Processes, Cooks, Fills {}
    record Breaks(String block) implements Source {}
    record Hunts(Species species) implements Source {}
    /** Station block: forge, crusher, drying rack or stonecutter; the forge burns logs; the cutter uses its own recipes. */
    record Processes(String station, String input) implements Source {}
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
        processes("minecraft:gunpowder", CRUSHER, NS + "sulphur");
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
    private static void processes(String item, String station, String input) { SOURCES.put(item, new Processes(station, input)); }

    /** Best first: the weapon taken in hand to hunt. */
    static final String[] WEAPONS = {NS + "bronze_longsword", NS + "bronze_hammer", NS + "keratin_spear", NS + "rock_sword",
            NS + "flint_knife", NS + "stone_hatchet"};
    /** Each kind of tool by tier, lowest first: the first one not yet carried is earned for a block that needs it. */
    static final Map<TagKey<Block>, List<String>> TOOLS = Map.of(
            BlockTags.MINEABLE_WITH_AXE, List.of(NS + "stone_hatchet", NS + "bronze_axe", "minecraft:iron_axe"),
            BlockTags.MINEABLE_WITH_PICKAXE, List.of(NS + "rock_pickaxe", NS + "bronze_pickaxe", "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe"),
            BlockTags.MINEABLE_WITH_SHOVEL, List.of(NS + "rock_shovel", NS + "bronze_shovel", "minecraft:iron_shovel"));
    /** Items whose block gives them only to this tool in hand (the grass tuft to shears). */
    static final Map<String, String> HELD = Map.of("minecraft:short_grass", "minecraft:shears");
    /** Kept in the bags when they are emptied into crates: the tools and weapons the run works with. */
    static final Set<String> KEEP = Set.of(NS + "bronze_longsword", NS + "bronze_hammer", NS + "keratin_spear", NS + "rock_sword",
            NS + "flint_knife", NS + "stone_hatchet", NS + "bronze_axe", NS + "bronze_pickaxe", NS + "bronze_shovel",
            NS + "rock_pickaxe", NS + "rock_shovel", "minecraft:shears", "minecraft:iron_pickaxe", "minecraft:iron_axe",
            "minecraft:iron_shovel", "minecraft:diamond_pickaxe");
    private static final int MAX_DEPTH = 12, STUCK = 2, FREE_SLOTS = 6, GRIND_LEVEL = 5, GRIND_CAP = 40;
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
                    if (current.tick(run, currentTicks++) || currentTicks > current.timeout()) current = null;
                    return false;
                }
                var player = run.player();
                pullDrops(run, player.blockPosition(), 6);
                if (mayStash && freeSlots(player) < FREE_SLOTS) {
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
        if (source instanceof Hunts hunts) return hunt(item, missing, hunts.species());
        if (source instanceof Fills fills) {
            var bottles = next(run, player, fills.container(), missing, depth + 1);
            return bottles != null ? bottles : fill(item, missing, fills.container());
        }
        if (source instanceof Processes process) {
            var station = station(run, player, process.station(), depth);
            if (station != null) return station;
            var input = next(run, player, process.input(), missing, depth + 1);
            if (input != null) return input;
            int fuel = process.station().equals(FORGE) ? missing / 4 + 1 : 0;
            if (fuel > 0 && !process.input().equals("minecraft:oak_log")) {
                var logs = next(run, player, "minecraft:oak_log", fuel, depth + 1);
                if (logs != null) return logs;
            }
            return process(item, missing, process.station(), process.input(), fuel);
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
            if (!ArkLevels.requires(player, level)) return grind(level);
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

    /** Null when the station stands in the camp; else the step towards it (earn its item, or put it down). */
    static Step station(PlayRun run, ServerPlayer player, String block, int depth) {
        var pos = run.camp.get(block);
        if (pos != null && BuiltInRegistries.BLOCK.getKey(run.level().getBlockState(pos).getBlock()).toString().equals(block)) return null;
        String item = Verbs.id(BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).asItem());
        if (count(player, item) > 0) return place(block, item);
        return next(run, player, item, 1, depth + 1);
    }

    /** Puts a station down in the camp from the bags; it stays there for the rest of the run. */
    static Step place(String block, String item) {
        return Step.of("place " + block, run -> {
            var player = run.player();
            if (Verbs.take(player, Verbs.item(item)).isEmpty()) return;
            int i = run.camp.size();
            var fire = run.camp.get(FIRE);
            // The pot cooks only standing on the fire.
            var pos = block.equals(POT) && fire != null ? fire.above() : run.at(CAMP_X + (i % 8) * 3, 1, CAMP_Z + (i / 8) * 3);
            if (!block.equals(POT)) run.level().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            run.level().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).defaultBlockState());
            run.camp.put(block, pos);
            var event = run.event("placed");
            event.addProperty("block", block);
            event.addProperty("at", pos.subtract(run.origin).toShortString());
            run.write(event);
        });
    }

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

    /** Takes an item from the bags in hand; returns what is in hand. */
    static String hold(ServerPlayer player, String item) {
        if (!player.getMainHandItem().is(Verbs.item(item)))
            for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(Verbs.item(item))) { swapIntoHand(player, i); break; }
        return Verbs.id(player.getMainHandItem().getItem());
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

    /** The bag item that breaks this block fastest, taken in hand. */
    static String bestTool(ServerPlayer player, BlockState state) {
        var inventory = player.getInventory();
        int best = -1;
        float speed = player.getMainHandItem().getDestroySpeed(state);
        for (int i = 0; i < 36; i++) {
            float s = inventory.getItem(i).getDestroySpeed(state);
            if (s > speed && i != inventory.getSelectedSlot()) { speed = s; best = i; }
        }
        if (best >= 0) swapIntoHand(player, best);
        return Verbs.id(player.getMainHandItem().getItem());
    }

    /** The best weapon in the bags, taken in hand. */
    static String bestWeapon(ServerPlayer player) {
        var inventory = player.getInventory();
        for (String weapon : WEAPONS) {
            if (player.getMainHandItem().is(Verbs.item(weapon))) return weapon;
            for (int i = 0; i < 36; i++) if (inventory.getItem(i).is(Verbs.item(weapon))) { swapIntoHand(player, i); return weapon; }
        }
        return Verbs.id(player.getMainHandItem().getItem());
    }

    static void swapIntoHand(ServerPlayer player, int slot) {
        var inventory = player.getInventory();
        if (slot == inventory.getSelectedSlot()) return;
        var tool = inventory.getItem(slot).copy();
        inventory.setItem(slot, player.getMainHandItem().copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
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
                    tool = HELD.containsKey(item) ? hold(player, HELD.get(item)) : bestTool(player, ripe(block));
                    return false;
                }
                pullDrops(run, pos, 4);
                if (count(player, item) >= target || tries >= cap) {
                    if (count(player, item) < target && ticks < tries + 12) return false;
                    var event = run.event("harvest");
                    event.addProperty("item", item);
                    event.addProperty("block", blockId);
                    event.addProperty("wanted", missing);
                    event.addProperty("tries", tries);
                    event.addProperty("tool", tool);
                    event.addProperty("hand_ticks", progress <= 0 ? -1 : (int) Math.ceil(1 / progress));
                    event.addProperty("short", Math.max(0, target - count(player, item)));
                    run.write(event);
                    return true;
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
        };
    }

    /** Hunts the species, frozen in the yard, with the best weapon carried, until its carcasses gave enough. */
    static Step hunt(String item, int missing, Species species) {
        return new Killing("hunt " + item + " " + missing, "hunt", species, 1, 8) {
            int target;
            @Override void begin(PlayRun run) { target = count(run.player(), item) + missing; }
            @Override boolean enough(PlayRun run) { return count(run.player(), item) >= target; }
            @Override void end(PlayRun run, JsonObject event) {
                event.addProperty("item", item);
                event.addProperty("wanted", missing);
                event.addProperty("short", Math.max(0, target - count(run.player(), item)));
            }
        };
    }

    /** Hunts for experience until the player reaches the level a craft asks: the experience wall, measured. */
    static Step grind(int level) {
        return new Killing("grind level " + level, "grind", Species.PARASAUR, GRIND_LEVEL, GRIND_CAP) {
            int from;
            @Override void begin(PlayRun run) { from = ArkLevels.get(run.player()).level(); }
            @Override boolean enough(PlayRun run) { return ArkLevels.requires(run.player(), level); }
            @Override void end(PlayRun run, JsonObject event) {
                event.addProperty("from_level", from);
                event.addProperty("to_level", ArkLevels.get(run.player()).level());
                event.addProperty("wanted_level", level);
            }
        };
    }

    /** Kills one frozen creature after another in the yard, picking up what they drop, until enough or the cap. */
    abstract static class Killing implements Step {
        final String name, type;
        final Species species;
        final int creatureLevel, cap;
        int kills, hits;
        long started;
        CreatureEntity creature;
        String weapon = "";

        Killing(String name, String type, Species species, int creatureLevel, int cap) {
            this.name = name; this.type = type; this.species = species; this.creatureLevel = creatureLevel; this.cap = cap;
        }

        abstract void begin(PlayRun run);
        abstract boolean enough(PlayRun run);
        abstract void end(PlayRun run, JsonObject event);

        @Override public String name() { return name; }
        @Override public int timeout() { return 20 * 60 * 15; }
        @Override public boolean tick(PlayRun run, int ticks) {
            var player = run.player();
            var level = run.level();
            var spot = run.at(YARD_X, 1, YARD_Z + 20);
            if (ticks == 0) { begin(run); started = level.getGameTime(); weapon = bestWeapon(player); }
            pullDrops(run, spot, 16);
            if (creature == null || creature.isRemoved()) {
                if (enough(run) || kills >= cap) {
                    var event = run.event(type);
                    event.addProperty("species", species.id);
                    event.addProperty("creature_level", creatureLevel);
                    event.addProperty("kills", kills);
                    event.addProperty("hits", hits);
                    event.addProperty("weapon", weapon);
                    event.addProperty("ticks", level.getGameTime() - started);
                    end(run, event);
                    run.write(event);
                    return true;
                }
                creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.COMMAND);
                if (creature == null) return true;
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
    }

    /** Puts the raw form through the camp's station and waits for what it makes. */
    static Step process(String item, int missing, String station, String input, int fuel) {
        return new Step() {
            int made;
            long started;
            @Override public String name() { return "process " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                var pos = run.camp.get(station);
                if (pos == null) return finish(run, "no_station");
                if (ticks == 0) { standAt(run, pos); started = level.getGameTime(); return false; }
                if (station.equals(STONECUTTER)) {
                    // The vanilla cutter's own recipes: one input block per cut, its count of pieces out.
                    var one = Verbs.take(player, Verbs.item(input));
                    if (one.isEmpty()) return finish(run, "short_of " + input);
                    var cut = cut(level, one, item);
                    if (cut.isEmpty()) { player.getInventory().placeItemBackInInventory(one); return finish(run, "no_cut"); }
                    made += cut.getCount();
                    player.getInventory().placeItemBackInInventory(cut);
                    return made >= missing && finish(run, "made");
                }
                ItemStack out = ItemStack.EMPTY;
                switch (level.getBlockEntity(pos)) {
                    case PrimitiveForgeBlockEntity forge -> {
                        if (forge.input().isEmpty()) feed(player, input, stack -> forge.insert(stack, PrimitiveForgeBlockEntity.INPUT));
                        if (forge.fuel().isEmpty() && !forge.isBurning() && fuel > 0) feed(player, "minecraft:oak_log", stack -> forge.insert(stack, PrimitiveForgeBlockEntity.FUEL));
                        if (!forge.output().isEmpty()) out = forge.extract();
                    }
                    case CrusherBlockEntity crusher -> {
                        if (crusher.getItem(CrusherBlockEntity.INPUT).isEmpty()) feed(player, input, crusher::insert);
                        out = crusher.takeOutput();
                    }
                    case DryingRackBlockEntity rack -> {
                        if (rack.input().isEmpty()) feed(player, input, rack::insert);
                        if (!rack.output().isEmpty()) out = rack.extract();
                    }
                    case null, default -> { return finish(run, "no_station"); }
                }
                if (!out.isEmpty()) {
                    if (out.is(Verbs.item(item))) made += out.getCount();
                    player.getInventory().placeItemBackInInventory(out);
                }
                if (made >= missing) return finish(run, "made");
                return false;
            }
            private boolean finish(PlayRun run, String end) {
                var event = run.event("process");
                event.addProperty("item", item);
                event.addProperty("station", station);
                event.addProperty("input", input);
                event.addProperty("wanted", missing);
                event.addProperty("made", made);
                event.addProperty("end", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                run.write(event);
                return true;
            }
        };
    }

    /** Fills bottles at a water source in the yard, standing over it and using the bottle the way a click does. */
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
                hold(player, container);
                if (!player.getMainHandItem().is(Verbs.item(container))) return false;
                player.getMainHandItem().use(run.level(), player, InteractionHand.MAIN_HAND);
                uses++;
                return false;
            }
        };
    }

    /**
     * Cooks a meal: the fire fed with logs and lit with the Fire Starter (the item's own use, which can fail and is
     * tried again), one of each input put in the pot's four slots, the meal taken out once done.
     */
    static Step cook(String item, int missing, List<String> inputs) {
        return new Step() {
            int made, strikes;
            long started;
            @Override public String name() { return "cook " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                var fire = run.camp.get(FIRE);
                if (fire == null || !(level.getBlockEntity(fire) instanceof StoneFireBlockEntity hearth)
                        || !(level.getBlockEntity(fire.above()) instanceof CookingPotBlockEntity pot)) return finish(run, "no_station");
                if (ticks == 0) { standAt(run, fire); started = level.getGameTime(); return false; }
                if (hearth.burnTicks() < 400) {
                    var log = Verbs.take(player, Verbs.item("minecraft:oak_log"));
                    if (!log.isEmpty() && !hearth.addFuel(log)) player.getInventory().placeItemBackInInventory(log);
                }
                if (!hearth.isLit() && ticks % 10 == 0) {
                    var inventory = player.getInventory();
                    for (int i = 0; i < 36; i++) if (inventory.getItem(i).is(Verbs.item(FIRE_STARTER))) { swapIntoHand(player, i); break; }
                    if (!player.getMainHandItem().is(Verbs.item(FIRE_STARTER))) return finish(run, "no_fire_starter");
                    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(fire), Direction.UP, fire, false)));
                    if (++strikes > 60) return finish(run, "not_lit");
                }
                var out = pot.getItem(CookingPotBlockEntity.OUTPUT_SLOT);
                if (!out.isEmpty()) {
                    var taken = pot.removeItem(CookingPotBlockEntity.OUTPUT_SLOT, 64);
                    if (taken.is(Verbs.item(item))) made += taken.getCount();
                    player.getInventory().placeItemBackInInventory(taken);
                    if (made >= missing) return finish(run, "made");
                }
                for (int slot = 0; slot < inputs.size(); slot++) {
                    if (!pot.getItem(slot).isEmpty()) continue;
                    var one = Verbs.take(player, Verbs.item(inputs.get(slot)));
                    if (one.isEmpty()) return finish(run, made > 0 ? "made" : "short_of " + inputs.get(slot));
                    pot.setItem(slot, one);
                }
                return false;
            }
            private boolean finish(PlayRun run, String end) {
                var event = run.event("cook");
                event.addProperty("item", item);
                event.addProperty("wanted", missing);
                event.addProperty("made", made);
                event.addProperty("strikes", strikes);
                event.addProperty("end", end);
                event.addProperty("ticks", run.level().getGameTime() - started);
                run.write(event);
                return true;
            }
        };
    }

    /** What the stonecutter's recipes make of one input block, if one of them makes the wanted item. */
    static ItemStack cut(net.minecraft.server.level.ServerLevel level, ItemStack input, String wanted) {
        var single = new net.minecraft.world.item.crafting.SingleRecipeInput(input);
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof net.minecraft.world.item.crafting.StonecutterRecipe recipe && recipe.input().test(input)) {
                var out = recipe.assemble(single);
                if (out.is(Verbs.item(wanted))) return out;
            }
        return ItemStack.EMPTY;
    }

    /** Hands a station one stack of an item from the bags; what it does not take stays. */
    private static void feed(ServerPlayer player, String item, java.util.function.ToIntFunction<ItemStack> insert) {
        var inventory = player.getInventory();
        var wanted = Verbs.item(item);
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(wanted)) continue;
            stack.shrink(insert.applyAsInt(stack.copy()));
            return;
        }
    }

    // ------------------------------------------------------------------ storage

    /**
     * Empties the bags into the player's crates, keeping the working tools and weapons; puts a crate down from the bags
     * when none has room, or earns one. Null when there is no way to store anything.
     */
    static Step stash(PlayRun run, ServerPlayer player) {
        for (var pos : run.crates)
            if (run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate && hasRoom(crate))
                return Step.of("stash", run2 -> deposit(run2, run2.player(), pos));
        if (count(player, CRATE) > 0) return Step.of("place crate", run2 -> {
            if (Verbs.take(run2.player(), Verbs.item(CRATE)).isEmpty()) return;
            var pos = run2.at(CRATE_X - run2.crates.size() * 2, 1, CRATE_Z);
            run2.level().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(CRATE)).defaultBlockState());
            run2.crates.add(pos);
            var event = run2.event("placed");
            event.addProperty("block", CRATE);
            event.addProperty("at", pos.subtract(run2.origin).toShortString());
            run2.write(event);
        });
        if (Verbs.find(CRATE) == null) return null;
        return obtain(CRATE, 1, false);
    }

    static int inCrates(PlayRun run, String item) {
        var wanted = Verbs.item(item);
        int total = 0;
        for (var pos : run.crates)
            if (run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate)
                for (int i = 0; i < crate.getContainerSize(); i++) if (crate.getItem(i).is(wanted)) total += crate.getItem(i).getCount();
        return total;
    }

    /** Takes what is needed back out of the player's crates. */
    static Step withdraw(String item, int missing) {
        return Step.of("withdraw " + item + " " + missing, run -> {
            var wanted = Verbs.item(item);
            int left = missing;
            for (var pos : run.crates) {
                if (!(run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate)) continue;
                for (int i = 0; i < crate.getContainerSize() && left > 0; i++) {
                    var stack = crate.getItem(i);
                    if (!stack.is(wanted)) continue;
                    var taken = stack.split(Math.min(left, stack.getCount()));
                    left -= taken.getCount();
                    run.player().getInventory().placeItemBackInInventory(taken);
                }
                crate.setChanged();
            }
        });
    }

    private static boolean hasRoom(StorageCrateBlockEntity crate) {
        for (int i = 0; i < crate.getContainerSize(); i++) if (crate.getItem(i).isEmpty()) return true;
        return false;
    }

    private static void deposit(PlayRun run, ServerPlayer player, BlockPos pos) {
        if (!(run.level().getBlockEntity(pos) instanceof StorageCrateBlockEntity crate)) return;
        var inventory = player.getInventory();
        int moved = 0;
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty() || i == inventory.getSelectedSlot() || KEEP.contains(Verbs.id(stack.getItem()))) continue;
            for (int slot = 0; slot < crate.getContainerSize() && !stack.isEmpty(); slot++) {
                var there = crate.getItem(slot);
                if (there.isEmpty()) { crate.setItem(slot, stack.copy()); stack.setCount(0); moved++; }
                else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                    int room = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                    there.grow(room);
                    stack.shrink(room);
                }
            }
        }
        crate.setChanged();
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
