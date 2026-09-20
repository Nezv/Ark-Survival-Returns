package dev.nez.arksurvivalreturns.feature.mass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Bounded carried-mass accounting.
 *
 * <p>Containers are inspected one level deep with a capped content mass, and a container nested
 * inside another contributes only its own base mass, so content sums and specialist reductions can
 * never multiply recursively. Category tags are consulted first; everything else falls back to a
 * stack-size heuristic so an unseen modded item always has a conservative cost.
 */
public final class MassCalculator {
    public static final double LIGHT = 0.05;
    public static final double BULK = 0.1;
    public static final double FOOD = 0.2;
    public static final double UNIT = 1.0;
    public static final double ORE = 2.0;
    public static final double TOOL = 2.0;
    public static final double ARMOR = 3.0;
    /** A full stack of 64 weighs this much; smaller stacks scale up to the ceiling below. */
    private static final double STACK_BASE = 6.4;
    private static final double FALLBACK_CEILING = 4.0;
    private static final Map<Item, Double> FALLBACK = new ConcurrentHashMap<>();

    public static double massOf(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        return (baseMass(stack) * stack.getCount() + contentMass(stack)) * Config.MASS_MULTIPLIER.get();
    }

    /** Carried items and equipment; the body itself is deliberately abstracted. */
    public static double playerMass(Player player) {
        double total = 0.0;
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getNonEquipmentItems()) total += massOf(stack);
        for (EquipmentSlot slot : Inventory.EQUIPMENT_SLOT_MAPPING.values()) total += massOf(player.getItemBySlot(slot));
        return total;
    }

    /** Cargo contents of one container; used for creature holds. */
    public static double cargoMass(Container container) {
        double total = 0.0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += massOf(container.getItem(slot));
        return total;
    }

    public static double baseMass(ItemStack stack) {
        if (stack.is(MassTags.UNKNOWN_CONTAINER)) return Config.MASS_UNKNOWN_CONTAINER.get();
        if (stack.is(MassTags.LIGHT)) return LIGHT;
        if (stack.is(MassTags.BULK)) return BULK;
        if (stack.is(MassTags.FOOD)) return FOOD;
        if (stack.is(MassTags.UNIT)) return UNIT;
        if (stack.is(MassTags.ORE)) return ORE;
        if (stack.is(MassTags.TOOL)) return TOOL;
        if (stack.is(MassTags.ARMOR)) return ARMOR;
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return UNIT;
        return FALLBACK.computeIfAbsent(stack.getItem(), item ->
                Math.clamp(STACK_BASE / Math.max(1, item.getDefaultMaxStackSize()), LIGHT, FALLBACK_CEILING));
    }

    /** Contents of the top-level container only; a container inside it keeps just its base mass. */
    private static double contentMass(ItemStack stack) {
        double content = 0.0;
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.nonEmptyItemCopyStream().toList()) content += baseMass(inner) * inner.getCount();
        }
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.itemCopyStream().toList()) content += baseMass(inner) * inner.getCount();
        }
        return Math.min(content, Config.MASS_CONTAINER_CONTENT_CAP.get());
    }

    private MassCalculator() {}
}
