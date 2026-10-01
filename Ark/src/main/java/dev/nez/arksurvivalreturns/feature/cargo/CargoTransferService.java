package dev.nez.arksurvivalreturns.feature.cargo;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassCalculator;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Bulk transfer between one tame's cargo and nearby storage.
 *
 * <p>Storage is any vanilla {@link Container} block entity, plus any block entity that exposes NeoForge's
 * item capability instead, such as a Tom's Storage network connector or other modded storage (I04).
 * Only already-loaded chunks are read, the position and container scans are capped, and no chunk
 * is ever requested. Fast Load stops before the automation ceiling; Fast Unload always proceeds,
 * because unloading can never be an exploit and manual slot moves remain the player's business.
 *
 * <p>Only storage the player could open by hand from where they stand takes part: outside spawn
 * protection and the world border, not locked against their held key, and in sight of their eyes, so a
 * tame parked against someone else's wall cannot empty the chests behind it.
 */
public final class CargoTransferService {
    private static final int MAX_CONTAINERS = 16;
    private static final int VERTICAL_REACH = 4;

    public static int unload(ServerPlayer player, CreatureEntity creature) {
        return transfer(player, creature, false);
    }

    public static int load(ServerPlayer player, CreatureEntity creature) {
        return transfer(player, creature, true);
    }

    private static int transfer(ServerPlayer player, CreatureEntity creature, boolean load) {
        if (!(creature.level() instanceof ServerLevel level) || player.level() != level) return 0;
        List<Container> containers = new ArrayList<>();
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        nearbyStorage(level, player, creature.blockPosition(), Config.CARGO_TRANSFER_RADIUS.get(), containers, handlers);
        if (containers.isEmpty() && handlers.isEmpty()) return 0;
        Container cargo = creature.tamingInventory();
        double ceiling = MassRules.enabled()
                ? MassService.creatureCapacity(creature) * MassRules.automationCeiling()
                : Double.MAX_VALUE;
        int moved = 0;
        if (load) {
            for (Container container : containers) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty() || !container.canTakeItem(cargo, slot, stack)) continue;
                    if (cargoMass(creature, cargo) + MassCalculator.massOf(stack) > ceiling) continue;
                    moved += merge(container, slot, cargo);
                }
            }
            for (ResourceHandler<ItemResource> handler : handlers) {
                for (int index = 0; index < handler.size(); index++) {
                    ItemResource resource = handler.getResource(index);
                    int amount = handler.getAmountAsInt(index);
                    if (resource.isEmpty() || amount <= 0) continue;
                    ItemStack view = resource.toStack(Math.min(amount, resource.getMaxStackSize()));
                    if (cargoMass(creature, cargo) + MassCalculator.massOf(view) > ceiling) continue;
                    int room = room(cargo, view);
                    if (room <= 0) continue;
                    int extracted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        extracted = handler.extract(index, resource, room, transaction);
                        transaction.commit();
                    }
                    if (extracted > 0) {
                        ItemStack taken = resource.toStack(extracted);
                        merge(taken, cargo);
                        moved += extracted - taken.getCount();
                    }
                }
            }
        } else {
            for (Container container : containers) {
                for (int slot = 0; slot < cargo.getContainerSize(); slot++) {
                    ItemStack stack = cargo.getItem(slot);
                    if (stack.isEmpty()) continue;
                    moved += merge(cargo, slot, container);
                }
            }
            for (ResourceHandler<ItemResource> handler : handlers) {
                for (int slot = 0; slot < cargo.getContainerSize(); slot++) {
                    ItemStack stack = cargo.getItem(slot);
                    if (stack.isEmpty()) continue;
                    int inserted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        inserted = handler.insert(ItemResource.of(stack), stack.getCount(), transaction);
                        transaction.commit();
                    }
                    if (inserted > 0) {
                        stack.shrink(inserted);
                        cargo.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
                        moved += inserted;
                    }
                }
            }
        }
        if (moved > 0) cargo.setChanged();
        return moved;
    }

    private static double cargoMass(CreatureEntity creature, Container cargo) {
        return MassCalculator.cargoMass(cargo) + MassCalculator.massOf(creature.harnessSlot().getItem(0));
    }

    /** How many of the stack the container would accept, without changing anything. */
    private static int room(Container to, ItemStack stack) {
        int room = 0;
        for (int target = 0; target < to.getContainerSize() && room < stack.getCount(); target++) {
            ItemStack existing = to.getItem(target);
            if (!to.canPlaceItem(target, stack)) continue;
            if (existing.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(existing, stack)) room += Math.max(0, existing.getMaxStackSize() - existing.getCount());
        }
        return Math.min(room, stack.getCount());
    }

    /** Merges a loose stack into the container; whatever does not fit stays in the stack. */
    private static void merge(ItemStack stack, Container to) {
        for (int target = 0; target < to.getContainerSize() && !stack.isEmpty(); target++) {
            ItemStack existing = to.getItem(target);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack) || !to.canPlaceItem(target, stack)) continue;
            int count = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
            if (count <= 0) continue;
            existing.grow(count);
            stack.shrink(count);
            to.setItem(target, existing);
        }
        for (int target = 0; target < to.getContainerSize() && !stack.isEmpty(); target++) {
            if (!to.getItem(target).isEmpty() || !to.canPlaceItem(target, stack)) continue;
            int count = Math.min(stack.getMaxStackSize(), stack.getCount());
            to.setItem(target, stack.copyWithCount(count));
            stack.shrink(count);
        }
    }

    /** Moves as much of one stack as the destination accepts; returns the moved count. */
    private static int merge(Container from, int slot, Container to) {
        ItemStack stack = from.getItem(slot);
        if (stack.isEmpty()) return 0;
        int original = stack.getCount();
        for (int target = 0; target < to.getContainerSize() && !stack.isEmpty(); target++) {
            ItemStack existing = to.getItem(target);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
            if (!to.canPlaceItem(target, stack)) continue;
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room <= 0) continue;
            int count = Math.min(room, stack.getCount());
            existing.grow(count);
            stack.shrink(count);
            to.setItem(target, existing);
        }
        for (int target = 0; target < to.getContainerSize() && !stack.isEmpty(); target++) {
            if (!to.getItem(target).isEmpty() || !to.canPlaceItem(target, stack)) continue;
            int count = Math.min(stack.getMaxStackSize(), stack.getCount());
            to.setItem(target, stack.copyWithCount(count));
            stack.shrink(count);
        }
        from.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original - from.getItem(slot).getCount();
    }

    /** Block storage within the radius, reading loaded chunks only: containers first, capabilities otherwise. */
    private static void nearbyStorage(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
            List<Container> containers, List<ResourceHandler<ItemResource>> handlers) {
        int minY = Math.max(level.getMinY(), center.getY() - VERTICAL_REACH);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + VERTICAL_REACH);
        // Cover the whole configured volume (2601 positions at radius 8), regardless of chunk alignment.
        int maxPositions = (2 * radius + 1) * (2 * radius + 1) * (maxY - minY + 1);
        int visited = 0;
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) continue;
                int minX = Math.max(center.getX() - radius, chunkX << 4);
                int maxX = Math.min(center.getX() + radius, (chunkX << 4) + 15);
                int minZ = Math.max(center.getZ() - radius, chunkZ << 4);
                int maxZ = Math.min(center.getZ() + radius, (chunkZ << 4) + 15);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            if (++visited > maxPositions) return;
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockEntity entity = level.getBlockEntity(pos);
                            if (entity == null) continue;
                            if (entity instanceof Container container) {
                                if (!usable(level, player, pos, entity) || !inSight(level, player, pos)) continue;
                                containers.add(container);
                            } else {
                                ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
                                if (handler == null || !usable(level, player, pos, entity) || !inSight(level, player, pos)) continue;
                                handlers.add(handler);
                            }
                            if (containers.size() + handlers.size() >= MAX_CONTAINERS) return;
                        }
                    }
                }
            }
        }
    }

    /** Spawn protection, the world border and a container lock all refuse the player's hand, so they refuse the tame too. */
    private static boolean usable(ServerLevel level, ServerPlayer player, BlockPos pos, BlockEntity entity) {
        if (!level.mayInteract(player, pos)) return false;
        return !(entity instanceof BaseContainerBlockEntity lockable) || lockable.canOpen(player);
    }

    /**
     * A sight line from the player's eyes to the block's centre. Usable storage along the way does not
     * block it, so rows of chests and the far half of a double chest stay reachable; walls, doors, glass
     * and locked storage do. An unloaded block on the way blocks it rather than being loaded.
     */
    private static boolean inSight(ServerLevel level, ServerPlayer player, BlockPos target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(target);
        CollisionContext shapes = CollisionContext.of(player);
        return BlockGetter.traverseBlocks(from, to, target, (goal, cursor) -> {
            if (cursor.equals(goal)) return Boolean.TRUE;
            BlockPos pos = cursor.immutable();
            if (!level.isLoaded(pos)) return Boolean.FALSE;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos, shapes);
            if (shape.isEmpty() || shape.clip(from, to, pos) == null) return null;
            BlockEntity passed = level.getBlockEntity(pos);
            boolean storage = passed instanceof Container
                    || passed != null && level.getCapability(Capabilities.Item.BLOCK, pos, null) != null;
            return storage && usable(level, player, pos, passed) ? null : Boolean.FALSE;
        }, goal -> Boolean.TRUE);
    }

    private CargoTransferService() {}
}
