package dev.nez.arksurvivalreturns.feature.taming;

import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingFeedback.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.Nullable;
import dev.nez.arksurvivalreturns.feature.taming.TamingFeedback.Result;

/**
 * Feeding validation, one-meal consumption, progress and completion.
 *
 * <p>Every rule is enforced here, on the server, at the moment of consumption: the food must be accepted
 * by the profile, the creature must be hungry enough, the feeding interval must have elapsed, the method
 * must match the creature's awake or unconscious state, and the acting player must hold the attempt.
 * A rejected interaction never consumes an item.
 */
public final class TamingService {
    public static TamingState of(LivingEntity entity) {
        return entity.getData(TamingAttachments.TAMING);
    }

    /** True when the entity has real taming state and is not a player. */
    public static boolean tracked(LivingEntity entity) {
        return entity.hasData(TamingAttachments.TAMING);
    }

    public static boolean isTamed(LivingEntity entity) {
        return tracked(entity) && of(entity).tamed();
    }

    public static boolean ownedBy(LivingEntity entity, @Nullable Player player) {
        return player != null && tracked(entity) && player.getUUID().equals(of(entity).owner());
    }

    /** Runtime tick for every torpor-tracked entity: appetite, decay, knock-out feeding and sync. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide() || entity instanceof Player) return;
        var state = of(entity);
        state.advanceTicks();
        long clock = state.ticks();
        if (!state.initialized()) state.initialize(wildHunger(entity), clock);
        // A tame grows hungry slowly and the trough feeds it; fed, it heals, starving it does not (BUG-23).
        boolean tame = state.tamed();
        if (clock % 20 == 0) state.addHunger(Config.HUNGER_INCREASE_PER_SECOND.get() * (tame ? Config.TAME_HUNGER_FACTOR.get() : 1.0));
        if (tame && clock % 100 == 0 && entity.isAlive() && entity.getHealth() < entity.getMaxHealth()
                && state.hunger() < STARVING && !TorporService.restricted(entity))
            entity.heal((float) (entity.getMaxHealth() * Config.TAME_REGEN_PER_MINUTE.get() / 12.0));
        if (entity instanceof CreatureEntity creature && !state.tamed()) {
            var profile = creature.profile();
            if (profile.method() == TamingMethod.KNOCKOUT) {
                if (TorporService.feedable(creature)) {
                    keepUnder(creature, state);
                    autoConsume(creature, state, profile, clock);
                }
            } else {
                decay(creature, state, clock);
            }
        } else if (entity instanceof CreatureEntity tamed && tamed.isTamed() && !tamed.isPersistenceRequired()) {
            // A tame reloaded from disk leaves the wildlife population and despawn accounting again.
            tamed.applyTameState();
        }
        sync(entity, state);
    }

    /** Hunger at which a tame counts as starving and stops healing. */
    public static final double STARVING = 90.0;

    /**
     * A meal given by hand to a sleeping knock-out animal (BUG-23): the same rules as one eaten from its taming
     * inventory (accepted food, hungry, interval, the attempt's holder), worth {@code handMealMultiplier} more.
     */
    public static TamingFeedback.Result feedSleeping(CreatureEntity creature, Player player, InteractionHand hand) {
        if (!ArkSurvivalReturns.tamingEnabled() || creature.level().isClientSide()) return TamingFeedback.Result.EXCLUDED;
        var profile = creature.profile();
        var state = of(creature);
        long clock = state.ticks();
        if (state.tamed()) return TamingFeedback.Result.ALREADY_TAMED;
        if (profile.method() != TamingMethod.KNOCKOUT || !TorporService.feedable(creature)) return TamingFeedback.Result.WRONG_STATE;
        var claim = claimCheck(creature, state, player);
        if (claim != null) return claim;
        ItemStack held = player.getItemInHand(hand);
        if (!profile.accepts(held) || held.getCount() < Config.FOOD_UNITS_PER_MEAL.get()) return reject(creature, TamingFeedback.Result.WRONG_FOOD);
        if (!state.hungryEnough()) return reject(creature, TamingFeedback.Result.NOT_HUNGRY);
        if (!cooldownElapsed(state, clock)) return reject(creature, TamingFeedback.Result.COOLDOWN);
        boolean favourite = profile.preferred(held);
        held.consume(Config.FOOD_UNITS_PER_MEAL.get(), player);
        if (claimantMissing(state) || state.claimExpired()) state.claim(player.getUUID(), clock);
        advance(creature, state, profile, clock, favourite, player, Config.HAND_MEAL_MULTIPLIER.get());
        return TamingFeedback.Result.ACCEPTED;
    }

    /** Rejections are logged once each with their reason, never per tick. */
    private static TamingFeedback.Result reject(CreatureEntity creature, TamingFeedback.Result result) {
        TorporService.log("rejected", creature, "feeding refused: " + result);
        return result;
    }

    private static double wildHunger(LivingEntity entity) {
        double min = Config.WILD_HUNGER_MIN.get();
        double max = Math.max(min, Config.WILD_HUNGER_MAX.get());
        return min + entity.getRandom().nextDouble() * (max - min);
    }

    /**
     * Hand feeding, used by the passive and aerial methods. The caller has already resolved the acting
     * player; this method decides the outcome and only shrinks the stack after every check passed.
     */
    public static TamingFeedback.Result feedByHand(CreatureEntity creature, Player player, InteractionHand hand) {
        if (!ArkSurvivalReturns.tamingEnabled()) return TamingFeedback.Result.EXCLUDED;
        if (creature.level().isClientSide()) return TamingFeedback.Result.EXCLUDED;
        if (!TorporService.eligible(creature)) return TamingFeedback.Result.EXCLUDED;
        var profile = creature.profile();
        var state = of(creature);
        long clock = state.ticks();
        if (!state.initialized()) state.initialize(wildHunger(creature), clock);
        ItemStack held = player.getItemInHand(hand);

        if (state.tamed()) return TamingFeedback.Result.ALREADY_TAMED;
        if (profile.method() == TamingMethod.KNOCKOUT && !TorporService.feedable(creature))
            return TamingFeedback.Result.WRONG_STATE;
        if (TorporService.restricted(creature)) return TamingFeedback.Result.WRONG_STATE;
        var claim = claimCheck(creature, state, player);
        if (claim != null) {
            TorporService.log("rejected", creature, "feeding refused: " + claim);
            return claim;
        }
        if (!profile.accepts(held) || held.getCount() < Config.FOOD_UNITS_PER_MEAL.get())
            return reject(creature, TamingFeedback.Result.WRONG_FOOD);
        if (!state.hungryEnough()) return reject(creature, TamingFeedback.Result.NOT_HUNGRY);
        if (!cooldownElapsed(state, clock)) return reject(creature, TamingFeedback.Result.COOLDOWN);

        boolean favourite = profile.preferred(held);
        held.consume(Config.FOOD_UNITS_PER_MEAL.get(), player);
        // An expired lease is transferred to the player who resumes the attempt, so the tame is never
        // awarded to somebody who walked away two minutes ago.
        if (claimantMissing(state) || state.claimExpired()) state.claim(player.getUUID(), clock);
        advance(creature, state, profile, clock, favourite, player, 1.0);
        return TamingFeedback.Result.ACCEPTED;
    }

    /**
     * One automatic feeding opportunity from the taming inventory. Called every tick for an unconscious
     * knock-out creature; at most one meal is consumed per eligible opportunity.
     */
    private static void autoConsume(CreatureEntity creature, TamingState state, CreatureTamingProfile profile,
            long clock) {
        // Only an attempt somebody holds is fed: waking clears the claim but keeps the deposit, so without
        // this an abandoned inventory would be eaten into a tame that has no owner to award.
        if (state.claimant() == null) return;
        if (!state.hungryEnough()) return;
        if (!cooldownElapsed(state, clock)) return;
        var inventory = creature.tamingInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!profile.accepts(stack) || stack.getCount() < Config.FOOD_UNITS_PER_MEAL.get()) continue;
            boolean favourite = profile.preferred(stack);
            stack.shrink(Config.FOOD_UNITS_PER_MEAL.get());
            inventory.setChanged();
            advance(creature, state, profile, clock, favourite, null, 1.0);
            return;
        }
    }

    /** Seconds of recovery left above the wake line when the hold gives its next sedative. */
    private static final double HOLD_MARGIN_SECONDS = 5.0;

    /**
     * Sedatives deposited in the taming hold keep a claimed knock-out under (BUG-01): one dose as its torpor nears
     * the wake line, with no damage event, so a sleep that is shorter than the feeding is no longer the end of it.
     * The strongest dose that fits under the ceiling is given, else the weakest, so little is wasted.
     */
    private static void keepUnder(CreatureEntity creature, TamingState state) {
        if (state.claimant() == null) return;
        var torpor = TorporService.of(creature);
        if (torpor.recoveryDelay() > 0) return;
        double margin = Math.max(1.0, torpor.maximum() * Config.TORPOR_RECOVERY_PER_SECOND.get() * HOLD_MARGIN_SECONDS);
        if (torpor.torpor() > torpor.wakeThreshold() + margin) return;
        var inventory = creature.tamingInventory();
        double room = torpor.maximum() - torpor.torpor(), resistance = TorporService.toleranceFor(creature);
        int best = -1;
        double bestDose = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            double dose = sedativePotency(inventory.getItem(slot)) * resistance;
            if (dose <= 0) continue;
            boolean fits = dose <= room, bestFits = best >= 0 && bestDose <= room;
            if (best < 0 || fits && (!bestFits || dose > bestDose) || !fits && !bestFits && dose < bestDose) {
                best = slot;
                bestDose = dose;
            }
        }
        if (best < 0) return;
        ItemStack stack = inventory.getItem(best);
        double potency = sedativePotency(stack);
        stack.shrink(1);
        inventory.setChanged();
        TorporService.sedate(creature, potency, null, "hold");
    }

    /** The dose one item of the hold gives: Narcotics and berries their own, an arrow its own; anything else none. */
    public static double sedativePotency(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(TamingTags.SEDATIVE)) return 0;
        if (stack.getItem() instanceof SedativeItem sedative) return sedative.potency();
        if (stack.getItem() instanceof SedativeArrowItem arrow) return arrow.potency();
        return 0;
    }

    private static void advance(CreatureEntity creature, TamingState state, CreatureTamingProfile profile,
            long clock, boolean favourite, @Nullable Player feeder, double multiplier) {
        double gain = profile.progressPerMeal() * (favourite ? Config.PREFERRED_FOOD_MULTIPLIER.get() : 1.0) * multiplier;
        state.recordMeal(clock);
        state.addProgress(gain);
        state.touchProgress(clock);
        TorporService.of(creature).markFeeding(TorporService.feedingTicks(creature));
        if (feeder != null) TamingFeedback.accepted(feeder, creature, state.progress(), favourite);
        TorporService.log("meal", creature, "progress " + TorporService.round(state.progress()) + "% ("
                + (favourite ? "preferred" : "baseline") + ", hunger " + TorporService.round(state.hunger()) + ")");
        if (state.progress() >= 100f) complete(creature, state, feeder);
    }

    /**
     * The feeding interval, measured on the entity's own tick counter.
     *
     * <p>A creature that has never eaten is not on cooldown: without this, a freshly loaded creature would
     * refuse its first meal until its tick counter passed the interval, and that counter restarts on load.
     */
    private static boolean cooldownElapsed(TamingState state, long clock) {
        return state.meals() == 0 || clock - state.lastMealAt() >= Config.MINIMUM_FEED_INTERVAL.get();
    }

    /** True when a fresh wild creature has its attempt clock primed; used by the access rules. */
    public static long clock(LivingEntity entity) {
        return of(entity).ticks();
    }

    /** Tames at once for this owner, through the same completion as a last meal: the testing ground's Tame action. */
    public static void tameNow(CreatureEntity creature, Player owner) {
        var state = of(creature);
        if (state.tamed() || creature.level().isClientSide()) return;
        state.claim(owner.getUUID(), state.ticks());
        complete(creature, state, owner);
    }

    private static void complete(CreatureEntity creature, TamingState state, @Nullable Player feeder) {
        // The claimant owns the tame; a stale lease cannot be stolen by the final meal because hand feeding
        // re-claims an expired attempt before awarding progress. Automatic inventory feeding has no feeder,
        // so the depositing claimant keeps ownership even if the lease lapsed while the creature ate.
        UUID owner = state.claimant() != null ? state.claimant()
                : feeder != null ? feeder.getUUID() : null;
        if (owner == null) return;
        state.setProgress(100f);
        state.setOwner(owner);
        state.releaseClaim();
        creature.onTamed(owner);
        applyTameEffects(creature, owner);
        if (creature.level() instanceof ServerLevel level) {
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player == null && feeder instanceof net.minecraft.server.level.ServerPlayer server && owner.equals(server.getUUID())) player = server;
            if (player != null) dev.nez.arksurvivalreturns.feature.levels.ArkLevels.addXp(player,
                    (long) Config.PLAYER_XP_TAME.get() * creature.creatureLevel(),
                    dev.nez.arksurvivalreturns.feature.levels.ArkLevels.Source.TAME);
        }
        TorporService.log("tamed", creature, "owner " + owner);
    }

    /** A creature that first appeared in the last danger zone. */
    private static boolean topZone(CreatureEntity creature) {
        return creature.originDanger() >= dev.nez.arksurvivalreturns.feature.spawn.DangerBands.ZONES;
    }

    /** Completion side effects that do not depend on who fed the final meal: the per-player discovery records. */
    public static void applyTameEffects(CreatureEntity creature, UUID owner) {
        if (creature.level() instanceof ServerLevel level && level.getEntity(owner) instanceof Player player) {
            TamingFeedback.completed(player, creature);
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                discovery(server, "journal/first_tame");
                if (topZone(creature)) discovery(server, "journal/top_zone_tame");
            }
        }
    }

    /**
     * Awards a hidden advancement. Advancements are the per-player discovery record: the map
     * entitlement and the tribe journal read them, and each player records the find once.
     */
    public static void discovery(net.minecraft.server.level.ServerPlayer player, String path) {
        if (player.level().getServer() == null) return;
        var holder = player.level().getServer().getAdvancements().get(
                dev.nez.arksurvivalreturns.ArkSurvivalReturns.id(path));
        if (holder != null) player.getAdvancements().award(holder, "discovered");
    }

    /**
     * Damage during an attempt costs a bounded share of the progress and cancels an aerial truce. A sedative's hit
     * (an arrow, a swing with Narcotics) is how torpor is given, so it costs no progress (BUG-01).
     */
    public static void onDamaged(LivingEntity entity, @Nullable Entity source, boolean sedative) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof CreatureEntity creature) creature.cancelFeedingTruce(source);
        if (sedative || !tracked(entity)) return;
        var state = of(entity);
        if (state.tamed() || state.progress() <= 0f) return;
        float before = state.progress();
        state.applyDamagePenalty();
        TorporService.log("damage", entity, "progress " + TorporService.round(before) + "% -> "
                + TorporService.round(state.progress()) + "%");
    }

    /** Waking before the tame completes abandons the attempt; deposited food stays in the inventory. */
    public static void onWakeWithoutCompletion(LivingEntity entity) {
        if (entity instanceof Player || !tracked(entity)) return;
        var state = of(entity);
        if (state.tamed()) return;
        if (state.progress() <= 0f && state.claimant() == null) return;
        state.resetAttempt();
        if (entity instanceof CreatureEntity creature) TamingFeedback.smoke(creature);
        TorporService.log("abandoned", entity, "attempt reset; inventory kept");
    }

    /** Passive and aerial progress decays only after the configured grace period, then one step at a time. */
    private static void decay(CreatureEntity creature, TamingState state, long clock) {
        if (state.progress() <= 0f) return;
        long idle = clock - state.lastProgressAt() - Config.PASSIVE_PROGRESS_GRACE.get();
        if (idle <= 0) return;
        long periods = idle / Config.PASSIVE_PROGRESS_DECAY_PERIOD.get();
        if (periods <= 0) return;
        float decayed = (float) (periods * Config.PASSIVE_PROGRESS_DECAY.get());
        float before = state.progress();
        state.setProgress(state.progress() - decayed);
        // The grace period is not refunded: keep the remainder of the last decay period.
        state.touchProgress(clock - idle % Config.PASSIVE_PROGRESS_DECAY_PERIOD.get());
        TorporService.log("decay", creature, "progress " + TorporService.round(before) + "% -> "
                + TorporService.round(state.progress()) + "%");
    }

    /**
     * Access rule for the horse-style inventory.
     *
     * <p>A wild creature is reachable only while it is unconscious, only through the knock-out method, and
     * only by the player holding the attempt. An absent or expired claim is open to everyone, and the player
     * who opens the inventory then takes the lease over. After taming the owner always has access, and
     * members of the owner's FTB Teams party gain it through the tribe cargo permission.
     */
    public static boolean canAccess(CreatureEntity creature, Player player) {
        var state = of(creature);
        if (state.tamed()) return dev.nez.arksurvivalreturns.feature.tribe.TribeService.canAccessCargo(creature, player);
        if (creature.profile().method() != TamingMethod.KNOCKOUT) return false;
        if (!TorporService.feedable(creature)) return false;
        if (state.claimant() == null || state.claimExpired() || state.claimedBy(player.getUUID())) return true;
        return false;
    }

    /** Registers the visiting player as the claimant when the creature has no active attempt. */
    public static void claimIfFree(CreatureEntity creature, Player player) {
        var state = of(creature);
        if (state.tamed()) return;
        if (state.claimant() != null && !state.claimExpired()) return;
        state.claim(player.getUUID(), state.ticks());
        TamingFeedback.claimed(player, creature);
    }

    private static @Nullable Result claimCheck(CreatureEntity creature, TamingState state, Player player) {
        if (claimantMissing(state) || state.claimExpired() || state.claimedBy(player.getUUID())) return null;
        return Result.NOT_CLAIMANT;
    }

    private static boolean claimantMissing(TamingState state) {
        return state.claimant() == null;
    }

    /** Persisted taming state is written by the attachment system; this keeps the client copy bounded. */
    private static void sync(LivingEntity entity, TamingState state) {
        if (!state.dirty()) return;
        if (state.ticks() % Math.max(1, Config.TORPOR_SYNC_INTERVAL.get()) != 0) return;
        entity.syncData(TamingAttachments.TAMING);
        state.clearDirty();
    }

    /** Human-readable summary for the inspect command. */
    public static Component summary(CreatureEntity creature) {
        var state = of(creature);
        var profile = creature.profile();
        return Component.literal("method=" + profile.method()
                + " size=" + profile.size()
                + " progress=" + TorporService.round(state.progress()) + "%"
                + " meals=" + state.meals()
                + " hunger=" + TorporService.round(state.hunger())
                + " torpor=" + TorporService.round(TorporService.of(creature).torpor())
                + "/" + TorporService.round(TorporService.of(creature).maximum())
                + " phase=" + TorporService.of(creature).phase()
                + " claimant=" + TamingFeedback.ownerName(state.claimant())
                + " owner=" + TamingFeedback.ownerName(state.owner())
                + " food=" + profile.describeFood());
    }

    /** Exposed for the verification tests: state serialization round trip through the value APIs. */
    public static void serialize(TamingState state, ValueOutput output) {
        state.serialize(output);
    }

    public static TamingState deserialize(ValueInput input) {
        var state = new TamingState();
        state.deserialize(input);
        return state;
    }

    private TamingService() {}
}
