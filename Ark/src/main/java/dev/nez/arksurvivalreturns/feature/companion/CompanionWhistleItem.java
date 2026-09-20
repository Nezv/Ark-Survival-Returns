package dev.nez.arksurvivalreturns.feature.companion;

import net.minecraft.world.item.Item;

/**
 * Command whistle for tamed creatures.
 *
 * <p>Use on your own tamed creature cycles its standing order; sneak-use pets it. The item itself has no
 * behavior: every decision is validated server-side in {@code CreatureEntity.mobInteract}, so a client
 * cannot order a creature it does not own.
 */
public final class CompanionWhistleItem extends Item {
    public CompanionWhistleItem(Properties properties) { super(properties); }
}
