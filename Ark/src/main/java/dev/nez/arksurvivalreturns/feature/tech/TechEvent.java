package dev.nez.arksurvivalreturns.feature.tech;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One gameplay event offered to the tree.
 *
 * <p>Only the fields relevant to a kind are set; triggers check the kind first. The game day is
 * carried so time-based triggers never need to query the level themselves.
 */
public record TechEvent(
        TechEventKind kind,
        @Nullable Player player,
        @Nullable ItemStack stack,
        @Nullable BlockState state,
        @Nullable Identifier species,
        long day
) {
    public static TechEvent obtain(Player player, ItemStack stack) {
        return new TechEvent(TechEventKind.OBTAIN, player, stack.copy(), null, null, day(player));
    }

    public static TechEvent craft(Player player, ItemStack stack) {
        return new TechEvent(TechEventKind.CRAFT, player, stack.copy(), null, null, day(player));
    }

    public static TechEvent consume(Player player, ItemStack stack) {
        return new TechEvent(TechEventKind.CONSUME, player, stack.copy(), null, null, day(player));
    }

    public static TechEvent place(Player player, BlockState state) {
        return new TechEvent(TechEventKind.PLACE_BLOCK, player, null, state, null, day(player));
    }

    public static TechEvent sleep(Player player) {
        return new TechEvent(TechEventKind.SLEEP, player, null, null, null, day(player));
    }

    public static TechEvent tame(Player player, Identifier species) {
        return new TechEvent(TechEventKind.TAME, player, null, null, species, day(player));
    }

    public static TechEvent simple(TechEventKind kind, Player player) {
        return new TechEvent(kind, player, null, null, null, day(player));
    }

    private static long day(Player player) {
        return player.level().getGameTime() / 24000L;
    }
}
