package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.inventory.SidePockets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Component-aware matching shared by the server and the client's affordability preview. A craft pays from everything
 * the player carries apart from the worn gear: the inventory's 36 slots, then the six side pockets.
 */
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
    /** The live stacks a craft can pay from, in payment slot order. */
    private static List<ItemStack> carried(Player player) {
        List<ItemStack> out = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        out.addAll(SidePockets.of(player).stacks());
        return out;
    }
    public static int count(Player player, String selector) {
        int count = 0;
        for (ItemStack stack : carried(player)) if (matches(stack, selector)) count += stack.getCount();
        return count;
    }
    public static WorkstationInventory read(Player player, Map<String, Integer> price) {
        List<ItemStack> live = carried(player);
        int size = live.size();
        List<ItemStack> stacks = new ArrayList<>(); int[] available = new int[size];
        for (int slot = 0; slot < size; slot++) { stacks.add(live.get(slot).copy()); available[slot] = stacks.get(slot).getCount(); }
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
    public boolean consume(Player player, WorkstationPayment.Plan plan) {
        // Check every snapshot before consuming any slot. The packet handler runs on the server thread.
        List<ItemStack> live = carried(player);
        if (live.size() != stacks.size()) return false;
        for (int s = 0; s < stacks.size(); s++) if (!ItemStack.matches(stacks.get(s), live.get(s))) return false;
        Inventory inventory = player.getInventory();
        int main = inventory.getNonEquipmentItems().size();
        SidePockets pockets = SidePockets.of(player);
        for (int s = 0; s < stacks.size(); s++) {
            if (plan.consumed()[s] <= 0) continue;
            if (s < main) inventory.removeItem(s, plan.consumed()[s]);
            else pockets.take(s - main, plan.consumed()[s]);
        }
        inventory.setChanged(); return true;
    }
}
