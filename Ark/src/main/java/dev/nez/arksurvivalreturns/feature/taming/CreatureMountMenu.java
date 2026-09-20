package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.feature.cargo.CargoProfiles;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassCalculator;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Horse-style adapter for a creature inventory: one saddle slot, nine storage slots and the player's own
 * inventory, with the vanilla horse slot coordinates and the vanilla horse GUI texture.
 *
 * <p>Why an adapter instead of extending {@code AbstractMountInventoryMenu}: that class passes a null menu
 * type to {@code AbstractContainerMenu}, so {@code getType()} throws and the menu cannot travel through
 * {@code ServerPlayer.openMenu} or through a screen registration in this Minecraft version. It also only
 * accepts {@code AbstractHorse}/{@code AbstractNautilus} on the client. The layout, the saddle container
 * contract ({@code Mob.createEquipmentSlotContainer}) and the slot sprites are still vanilla; only the
 * menu type is ours.
 */
public final class CreatureMountMenu extends AbstractContainerMenu {
    public static final int SADDLE_SLOT = 0;
    public static final int HARNESS_SLOT = 1;
    public static final int STORAGE_START = 2;
    public static final int STORAGE_COLUMNS = 3;
    public static final int STORAGE_ROWS = 3;
    public static final int MOUNT_SLOTS = STORAGE_START + CreatureInventory.STORAGE_SIZE;

    /** Container data layout shared with the client screen. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_HUNGER = 1;
    public static final int DATA_TORPOR = 2;
    public static final int DATA_TORPOR_MAX = 3;
    public static final int DATA_PHASE = 4;
    public static final int DATA_METHOD = 5;
    public static final int DATA_CARGO = 6;
    public static final int DATA_CARGO_MAX = 7;
    public static final int DATA_COUNT = 8;

    private final @Nullable CreatureEntity creature;
    private final Container storage;

    public CreatureMountMenu(int containerId, Inventory playerInventory, @Nullable CreatureEntity creature) {
        super(ModContent.CREATURE_MOUNT_MENU.get(), containerId);
        this.creature = creature;
        this.storage = creature == null ? new SimpleContainer(CreatureInventory.STORAGE_SIZE) : creature.tamingInventory();
        Container saddle = creature == null ? new SimpleContainer(1)
                : creature.createEquipmentSlotContainer(EquipmentSlot.SADDLE);
        this.addSlot(new Slot(saddle, 0, 8, 18) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.SADDLE);
            }
        });
        Container harness = creature == null ? new SimpleContainer(1) : creature.harnessSlot();
        this.addSlot(new Slot(harness, 0, 8, 36) {
            @Override public boolean mayPlace(ItemStack stack) {
                return CargoProfiles.tier(stack) != CargoProfiles.Harness.NONE;
            }
        });
        for (int row = 0; row < STORAGE_ROWS; row++) {
            for (int column = 0; column < STORAGE_COLUMNS; column++) {
                this.addSlot(new Slot(storage, column + row * STORAGE_COLUMNS,
                        80 + column * 18, 18 + row * 18));
            }
        }
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlots(containerData());
    }

    /** Progress, appetite, sedation and method, so the screen can show the attempt without extra packets. */
    private ContainerData containerData() {
        return new SimpleContainerData(DATA_COUNT) {
            @Override public int get(int id) {
                if (creature == null) return 0;
                var torpor = TorporService.of(creature);
                var taming = TamingService.of(creature);
                return switch (id) {
                    case DATA_PROGRESS -> Math.round(taming.progress() * 100f);
                    case DATA_HUNGER -> (int) Math.round(taming.hunger());
                    case DATA_TORPOR -> (int) Math.round(torpor.torpor());
                    case DATA_TORPOR_MAX -> (int) Math.round(torpor.maximum());
                    case DATA_PHASE -> torpor.phase().ordinal();
                    case DATA_METHOD -> creature.profile().method().ordinal();
                    case DATA_CARGO -> (int) Math.round(MassCalculator.cargoMass(creature.tamingInventory())
                            + MassCalculator.massOf(creature.harnessSlot().getItem(0)));
                    case DATA_CARGO_MAX -> (int) Math.round(MassService.creatureCapacity(creature));
                    default -> 0;
                };
            }

            @Override public void set(int id, int value) {
            }
        };
    }

    /** Nullable: the client factory never forces a chunk load, so an unloaded creature yields no menu. */
    public @Nullable CreatureEntity creature() {
        return creature;
    }

    public int rawProgress() {
        return containerData().get(DATA_PROGRESS);
    }

    public int rawHunger() {
        return containerData().get(DATA_HUNGER);
    }

    public int rawTorpor() {
        return containerData().get(DATA_TORPOR);
    }

    public int rawTorporMax() {
        return containerData().get(DATA_TORPOR_MAX);
    }

    public int rawCargoMass() {
        return containerData().get(DATA_CARGO);
    }

    public int rawCargoMax() {
        return containerData().get(DATA_CARGO_MAX);
    }

    public static int inventoryColumns() {
        return STORAGE_COLUMNS;
    }

    @Override public boolean stillValid(Player player) {
        // Revalidated every tick by the server, so waking, death, removal, distance and expiry all close the
        // menu. A removed (unloaded) creature cannot keep accepting edits to a detached inventory.
        return creature != null && creature.isAlive() && !creature.isRemoved()
                && TamingService.canAccess(creature, player)
                && player.isWithinEntityInteractionRange(creature, 4.0);
    }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return clicked;
        ItemStack stack = slot.getItem();
        clicked = stack.copy();
        if (slotIndex < MOUNT_SLOTS) {
            if (!this.moveItemStackTo(stack, MOUNT_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (this.getSlot(SADDLE_SLOT).mayPlace(stack) && !this.getSlot(SADDLE_SLOT).hasItem()) {
            if (!this.moveItemStackTo(stack, SADDLE_SLOT, STORAGE_START, false)) return ItemStack.EMPTY;
        } else if (this.getSlot(HARNESS_SLOT).mayPlace(stack) && !this.getSlot(HARNESS_SLOT).hasItem()) {
            if (!this.moveItemStackTo(stack, HARNESS_SLOT, STORAGE_START, false)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(stack, STORAGE_START, MOUNT_SLOTS, false)) {
            int playerStart = MOUNT_SLOTS;
            int hotbarStart = playerStart + 27;
            int playerEnd = hotbarStart + 9;
            if (slotIndex >= hotbarStart && slotIndex < playerEnd) {
                if (!this.moveItemStackTo(stack, playerStart, hotbarStart, false)) return ItemStack.EMPTY;
            } else if (slotIndex >= playerStart && slotIndex < hotbarStart) {
                if (!this.moveItemStackTo(stack, hotbarStart, playerEnd, false)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return clicked;
    }

    /** Every slot edit, including feeding and transfers, refreshes the creature load once per tick. */
    @Override public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (creature != null && !creature.level().isClientSide()) MassService.markDirty(creature);
    }

    /** A deposited item is the moment a wild attempt becomes this player's; claiming happens on open. */

    /** Kept for symmetry with the vanilla adapter; the storage container lives on the entity. */
    public static int getInventorySize(int columns) {
        return columns * STORAGE_ROWS;
    }
}
