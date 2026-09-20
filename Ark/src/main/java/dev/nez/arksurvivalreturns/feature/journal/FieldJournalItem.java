package dev.nez.arksurvivalreturns.feature.journal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Field Journal opens the tribe's FTB Quests book.
 *
 * <p>The book screen is a client-only FTB class, so this item reaches it through
 * {@link JournalOpener}, which is installed by the client. The dedicated server never touches
 * the screen code.
 */
public final class FieldJournalItem extends Item {
    public FieldJournalItem(Properties properties) {
        super(properties);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) JournalOpener.open();
        return InteractionResult.SUCCESS;
    }
}
