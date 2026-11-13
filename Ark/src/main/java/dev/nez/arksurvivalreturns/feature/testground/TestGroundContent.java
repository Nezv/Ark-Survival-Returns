package dev.nez.arksurvivalreturns.feature.testground;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The testing ground's one block, the Specimen Post: it holds a chosen creature (species, colour, level, hunger, how
 * it may move) and puts it on itself on a redstone pulse, a command or its screen, and times what happens to it.
 * Operators and creative players only; it is a development tool, not a survival block (no recipe, no drop).
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TestGroundContent {
    public static final DeferredBlock<SpecimenPostBlock> SPECIMEN_POST = ModContent.BLOCKS.registerBlock("specimen_post",
            SpecimenPostBlock::new, p -> p.strength(-1f, 3_600_000f).noLootTable().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> SPECIMEN_POST_ITEM = ModContent.ITEMS.registerSimpleBlockItem(SPECIMEN_POST);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpecimenPostBlockEntity>> SPECIMEN_POST_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("specimen_post",
                    () -> new BlockEntityType<>(SpecimenPostBlockEntity::new, SPECIMEN_POST.get()));

    /** Loads the registrations above before the registers fire. */
    public static void init() {}

    @SubscribeEvent public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().identifier().equals(ArkSurvivalReturns.id("main"))) event.accept(SPECIMEN_POST_ITEM.get());
    }

    private TestGroundContent() {}
}
