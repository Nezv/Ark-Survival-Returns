package dev.nez.arksurvivalreturns.feature.work;

import java.util.EnumSet;
import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.companion.CompanionOrder;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassCalculator;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Harvest jobs behind the WORK order.
 *
 * <p>A job anchors where the order was given, samples only loaded chunks inside a bounded radius, and
 * refuses player-placed blocks. It runs only while an authorized survivor stays nearby, so a worker
 * never becomes unattended automation, and it stops at the cargo automation ceiling so the hold is
 * filled by choice, not by drift.
 */
public final class WorkGoal extends Goal {
    private static final int SCAN_INTERVAL = 20;
    private static final double REACH_SQR = 6.25;

    private final CreatureEntity mob;
    private @Nullable BlockPos target;
    private int scanCooldown;
    private int actionCooldown;
    private boolean fullMessageSent;
    private boolean blockedMessageSent;

    public WorkGoal(CreatureEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        return Config.WORK_ENABLED.get() && mob.isAlive() && mob.isTamed() && !TorporService.restricted(mob)
                && CompanionService.of(mob).order() == CompanionOrder.WORK
                && WorkProfiles.of(mob.species()).job() != WorkProfiles.Job.NONE;
    }

    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() { mob.setBehavior(BehaviorState.FORAGE); }

    @Override public void stop() {
        target = null;
        mob.getNavigation().stop();
        mob.setBehavior(BehaviorState.ROAM);
    }

    @Override public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!supervised(mob)) {
            if (!blockedMessageSent) { feedback("work.arksurvivalreturns.blocked"); blockedMessageSent = true; }
            pause();
            return;
        }
        blockedMessageSent = false;
        if (cargoFull(mob)) {
            if (!fullMessageSent) { feedback("work.arksurvivalreturns.full"); fullMessageSent = true; }
            pause();
            return;
        }
        fullMessageSent = false;
        if (target == null) {
            if (--scanCooldown > 0) return;
            scanCooldown = SCAN_INTERVAL;
            target = findTarget(mob);
            return;
        }
        if (!isTarget(mob, level, target)) { target = null; return; }
        if (mob.distanceToSqr(Vec3.atCenterOf(target)) > REACH_SQR) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
            return;
        }
        mob.getNavigation().stop();
        if (actionCooldown > 0) { actionCooldown--; return; }
        actionCooldown = Config.WORK_ACTION_INTERVAL.get();
        harvest(mob, target);
        target = null;
    }

    private void pause() {
        target = null;
        mob.getNavigation().stop();
    }

    private void feedback(String key) {
        if (CompanionService.owner(mob) instanceof ServerPlayer owner) {
            owner.sendSystemMessage(Component.translatable(key, mob.getDisplayName()), true);
        }
    }

    /** An authorized survivor must be nearby: jobs never run as unattended automation. */
    public static boolean supervised(CreatureEntity mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        double radius = Config.WORK_SUPERVISION_RADIUS.get();
        for (Player player : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(radius))) {
            if (TribeService.canWork(mob, player)) return true;
        }
        return false;
    }

    /** True when the hold reaches the automation ceiling; jobs stop rather than overfill it. */
    public static boolean cargoFull(CreatureEntity mob) {
        if (!MassRules.enabled()) return false;
        double ceiling = MassService.creatureCapacity(mob) * MassRules.automationCeiling();
        return MassCalculator.cargoMass(mob.tamingInventory()) >= ceiling;
    }

    /** Samples loaded targets around the work anchor; never requests a chunk. */
    public static @Nullable BlockPos findTarget(CreatureEntity mob) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        BlockPos anchor = CompanionService.of(mob).anchor();
        if (anchor == null) anchor = mob.blockPosition();
        int radius = Config.WORK_RADIUS.get();
        var random = mob.getRandom();
        for (int attempt = 0; attempt < Config.WORK_SCAN_ATTEMPTS.get(); attempt++) {
            BlockPos candidate = anchor.offset(random.nextInt(radius * 2 + 1) - radius,
                    random.nextInt(5) - 2, random.nextInt(radius * 2 + 1) - radius);
            if (isTarget(mob, level, candidate)) return candidate;
        }
        return null;
    }

    /** Loaded, tagged, without a block entity, and never a player-placed block. */
    public static boolean isTarget(CreatureEntity mob, ServerLevel level, BlockPos pos) {
        TagKey<Block> tag = WorkProfiles.of(mob.species()).job() == WorkProfiles.Job.FORAGE
                ? WorkTags.FORAGE : WorkTags.MINERAL;
        if (!SpawnRules.loaded(level, new AABB(pos).inflate(1))) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(tag)) return false;
        if (level.getBlockEntity(pos) != null) return false;
        return !Config.WORK_RESPECT_PLACED.get() || !WorkProtection.get(level).protectedAt(pos);
    }

    /** One harvest action; public so the headless suite can drive it without AI arbitration. */
    public static void harvest(CreatureEntity mob, BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel level) || !isTarget(mob, level, pos)) return;
        WorkProfiles.Job job = WorkProfiles.of(mob.species()).job();
        if (job == WorkProfiles.Job.FORAGE) forage(mob, level, pos);
        else if (job == WorkProfiles.Job.MINERAL) mine(mob, level, pos);
        dev.nez.arksurvivalreturns.feature.tech.TechEvents.onTameWork(mob);
    }

    /** Grass is grazed in place; ferns and tufts are cut; ripe berry bushes reset to growing. */
    private static void forage(CreatureEntity mob, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 3) {
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_ALL);
            deposit(mob, new ItemStack(Items.SWEET_BERRIES, 2 + mob.getRandom().nextInt(2)));
        } else if (!state.is(Blocks.GRASS_BLOCK)) {
            level.destroyBlock(pos, false);
        }
        deposit(mob, new ItemStack(ModContent.PLANT_FIBER.get(), (int) (double) Config.WORK_FIBER_PER_ACTION.get()));
        if (mob.getRandom().nextDouble() < Config.WORK_BERRY_CHANCE.get()) deposit(mob, new ItemStack(berry(mob), 1));
    }

    /** The ore tag earns a configurable extra drop; the block is removed with its vanilla drops. */
    private static void mine(CreatureEntity mob, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        boolean ore = state.is(WorkTags.ORE);
        for (ItemStack drop : drops) {
            int count = drop.getCount() + (ore && mob.getRandom().nextDouble() < Config.WORK_MINERAL_BONUS.get() ? 1 : 0);
            deposit(mob, drop.copyWithCount(count));
        }
        level.destroyBlock(pos, false);
    }

    /** Merges into the hold; a yield that does not fit is dropped at the worker, never deleted. */
    public static void deposit(CreatureEntity mob, ItemStack stack) {
        if (stack.isEmpty()) return;
        Container cargo = mob.tamingInventory();
        for (int slot = 0; slot < cargo.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = cargo.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
            if (!cargo.canPlaceItem(slot, stack)) continue;
            int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
            if (moved <= 0) continue;
            existing.grow(moved);
            stack.shrink(moved);
            cargo.setItem(slot, existing);
        }
        for (int slot = 0; slot < cargo.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!cargo.getItem(slot).isEmpty() || !cargo.canPlaceItem(slot, stack)) continue;
            int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
            cargo.setItem(slot, stack.copyWithCount(moved));
            stack.shrink(moved);
        }
        if (!stack.isEmpty() && mob.level() instanceof ServerLevel level) mob.spawnAtLocation(level, stack);
        cargo.setChanged();
    }

    /** Weighted like the grass route: 30/30/30 ordinary berries and 10 narcoberry. */
    private static Item berry(CreatureEntity mob) {
        int roll = mob.getRandom().nextInt(100);
        if (roll < 10) return ModContent.BERRIES.get("narcoberry").get();
        if (roll < 43) return ModContent.BERRIES.get("tintoberry").get();
        if (roll < 76) return ModContent.BERRIES.get("amarberry").get();
        return ModContent.BERRIES.get("azulberry").get();
    }
}
