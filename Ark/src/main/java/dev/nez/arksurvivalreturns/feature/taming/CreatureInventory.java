package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.Nullable;

/**
 * Persistent food and storage slots of one creature (nine slots, the horse chest layout).
 *
 * <p>The saddle is not stored here: it lives in the entity's real {@code EquipmentSlot.SADDLE}, which
 * vanilla already persists, syncs and drops on death, and which the horse-style menu can wrap with
 * {@code Mob.createEquipmentSlotContainer}. That keeps one authority for "is this creature saddled".
 */
public final class CreatureInventory extends SimpleContainer implements ValueIOSerializable {
    public static final int STORAGE_SIZE = 9;

    private final CreatureEntity owner;

    public CreatureInventory(CreatureEntity owner) {
        super(STORAGE_SIZE);
        this.owner = owner;
    }

    /** While wild only taming food and sedatives are accepted; a tame creature takes ordinary cargo. */
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (TamingService.isTamed(owner)) return true;
        if (owner.profile().accepts(stack)) return true;
        return stack.is(TamingTags.SEDATIVE);
    }

    @Override public boolean stillValid(Player player) {
        return owner.isAlive() && TamingService.canAccess(owner, player);
    }

    /** First accepted food item, or empty when none is deposited. */
    public ItemStack firstAccepted() {
        var profile = owner.profile();
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (profile.accepts(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public int countAccepted() {
        var profile = owner.profile();
        int count = 0;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (profile.accepts(getItem(slot))) count++;
        }
        return count;
    }

    public int countSedatives() {
        int count = 0;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (getItem(slot).is(TamingTags.SEDATIVE)) count++;
        }
        return count;
    }

    /** Contents are dropped exactly once by the entity's death path; nothing here may delete them. */
    public void dropAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty()) continue;
            setItem(slot, ItemStack.EMPTY);
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, position.x, position.y, position.z, stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        setChanged();
    }

    /** Total accepted food, used by the inspection command and the tests. */
    public int acceptedCount(@Nullable CreatureTamingProfile profile) {
        if (profile == null) return 0;
        int total = 0;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (profile.accepts(stack)) total += stack.getCount();
        }
        return total;
    }

    @Override public void setChanged() {
        if (owner.level() != null && !owner.level().isClientSide()) MassService.markDirty(owner);
    }

    @Override public void serialize(ValueOutput output) {
        ContainerHelper.saveAllItems(output, getItems());
    }

    @Override public void deserialize(ValueInput input) {
        clearContent();
        ContainerHelper.loadAllItems(input, getItems());
    }
}
