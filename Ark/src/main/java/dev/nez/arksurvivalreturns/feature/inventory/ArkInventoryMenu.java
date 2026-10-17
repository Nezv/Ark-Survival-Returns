package dev.nez.arksurvivalreturns.feature.inventory;

import static dev.nez.arksurvivalreturns.feature.inventory.ArkInventoryLayout.*;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The survival inventory (the inventory key opens it instead of the vanilla one): armour, offhand and the Curios slots
 * around the player, the inventory's crafting graph beside them (drawn by the screen; it needs no slots), and below
 * the storage, eleven columns of three rows (the nine vanilla columns and a side pocket column on each side) over the
 * hotbar. It also syncs the player level the graph unlocks by.
 */
public final class ArkInventoryMenu extends AbstractContainerMenu {
    public static final int ARMOR = 0, OFFHAND = 4, STORAGE = 5, HOTBAR = 32, POCKETS = 41, CURIOS = POCKETS + SidePockets.SIZE;
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    public final Player owner;
    private int level, gate;

    public ArkInventoryMenu(int id, Inventory inventory) {
        super(InventoryContent.MENU.get(), id);
        owner = inventory.player;
        for (int i = 0; i < 4; i++) addSlot(new Armor(inventory, owner, ARMOR_SLOTS[i], 39 - i, ARMOR_X, row(ARMOR_ROWS[i]), ARMOR_ICONS[i]));
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, OFFHAND_X, OFFHAND_Y) {
            @Override public void setByPlayer(ItemStack stack, ItemStack previous) {
                owner.onEquipItem(EquipmentSlot.OFFHAND, previous, stack);
                super.setByPlayer(stack, previous);
            }
            @Override public Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, STORAGE_X + col * 18, STORAGE_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, STORAGE_X + col * 18, HOTBAR_Y));
        SidePockets pockets = SidePockets.of(owner);
        for (int i = 0; i < SidePockets.SIZE; i++) {
            int[] at = pocket(i);
            addSlot(new ResourceHandlerSlot(pockets, pockets::set, i, at[0], at[1]));
        }
        CurioSlots.add(this, owner);
        addDataSlot(new DataSlot() {
            @Override public int get() { return ArkLevels.get(owner).level(); }
            @Override public void set(int value) { level = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return Config.WORKSTATION_LEVEL_GATE.get() ? 1 : 0; }
            @Override public void set(int value) { gate = value; }
        });
    }

    void addCurio(Slot slot) { addSlot(slot); }
    public int displayedLevel() { return level; }
    public int craftingLevel() { return gate != 0 ? level : 1000000; }
    public boolean levelGate() { return gate != 0; }

    @Override public boolean stillValid(Player player) { return player == owner; }

    /** Shift-click: gear goes on (armour, offhand, then a curio slot that takes it), the rest between storage and hotbar. */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        int pocketsEnd = POCKETS + SidePockets.SIZE, curiosEnd = slots.size();
        boolean moved;
        if (index >= STORAGE && index < pocketsEnd) {
            EquipmentSlot equip = player.getEquipmentSlotForItem(stack);
            int armor = equip.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? ARMOR + 3 - equip.getIndex() : -1;
            if (armor >= 0 && !slots.get(armor).hasItem()) moved = moveItemStackTo(stack, armor, armor + 1, false);
            else if (equip == EquipmentSlot.OFFHAND && !slots.get(OFFHAND).hasItem()) moved = moveItemStackTo(stack, OFFHAND, OFFHAND + 1, false);
            else moved = curiosEnd > CURIOS && moveItemStackTo(stack, CURIOS, curiosEnd, false);
            if (!moved) {
                if (index < HOTBAR) moved = moveItemStackTo(stack, HOTBAR, POCKETS, false);
                else if (index < POCKETS) moved = moveItemStackTo(stack, STORAGE, HOTBAR, false) || moveItemStackTo(stack, POCKETS, pocketsEnd, false);
                else moved = moveItemStackTo(stack, STORAGE, POCKETS, false);
            }
        } else {
            moved = moveItemStackTo(stack, STORAGE, HOTBAR, false) || moveItemStackTo(stack, POCKETS, pocketsEnd, false)
                    || moveItemStackTo(stack, HOTBAR, POCKETS, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    /** The vanilla armour slot, which is not public. */
    private static final class Armor extends Slot {
        private final Player owner;
        private final EquipmentSlot slot;
        private final Identifier icon;
        Armor(Inventory inventory, Player owner, EquipmentSlot slot, int index, int x, int y, Identifier icon) {
            super(inventory, index, x, y);
            this.owner = owner; this.slot = slot; this.icon = icon;
        }
        @Override public void setByPlayer(ItemStack stack, ItemStack previous) {
            owner.onEquipItem(slot, previous, stack);
            super.setByPlayer(stack, previous);
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean mayPlace(ItemStack stack) { return stack.canEquip(slot, owner); }
        @Override public boolean isActive() { return owner.canUseSlot(slot); }
        @Override public boolean mayPickup(Player player) {
            ItemStack worn = getItem();
            return !(!worn.isEmpty() && !player.isCreative() && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                    && super.mayPickup(player);
        }
        @Override public Identifier getNoItemIcon() { return icon; }
    }
}
