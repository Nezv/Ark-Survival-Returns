package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jspecify.annotations.Nullable;

/**
 * The single server-authoritative entry point for sedation.
 *
 * <p>Every sedative source - a thrown dose, a melee dose, a consumed dose or a command - calls
 * {@link #sedate}. Nothing else may write the meter, so a client cannot invent torpor: the client only
 * ever expresses intent, and the requests that reach this class carry a potency resolved from the item
 * the server already validated.
 *
 * <p>Torpor covers three populations through one shared integration:
 * <ul>
 *   <li>mod creatures, whose ceiling and resistance come from {@link CreatureProfileRegistry};</li>
 *   <li>players, whose ceiling comes from the config and who can never be tamed;</li>
 *   <li>ordinary living mobs, whose ceiling is derived from their real hitbox height.</li>
 * </ul>
 *
 * <p>Documented exclusions: non-living entities (items, boats, minecarts, arrows and other
 * {@code Entity} subclasses) have no attachment and are never sedated; {@link ArmorStand} is excluded as
 * furniture; spectators, creative-mode players and dead entities are excluded because the restriction
 * contract cannot be enforced on them. The vanilla hostile mobs of the theme patch do not exist in this
 * world, so there is no further special case.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TorporService {
    private static final String SUFFIX = "]";

    public static TorporState of(LivingEntity entity) {
        return entity.getData(TamingAttachments.TORPOR);
    }

    public static boolean tracked(LivingEntity entity) {
        return entity.hasData(TamingAttachments.TORPOR);
    }

    /** True when this entity is a legal sedation target. */
    public static boolean eligible(@Nullable LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity instanceof ArmorStand) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return true;
    }

    public static double maximumFor(LivingEntity entity) {
        var species = speciesOf(entity);
        if (species != null) return CreatureProfileRegistry.of(species).size().maxTorpor();
        if (entity instanceof Player) return Config.PLAYER_MAX_TORPOR.get();
        return CreatureSize.of(entity.getBbHeight()).maxTorpor();
    }

    public static double toleranceFor(LivingEntity entity) {
        var species = speciesOf(entity);
        if (species != null) return CreatureProfileRegistry.of(species).sedativeResistance();
        if (entity instanceof Player) return Config.PLAYER_TORPOR_RESISTANCE.get();
        return 1.0;
    }

    public static @Nullable Species speciesOf(LivingEntity entity) {
        return entity instanceof CreatureEntity creature ? creature.species() : null;
    }

    /**
     * Applies one dose.
     *
     * @param potency normalized dose, resolved by the server from the sedative actually used
     * @param source  the acting entity, for logging only
     * @param cause   a short reason used by the debug log
     * @return true when the dose was accepted
     */
    public static boolean sedate(@Nullable LivingEntity target, double potency, @Nullable Entity source, String cause) {
        if (!ArkSurvivalReturns.tamingEnabled()) return false;
        if (!eligible(target) || potency <= 0) return false;
        if (target.level().isClientSide()) return false;
        var state = of(target);
        state.setMaximum(maximumFor(target));
        installSuppression(target);
        boolean changed = state.sedate(potency, toleranceFor(target));
        log(cause, target, "torpor " + round(state.torpor()) + "/" + round(state.maximum()) + " delay "
                + state.recoveryDelay() + " from " + (source == null ? "world" : source.getName().getString()));
        return changed;
    }

    /**
     * Gives an ordinary mob the same goal-level restraint a mod creature has.
     *
     * <p>{@code setNoAi(true)} is deliberately not used here: in this Minecraft version that flag suppresses
     * {@code LivingEntity.aiStep}'s travel step as well, so a restrained vanilla mob would neither fall nor
     * be pushed. Injecting the suppression goal keeps the AI silent while leaving gravity, fluids, knockback
     * and damage untouched, which is exactly what the restriction contract requires.
     */
    private static void installSuppression(LivingEntity entity) {
        if (entity instanceof CreatureEntity) return;
        if (!(entity instanceof Mob mob)) return;
        // Presence is the guard, not a saved flag: a goal selector is rebuilt from scratch on reload, so a
        // persisted "already installed" flag would leave a reloaded mob acting freely when sedated again.
        if (mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof UnconsciousBehavior)) return;
        mob.goalSelector.addGoal(-1, new UnconsciousBehavior(mob));
        log("restrain", entity, "goal-level suppression installed");
    }
    /** Administrative override, used by the debug command. Returns false when nothing was changed. */
    public static boolean setTorpor(@Nullable LivingEntity target, double value) {
        if (!eligible(target) || target.level().isClientSide()) return false;
        var state = of(target);
        state.setMaximum(maximumFor(target));
        state.setTorpor(value);
        installSuppression(target);
        log("command", target, "torpor set to " + round(state.torpor()));
        return true;
    }

    /** Forces the entity upright and clears sedation, used by death handling and the command. */
    public static void clear(@Nullable LivingEntity target) {
        if (target == null || !tracked(target) || target.level().isClientSide()) return;
        of(target).reset();
        log("clear", target, "sedation cleared");
    }

    /** True while the entity is in any phase past awake. */
    public static boolean restricted(@Nullable LivingEntity entity) {
        return entity != null && tracked(entity) && of(entity).restricted();
    }

    /**
     * Advances the meter for one entity. Hooked once for every living entity through
     * {@link EntityTickEvent}, so creatures, players and ordinary mobs share the same timing.
     */
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        tickEntity(living);
    }

    /** One simulation step for a single entity; also the deterministic entry point for the tests. */
    public static void tickEntity(LivingEntity living) {
        if (living.level().isClientSide()) return;
        if (!tracked(living)) return;
        TamingService.tick(living);
        var state = of(living);
        state.setMaximum(maximumFor(living));
        // A goal selector is rebuilt on reload, so the restraint is re-installed while any phase holds.
        if (state.restricted()) installSuppression(living);
        var transition = state.tick();
        switch (transition) {
            case COLLAPSE_STARTED -> collapse(living, state);
            case WAKE_REQUESTED -> beginWake(living, state);
            case WOKE -> woke(living, state);
            case TORPID_ENTERED -> log("sink", living, "collapse finished; unconscious loop holds");
            case NONE -> {
            }
        }
        sync(living, state);
    }

    /**
     * Applies the physical restrictions and the animation phase at the moment of the knockout. The
     * entity is treated as unable to act from here, before the collapse clip has finished.
     */
    private static void collapse(LivingEntity entity, TorporState state) {
        int ticks = collapseTicks(entity);
        state.startCollapse(ticks);
        if (entity.getVehicle() != null) entity.stopRiding();
        dismountRiders(entity);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            if (mob.getLastHurtByMob() != null) mob.setLastHurtByMob(null);
        }
        if (entity instanceof CreatureEntity creature) creature.onUnconsciousnessChanged(true);
        log("collapse", entity, "knockout phase " + ticks + " ticks");
        TamingFeedback.knockout(entity);
    }

    private static void beginWake(LivingEntity entity, TorporState state) {
        boolean tamed = entity.hasData(TamingAttachments.TAMING) && entity.getData(TamingAttachments.TAMING).tamed();
        if (!tamed) TamingService.onWakeWithoutCompletion(entity);
        state.startWake(tamed, wakeTicks(entity, tamed));
        log("wake", entity, tamed ? "waking tame" : "waking wild; incomplete attempt dropped");
        TamingFeedback.wake(entity, tamed);
    }

    private static void woke(LivingEntity entity, TorporState state) {
        if (entity instanceof CreatureEntity creature) creature.onUnconsciousnessChanged(false);
        log("awake", entity, "restrictions lifted");
    }

    /** Moves every rider off an unconscious entity, including riders of an ordinary vanilla mount. */
    private static void dismountRiders(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (var passenger : java.util.List.copyOf(entity.getPassengers())) {
            passenger.stopRiding();
            if (!(entity instanceof CreatureEntity creature)) continue;
            if (!(passenger instanceof LivingEntity)) continue;
            var safe = CreatureRideController.findSafePosition(level, creature, passenger);
            if (safe != null && !passenger.isPassenger() && !passenger.onGround()) passenger.setPos(safe);
        }
    }

    private static int collapseTicks(LivingEntity entity) {
        var species = speciesOf(entity);
        var clips = species == null ? null : CreatureTorporClips.of(species);
        if (clips != null && clips.in() != null) return clips.inTicks();
        return Config.TORPOR_COLLAPSE_FALLBACK_TICKS.get();
    }

    private static int wakeTicks(LivingEntity entity, boolean tamed) {
        var species = speciesOf(entity);
        var clips = species == null ? null : CreatureTorporClips.of(species);
        if (clips != null) {
            int authored = tamed ? clips.outTamedTicks() : clips.outWildTicks();
            String clip = tamed ? clips.outTamed() : clips.outWild();
            if (clip != null && authored > 0) return authored;
        }
        return Config.TORPOR_WAKE_FALLBACK_TICKS.get();
    }

    /** Sends the changed meter to tracking clients at a bounded cadence. */
    private static void sync(LivingEntity entity, TorporState state) {
        if (!state.dirty()) return;
        if (entity.tickCount % Math.max(1, Config.TORPOR_SYNC_INTERVAL.get()) != 0) return;
        entity.syncData(TamingAttachments.TORPOR);
        state.clearDirty();
    }

    /** True when this entity may be fed from a taming inventory right now. */
    public static boolean feedable(LivingEntity entity) {
        var state = of(entity);
        return state.torpid();
    }

    /** Number of ticks one meal animation lasts for this entity, used for the feeding feedback. */
    public static int feedingTicks(LivingEntity entity) {
        var species = speciesOf(entity);
        var clips = species == null ? null : CreatureTorporClips.of(species);
        if (clips != null && clips.eat() != null) return clips.eatTicks();
        return Config.TORPOR_FEED_FALLBACK_TICKS.get();
    }

    /** Convenience for services that need the creature behind an entity. */
    public static @Nullable CreatureEntity creature(LivingEntity entity) {
        return entity instanceof CreatureEntity creature ? creature : null;
    }

    /** True when the entity is one of this mod's creatures. */
    public static boolean isModCreature(LivingEntity entity) {
        return entity instanceof CreatureEntity;
    }

    /** Registry id for logs, so a creature and a player read the same way. */
    public static String describe(LivingEntity entity) {
        var species = speciesOf(entity);
        return species != null ? species.id : entity.getType().toShortString();
    }

    static void log(String event, LivingEntity entity, String detail) {
        if (!Config.TAMING_DEBUG_LOG.get()) return;
        ArkSurvivalReturns.LOGGER.info("[taming{} {} {} {}", SUFFIX, event, describe(entity), detail);
    }

    static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** The torpor state of a player, created on demand. */
    public static TorporState player(Player player) {
        return of(player);
    }

    /** Attachment holder for this entity, kept private so only this class writes torpor. */
    static DeferredHolder<AttachmentType<?>, AttachmentType<TorporState>> holder() {
        return TamingAttachments.TORPOR;
    }

    /** Weight of a rider, used when a knockback impulse has to survive an unconscious rider. */
    static Vec3 impulse(@Nullable Entity source) {
        return source == null ? Vec3.ZERO : source.getDeltaMovement();
    }

    /** Registry lookup used by the roster audit. */
    public static boolean registered(Species species) {
        return ModContent.CREATURES.containsKey(species);
    }

    private TorporService() {}
}
