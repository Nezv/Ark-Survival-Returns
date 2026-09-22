package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An item whose tooltip explains its part in the guardian campaign. */
public class GuardianLoreItem extends Item {
    private final String tooltipKey;

    public GuardianLoreItem(Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
