package dev.nez.arksurvivalreturns.feature.bronze;

import java.util.EnumMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Bronze Age metal and gear: tin ore, the copper+tin alloy, the bronze tool set, the two Bronze
 * weapons (longsword and hammer) and bronze armour. Sits above keratin, mining at iron's tier;
 * iron gear itself is rebalanced later.
 */
public final class BronzeContent {
    // ------------------------------------------------------------------------------------------- tags
    /** Item tag holding the ore blocks' item form (tin_ore, deepslate_tin_ore), for smelting by tag. */
    public static final TagKey<Item> TIN_ORES = c("ores/tin");
    public static final TagKey<Item> RAW_TIN_MATERIALS = c("raw_materials/tin");
    public static final TagKey<Item> TIN_INGOTS = c("ingots/tin");
    public static final TagKey<Item> BRONZE_INGOTS = c("ingots/bronze");

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    // --------------------------------------------------------------------------------------- tin ore
    public static final DeferredBlock<Block> TIN_ORE = ModContent.BLOCKS.registerBlock("tin_ore", Block::new,
            p -> p.strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> TIN_ORE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(TIN_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE = ModContent.BLOCKS.registerBlock("deepslate_tin_ore", Block::new,
            p -> p.strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredItem<BlockItem> DEEPSLATE_TIN_ORE_ITEM = ModContent.ITEMS.registerSimpleBlockItem(DEEPSLATE_TIN_ORE);

    public static final DeferredItem<Item> RAW_TIN = ModContent.ITEMS.registerSimpleItem("raw_tin", p -> p.stacksTo(64));
    public static final DeferredItem<Item> TIN_INGOT = ModContent.ITEMS.registerSimpleItem("tin_ingot", p -> p.stacksTo(64));
    /** The alloy step: 3 copper + 1 tin, smelted into bronze at the Primitive Forge. */
    public static final DeferredItem<Item> BRONZE_BLEND = ModContent.ITEMS.registerSimpleItem("bronze_blend", p -> p.stacksTo(64));
    public static final DeferredItem<Item> BRONZE_INGOT = ModContent.ITEMS.registerSimpleItem("bronze_ingot", p -> p.stacksTo(64));

    // ------------------------------------------------------------------------------------ bronze tier
    /** Mines at iron's tier; clearly above the rock/keratin set, ahead of the (future rebalanced) iron. */
    public static final ToolMaterial BRONZE_TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 200, 5.5f, 1.5f, 12, BRONZE_INGOTS);
    public static final ResourceKey<EquipmentAsset> BRONZE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, ArkSurvivalReturns.id("bronze"));
    /** Thirteen armour points for the set: above keratin (8), below iron (15). */
    public static final ArmorMaterial BRONZE_ARMOR = new ArmorMaterial(13, defense(2, 4, 5, 2, 5), 11,
            SoundEvents.ARMOR_EQUIP_IRON, 0.0f, 0.0f, BRONZE_INGOTS, BRONZE_ASSET);

    public static final DeferredItem<Item> BRONZE_PICKAXE = ModContent.ITEMS.registerItem("bronze_pickaxe", Item::new,
            p -> p.pickaxe(BRONZE_TOOL, 1.0f, -2.8f));
    public static final DeferredItem<AxeItem> BRONZE_AXE = ModContent.ITEMS.registerItem("bronze_axe",
            p -> new AxeItem(BRONZE_TOOL, 6.0f, -3.1f, p));
    public static final DeferredItem<ShovelItem> BRONZE_SHOVEL = ModContent.ITEMS.registerItem("bronze_shovel",
            p -> new ShovelItem(BRONZE_TOOL, 1.5f, -3.0f, p));
    public static final DeferredItem<HoeItem> BRONZE_HOE = ModContent.ITEMS.registerItem("bronze_hoe",
            p -> new HoeItem(BRONZE_TOOL, 0.0f, -2.0f, p));

    /** Two Bronze weapons, either unlocks "Knight of the Realm": a longer-reaching blade, a slow heavy maul. */
    public static final DeferredItem<Item> BRONZE_LONGSWORD = ModContent.ITEMS.registerItem("bronze_longsword", Item::new,
            p -> p.sword(BRONZE_TOOL, 4.5f, -2.7f));
    public static final DeferredItem<Item> BRONZE_HAMMER = ModContent.ITEMS.registerItem("bronze_hammer", Item::new,
            BronzeContent::hammerProperties);

    /** A sword's tool profile (cobweb, disable-block) with heavier damage, slower swing and bonus knockback. */
    private static Item.Properties hammerProperties(Item.Properties p) {
        return p.sword(BRONZE_TOOL, 6.5f, -3.1f).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        6.5f + BRONZE_TOOL.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        -3.1f, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ArkSurvivalReturns.id("bronze_hammer_knockback"),
                        1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
    }

    public static final DeferredItem<Item> BRONZE_HELMET = armor("bronze_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> BRONZE_CHESTPLATE = armor("bronze_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> BRONZE_LEGGINGS = armor("bronze_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> BRONZE_BOOTS = armor("bronze_boots", ArmorType.BOOTS);

    private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helmet, int body) {
        var map = new EnumMap<ArmorType, Integer>(ArmorType.class);
        map.put(ArmorType.BOOTS, boots);
        map.put(ArmorType.LEGGINGS, legs);
        map.put(ArmorType.CHESTPLATE, chest);
        map.put(ArmorType.HELMET, helmet);
        map.put(ArmorType.BODY, body);
        return map;
    }

    private static DeferredItem<Item> armor(String id, ArmorType type) {
        return ModContent.ITEMS.registerItem(id, Item::new, p -> p.humanoidArmor(BRONZE_ARMOR, type));
    }

    /** Forces this class to load before the RegisterEvent fires; it owns no extra registries of its own. */
    public static void register(IEventBus bus) {}

    /** Creative tab order: ore and ingots, the alloy, tools, the two weapons, then armour. */
    public static void displayItems(CreativeModeTab.Output output) {
        output.accept(TIN_ORE_ITEM.get());
        output.accept(DEEPSLATE_TIN_ORE_ITEM.get());
        output.accept(RAW_TIN.get());
        output.accept(TIN_INGOT.get());
        output.accept(BRONZE_BLEND.get());
        output.accept(BRONZE_INGOT.get());
        output.accept(BRONZE_PICKAXE.get());
        output.accept(BRONZE_AXE.get());
        output.accept(BRONZE_SHOVEL.get());
        output.accept(BRONZE_HOE.get());
        output.accept(BRONZE_LONGSWORD.get());
        output.accept(BRONZE_HAMMER.get());
        output.accept(BRONZE_HELMET.get());
        output.accept(BRONZE_CHESTPLATE.get());
        output.accept(BRONZE_LEGGINGS.get());
        output.accept(BRONZE_BOOTS.get());
    }

    private BronzeContent() {}
}
