package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.station.StationBlock;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition;
import dev.nez.arksurvivalreturns.feature.station.WorkstationMenu;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The playthrough's verbs: each goes through the game's own code (block breaking with loot tables and tools, a
 * workstation's menu and craft packet, the equipment slots), and anything the run had to hand the player instead of
 * earning it is written down as given.
 */
public final class Verbs {
    // ------------------------------------------------------------------ run control

    public static Step stage(String name) { return Step.of("stage " + name, run -> { run.stage(name); run.write(run.event("stage")); }); }

    public static Step waitTicks(int ticks) {
        return new Step() {
            @Override public String name() { return "wait " + ticks; }
            @Override public boolean tick(PlayRun run, int done) { return done >= ticks; }
        };
    }

    public static Step command(String command) {
        return Step.of("command " + command, run -> run.server.getCommands().performPrefixedCommand(
                run.server.createCommandSourceStack().withSuppressedOutput(), "execute in minecraft:overworld run " + command));
    }

    /** Stands the player at an offset from the run's origin, looking this way. */
    public static Step teleport(double dx, double dy, double dz, float yaw, float pitch) {
        return Step.of("teleport " + dx + " " + dy + " " + dz, run -> tp(run, dx, dy, dz, yaw, pitch));
    }

    static void tp(PlayRun run, double dx, double dy, double dz, float yaw, float pitch) {
        var o = run.origin;
        run.server.getCommands().performPrefixedCommand(run.server.createCommandSourceStack().withSuppressedOutput(),
                "execute in minecraft:overworld run tp " + run.player().getScoreboardName() + " " + (o.getX() + 0.5 + dx) + " "
                        + (o.getY() + 1 + dy) + " " + (o.getZ() + 0.5 + dz) + " " + yaw + " " + pitch);
    }

    /** Asks the client for a picture after the view has settled ({@code settle} ticks), into the run's screenshots. */
    public static Step shot(String name, int settle) {
        return new Step() {
            @Override public String name() { return "shot " + name; }
            @Override public int timeout() { return settle + 200; }
            @Override public boolean tick(PlayRun run, int ticks) {
                if (ticks == settle) Playthrough.requestShot(name);
                if (ticks <= settle || Playthrough.pendingShot() != null) return false;
                var event = run.event("shot");
                event.addProperty("name", name);
                run.write(event);
                return true;
            }
        };
    }

    /** Inventory, level and what the player wears: a snapshot for the report. */
    public static Step checkpoint(String label) {
        return Step.of("checkpoint " + label, run -> {
            var player = run.player();
            var event = run.event("checkpoint");
            event.addProperty("label", label);
            var progress = ArkLevels.get(player);
            event.addProperty("level", progress.level());
            event.addProperty("armor", player.getAttributeValue(Attributes.ARMOR));
            event.addProperty("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            event.addProperty("health", player.getMaxHealth());
            event.add("inventory", inventory(player));
            run.write(event);
        });
    }

    // ------------------------------------------------------------------ the world

    /** Breaks a block of this kind {@code times} times at one spot with whatever is in hand, then picks the drops up. */
    public static Step breakBlock(Block block, int dx, int dz, int times) {
        String name = "break " + id(block) + " x" + times;
        return new Step() {
            Map<String, Integer> before;
            int broken;
            float progress;
            @Override public String name() { return name; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                var pos = run.at(dx, 1, dz);
                if (ticks == 0) {
                    before = counts(player);
                    tp(run, dx + 1.5, 0, dz, 90, 30);
                    return false;
                }
                if (broken < times) {
                    run.level().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                    run.level().setBlockAndUpdate(pos, block.defaultBlockState());
                    progress = run.level().getBlockState(pos).getDestroyProgress(player, run.level(), pos);
                    player.gameMode.destroyBlock(pos);
                    broken++;
                    return false;
                }
                // The drops are walked to the player so the game's own pickup gives them (and their collect triggers).
                for (var item : run.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
                    item.setNoPickUpDelay();
                    item.setPos(player.position());
                }
                if (ticks < times + 10) return false;
                var event = run.event("break");
                event.addProperty("block", id(block));
                event.addProperty("times", times);
                event.addProperty("tool", id(player.getMainHandItem().getItem()));
                event.addProperty("hand_ticks", progress <= 0 ? -1 : (int) Math.ceil(1 / progress));
                event.add("gained", difference(before, counts(player)));
                run.write(event);
                return true;
            }
        };
    }

    /**
     * Crafts an item at whichever workstation makes it: the station is put down beside the player, opened through its
     * block, and the craft goes through the same packet handler as a click. A level gate is written down and, so the run
     * goes on, the level is raised to what the craft asks ({@code forced}).
     */
    public static Step craft(String item, int times) {
        return Step.of("craft " + item + " x" + times, run -> {
            var player = run.player();
            var event = run.event("craft");
            event.addProperty("item", item);
            event.addProperty("times", times);
            var found = find(item);
            if (found == null) { event.addProperty("result", "no_station"); run.write(event); return; }
            var station = found.station();
            event.addProperty("station", station.station());
            event.addProperty("level", found.craft().level());
            event.add("cost", json(found.craft().variant().cost()));
            int menu = open(run, player, station.station());
            if (menu < 0) { event.addProperty("result", "no_menu"); run.write(event); return; }
            if (!ArkLevels.requires(player, found.craft().level())) {
                event.addProperty("gate", ArkLevels.get(player).level());
                ArkLevels.setLevel(player, found.craft().level());
                event.addProperty("forced", true);
            }
            var before = counts(player);
            boolean ok = WorkstationCrafting.craft(player, new WorkstationPayload.Craft(station.station(), item, times, found.key(), found.index(), menu));
            event.addProperty("result", ok ? "crafted" : "refused");
            event.add("changed", difference(before, counts(player)));
            player.closeContainer();
            run.write(event);
        });
    }

    record Found(WorkstationDefinition station, WorkstationDefinition.Craft craft, String key, int index) {}

    static Found find(String item) {
        for (var station : WorkstationCatalog.all().values())
            for (var craft : station.crafts()) {
                if (!craft.variant().item().equals(item) || craft.variant().apply()) continue;
                String key = "i:" + craft.category().id() + "/" + (craft.group() == null ? "" : craft.group().group() + "/") + craft.entry().family();
                return new Found(station, craft, key, craft.entry().variants().indexOf(craft.variant()));
            }
        return null;
    }

    /** Opens a station's menu the way a click does; the inventory's own graph needs no block. Returns the menu id. */
    static int open(PlayRun run, ServerPlayer player, String station) {
        player.closeContainer();
        if ("inventory".equals(station)) return player.containerMenu.containerId;
        Block block = null;
        for (var candidate : BuiltInRegistries.BLOCK) if (candidate instanceof StationBlock bench && station.equals(bench.stationId())) { block = candidate; break; }
        if (block == null) return -1;
        var pos = BlockPos.containing(player.position()).relative(player.getDirection(), 2);
        run.level().setBlockAndUpdate(pos, block.defaultBlockState());
        run.level().getBlockState(pos).useWithoutItem(run.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return player.containerMenu instanceof WorkstationMenu bench && bench.station.equals(station) ? bench.containerId : -1;
    }

    // ------------------------------------------------------------------ gear

    /** Puts on an armour set from the inventory; pieces not in it are given and written down as such. */
    public static Step equip(String... pieces) {
        return Step.of("equip " + String.join(",", pieces), run -> {
            var player = run.player();
            var event = run.event("equip");
            var given = new JsonArray();
            for (String piece : pieces) {
                var stack = take(player, item(piece));
                if (stack.isEmpty()) { stack = new ItemStack(item(piece)); given.add(piece); }
                var slot = player.getEquipmentSlotForItem(stack);
                var old = player.getItemBySlot(slot);
                if (!old.isEmpty()) player.getInventory().add(old);
                player.setItemSlot(slot, stack);
            }
            event.add("pieces", json(List.of(pieces)));
            event.add("given", given);
            event.addProperty("armor", player.getAttributeValue(Attributes.ARMOR));
            event.addProperty("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            run.write(event);
        });
    }

    public static Step unequip() {
        return Step.of("unequip", run -> {
            var player = run.player();
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                var old = player.getItemBySlot(slot);
                if (!old.isEmpty()) { player.getInventory().add(old.copy()); player.setItemSlot(slot, ItemStack.EMPTY); }
            }
        });
    }

    /** Takes a weapon in the main hand, from the inventory if it is there, else given. */
    public static Step wield(String weapon) {
        return Step.of("wield " + weapon, run -> {
            var player = run.player();
            var event = run.event("wield");
            event.addProperty("item", weapon);
            var stack = weapon.isEmpty() ? ItemStack.EMPTY : take(player, item(weapon));
            if (!weapon.isEmpty() && stack.isEmpty()) { stack = new ItemStack(item(weapon)); event.addProperty("given", true); }
            var old = player.getMainHandItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            if (!old.isEmpty()) player.getInventory().add(old);
            event.addProperty("damage", player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            event.addProperty("speed", player.getAttributeValue(Attributes.ATTACK_SPEED));
            run.write(event);
        });
    }

    // ------------------------------------------------------------------ supply drops

    /** Rolls a supply tier's loot table {@code times} times and writes each crate's contents. */
    public static Step lootRolls(SupplyTier tier, int times) {
        return Step.of("loot " + tier.id + " x" + times, run -> {
            var table = run.server.reloadableRegistries().getLootTable(tier.loot);
            for (int i = 0; i < times; i++) {
                var params = new LootParams.Builder(run.level()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(run.origin)).create(LootContextParamSets.CHEST);
                var counts = new LinkedHashMap<String, Integer>();
                for (var stack : table.getRandomItems(params)) counts.merge(id(stack.getItem()), stack.getCount(), Integer::sum);
                var event = run.event("loot");
                event.addProperty("tier", tier.id);
                event.addProperty("roll", i);
                event.add("items", json(counts));
                run.write(event);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    static String id(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }

    static ItemStack take(ServerPlayer player, Item item) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (inventory.getItem(i).is(item)) return inventory.removeItem(i, 1);
        return ItemStack.EMPTY;
    }

    static Map<String, Integer> counts(ServerPlayer player) {
        var counts = new LinkedHashMap<String, Integer>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty()) counts.merge(id(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    static JsonObject inventory(ServerPlayer player) { return json(counts(player)); }

    static JsonObject difference(Map<String, Integer> before, Map<String, Integer> after) {
        var out = new JsonObject();
        var keys = new java.util.LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (var key : keys) {
            int change = after.getOrDefault(key, 0) - before.getOrDefault(key, 0);
            if (change != 0) out.addProperty(key, change);
        }
        return out;
    }

    static JsonObject json(Map<String, Integer> map) {
        var out = new JsonObject();
        map.forEach(out::addProperty);
        return out;
    }

    static JsonArray json(List<String> list) {
        var out = new JsonArray();
        list.forEach(out::add);
        return out;
    }

    private Verbs() {}
}
