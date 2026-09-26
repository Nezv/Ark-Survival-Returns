package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryAttributes;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryContent;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryEffects;
import dev.nez.arksurvivalreturns.feature.accessory.TrophyDrops;
import dev.nez.arksurvivalreturns.feature.accessory.Worn;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Accessories (P13 / I07): catalogue completeness, the Curios wiring, the senses and the creature parts. */
public final class AccessoryGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, ArkSurvivalReturns.MOD_ID);

    static {
        FUNCTIONS.register("accessory_catalog", () -> AccessoryGameTests::catalog);
        FUNCTIONS.register("accessory_curios", () -> AccessoryGameTests::curios);
        FUNCTIONS.register("accessory_senses", () -> AccessoryGameTests::senses);
        FUNCTIONS.register("accessory_trophies", () -> AccessoryGameTests::trophies);
    }

    /** Every accessory ships an icon, a worn model and texture, a recipe and its Curios slot tag. */
    static void catalog(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (Accessory accessory : Accessory.values()) {
            Item item = accessory.item();
            h.assertTrue(Accessory.of(item) == accessory, accessory.id + " must map back to its accessory");
            for (String path : new String[]{"textures/item/accessory/" + accessory.id + ".png", "worn/" + accessory.id + ".json",
                    "textures/entity/accessory/" + accessory.id + ".png"}) {
                h.assertTrue(AccessoryGameTests.class.getResource("/assets/" + ArkSurvivalReturns.MOD_ID + "/" + path) != null,
                        accessory.id + " is missing " + path);
            }
            var key = ResourceKey.create(Registries.RECIPE, ArkSurvivalReturns.id("accessory/" + accessory.id));
            h.assertTrue(recipes.byKey(key).isPresent(), accessory.id + " has no recipe");
            TagKey<Item> slot = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("curios", accessory.slot.id()));
            h.assertTrue(item.getDefaultInstance().is(slot), accessory.id + " is not tagged for the " + accessory.slot.id() + " slot");
            h.assertTrue(item.getDefaultMaxStackSize() == 1, accessory.id + " must not stack");
        }
        for (String trophy : AccessoryContent.TROPHY_IDS) {
            h.assertTrue(AccessoryGameTests.class.getResource("/assets/" + ArkSurvivalReturns.MOD_ID + "/textures/item/trophy/" + trophy + ".png") != null,
                    "Trophy " + trophy + " has no icon");
        }
        h.assertTrue(AccessoryContent.ITEMS.get(Accessory.FUR_MANTLE).get().getDefaultInstance()
                .is(net.minecraft.tags.ItemTags.FREEZE_IMMUNE_WEARABLES), "The Fur Mantle keeps its wearer from freezing");
        h.succeed();
    }

    /** With Curios installed: worn detection, the one-of-each rule and the attribute bonuses. */
    static void curios(GameTestHelper h) {
        if (!ModList.get().isLoaded("curios")) {
            h.succeed();
            return;
        }
        CuriosProbe.check(h);
    }

    /** Stealth attributes scale the senses and the pack frame's attribute reaches the carried-mass capacity. */
    static void senses(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkAccessorySenses"));
        for (var attribute : java.util.List.of(AccessoryAttributes.VISIBILITY, AccessoryAttributes.NOISE,
                AccessoryAttributes.SCENT, AccessoryAttributes.CARRY_CAPACITY)) {
            h.assertTrue(player.getAttribute(attribute) != null, "Players must carry " + attribute.getId());
            h.assertTrue(AccessoryAttributes.value(player, attribute) == 1.0, attribute.getId() + " starts at 1.0");
        }
        var visibility = player.getAttribute(AccessoryAttributes.VISIBILITY);
        Identifier test = ArkSurvivalReturns.id("test/ghillie");
        visibility.addTransientModifier(new AttributeModifier(test, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        h.assertTrue(Math.abs(AccessoryAttributes.value(player, AccessoryAttributes.VISIBILITY) - 0.6) < 1e-6,
                "A -40% visibility bonus leaves 0.6");
        visibility.removeModifier(test);
        var carry = player.getAttribute(AccessoryAttributes.CARRY_CAPACITY);
        carry.addTransientModifier(new AttributeModifier(test, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        MassService.refresh(player);
        double base = dev.nez.arksurvivalreturns.feature.mass.MassRules.playerCapacity();
        if (dev.nez.arksurvivalreturns.feature.mass.MassRules.enabled()) {
            h.assertTrue(Math.abs(MassService.load(player).capacity() - base * 1.4) < 1e-6, "A pack frame carries 40% more");
        }
        carry.removeModifier(test);
        CreatureEntity parasaur = ModContent.CREATURES.get(Species.PARASAUR).get().create(level, EntitySpawnReason.COMMAND);
        parasaur.setNoAi(true);
        parasaur.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 4))));
        level.addFreshEntity(parasaur);
        h.assertTrue(AccessoryEffects.unaware(parasaur, player), "A calm creature has not noticed the hunter");
        h.assertFalse(AccessoryEffects.herdDisguise(parasaur, player), "No frontlet, no disguise");
        parasaur.discard();
        h.succeed();
    }

    /** Player kills roll the trophy tables; each listed species can drop its part. */
    static void trophies(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkTrophyHunter"));
        for (var entry : TrophyDrops.TABLE.entrySet()) {
            CreatureEntity body = ModContent.CREATURES.get(entry.getKey()).get().create(level, EntitySpawnReason.COMMAND);
            body.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2))));
            var table = level.getServer().reloadableRegistries().getLootTable(TrophyDrops.table(entry.getKey()));
            var params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, body)
                    .withParameter(LootContextParams.ORIGIN, body.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);
            Item expected = AccessoryContent.trophy(entry.getValue().getFirst().trophy());
            boolean dropped = false;
            for (int roll = 0; roll < 40 && !dropped; roll++) {
                for (ItemStack stack : table.getRandomItems(params)) dropped |= stack.is(expected);
            }
            h.assertTrue(dropped, entry.getKey().id + " never dropped " + entry.getValue().getFirst().trophy());
            body.discard();
        }
        h.succeed();
    }

    /** Kept apart so Curios classes load only when Curios is installed. */
    private static final class CuriosProbe {
        static void check(GameTestHelper h) {
            var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArkAccessoryProbe"));
            var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
            handler.reset();
            ItemStack frame = new ItemStack(AccessoryContent.ITEMS.get(Accessory.PACK_FRAME).get());
            handler.setEquippedCurio("back", 0, frame);
            Worn.invalidate(player);
            h.assertTrue(Worn.has(player, Accessory.PACK_FRAME), "An equipped pack frame is worn");
            h.assertFalse(Worn.has(player, Accessory.GHILLIE_WRAP), "Nothing else is worn");
            var curio = top.theillusivec4.curios.api.CuriosApi.getCurio(frame).orElseThrow();
            var context = new top.theillusivec4.curios.api.SlotContext("curio", player, 0, false, true);
            h.assertFalse(curio.canEquip(context), "A second pack frame in the curio slot would stack its bonus");
            var modifiers = top.theillusivec4.curios.api.type.capability.ICurioItem.getAttributeModifiers(frame);
            h.assertFalse(modifiers.modifiers().isEmpty(), "The pack frame carries its capacity bonus");
            for (Accessory accessory : Accessory.values()) {
                h.assertTrue(top.theillusivec4.curios.api.CuriosApi.getCurio(accessory.item().getDefaultInstance()).isPresent(),
                        accessory.id + " must be a curio");
            }
            h.succeed();
        }
    }

    private AccessoryGameTests() {}
}
