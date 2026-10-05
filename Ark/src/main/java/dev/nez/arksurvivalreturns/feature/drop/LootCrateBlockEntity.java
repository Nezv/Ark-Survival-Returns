package dev.nez.arksurvivalreturns.feature.drop;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * As many slots as the tier gives, filled from the tier's loot table when first opened. The beam is the vanilla
 * beacon's, in the tier's colour, and stands as long as the crate does: until {@link #expiresAt} or the last item.
 */
public final class LootCrateBlockEntity extends RandomizableContainerBlockEntity implements BeaconBeamOwner {
    /** Game time after which the crate goes; 0 for one placed by hand, which stays. */
    private long expiresAt;
    private NonNullList<ItemStack> items;
    private final List<Section> beam;

    public LootCrateBlockEntity(BlockPos pos, BlockState state) {
        super(DropContent.LOOT_CRATE_BLOCK_ENTITY.get(), pos, state);
        items = NonNullList.withSize(tier().slots, ItemStack.EMPTY);
        beam = List.of(new Section(0xFF000000 | tier().colour));
    }

    public SupplyTier tier() { return getBlockState().getValue(LootCrateBlock.TIER); }

    public long expiresAt() { return expiresAt; }

    /** A fresh drop: the tier's loot, still to be rolled, and the hour it goes. */
    public void arm(long expiresAt, long seed) {
        this.expiresAt = expiresAt;
        setLootTable(tier().loot, seed);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LootCrateBlockEntity crate) {
        if (crate.expiresAt > 0 && level.getGameTime() >= crate.expiresAt && level.getGameTime() % 20 == 0) crate.vanish(false);
    }

    /** Emptied by the player who just closed it. */
    @Override public void stopOpen(ContainerUser user) {
        if (!isRemoved() && level != null && !level.isClientSide() && lootTable == null && isEmpty()) vanish(true);
    }

    /** The crate goes with whatever it still holds; nothing spills. */
    private void vanish(boolean looted) {
        if (!(level instanceof ServerLevel world)) return;
        BlockPos pos = getBlockPos();
        lootTable = null;
        items.clear();
        world.removeBlock(pos, false);
        world.sendParticles(ParticleTypes.POOF, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 12, .3, .3, .3, .02);
        world.playSound(null, pos, looted ? SoundEvents.BARREL_CLOSE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, .7f, 1f);
    }

    @Override public List<Section> getBeamSections() { return beam; }

    @Override public int getContainerSize() { return items.size(); }

    @Override protected NonNullList<ItemStack> getItems() { return items; }

    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override protected Component getDefaultName() { return tier().crateName(); }

    @Override protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LootCrateMenu(containerId, inventory, this, getContainerSize());
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("expires_at", expiresAt);
        if (!trySaveLootTable(output)) ContainerHelper.saveAllItems(output, items);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        expiresAt = input.getLongOr("expires_at", 0L);
        items = NonNullList.withSize(tier().slots, ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) ContainerHelper.loadAllItems(input, items);
    }

    /** Broken by hand: the loot spills, rolled first if nobody had opened the crate. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, this);
        super.preRemoveSideEffects(pos, state);
    }
}
