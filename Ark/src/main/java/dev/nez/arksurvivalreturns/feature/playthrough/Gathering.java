package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How the playthrough earns an item the way a player would. {@link #obtain} looks at the live inventory each time it is
 * free and picks the next thing to do: break the block the item comes from (on a spot that grows it back), hunt the
 * creature whose carcass drops it, put its raw form through the forge, crusher or drying rack, or, once every input is
 * in the bags, craft it at its workstation (through {@link Verbs#craft}). What has no known source, or does not come
 * after several tries, is given and written down as such, so the report can show every gap in the economy.
 */
public final class Gathering {
    sealed interface Source permits Breaks, Hunts, Processes {}
    record Breaks(String block) implements Source {}
    record Hunts(Species species) implements Source {}
    /** Station: forge, crusher or rack; the input is used one for one; fuel is logs, two per eight outputs plus one. */
    record Processes(String station, String input) implements Source {}

    static final String NS = "arksurvivalreturns:";
    static final Map<String, Source> SOURCES = new HashMap<>();
    static {
        breaks("rock", NS + "loose_rock");
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
        processes(NS + "bronze_ingot", "forge", NS + "bronze_blend");
        processes(NS + "tin_ingot", "forge", NS + "raw_tin");
        processes("minecraft:iron_ingot", "forge", "minecraft:raw_iron");
        processes("minecraft:copper_ingot", "forge", "minecraft:raw_copper");
        processes("minecraft:glass", "forge", "minecraft:sand");
        processes("minecraft:charcoal", "forge", "minecraft:oak_log");
        processes("minecraft:gunpowder", "crusher", NS + "sulphur");
        processes(NS + "dried_meat", "rack", NS + "raw_herbivore_meat");
    }
    private static void breaks(String item, String block) { SOURCES.put(item, new Breaks(block)); }
    private static void hunts(String item, Species species) { SOURCES.put(item, new Hunts(species)); }
    private static void processes(String item, String station, String input) { SOURCES.put(item, new Processes(station, input)); }

    static final String[] WEAPONS = {NS + "bronze_longsword", NS + "bronze_hammer", NS + "keratin_spear", NS + "rock_sword",
            NS + "flint_knife", NS + "stone_hatchet"};
    private static final int MAX_DEPTH = 10, STUCK = 3;
    /** Where gathering happens, from the run's origin: east of the fight lane. */
    static final int YARD_X = 30, YARD_Z = 140;

    // ------------------------------------------------------------------ obtain

    /** Have at least {@code count} of the item in the bags, earned where the game allows it. */
    public static Step obtain(String item, int count) {
        return new Step() {
            Step current;
            int currentTicks, repeats;
            String last;
            @Override public String name() { return "obtain " + item + " x" + count; }
            @Override public int timeout() { return 20 * 60 * 20; }
            @Override public boolean tick(PlayRun run, int ticks) {
                if (current != null) {
                    if (current.tick(run, currentTicks++) || currentTicks > current.timeout()) current = null;
                    return false;
                }
                var player = run.player();
                var next = next(player, item, count, 0);
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
                // The same action again and again without the bags changing: give what it was after and go on.
                current = repeats >= STUCK ? give(target(next), "stuck") : next;
                if (repeats >= STUCK) repeats = 0;
                currentTicks = 0;
                return false;
            }
        };
    }

    /** What an action was after, read back from its name ("verb item count" or "craft item xN"): "item count". */
    private static String target(Step step) {
        var words = step.name().split(" ");
        if (words.length < 2) return "";
        String amount = words.length > 2 ? words[2].replace("x", "") : "1";
        return words[1] + " " + (amount.chars().allMatch(Character::isDigit) ? amount : "1");
    }

    /** The first thing to do towards {@code count} of {@code item}, or null when the bags already hold it. */
    static Step next(ServerPlayer player, String item, int count, int depth) {
        int missing = count - count(player, item);
        if (missing <= 0) return null;
        if (depth > MAX_DEPTH) return give(item + " " + missing, "too_deep");
        var source = SOURCES.get(item);
        if (source instanceof Breaks breaks) return harvest(item, missing, breaks.block());
        if (source instanceof Hunts hunts) return hunt(item, missing, hunts.species());
        if (source instanceof Processes process) {
            var input = next(player, process.input(), missing, depth + 1);
            if (input != null) return input;
            int fuel = process.station().equals("forge") ? missing / 4 + 1 : 0;
            if (fuel > 0 && !process.input().equals("minecraft:oak_log")) {
                var logs = next(player, "minecraft:oak_log", fuel, depth + 1);
                if (logs != null) return logs;
            }
            return process(item, missing, process.station(), process.input(), fuel);
        }
        var found = Verbs.find(item);
        if (found != null) {
            int each = Math.max(1, found.craft().variant().count());
            int times = (missing + each - 1) / each;
            for (var cost : found.craft().variant().cost().entrySet()) {
                String needed = resolve(player, cost.getKey());
                if (needed == null) return give(cost.getKey() + " " + cost.getValue() * times, "unknown_cost");
                var step = next(player, needed, cost.getValue() * times, depth + 1);
                if (step != null) return step;
            }
            return Verbs.craft(item, times);
        }
        return give(item + " " + missing, "no_source");
    }

    /** An item for a cost key: the item itself, or for a tag the member held, else one with a source, else the first. */
    static String resolve(ServerPlayer player, String key) {
        if (!key.startsWith("#")) return BuiltInRegistries.ITEM.containsKey(Identifier.parse(key)) ? key : null;
        var tag = TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1)));
        String held = null, sourced = null, first = null;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            String id = Verbs.id(holder.value());
            if (first == null) first = id;
            if (sourced == null && (SOURCES.containsKey(id) || Verbs.find(id) != null)) sourced = id;
            if (held == null && count(player, id) > 0) held = id;
        }
        return held != null ? held : sourced != null ? sourced : first;
    }

    static int count(ServerPlayer player, String item) {
        var wanted = Verbs.item(item);
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(wanted)) total += inventory.getItem(i).getCount();
        return total;
    }

    // ------------------------------------------------------------------ the actions

    /** Gives what could not be earned, written down with the reason. {@code what} is "item count". */
    static Step give(String what, String why) {
        return Step.of("give " + what, run -> {
            var parts = what.split(" ");
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            var event = run.event("given");
            event.addProperty("item", parts[0]);
            event.addProperty("count", amount);
            event.addProperty("why", why);
            run.write(event);
            if (parts[0].startsWith("#") || !BuiltInRegistries.ITEM.containsKey(Identifier.parse(parts[0]))) return;
            var item = Verbs.item(parts[0]);
            while (amount > 0) {
                int stack = Math.min(amount, new ItemStack(item).getMaxStackSize());
                run.player().getInventory().placeItemBackInInventory(new ItemStack(item, stack));
                amount -= stack;
            }
        });
    }

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
                    cap = Math.max(24, missing * 8);
                    Verbs.tp(run, YARD_X + 1.5, 0, YARD_Z, 90, 30);
                    tool = bestTool(player, ripe(block));
                    return false;
                }
                pullDrops(run, pos, 4);
                if (count(player, item) >= target || tries >= cap) {
                    if (ticks < tries + 12 && count(player, item) < target) return false;
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
        float speed = 1f;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            float s = inventory.getItem(i).getDestroySpeed(state);
            if (s > speed) { speed = s; best = i; }
        }
        if (best >= 0 && best != inventory.getSelectedSlot()) {
            var tool = inventory.getItem(best).copy();
            inventory.setItem(best, player.getMainHandItem().copy());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        }
        return Verbs.id(player.getMainHandItem().getItem());
    }

    /** The best weapon in the bags, taken in hand. */
    static String bestWeapon(ServerPlayer player) {
        for (String weapon : WEAPONS) {
            if (player.getMainHandItem().is(Verbs.item(weapon))) return weapon;
            var stack = Verbs.take(player, Verbs.item(weapon));
            if (stack.isEmpty()) continue;
            var old = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
            return weapon;
        }
        return Verbs.id(player.getMainHandItem().getItem());
    }

    /** Hunts the species, frozen in the yard, with the best weapon carried, until its carcasses gave enough. */
    static Step hunt(String item, int missing, Species species) {
        return new Step() {
            int target, kills, hits;
            long started;
            CreatureEntity creature;
            String weapon = "";
            @Override public String name() { return "hunt " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 8; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                var spot = run.at(YARD_X, 1, YARD_Z + 20);
                if (ticks == 0) { target = count(player, item) + missing; started = level.getGameTime(); weapon = bestWeapon(player); }
                pullDrops(run, spot, 16);
                if (creature == null || creature.isRemoved()) {
                    if (count(player, item) >= target || kills >= 8) {
                        var event = run.event("hunt");
                        event.addProperty("item", item);
                        event.addProperty("species", species.id);
                        event.addProperty("wanted", missing);
                        event.addProperty("kills", kills);
                        event.addProperty("hits", hits);
                        event.addProperty("weapon", weapon);
                        event.addProperty("ticks", level.getGameTime() - started);
                        event.addProperty("short", Math.max(0, target - count(player, item)));
                        run.write(event);
                        return true;
                    }
                    creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.COMMAND);
                    if (creature == null) return true;
                    creature.initializeLevel(1);
                    creature.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
                    creature.setNoAi(true);
                    level.addFreshEntity(creature);
                    var body = dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder.extent(
                            dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder.extents(level), species);
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
        };
    }

    /** Puts the raw form through a station put down in the yard and waits for what it makes. */
    static Step process(String item, int missing, String station, String input, int fuel) {
        return new Step() {
            BlockPos pos;
            int made, target;
            long started;
            @Override public String name() { return "process " + item + " " + missing; }
            @Override public int timeout() { return 20 * 60 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var level = run.level();
                if (ticks == 0) {
                    pos = run.at(YARD_X - 6, 1, YARD_Z - 6);
                    var block = switch (station) {
                        case "forge" -> NS + "primitive_forge";
                        case "crusher" -> NS + "crusher";
                        default -> NS + "drying_rack";
                    };
                    level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).defaultBlockState());
                    Verbs.tp(run, YARD_X - 6, 0, YARD_Z - 3, 180, 30);
                    target = missing;
                    started = level.getGameTime();
                    return false;
                }
                var entity = level.getBlockEntity(pos);
                ItemStack out = ItemStack.EMPTY;
                switch (entity) {
                    case PrimitiveForgeBlockEntity forge -> {
                        if (forge.input().isEmpty()) feed(player, input, stack -> forge.insert(stack, PrimitiveForgeBlockEntity.INPUT));
                        if (forge.fuel().isEmpty() && fuel > 0) feed(player, "minecraft:oak_log", stack -> forge.insert(stack, PrimitiveForgeBlockEntity.FUEL));
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
                if (made >= target) return finish(run, "made");
                return false;
            }
            private boolean finish(PlayRun run, String end) {
                var level = run.level();
                // What is left in the station goes back to the bags with the block.
                level.destroyBlock(pos, true, run.player());
                pullDrops(run, pos, 3);
                var event = run.event("process");
                event.addProperty("item", item);
                event.addProperty("station", station);
                event.addProperty("input", input);
                event.addProperty("wanted", missing);
                event.addProperty("made", made);
                event.addProperty("end", end);
                event.addProperty("ticks", level.getGameTime() - started);
                run.write(event);
                return true;
            }
        };
    }

    /** Hands a station the whole stack of one item from the bags; what it does not take goes back. */
    private static void feed(ServerPlayer player, String item, java.util.function.ToIntFunction<ItemStack> insert) {
        var inventory = player.getInventory();
        var wanted = Verbs.item(item);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(wanted)) continue;
            int taken = insert.applyAsInt(stack.copy());
            stack.shrink(taken);
            return;
        }
    }

    /** Drops lying around a spot are walked to the player, so the game's own pickup gives them. */
    static void pullDrops(PlayRun run, BlockPos around, double radius) {
        var player = run.player();
        for (var item : run.level().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(radius))) {
            item.setNoPickUpDelay();
            item.setPos(player.position());
        }
    }

    static Vec3 centre(BlockPos pos) { return Vec3.atBottomCenterOf(pos); }

    private Gathering() {}
}
