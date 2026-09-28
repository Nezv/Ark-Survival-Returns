package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import dev.nez.arksurvivalreturns.feature.bronze.BronzeContent;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

/** Bronze Age metal and gear: tin ore, the copper+tin alloy, bronze tools, weapons and armour. */
final class BronzeGameTests {

    /** Tin ore mines at stone tier (like copper and iron) and drops raw tin for a rock pickaxe. */
    static void tinOre(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos oreRel = new BlockPos(8, 3, 8), deepRel = new BlockPos(8, 3, 6);
        h.setBlock(oreRel, BronzeContent.TIN_ORE.get().defaultBlockState());
        h.setBlock(deepRel, BronzeContent.DEEPSLATE_TIN_ORE.get().defaultBlockState());
        ItemStack pickaxe = new ItemStack(PrimitiveContent.ROCK_PICKAXE.get());
        h.assertTrue(pickaxe.isCorrectToolForDrops(BronzeContent.TIN_ORE.get().defaultBlockState()),
                "The rock pickaxe must mine tin ore");
        h.assertTrue(pickaxe.isCorrectToolForDrops(BronzeContent.DEEPSLATE_TIN_ORE.get().defaultBlockState()),
                "The rock pickaxe must mine deepslate tin ore");
        var oreDrops = Block.getDrops(BronzeContent.TIN_ORE.get().defaultBlockState(), level, h.absolutePos(oreRel), null, null, pickaxe);
        h.assertTrue(oreDrops.stream().anyMatch(stack -> stack.is(BronzeContent.RAW_TIN.get())), "Tin ore must drop raw tin");
        var deepDrops = Block.getDrops(BronzeContent.DEEPSLATE_TIN_ORE.get().defaultBlockState(), level, h.absolutePos(deepRel), null, null, pickaxe);
        h.assertTrue(deepDrops.stream().anyMatch(stack -> stack.is(BronzeContent.RAW_TIN.get())), "Deepslate tin ore must drop raw tin");
        h.succeed();
    }

    /** The Primitive Forge reduces tin ore and raw tin to tin ingots, and bronze blend to bronze. */
    static void smelting(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos forgeRel = new BlockPos(8, 3, 8);
        h.setBlock(forgeRel, PrimitiveContent.PRIMITIVE_FORGE.get().defaultBlockState());
        PrimitiveForgeBlockEntity forge = (PrimitiveForgeBlockEntity) level.getBlockEntity(h.absolutePos(forgeRel));
        h.assertTrue(forge != null, "The forge block entity is missing");
        h.assertTrue(forge.isSmeltable(new ItemStack(BronzeContent.RAW_TIN.get())), "Raw tin must be smeltable");
        h.assertTrue(forge.isSmeltable(new ItemStack(BronzeContent.TIN_ORE_ITEM.get())), "Tin ore must be smeltable");
        h.assertTrue(forge.isSmeltable(new ItemStack(BronzeContent.DEEPSLATE_TIN_ORE_ITEM.get())), "Deepslate tin ore must be smeltable");
        h.assertTrue(forge.insert(new ItemStack(BronzeContent.BRONZE_BLEND.get(), 4), PrimitiveForgeBlockEntity.INPUT) == 4,
                "The forge must take bronze blend");
        h.assertTrue(forge.insert(new ItemStack(Items.CHARCOAL, 4), PrimitiveForgeBlockEntity.FUEL) == 4, "Charcoal must fuel the forge");
        for (int tick = 0; tick < 2000 && forge.output().getCount() < 4; tick++) forge.step();
        h.assertTrue(forge.output().is(BronzeContent.BRONZE_INGOT.get()) && forge.output().getCount() == 4,
                "Four bronze blend must become four bronze ingots, got " + forge.output());
        h.succeed();
    }

    /** Every bronze recipe resolves: the alloy, the four tools, the two weapons and the four armour pieces. */
    static void recipes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var recipes = level.getServer().getRecipeManager();
        ItemStack copper = new ItemStack(Items.COPPER_INGOT), tin = new ItemStack(BronzeContent.TIN_INGOT.get()),
                bronze = new ItemStack(BronzeContent.BRONZE_INGOT.get()), stick = new ItemStack(Items.STICK), none = ItemStack.EMPTY;

        var blend = CraftingInput.of(2, 2, List.of(copper, copper, copper, tin));
        var blendRecipe = recipes.getRecipeFor(RecipeType.CRAFTING, blend, level);
        h.assertTrue(blendRecipe.isPresent() && blendRecipe.get().value().assemble(blend).is(BronzeContent.BRONZE_BLEND.get()),
                "Three copper and one tin must make bronze blend");

        var pickaxe = CraftingInput.of(3, 3, List.of(bronze, bronze, bronze, none, stick, none, none, stick, none));
        assemble(h, recipes, level, pickaxe, BronzeContent.BRONZE_PICKAXE.get(), "bronze pickaxe");
        var axe = CraftingInput.of(2, 3, List.of(bronze, bronze, bronze, stick, none, stick));
        assemble(h, recipes, level, axe, BronzeContent.BRONZE_AXE.get(), "bronze axe");
        var shovel = CraftingInput.of(1, 3, List.of(bronze, stick, stick));
        assemble(h, recipes, level, shovel, BronzeContent.BRONZE_SHOVEL.get(), "bronze shovel");
        var hoe = CraftingInput.of(2, 3, List.of(bronze, bronze, none, stick, none, stick));
        assemble(h, recipes, level, hoe, BronzeContent.BRONZE_HOE.get(), "bronze hoe");
        var longsword = CraftingInput.of(2, 3, List.of(none, bronze, bronze, bronze, none, stick));
        assemble(h, recipes, level, longsword, BronzeContent.BRONZE_LONGSWORD.get(), "bronze longsword");
        var hammer = CraftingInput.of(3, 3, List.of(bronze, bronze, bronze, bronze, stick, bronze, none, stick, none));
        assemble(h, recipes, level, hammer, BronzeContent.BRONZE_HAMMER.get(), "bronze hammer");

        var helmet = CraftingInput.of(3, 2, List.of(bronze, bronze, bronze, bronze, none, bronze));
        assemble(h, recipes, level, helmet, BronzeContent.BRONZE_HELMET.get(), "bronze helmet");
        var chest = CraftingInput.of(3, 3, List.of(bronze, none, bronze, bronze, bronze, bronze, bronze, bronze, bronze));
        assemble(h, recipes, level, chest, BronzeContent.BRONZE_CHESTPLATE.get(), "bronze chestplate");
        var legs = CraftingInput.of(3, 3, List.of(bronze, bronze, bronze, bronze, none, bronze, bronze, none, bronze));
        assemble(h, recipes, level, legs, BronzeContent.BRONZE_LEGGINGS.get(), "bronze leggings");
        var boots = CraftingInput.of(3, 2, List.of(bronze, none, bronze, bronze, none, bronze));
        assemble(h, recipes, level, boots, BronzeContent.BRONZE_BOOTS.get(), "bronze boots");
        h.succeed();
    }

    private static void assemble(GameTestHelper h, net.minecraft.world.item.crafting.RecipeManager recipes, ServerLevel level,
            CraftingInput input, net.minecraft.world.item.Item expected, String label) {
        var found = recipes.getRecipeFor(RecipeType.CRAFTING, input, level);
        h.assertTrue(found.isPresent() && found.get().value().assemble(input).is(expected), "Missing or wrong recipe for " + label);
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.001; }

    /** Thirteen armour points, split 2/5/4/2 across helmet, chestplate, leggings and boots. */
    static void armor(GameTestHelper h) {
        h.assertTrue(near(armorPoints(BronzeContent.BRONZE_HELMET.get()), 2.0), "The bronze helmet must give 2 armour");
        h.assertTrue(near(armorPoints(BronzeContent.BRONZE_CHESTPLATE.get()), 5.0), "The bronze chestplate must give 5 armour");
        h.assertTrue(near(armorPoints(BronzeContent.BRONZE_LEGGINGS.get()), 4.0), "The bronze leggings must give 4 armour");
        h.assertTrue(near(armorPoints(BronzeContent.BRONZE_BOOTS.get()), 2.0), "The bronze boots must give 2 armour");
        h.succeed();
    }

    private static double armorPoints(net.minecraft.world.item.Item item) {
        return new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().is(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    /** The longsword hits lighter and faster than the hammer, which swings slow, heavy and knocks back harder. */
    static void weapons(GameTestHelper h) {
        double swordDamage = modifier(BronzeContent.BRONZE_LONGSWORD.get(), Attributes.ATTACK_DAMAGE);
        double swordSpeed = modifier(BronzeContent.BRONZE_LONGSWORD.get(), Attributes.ATTACK_SPEED);
        double hammerDamage = modifier(BronzeContent.BRONZE_HAMMER.get(), Attributes.ATTACK_DAMAGE);
        double hammerSpeed = modifier(BronzeContent.BRONZE_HAMMER.get(), Attributes.ATTACK_SPEED);
        double hammerKnockback = modifier(BronzeContent.BRONZE_HAMMER.get(), Attributes.ATTACK_KNOCKBACK);
        // Plus the player's inherent fist damage (1.0) and base attack speed (4.0): ~7.0/1.3 and ~9.0/0.9.
        h.assertTrue(near(swordDamage, 6.0), "The longsword must add 6.0 attack damage over the fist, got " + swordDamage);
        h.assertTrue(near(swordSpeed, -2.7), "The longsword must slow the base attack speed by 2.7, got " + swordSpeed);
        h.assertTrue(near(hammerDamage, 8.0), "The hammer must add 8.0 attack damage over the fist, got " + hammerDamage);
        h.assertTrue(near(hammerSpeed, -3.1), "The hammer must slow the base attack speed by 3.1, got " + hammerSpeed);
        h.assertTrue(hammerDamage > swordDamage, "The hammer must hit harder than the longsword");
        h.assertTrue(hammerSpeed < swordSpeed, "The hammer must swing slower than the longsword");
        h.assertTrue(near(hammerKnockback, 1.0), "The hammer must carry a bonus knockback attribute, got " + hammerKnockback);
        h.succeed();
    }

    private static double modifier(net.minecraft.world.item.Item item, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().is(attribute)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private BronzeGameTests() {}
}
