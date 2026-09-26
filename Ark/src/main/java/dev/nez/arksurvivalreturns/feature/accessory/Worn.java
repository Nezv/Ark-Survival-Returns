package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * What a player is wearing, for every system that reacts to an accessory. Curios is optional: without it
 * nothing is ever worn. The set is read from Curios at most once per tick per player, on each side, so hot
 * paths (climbing checks, wildlife senses) cost a field read.
 */
public final class Worn {
    public static final boolean CURIOS = ModList.get() != null && ModList.get().isLoaded("curios");

    /** Per-player runtime state: the worn set plus the short-lived effect timers that ride along with it. */
    public static final class State {
        final EnumSet<Accessory> worn = EnumSet.noneOf(Accessory.class);
        int stamp = Integer.MIN_VALUE;
        /** Game time until which the Antler Frontlet disguise is broken (the wearer struck a creature). */
        public long disguiseBrokenUntil;
        /** Quetzal Mantle: the mid-air jump is spent until the player lands. */
        public boolean airJumpUsed;
        /** Membrane Glider: ticks spent gliding (client physics and the server's fall reset). */
        public int glideTicks;
    }

    public static boolean has(LivingEntity entity, Accessory accessory) {
        if (!CURIOS || !(entity instanceof Player player)) return false;
        return state(player).worn.contains(accessory);
    }

    public static State state(Player player) {
        State state = player.getData(AccessoryContent.WORN);
        if (state.stamp != player.tickCount) {
            state.stamp = player.tickCount;
            state.worn.clear();
            if (CURIOS) CuriosBridge.collect(player, state.worn);
        }
        return state;
    }

    /** The worn stack of an accessory, or empty. */
    public static ItemStack stack(Player player, Accessory accessory) {
        return CURIOS ? CuriosBridge.find(player, accessory.item()) : ItemStack.EMPTY;
    }

    /** Forces a fresh read on the next query (equip and unequip). */
    public static void invalidate(Player player) {
        player.getData(AccessoryContent.WORN).stamp = Integer.MIN_VALUE;
    }

    private Worn() {}
}
