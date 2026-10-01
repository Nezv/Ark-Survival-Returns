package dev.nez.arksurvivalreturns.feature.station;

import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Position-bound menu with synced inventory and level; crafting has no grid slots. */
public final class WorkstationMenu extends AbstractContainerMenu {
    public final BlockPos position;
    public final String station;
    private final Player owner;
    private int level, gate;
    public static int playerLevel(Player player) { return ArkLevels.get(player).level(); }
    public WorkstationMenu(int id, Inventory inventory, BlockPos position, String station) {
        super(ModContent.WORKSTATION_MENU.get(), id);
        this.position = position.immutable(); this.station = station; owner = inventory.player;
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) addSlot(new Slot(inventory, slot, -10000, -10000));
        addDataSlot(new DataSlot() {
            @Override public int get() { return playerLevel(owner); }
            @Override public void set(int value) { level = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return Config.WORKSTATION_LEVEL_GATE.get() ? 1 : 0; }
            @Override public void set(int value) { gate = value; }
        });
    }
    public int displayedLevel() { return level; }
    public int craftingLevel() { return gate != 0 ? level : 1000000; }
    public boolean levelGate() { return gate != 0; }
    @Override public boolean stillValid(Player player) {
        if (!owner.level().hasChunkAt(position) || player != owner) return false;
        var block = owner.level().getBlockState(position).getBlock();
        return block instanceof StationBlock bench && bench.stationId().equals(station)
                && player.isWithinBlockInteractionRange(position, 0);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
