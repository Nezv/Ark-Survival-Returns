package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/** Component-aware matching shared by the server and the client's affordability preview. */
public record WorkstationInventory(List<ItemStack> stacks, List<String> selectors, int[] available, int[] cost, boolean[][] matches) {
    public static boolean matches(ItemStack stack, String selector) {
        if (stack.isEmpty()) return false;
        if (selector.equals("minecraft:water_bottle")) {
            var contents = stack.get(DataComponents.POTION_CONTENTS);
            return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
        }
        if (selector.startsWith("#")) return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(selector.substring(1))));
        var id = Identifier.tryParse(selector);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && stack.is(BuiltInRegistries.ITEM.getValue(id));
    }
    public static WorkstationInventory read(Inventory inventory, Map<String, Integer> price) {
        int size = inventory.getNonEquipmentItems().size();
        List<ItemStack> stacks = new ArrayList<>(); int[] available = new int[size];
        for (int slot = 0; slot < size; slot++) { stacks.add(inventory.getItem(slot).copy()); available[slot] = stacks.get(slot).getCount(); }
        var selectors = new ArrayList<>(price.keySet()); int[] cost = new int[selectors.size()]; boolean[][] matches = new boolean[cost.length][size];
        for (int r = 0; r < cost.length; r++) {
            cost[r] = price.get(selectors.get(r));
            for (int slot = 0; slot < size; slot++) matches[r][slot] = matches(stacks.get(slot), selectors.get(r));
        }
        return new WorkstationInventory(stacks, selectors, available, cost, matches);
    }
    public int maxCrafts() { return WorkstationPayment.maxCrafts(available, cost, matches); }
    public WorkstationPayment.Plan plan(int times) { return WorkstationPayment.plan(available, cost, matches, times); }
    public ItemStack allocated(WorkstationPayment.Plan plan, String selector) {
        int r = selectors.indexOf(selector); if (r < 0) return ItemStack.EMPTY;
        for (int s = 0; s < stacks.size(); s++) if (plan.allocation()[r][s] > 0) return stacks.get(s);
        return ItemStack.EMPTY;
    }
    public boolean consume(Inventory inventory, WorkstationPayment.Plan plan) {
        // Check every snapshot before consuming any slot. The packet handler runs on the server thread.
        for (int s = 0; s < stacks.size(); s++) if (!ItemStack.matches(stacks.get(s), inventory.getItem(s))) return false;
        for (int s = 0; s < stacks.size(); s++) if (plan.consumed()[s] > 0) inventory.removeItem(s, plan.consumed()[s]);
        inventory.setChanged(); return true;
    }
}
