package dev.nez.arksurvivalreturns.feature.taming;

import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.Nullable;

/**
 * Taming progress, the claimant, feeding appetite and the persistent owner of one living entity.
 *
 * <p>Consciousness is not stored here: {@link TorporState} owns it, because a tamed creature can still
 * be sedated.
 */
public final class TamingState implements ValueIOSerializable {
    public static final StreamCodec<RegistryFriendlyByteBuf, TamingState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> state.writeTo(buffer), TamingState::readFrom);

    private float progress;
    private @Nullable UUID claimant;
    private @Nullable UUID owner;
    private double hunger;
    private int meals;
    private long lastMealAt;
    private long lastProgressAt;
    private long claimStartedAt;
    private long ticks;
    private @Nullable UUID truceFeeder;
    private long truceUntil;
    private boolean initialized;
    private boolean dirty = true;

    public float progress() {
        return progress;
    }

    public void setProgress(float value) {
        float clamped = Math.clamp(value, 0f, 100f);
        dirty |= clamped != progress;
        progress = clamped;
    }

    public void addProgress(double amount) {
        setProgress((float) (progress + amount));
    }

    public boolean tamed() {
        return owner != null;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID value) {
        dirty |= !java.util.Objects.equals(owner, value);
        owner = value;
    }

    public @Nullable UUID claimant() {
        return claimant;
    }

    public long claimStartedAt() {
        return claimStartedAt;
    }

    /**
     * The attempt clock: one tick per server tick the entity is loaded and simulating.
     *
     * <p>Meal timing, appetite, progress decay, the claim lease and the feeding truce all use this counter
     * rather than the world clock, so an unloaded attempt pauses instead of progressing offline, and a
     * reloaded creature resumes exactly where it stopped.
     */
    public long ticks() {
        return ticks;
    }

    public void advanceTicks() {
        ticks++;
    }

    public boolean claimedBy(UUID player) {
        return player != null && player.equals(claimant);
    }

    /** Aerial feeding truce: the feeder is ignored for a bounded window. */
    public void grantTruce(UUID feeder, int durationTicks) {
        truceFeeder = feeder;
        truceUntil = ticks + durationTicks;
        dirty = true;
    }

    public boolean truceActive(@Nullable UUID player) {
        return truceFeeder != null && player != null && truceFeeder.equals(player) && ticks < truceUntil;
    }

    public void cancelTruce(@Nullable UUID source) {
        if (source == null || truceFeeder == null || !truceFeeder.equals(source)) return;
        truceFeeder = null;
        truceUntil = 0;
        dirty = true;
    }

    public @Nullable UUID truceFeeder() {
        return truceFeeder;
    }

    /** First actor to interact becomes the claimant; the final meal cannot be stolen. */
    public void claim(UUID player, long gameTime) {
        claimant = player;
        claimStartedAt = gameTime;
        dirty = true;
    }

    public void releaseClaim() {
        claimant = null;
        claimStartedAt = 0;
        dirty = true;
    }

    /** True when an abandoned attempt held by another player has expired. */
    public boolean claimExpired() {
        return claimant != null && ticks - claimStartedAt > Config.CLAIM_EXPIRY.get();
    }

    public double hunger() {
        return hunger;
    }

    public void setHunger(double value) {
        double clamped = Math.clamp(value, 0.0, 100.0);
        dirty |= clamped != hunger;
        hunger = clamped;
    }

    public boolean hungryEnough() {
        return hunger >= Config.FEED_HUNGER_THRESHOLD.get();
    }

    public void addHunger(double amount) {
        setHunger(hunger + amount);
    }

    public int meals() {
        return meals;
    }

    public long lastMealAt() {
        return lastMealAt;
    }

    public long lastProgressAt() {
        return lastProgressAt;
    }

    public boolean initialized() {
        return initialized;
    }

    public void initialize(double startingHunger, long gameTime) {
        hunger = Math.clamp(startingHunger, 0.0, 100.0);
        initialized = true;
        lastProgressAt = gameTime;
        dirty = true;
    }

    /** Records one consumed meal: satiation, the feeding clock and the meal counter. */
    public void recordMeal(long gameTime) {
        hunger = Math.clamp(hunger - Config.HUNGER_REDUCTION_PER_MEAL.get(), 0.0, 100.0);
        meals++;
        lastMealAt = gameTime;
        lastProgressAt = gameTime;
        dirty = true;
    }

    public void touchProgress(long gameTime) {
        lastProgressAt = gameTime;
        dirty = true;
    }

    /** Damage during an attempt costs a bounded share of the progress. */
    public void applyDamagePenalty() {
        setProgress((float) (progress - Config.DAMAGE_PROGRESS_PENALTY.get()));
    }

    /** Waking before the tame completes discards the attempt but keeps the meals already eaten. */
    public void resetAttempt() {
        setProgress(0f);
        releaseClaim();
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    @Override public void serialize(ValueOutput output) {
        output.putFloat("TamingProgress", progress);
        if (claimant != null) output.putString("TamingClaimant", claimant.toString());
        if (owner != null) output.putString("TamingOwner", owner.toString());
        output.putDouble("FeedingHunger", hunger);
        output.putInt("TamingMeals", meals);
        output.putLong("TamingLastMeal", lastMealAt);
        output.putLong("TamingLastProgress", lastProgressAt);
        output.putLong("TamingClaimStarted", claimStartedAt);
        output.putLong("TamingTicks", ticks);
        output.putBoolean("TamingInitialized", initialized);
    }

    @Override public void deserialize(ValueInput input) {
        progress = Math.clamp(input.getFloatOr("TamingProgress", 0f), 0f, 100f);
        claimant = readUuid(input.getStringOr("TamingClaimant", ""));
        owner = readUuid(input.getStringOr("TamingOwner", ""));
        hunger = Math.clamp(input.getDoubleOr("FeedingHunger", 0.0), 0.0, 100.0);
        meals = Math.max(0, input.getIntOr("TamingMeals", 0));
        lastMealAt = input.getLongOr("TamingLastMeal", 0);
        lastProgressAt = input.getLongOr("TamingLastProgress", 0);
        claimStartedAt = input.getLongOr("TamingClaimStarted", 0);
        ticks = input.getLongOr("TamingTicks", 0);
        initialized = input.getBooleanOr("TamingInitialized", false);
        truceFeeder = null;
        truceUntil = 0;
        dirty = true;
    }

    private void writeTo(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(progress);
        buffer.writeDouble(hunger);
        buffer.writeVarInt(meals);
        buffer.writeBoolean(owner != null);
        if (owner != null) buffer.writeUUID(owner);
        buffer.writeBoolean(claimant != null);
        if (claimant != null) buffer.writeUUID(claimant);
    }

    private static TamingState readFrom(RegistryFriendlyByteBuf buffer) {
        var state = new TamingState();
        state.progress = buffer.readFloat();
        state.hunger = buffer.readDouble();
        state.meals = buffer.readVarInt();
        state.owner = buffer.readBoolean() ? buffer.readUUID() : null;
        state.claimant = buffer.readBoolean() ? buffer.readUUID() : null;
        state.initialized = true;
        state.dirty = false;
        return state;
    }

    private static @Nullable UUID readUuid(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
