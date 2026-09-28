package dev.nez.arksurvivalreturns.feature.sulphur;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sulphur crystals: an amethyst-alike ore chain (a plain block, a budding block that grows buds on
 * random ticks, three bud stages and the cluster). Sulphur items feed the Crusher's gunpowder recipe
 * ({@link dev.nez.arksurvivalreturns.feature.station.CrusherRecipes}). Grown in caves by
 * {@link SulphurPatchFeature}.
 */
public final class SulphurContent {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ArkSurvivalReturns.MOD_ID);

    public static final DeferredHolder<Feature<?>, SulphurPatchFeature> SULPHUR_PATCH_FEATURE =
            FEATURES.register("sulphur_patch", () -> new SulphurPatchFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredItem<Item> SULPHUR = ModContent.ITEMS.registerSimpleItem("sulphur", p -> p.stacksTo(64));

    public static final DeferredBlock<Block> SULPHUR_BLOCK = ModContent.BLOCKS.registerBlock("sulphur_block", Block::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.5f).sound(SoundType.AMETHYST).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SULPHUR_BLOCK_ITEM = ModContent.ITEMS.registerSimpleBlockItem(SULPHUR_BLOCK);

    /** Cannot be silk-touched: it has no loot table entry at all, same as budding amethyst. */
    public static final DeferredBlock<BuddingSulphurBlock> BUDDING_SULPHUR = ModContent.BLOCKS.registerBlock("budding_sulphur",
            BuddingSulphurBlock::new, p -> p.mapColor(MapColor.COLOR_YELLOW).randomTicks().strength(1.5f)
                    .sound(SoundType.AMETHYST).requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> BUDDING_SULPHUR_ITEM = ModContent.ITEMS.registerSimpleBlockItem(BUDDING_SULPHUR);

    public static final DeferredBlock<SulphurClusterBlock> SMALL_SULPHUR_BUD = ModContent.BLOCKS.registerBlock("small_sulphur_bud",
            p -> new SulphurClusterBlock(3.0f, 8.0f, p), SulphurContent::budProperties);
    public static final DeferredItem<BlockItem> SMALL_SULPHUR_BUD_ITEM = ModContent.ITEMS.registerSimpleBlockItem(SMALL_SULPHUR_BUD);

    public static final DeferredBlock<SulphurClusterBlock> MEDIUM_SULPHUR_BUD = ModContent.BLOCKS.registerBlock("medium_sulphur_bud",
            p -> new SulphurClusterBlock(4.0f, 10.0f, p), SulphurContent::budProperties);
    public static final DeferredItem<BlockItem> MEDIUM_SULPHUR_BUD_ITEM = ModContent.ITEMS.registerSimpleBlockItem(MEDIUM_SULPHUR_BUD);

    public static final DeferredBlock<SulphurClusterBlock> LARGE_SULPHUR_BUD = ModContent.BLOCKS.registerBlock("large_sulphur_bud",
            p -> new SulphurClusterBlock(5.0f, 10.0f, p), SulphurContent::budProperties);
    public static final DeferredItem<BlockItem> LARGE_SULPHUR_BUD_ITEM = ModContent.ITEMS.registerSimpleBlockItem(LARGE_SULPHUR_BUD);

    public static final DeferredBlock<SulphurClusterBlock> SULPHUR_CLUSTER = ModContent.BLOCKS.registerBlock("sulphur_cluster",
            p -> new SulphurClusterBlock(7.0f, 10.0f, p), SulphurContent::budProperties);
    public static final DeferredItem<BlockItem> SULPHUR_CLUSTER_ITEM = ModContent.ITEMS.registerSimpleBlockItem(SULPHUR_CLUSTER);

    private static BlockBehaviour.Properties budProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_YELLOW).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
                .strength(1.5f).pushReaction(PushReaction.DESTROY);
    }

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }

    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(SULPHUR.get());
        output.accept(SULPHUR_BLOCK_ITEM.get());
        output.accept(BUDDING_SULPHUR_ITEM.get());
        output.accept(SMALL_SULPHUR_BUD_ITEM.get());
        output.accept(MEDIUM_SULPHUR_BUD_ITEM.get());
        output.accept(LARGE_SULPHUR_BUD_ITEM.get());
        output.accept(SULPHUR_CLUSTER_ITEM.get());
    }

    private SulphurContent() {}
}
