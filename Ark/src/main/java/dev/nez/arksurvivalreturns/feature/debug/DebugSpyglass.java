package dev.nez.arksurvivalreturns.feature.debug;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpyglassItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** A separate development tool; vanilla scopes retain their ordinary behavior. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DebugSpyglass {
    public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ArkSurvivalReturns.id("debug_spyglass"));

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, KEY.identifier(), () -> new SpyglassItem(new Item.Properties()
                .setId(KEY).stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    }
    public static Item item() { return BuiltInRegistries.ITEM.getValue(KEY); }
    public static boolean using(Player player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.isUsingItem() && player.getUseItem().is(item());
    }
    @SubscribeEvent public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().identifier().equals(ArkSurvivalReturns.id("main"))) event.accept(item());
    }
    private DebugSpyglass() {}
}
