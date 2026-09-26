package dev.nez.arksurvivalreturns.feature.station;

import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.camp.FiberBandageItem;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Exclusive workstations: the Working Station (the crafting table), the Storage Crate (every wooden chest),
 * the Ark smithing table, the Medicine Bench and the unpowered Crusher. Vanilla recipes for the replaced
 * blocks now make these, and stray vanilla items convert on pickup ({@link StationEvents}).
 */
public final class StationContent {
    /** Results only the Medicine Bench makes: the advanced bandages and the mixtures. */
    public static final TagKey<Item> MEDICINE = TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("medicine"));

    public static final DeferredBlock<StationBlock> WORKING_STATION = ModContent.BLOCKS.registerBlock("working_station",
            p -> new StationBlock(StationBlock.Kind.WORKING, p), p -> p.strength(2.5f).noOcclusion().sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredItem<BlockItem> WORKING_STATION_ITEM = ModContent.ITEMS.registerSimpleBlockItem(WORKING_STATION);

    public static final DeferredBlock<StationBlock> MEDICINE_BENCH = ModContent.BLOCKS.registerBlock("medicine_bench",
            p -> new StationBlock(StationBlock.Kind.MEDICINE, p), p -> p.strength(2.5f).noOcclusion().sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredItem<BlockItem> MEDICINE_BENCH_ITEM = ModContent.ITEMS.registerSimpleBlockItem(MEDICINE_BENCH);

    public static final DeferredBlock<StationBlock> SMITHING_TABLE = ModContent.BLOCKS.registerBlock("smithing_table",
            p -> new StationBlock(StationBlock.Kind.SMITHING, p), p -> p.strength(3.5f).noOcclusion().sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SMITHING_TABLE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(SMITHING_TABLE);

    public static final DeferredBlock<StorageCrateBlock> STORAGE_CRATE = ModContent.BLOCKS.registerBlock("storage_crate",
            StorageCrateBlock::new, p -> p.strength(2.5f).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredItem<BlockItem> STORAGE_CRATE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(STORAGE_CRATE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCrateBlockEntity>> STORAGE_CRATE_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("storage_crate",
                    () -> new BlockEntityType<>(StorageCrateBlockEntity::new, STORAGE_CRATE.get()));

    public static final DeferredBlock<CrusherBlock> CRUSHER = ModContent.BLOCKS.registerBlock("crusher",
            CrusherBlock::new, p -> p.strength(3.5f).noOcclusion().sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> CRUSHER_ITEM = ModContent.ITEMS.registerSimpleBlockItem(CRUSHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("crusher",
                    () -> new BlockEntityType<>(CrusherBlockEntity::new, CRUSHER.get()));

    /** Herbal bandage: double the fiber bandage's healing plus a short regeneration. */
    public static final DeferredItem<FiberBandageItem> HERBAL_BANDAGE = ModContent.ITEMS.registerItem("herbal_bandage",
            p -> new FiberBandageItem(p, 2f, 100), p -> p.stacksTo(16));
    /** Healing mixture: a bottled draught that regenerates and cures poison. */
    public static final DeferredItem<Item> HEALING_MIXTURE = ModContent.ITEMS.registerSimpleItem("healing_mixture",
            p -> p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE).component(DataComponents.CONSUMABLE,
                    Consumables.defaultDrink()
                            .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 300, 1))))
                            .onConsume(new RemoveStatusEffectsConsumeEffect(MobEffects.POISON))
                            .build()));

    public static boolean medicine(ItemStack stack) { return stack.is(MEDICINE); }

    public static void register(IEventBus bus) {
        bus.addListener(StationContent::capabilities);
    }

    /**
     * Crates and the crusher expose their own slots only. Tom's Storage, hoppers and pipes reach each crate
     * separately, so a joined group is never counted twice.
     */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, STORAGE_CRATE_BLOCK_ENTITY.get(),
                (crate, side) -> VanillaContainerWrapper.of(crate));
        event.registerBlockEntity(Capabilities.Item.BLOCK, CRUSHER_BLOCK_ENTITY.get(),
                (crusher, side) -> VanillaContainerWrapper.of(crusher));
    }

    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(WORKING_STATION_ITEM.get());
        output.accept(STORAGE_CRATE_ITEM.get());
        output.accept(SMITHING_TABLE_ITEM.get());
        output.accept(MEDICINE_BENCH_ITEM.get());
        output.accept(CRUSHER_ITEM.get());
        output.accept(HERBAL_BANDAGE.get());
        output.accept(HEALING_MIXTURE.get());
    }

    private StationContent() {}
}
