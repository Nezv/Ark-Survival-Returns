package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.station.InventoryCrafting;
import dev.nez.arksurvivalreturns.feature.station.StationBlock;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition;
import dev.nez.arksurvivalreturns.feature.station.WorkstationMenu;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The playthrough's verbs. Each goes through the path a real client's packet takes on the server: a block broken by
 * the game mode, a right-click on a block or with an item ({@code ServerPlayerGameMode.useItemOn / useItem}), a click
 * in an open menu ({@code AbstractContainerMenu.clicked}, as the container-click packet does), a hotbar change, a
 * workstation's craft packet. Nothing is ever handed to the player and no block is set for them.
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
        var player = run.player();
        player.teleportTo(run.level(), o.getX() + 0.5 + dx, o.getY() + 1 + dy, o.getZ() + 0.5 + dz, java.util.Set.of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    /**
     * Holds the player in the air for a picture from above, flying, so nothing falls; {@link #land} puts them back on
     * the ground with flight off. (A survival player sent 30 blocks up without this fell to their death.)
     */
    public static Step hover(double dx, double dy, double dz, float yaw, float pitch) {
        return Step.of("hover " + dx + " " + dy + " " + dz, run -> {
            var player = run.player();
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            tp(run, dx, dy, dz, yaw, pitch);
        });
    }

    public static Step land(double dx, double dz) {
        return Step.of("land", run -> {
            tp(run, dx, 0, dz, 180, 10);
            var player = run.player();
            if (!Playthrough.headless) {
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
            }
        });
    }

    /** Asks the client for a picture after the view has settled ({@code settle} ticks), into the run's screenshots. */
    public static Step shot(String name, int settle) { return client("shot:" + name, settle); }

    /** Asks the client for something (see {@link Playthrough#pendingRequest}) and writes which screen was open. */
    public static Step client(String what, int settle) {
        return new Step() {
            @Override public String name() { return "client " + what; }
            @Override public int timeout() { return settle + 200; }
            @Override public boolean tick(PlayRun run, int ticks) {
                if (Playthrough.headless) {
                    var event = run.event("client");
                    event.addProperty("request", what);
                    event.addProperty("skipped", "headless");
                    run.write(event);
                    return true;
                }
                if (ticks == settle) Playthrough.request(what);
                if (ticks <= settle || Playthrough.pendingRequest() != null) return false;
                var event = run.event("client");
                event.addProperty("request", what);
                event.addProperty("screen", Playthrough.clientScreen());
                run.write(event);
                return true;
            }
        };
    }

    /** Inventory, level (earned and lifted XP), food and what the player wears: a snapshot for the report. */
    public static Step checkpoint(String label) {
        return Step.of("checkpoint " + label, run -> {
            var player = run.player();
            var event = run.event("checkpoint");
            event.addProperty("label", label);
            var progress = ArkLevels.get(player);
            event.addProperty("level", progress.level());
            event.addProperty("earned_xp", run.earnedXp(player));
            event.addProperty("supplied_xp", run.suppliedXp);
            event.addProperty("food", player.getFoodData().getFoodLevel());
            event.addProperty("armor", player.getAttributeValue(Attributes.ARMOR));
            event.addProperty("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            event.addProperty("health", player.getMaxHealth());
            event.add("inventory", inventory(player));
            run.write(event);
        });
    }

    // ------------------------------------------------------------------ the hand and the hotbar

    /**
     * Brings an item into the main hand the way a player does: picked on the hotbar if it is there, else swapped onto
     * the selected hotbar slot from the bags with the inventory screen's number-key swap. False when the bags hold none.
     */
    static boolean select(ServerPlayer player, Item item) {
        if (player.getMainHandItem().is(item)) return true;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) if (inventory.getItem(slot).is(item)) { pick(player, slot); return true; }
        for (int slot = 9; slot < 36; slot++)
            if (inventory.getItem(slot).is(item)) {
                player.closeContainer();
                player.inventoryMenu.clicked(slot, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
                player.inventoryMenu.broadcastChanges();
                return player.getMainHandItem().is(item);
            }
        return false;
    }

    static boolean select(ServerPlayer player, String item) { return select(player, item(item)); }

    /** A hotbar slot picked, as the held-item packet does; the client is told, the way the server tells it on a join. */
    static void pick(ServerPlayer player, int slot) {
        if (player.getInventory().getSelectedSlot() == slot) return;
        player.getInventory().setSelectedSlot(slot);
        if (player.connection != null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(slot));
    }

    /** An empty hand: an empty hotbar slot picked, else the held stack swapped into an empty slot of the bags. */
    static boolean emptyHand(ServerPlayer player) {
        if (player.getMainHandItem().isEmpty()) return true;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) if (inventory.getItem(slot).isEmpty()) { pick(player, slot); return true; }
        for (int slot = 9; slot < 36; slot++)
            if (inventory.getItem(slot).isEmpty()) {
                player.closeContainer();
                player.inventoryMenu.clicked(slot, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
                player.inventoryMenu.broadcastChanges();
                return player.getMainHandItem().isEmpty();
            }
        return false;
    }

    /** A right-click on a block's face, with this item in hand (null: an empty hand), through the game mode. */
    static InteractionResult use(PlayRun run, BlockPos pos, Direction face, Vec3 at, String item) {
        var player = run.player();
        if (item == null ? !emptyHand(player) : !select(player, item)) return InteractionResult.FAIL;
        var result = player.gameMode.useItemOn(player, run.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(at, face, pos, false));
        player.inventoryMenu.broadcastChanges();
        return result;
    }

    /** A right-click on the middle of a block's south face (the side the player stands on). */
    static InteractionResult use(PlayRun run, BlockPos pos, String item) {
        return use(run, pos, Direction.SOUTH, new Vec3(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 1.0), item);
    }

    /** A right-click in the air with an item: eating, drinking, putting armour on, drawing a bow. */
    static InteractionResult useItem(ServerPlayer player, String item) {
        if (!select(player, item)) return InteractionResult.FAIL;
        return player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /**
     * Puts a block item down where a player would: standing south of the spot, facing it, right-clicking the top of
     * the block under it (so placement rules, the block's own {@code setPlacedBy} and the place event all run).
     * Returns whether the block now stands there.
     */
    static boolean placeItem(PlayRun run, String blockId, String item, BlockPos pos) {
        var player = run.player();
        player.closeContainer();
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
        tp(run, pos.getX() - run.origin.getX(), pos.getY() - run.origin.getY() - 1, pos.getZ() - run.origin.getZ() + 2.5, 180, 40);
        var ground = pos.below();
        use(run, ground, Direction.UP, new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5), item);
        return run.level().getBlockState(pos).is(block);
    }

    // ------------------------------------------------------------------ menus

    /** The slot of the open menu that shows this container's index, or -1. */
    static int slotFor(ServerPlayer player, Container container, int index) {
        var menu = player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (slot.container == container && slot.getContainerSlot() == index) return i;
        }
        return -1;
    }

    /** The first slot of the open menu over this container holding a matching stack, or -1. */
    static int slotWith(ServerPlayer player, Container container, Predicate<ItemStack> wanted) {
        var menu = player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (slot.container == container && !slot.getItem().isEmpty() && wanted.test(slot.getItem())) return i;
        }
        return -1;
    }

    /** A shift-click on a slot of the open menu. */
    static void quickMove(ServerPlayer player, int slot) {
        if (slot < 0) return;
        player.containerMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
        player.containerMenu.broadcastChanges();
    }

    /** One item from one slot into another: pick the stack up, right-click one onto the target, put the rest back. */
    static void placeOne(ServerPlayer player, int from, int to) {
        if (from < 0 || to < 0) return;
        var menu = player.containerMenu;
        menu.clicked(from, 0, ContainerInput.PICKUP, player);
        menu.clicked(to, 1, ContainerInput.PICKUP, player);
        menu.clicked(from, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            var carried = menu.getCarried();
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(carried);
        }
        menu.broadcastChanges();
    }

    /** Opens a block's screen with an empty hand, standing beside it; true when a menu other than the bags opened. */
    static boolean openAt(PlayRun run, BlockPos pos) {
        var player = run.player();
        player.closeContainer();
        Gathering.standAt(run, pos);
        use(run, pos, null);
        return player.containerMenu != player.inventoryMenu;
    }

    // ------------------------------------------------------------------ crafting

    /**
     * Crafts an item at whichever workstation makes it: its screen is opened by a click on the station the player put
     * down in the camp, and the craft goes through the same packet handler as the screen's button. The station's place
     * is written down, so the report can check it is one the player earned and placed.
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
            if (!InventoryCrafting.STATION.equals(station.station())) {
                var pos = run.camp.get(benchBlock(station.station()));
                if (pos != null) event.addProperty("at", pos.subtract(run.origin).toShortString());
            }
            if (menu < 0) { event.addProperty("result", "no_menu"); run.write(event); return; }
            if (!ArkLevels.requires(player, found.craft().level())) event.addProperty("gate", ArkLevels.get(player).level());
            var before = counts(player);
            boolean ok = WorkstationCrafting.craft(player, new WorkstationPayload.Craft(station.station(), item, times, found.key(), found.index(), menu));
            event.addProperty("result", ok ? "crafted" : "refused");
            event.add("changed", difference(before, counts(player)));
            player.closeContainer();
            run.write(event);
        });
    }

    /** The block id of the workstation with this station id, or null. */
    static String benchBlock(String station) {
        for (var candidate : BuiltInRegistries.BLOCK)
            if (candidate instanceof StationBlock bench && station.equals(bench.stationId())) return id(candidate);
        return null;
    }

    record Found(WorkstationDefinition station, WorkstationDefinition.Craft craft, String key, int index) {}

    /**
     * Where an item is crafted: of its recipes, one whose every input has a known way in (gathered, hunted, processed or
     * crafted) first, then the inventory's own graph, then the lowest level.
     */
    static Found find(String item) {
        Found best = null;
        int bestScore = Integer.MIN_VALUE;
        for (var station : WorkstationCatalog.all().values())
            for (var craft : station.crafts()) {
                if (!craft.variant().item().equals(item) || craft.variant().apply()) continue;
                String key = "i:" + craft.category().id() + "/" + (craft.group() == null ? "" : craft.group().group() + "/") + craft.entry().family();
                int score = (inputsKnown(craft) ? 10000 : 0) + (InventoryCrafting.STATION.equals(station.station()) ? 1000 : 0) - craft.level();
                if (score > bestScore) { bestScore = score; best = new Found(station, craft, key, craft.entry().variants().indexOf(craft.variant())); }
            }
        return best;
    }

    private static boolean inputsKnown(WorkstationDefinition.Craft craft) {
        for (String key : craft.variant().cost().keySet()) if (!known(key)) return false;
        return true;
    }

    /** Whether a cost key has a way in: a source, or some bench makes it (one level deep, no recursion). */
    private static boolean known(String key) {
        if (!key.startsWith("#")) return Gathering.SOURCES.containsKey(key) || made().contains(key);
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(key.substring(1)));
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) if (known(id(holder.value()))) return true;
        return false;
    }

    private static java.util.Set<String> made;
    private static Object madeFrom;

    private static java.util.Set<String> made() {
        var all = WorkstationCatalog.all();
        if (made == null || madeFrom != all) {
            var out = new java.util.HashSet<String>();
            for (var station : all.values()) for (var craft : station.crafts()) out.add(craft.variant().item());
            made = out;
            madeFrom = all;
        }
        return made;
    }

    /** Opens a station's menu the way a click does, standing beside it in the camp. Returns the menu id, or -1. */
    static int open(PlayRun run, ServerPlayer player, String station) {
        player.closeContainer();
        if (InventoryCrafting.STATION.equals(station)) InventoryCrafting.open(player);
        else {
            String block = benchBlock(station);
            var pos = block == null ? null : run.camp.get(block);
            if (pos == null) return -1;
            openAt(run, pos);
        }
        return player.containerMenu instanceof WorkstationMenu bench && bench.station.equals(station) ? bench.containerId : -1;
    }

    // ------------------------------------------------------------------ gear

    static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** Puts on an armour set from the bags, each piece by its own right-click; pieces not in the bags are written down. */
    public static Step equip(String... pieces) {
        return Step.of("equip " + String.join(",", pieces), run -> {
            var player = run.player();
            var event = run.event("equip");
            var missing = new JsonArray();
            for (String piece : pieces) {
                var wanted = item(piece);
                useItem(player, piece);
                boolean worn = false;
                for (var slot : ARMOUR) worn |= player.getItemBySlot(slot).is(wanted);
                if (!worn) missing.add(piece);
            }
            event.add("pieces", json(List.of(pieces)));
            event.add("missing", missing);
            event.addProperty("armor", player.getAttributeValue(Attributes.ARMOR));
            event.addProperty("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            run.write(event);
        });
    }

    /** Takes the armour off by shift-clicking each worn piece in the inventory screen. */
    public static Step unequip() {
        return Step.of("unequip", run -> {
            var player = run.player();
            player.closeContainer();
            for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++)
                if (player.inventoryMenu.getSlot(slot).hasItem()) player.inventoryMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
            player.inventoryMenu.broadcastChanges();
        });
    }

    /** Takes a weapon from the bags in the main hand (the hotbar); one not in them is written down as missing. */
    public static Step wield(String weapon) {
        return Step.of("wield " + weapon, run -> {
            var player = run.player();
            var event = run.event("wield");
            event.addProperty("item", weapon);
            boolean held = weapon.isEmpty() ? emptyHand(player) : select(player, weapon);
            if (!held) event.addProperty("missing", true);
            event.addProperty("damage", player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            event.addProperty("speed", player.getAttributeValue(Attributes.ATTACK_SPEED));
            run.write(event);
        });
    }

    // ------------------------------------------------------------------ eating

    /** Food worth eating from the bags: no sedatives (a narcoberry knocks the eater out), cooked or dried first. */
    static String food(ServerPlayer player) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            var stack = inventory.getItem(slot);
            var food = stack.get(DataComponents.FOOD);
            if (food == null || TamingService.sedativePotency(stack) > 0) continue;
            String id = id(stack.getItem());
            if (id.contains("raw_") || id.equals("minecraft:rotten_flesh")) continue;
            int score = food.nutrition() * 10 + (int) (food.saturation() * 10);
            if (score > bestScore) { bestScore = score; best = id; }
        }
        return best;
    }

    static boolean hasFood(ServerPlayer player) { return food(player) != null; }

    /** Eats one earned meal by its own use (held down until eaten), and writes what and how full the player got. */
    public static Step eat() {
        return new Step() {
            String item;
            int before;
            @Override public String name() { return "eat"; }
            @Override public int timeout() { return 20 * 6; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                if (ticks == 0) {
                    item = food(player);
                    if (item == null) return true;
                    before = player.getFoodData().getFoodLevel();
                    useItem(player, item);
                    return false;
                }
                if (player.isUsingItem() && ticks < 80) return false;
                var event = run.event("ate");
                event.addProperty("item", item);
                event.addProperty("food_before", before);
                event.addProperty("food_after", player.getFoodData().getFoodLevel());
                run.write(event);
                return true;
            }
            @Override public void abandon(PlayRun run) { run.player().stopUsingItem(); }
        };
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
