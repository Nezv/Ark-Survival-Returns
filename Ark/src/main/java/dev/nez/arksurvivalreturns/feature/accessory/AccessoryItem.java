package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An Ark accessory. Worn in a Curios slot; the tooltip names its effect, a line of lore, and (for relics
 * that recharge) how long until it is ready again. Attribute bonuses are listed by Curios under "When worn".
 */
public class AccessoryItem extends Item {
    private final Accessory accessory;

    public AccessoryItem(Properties properties, Accessory accessory) {
        super(properties);
        this.accessory = accessory;
    }

    public Accessory accessory() { return accessory; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(accessory.effectKey()).withStyle(ChatFormatting.BLUE));
        long ready = stack.getOrDefault(AccessoryContent.RECHARGE.get(), 0L);
        if (ready > 0 && context.level() != null) {
            long left = ready - context.level().getGameTime();
            if (left > 0) builder.accept(Component.translatable("accessory.arksurvivalreturns.recharging",
                    Math.max(1, left / 1200)).withStyle(ChatFormatting.DARK_GRAY));
        }
        builder.accept(Component.translatable(accessory.loreKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
