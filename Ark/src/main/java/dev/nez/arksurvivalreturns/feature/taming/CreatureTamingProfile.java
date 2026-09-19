package dev.nez.arksurvivalreturns.feature.taming;

import java.util.List;
import java.util.function.Supplier;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Taming balance for one registered creature: method, food, size band, target duration and sedation
 * resistance.
 *
 * <p>Progress per meal is derived, never stored twice: a tame takes {@code targetSeconds} at the
 * configured feeding interval, so {@code mealsPerTame = ceil(targetSeconds / minimumFeedInterval)} and
 * one meal is worth {@code 100 / mealsPerTame} percent. That keeps the configured duration and the
 * actual meal count from contradicting each other.
 */
public record CreatureTamingProfile(
        Species species,
        CreatureSize size,
        TamingMethod method,
        List<TagKey<Item>> accepted,
        List<Supplier<Item>> preferred,
        int targetSeconds,
        double sedativeResistance,
        String note) {

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (var tag : accepted) if (stack.is(tag)) return true;
        return false;
    }

    /** Preferred food still has to be accepted food; a favourite outside the diet is a profile bug. */
    public boolean preferred(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (var item : preferred) if (stack.is(item.get())) return true;
        return false;
    }

    /** Meals required for a full tame at baseline food, at least one. */
    public int mealsPerTame() {
        return mealsPerTame(Config.MINIMUM_FEED_INTERVAL.get());
    }

    /**
     * Meals for a given feeding interval. Parameterized so the derivation itself is unit testable without
     * a loaded server configuration.
     */
    public int mealsPerTame(int feedIntervalTicks) {
        return Math.max(1, (int) Math.ceil(targetSeconds * 20.0 / Math.max(1, feedIntervalTicks)));
    }

    public double progressPerMeal() {
        return progressPerMeal(Config.MINIMUM_FEED_INTERVAL.get());
    }

    public double progressPerMeal(int feedIntervalTicks) {
        return 100.0 / mealsPerTame(feedIntervalTicks);
    }

    public double progressFor(ItemStack stack) {
        return progressPerMeal() * (preferred(stack) ? Config.PREFERRED_FOOD_MULTIPLIER.get() : 1.0);
    }

    /** Progress a favourite meal is worth at the given feeding interval and multiplier. */
    public double progressForPreferred(int feedIntervalTicks, double multiplier) {
        return progressPerMeal(feedIntervalTicks) * multiplier;
    }

    /** Ticks a full tame needs if every meal lands as soon as the creature is willing. */
    public int targetTicks() {
        return targetSeconds * 20;
    }

    public String describeFood() {
        var names = new java.util.ArrayList<String>();
        for (var tag : accepted) names.add("#" + tag.location());
        return String.join(" ", names);
    }
}
