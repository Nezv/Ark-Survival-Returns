package dev.nez.arksurvivalreturns.feature.explosive;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The explosive arrow: an {@link ExplosiveArrowItem} (an {@link net.minecraft.world.item.ArrowItem})
 * paired with the {@link ExplosiveArrow} entity. Works from bows and crossbows through the
 * {@code #minecraft:arrows} item tag (SulphurData datagen) and from dispensers through the projectile
 * dispense behaviour registered below.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class ExplosiveContent {
    public static final DeferredHolder<EntityType<?>, EntityType<ExplosiveArrow>> EXPLOSIVE_ARROW = ModContent.ENTITIES.register(
            "explosive_arrow", () -> EntityType.Builder.<ExplosiveArrow>of(ExplosiveArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.id("explosive_arrow"))));

    public static final DeferredItem<ExplosiveArrowItem> EXPLOSIVE_ARROW_ITEM = ModContent.ITEMS.registerItem(
            "explosive_arrow", ExplosiveArrowItem::new, p -> p.stacksTo(64));

    public static void register(IEventBus bus) {
        // No bus-side registration of its own; the item and entity type register through the shared
        // ModContent registers, and the field initializers above already ran by the time this is called.
    }

    /** A dispenser fires it like any other arrow instead of just dropping it out. */
    @SubscribeEvent static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerProjectileBehavior(EXPLOSIVE_ARROW_ITEM.get()));
    }

    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(EXPLOSIVE_ARROW_ITEM.get());
    }

    private ExplosiveContent() {}
}
