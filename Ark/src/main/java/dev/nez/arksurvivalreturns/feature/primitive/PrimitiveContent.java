package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.LinkedHashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Prehistoric progression content: loose rocks, the first stone tools, the stone fire, the primitive
 * forge, tiered dried meat and the dinosaur meats. Registered into the shared {@link ModContent} registers.
 */
public final class PrimitiveContent {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ArkSurvivalReturns.MOD_ID);
    public static final TagKey<Item> ROCK_REPAIR = TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("primitive/rock_materials"));
    public static final TagKey<Block> LOOSE_ROCK_GROUND = TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id("primitive/loose_rock_ground"));
    /** Knapped stone: cheap, short-lived, and only ever the first rung. */
    public static final ToolMaterial KNAPPED_STONE = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 48, 2.0f, 0.0f, 5, ROCK_REPAIR);

    public static final DeferredHolder<Feature<?>, LooseRockFeature> LOOSE_ROCK_FEATURE =
            FEATURES.register("loose_rock", () -> new LooseRockFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredItem<Item> ROCK = ModContent.ITEMS.registerSimpleItem("rock", p -> p.stacksTo(64));
    public static final DeferredBlock<LooseRockBlock> LOOSE_ROCK = ModContent.BLOCKS.registerBlock("loose_rock", LooseRockBlock::new,
            p -> p.instabreak().noCollision().noOcclusion().pushReaction(PushReaction.DESTROY).sound(SoundType.STONE));

    public static final DeferredItem<Item> STONE_KNIFE = ModContent.ITEMS.registerItem("stone_knife", Item::new,
            p -> p.sword(KNAPPED_STONE, 0.5f, -1.8f));
    public static final DeferredItem<Item> STONE_HATCHET = ModContent.ITEMS.registerItem("stone_hatchet", Item::new,
            p -> p.axe(KNAPPED_STONE, 4.0f, -3.2f));
    public static final DeferredItem<FireStarterItem> FIRE_STARTER = ModContent.ITEMS.registerItem("fire_starter",
            FireStarterItem::new, p -> p.stacksTo(1).durability(16));

    public static final DeferredBlock<StoneFireBlock> STONE_FIRE = ModContent.BLOCKS.registerBlock("stone_fire", StoneFireBlock::new,
            p -> p.strength(1.5f).noOcclusion().sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(StoneFireBlock.LIT) ? 13 : 0));
    public static final DeferredItem<BlockItem> STONE_FIRE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(STONE_FIRE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFireBlockEntity>> STONE_FIRE_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("stone_fire",
                    () -> new BlockEntityType<>(StoneFireBlockEntity::new, STONE_FIRE.get()));

    public static final DeferredBlock<PrimitiveForgeBlock> PRIMITIVE_FORGE = ModContent.BLOCKS.registerBlock("primitive_forge",
            PrimitiveForgeBlock::new, p -> p.strength(2.5f).requiresCorrectToolForDrops().sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(PrimitiveForgeBlock.LIT) ? 11 : 0));
    public static final DeferredItem<BlockItem> PRIMITIVE_FORGE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(PRIMITIVE_FORGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrimitiveForgeBlockEntity>> PRIMITIVE_FORGE_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("primitive_forge",
                    () -> new BlockEntityType<>(PrimitiveForgeBlockEntity::new, PRIMITIVE_FORGE.get()));

    /** Dried meat keeps one id; its dryness tier (I-III) lives on the stack and sets its food and effects. */
    public static final DeferredItem<DriedMeatItem> DRIED_MEAT = ModContent.ITEMS.registerItem("dried_meat", DriedMeatItem::new,
            p -> p.stacksTo(64).food(DriedMeatItem.food(1), DriedMeatItem.consumable(1)));

    /** Raw and cooked dinosaur meats, keyed by meat family. */
    public static final Map<DinoMeat, DeferredItem<Item>> RAW_MEAT = new LinkedHashMap<>();
    public static final Map<DinoMeat, DeferredItem<Item>> COOKED_MEAT = new LinkedHashMap<>();

    static {
        for (DinoMeat meat : DinoMeat.values()) {
            RAW_MEAT.put(meat, ModContent.ITEMS.registerSimpleItem(meat.rawId(), p -> p.stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(meat.rawNutrition).saturationModifier(0.3f).build())));
            COOKED_MEAT.put(meat, ModContent.ITEMS.registerSimpleItem(meat.cookedId(), p -> p.stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(meat.cookedNutrition).saturationModifier(meat.cookedSaturation).build())));
        }
    }

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }

    /** Creative tab order: the progression reads left to right. */
    public static void displayItems(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(ROCK.get());
        output.accept(STONE_KNIFE.get());
        output.accept(STONE_HATCHET.get());
        output.accept(FIRE_STARTER.get());
        output.accept(STONE_FIRE_ITEM.get());
        output.accept(PRIMITIVE_FORGE_ITEM.get());
        for (int tier = 1; tier <= DriedMeatItem.MAX_TIER; tier++) output.accept(DriedMeatItem.withTier(tier));
        RAW_MEAT.values().forEach(item -> output.accept(item.get()));
        COOKED_MEAT.values().forEach(item -> output.accept(item.get()));
    }

    private PrimitiveContent() {}
}
