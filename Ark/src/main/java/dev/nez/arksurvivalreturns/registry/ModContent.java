package dev.nez.arksurvivalreturns.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.CreatureMountMenu;
import dev.nez.arksurvivalreturns.feature.taming.SedativeArrow;
import dev.nez.arksurvivalreturns.feature.taming.SedativeArrowItem;
import dev.nez.arksurvivalreturns.feature.taming.SedativeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class ModContent {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArkSurvivalReturns.MOD_ID);
    public static final EnumMap<Species, DeferredBlock<dev.nez.arksurvivalreturns.feature.flying.NestBlock>> NESTS = new EnumMap<>(Species.class);
    public static final EnumMap<Species, DeferredItem<Item>> NEST_EGGS = new EnumMap<>(Species.class);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ArkSurvivalReturns.MOD_ID);
    public static final EnumMap<Species, DeferredHolder<EntityType<?>, EntityType<CreatureEntity>>> CREATURES = new EnumMap<>(Species.class);
    public static final Map<String, DeferredItem<Item>> BERRIES = new LinkedHashMap<>();
    public static final EnumMap<Species, DeferredItem<SpawnEggItem>> EGGS = new EnumMap<>(Species.class);
    /** Horse-style creature inventory, opened through the vanilla open-screen path. */
    public static final DeferredHolder<MenuType<?>, MenuType<CreatureMountMenu>> CREATURE_MOUNT_MENU = MENUS.register(
            "creature_mount", () -> IMenuTypeExtension.create((containerId, inventory, data) ->
                    new CreatureMountMenu(containerId, inventory, resolve(inventory, data))));
    public static final DeferredHolder<EntityType<?>, EntityType<SedativeArrow>> TRANQUILIZER_ARROW = ENTITIES.register(
            "tranquilizer_arrow", () -> EntityType.Builder.<SedativeArrow>of(SedativeArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.id("tranquilizer_arrow"))));
    /** The encounter boss: an ordinary Giga body with the guardian contract, outside every spawn list. */
    public static final DeferredHolder<EntityType<?>, EntityType<dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity>>
            GUARDIAN_GIGANOTOSAURUS = ENTITIES.register("guardian_giganotosaurus", () -> EntityType.Builder
                    .<dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity>of(
                            dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity::new,
                            MobCategory.MONSTER)
                    .sized(10.5f, 15.0f).eyeHeight(12.75f).clientTrackingRange(12)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.id("guardian_giganotosaurus"))));
    /** Allosaur Heart: the ritual key, one guaranteed drop from a player kill on a wild Allosaurus. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem> ALLOSAUR_HEART =
            ITEMS.registerItem("allosaur_heart",
                    p -> new dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem(
                            p, "tooltip.arksurvivalreturns.allosaur_heart"),
                    p -> p.stacksTo(16));
    /** Shared workshop progression: the tribe flag is authoritative, the item is the visible memento. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem> WORKSHOP_SCHEMATIC =
            ITEMS.registerItem("workshop_schematic",
                    p -> new dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem(
                            p, "tooltip.arksurvivalreturns.workshop_schematic"),
                    p -> p.stacksTo(1));
    /** Victory trophy dropped by the Guardian. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem> GUARDIAN_TROPHY =
            ITEMS.registerItem("guardian_trophy",
                    p -> new dev.nez.arksurvivalreturns.feature.guardian.GuardianLoreItem(
                            p, "tooltip.arksurvivalreturns.guardian_trophy"),
                    p -> p.stacksTo(16));
    public static final DeferredItem<SedativeArrowItem> TRANQUILIZER_ARROW_ITEM = ITEMS.registerItem(
            "tranquilizer_arrow", SedativeArrowItem::new, p -> p.stacksTo(64));
    /** Concentrated sedative: crafted from narcoberries and fiber, a stronger dose than the berry itself. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.taming.ConcentratedSedativeItem> CONCENTRATED_SEDATIVE =
            ITEMS.registerItem("concentrated_sedative",
                    dev.nez.arksurvivalreturns.feature.taming.ConcentratedSedativeItem::new, p -> p.stacksTo(16));
    /** Improved tranquilizer arrow: the concentrated dose in arrow form. */
    public static final DeferredItem<SedativeArrowItem> IMPROVED_TRANQUILIZER_ARROW_ITEM = ITEMS.registerItem(
            "improved_tranquilizer_arrow",
            p -> new SedativeArrowItem(p, dev.nez.arksurvivalreturns.Config.CONCENTRATED_SEDATIVE_POTENCY::get),
            p -> p.stacksTo(64));
    /** Companion order whistle: cycles FOLLOW/STAY/WANDER on a tamed creature, sneak-use pets it. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.companion.CompanionWhistleItem> COMPANION_WHISTLE =
            ITEMS.registerItem("companion_whistle",
                    dev.nez.arksurvivalreturns.feature.companion.CompanionWhistleItem::new, p -> p.stacksTo(1));
    /** Field Journal: opens the tribe's survival journal (the FTB Quests book). */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.journal.FieldJournalItem> FIELD_JOURNAL =
            ITEMS.registerItem("field_journal",
                    dev.nez.arksurvivalreturns.feature.journal.FieldJournalItem::new, p -> p.stacksTo(1));
    /** Plant fiber: the primitive binding material, harvested from grass. */
    public static final DeferredItem<Item> PLANT_FIBER = ITEMS.registerSimpleItem("plant_fiber", p -> p.stacksTo(64));
    /** Pack harness: unlocks the species cargo capacity for ordinary haulers. */
    public static final DeferredItem<Item> PACK_HARNESS = ITEMS.registerSimpleItem("pack_harness", p -> p.stacksTo(1));
    /** Reinforced harness: required by the heavy haulers and giants. */
    public static final DeferredItem<Item> REINFORCED_HARNESS =
            ITEMS.registerSimpleItem("reinforced_harness", p -> p.stacksTo(1));
    /** Fiber bandage: field medicine; the downed revive is wired with the downed state. */
    public static final DeferredItem<dev.nez.arksurvivalreturns.feature.camp.FiberBandageItem> FIBER_BANDAGE =
            ITEMS.registerItem("fiber_bandage",
                    dev.nez.arksurvivalreturns.feature.camp.FiberBandageItem::new, p -> p.stacksTo(16));
    /** Flint knife: fast, fragile and repairable with flint. */
    public static final DeferredItem<Item> FLINT_KNIFE = ITEMS.registerItem("flint_knife", Item::new,
            p -> p.sword(dev.nez.arksurvivalreturns.feature.camp.CampMaterials.FLINT, 1.0f, -1.6f));
    /** Bedroll: sets the personal respawn point; the saved point survives the block. */
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.camp.BedrollBlock> BEDROLL =
            BLOCKS.registerBlock("bedroll", dev.nez.arksurvivalreturns.feature.camp.BedrollBlock::new,
                    p -> p.strength(0.4f).noOcclusion().noCollision()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                            .sound(net.minecraft.world.level.block.SoundType.WOOL));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BEDROLL_ITEM =
            ITEMS.registerSimpleBlockItem(BEDROLL, p -> p.stacksTo(1));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.nez.arksurvivalreturns.feature.kitchen.CookingPotMenu>> COOKING_POT_MENU =
            MENUS.register("cooking_pot", () -> IMenuTypeExtension.create((containerId, inventory, data) ->
                    new dev.nez.arksurvivalreturns.feature.kitchen.CookingPotMenu(containerId, inventory, data.readBlockPos())));
    /** Homestead stations are interaction-only (no menus): food in, feeding out; raw in, ration out. */
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArkSurvivalReturns.MOD_ID);
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.TroughBlock> TROUGH =
            BLOCKS.registerBlock("trough", dev.nez.arksurvivalreturns.feature.farm.TroughBlock::new,
                    p -> p.strength(1.0f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.WOOD));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> TROUGH_ITEM = ITEMS.registerSimpleBlockItem(TROUGH);
    /** The existing trough id remains oak, so older worlds and stacks stay valid. */
    public static final Map<String, DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.TroughBlock>> TROUGHS = registerTroughs();
    private static Map<String, DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.TroughBlock>> registerTroughs() {
        var variants = new LinkedHashMap<String, DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.TroughBlock>>();
        variants.put("oak", TROUGH);
        for (String wood : java.util.List.of("spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo")) {
            var block = BLOCKS.registerBlock(wood + "_trough", dev.nez.arksurvivalreturns.feature.farm.TroughBlock::new,
                    p -> p.strength(1.0f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.WOOD));
            ITEMS.registerSimpleBlockItem(block);
            variants.put(wood, block);
        }
        return java.util.Collections.unmodifiableMap(variants);
    }
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.DryingRackBlock> DRYING_RACK =
            BLOCKS.registerBlock("drying_rack", dev.nez.arksurvivalreturns.feature.farm.DryingRackBlock::new,
                    p -> p.strength(0.8f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.WOOD));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRYING_RACK_ITEM = ITEMS.registerSimpleBlockItem(DRYING_RACK);
    public static final DeferredItem<net.minecraft.world.item.Item> DRIED_RATION = ITEMS.registerSimpleItem("dried_ration",
            p -> p.stacksTo(64).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6)
                    .saturationModifier(0.6f).build()));
    // Four mechanically distinct Ark berry bushes over shared vanilla stage art; they drop berries, not items.
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock> TINTOBERRY_BUSH =
            BLOCKS.registerBlock("tintoberry_bush",
                    p -> new dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock(() -> BERRIES.get("tintoberry").get(), p),
                    p -> p.noCollision().instabreak().randomTicks()
                            .sound(net.minecraft.world.level.block.SoundType.SWEET_BERRY_BUSH));
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock> AMARBERRY_BUSH =
            BLOCKS.registerBlock("amarberry_bush",
                    p -> new dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock(() -> BERRIES.get("amarberry").get(), p),
                    p -> p.noCollision().instabreak().randomTicks()
                            .sound(net.minecraft.world.level.block.SoundType.SWEET_BERRY_BUSH));
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock> AZULBERRY_BUSH =
            BLOCKS.registerBlock("azulberry_bush",
                    p -> new dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock(() -> BERRIES.get("azulberry").get(), p),
                    p -> p.noCollision().instabreak().randomTicks()
                            .sound(net.minecraft.world.level.block.SoundType.SWEET_BERRY_BUSH));
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock> NARCOBERRY_BUSH =
            BLOCKS.registerBlock("narcoberry_bush",
                    p -> new dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock(() -> BERRIES.get("narcoberry").get(), p),
                    p -> p.noCollision().instabreak().randomTicks()
                            .sound(net.minecraft.world.level.block.SoundType.SWEET_BERRY_BUSH));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity>>
            TROUGH_BLOCK_ENTITY = BLOCK_ENTITIES.register("trough",
                    () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity::new,
                            TROUGHS.values().stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity>>
            DRYING_RACK_BLOCK_ENTITY = BLOCK_ENTITIES.register("drying_rack",
                    () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity::new, DRYING_RACK.get()));
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock> COOKING_POT =
            BLOCKS.registerBlock("cooking_pot", dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock::new,
                    p -> p.strength(1.5f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> COOKING_POT_ITEM =
            ITEMS.registerSimpleBlockItem(COOKING_POT);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity>>
            COOKING_POT_BLOCK_ENTITY = BLOCK_ENTITIES.register("cooking_pot",
                    () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity::new, COOKING_POT.get()));
    /** Prepared meals: one clear activity benefit each, no nutrient bars. */
    public static final DeferredItem<Item> HEARTY_STEW = ITEMS.registerSimpleItem("hearty_stew", p -> p.stacksTo(16)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.8f).build(),
                    meal(net.minecraft.world.effect.MobEffects.REGENERATION, 200)));
    public static final DeferredItem<Item> TRAIL_MIX = ITEMS.registerSimpleItem("trail_mix", p -> p.stacksTo(64)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).build(),
                    meal(net.minecraft.world.effect.MobEffects.SPEED, 300)));

    private static net.minecraft.world.item.component.Consumable meal(
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {
        return net.minecraft.world.item.component.Consumable.builder()
                .consumeSeconds(1.6f)
                .animation(net.minecraft.world.item.ItemUseAnimation.EAT)
                .onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
                        new net.minecraft.world.effect.MobEffectInstance(effect, ticks, 0), 1.0f))
                .build();
    }

    static {
        for (Species s : Species.values()) {
            var type = ENTITIES.register(s.id, () -> EntityType.Builder
                    .<CreatureEntity>of((t, l) -> switch (s.realm()) {
                        case AIR -> new dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity(t, l, s);
                        case WATER -> new dev.nez.arksurvivalreturns.feature.aquatic.AquaticCreatureEntity(t, l, s);
                        case AMPHIBIOUS -> new dev.nez.arksurvivalreturns.feature.creature.AmphibiousCreatureEntity(t, l, s);
                        case LAND -> new CreatureEntity(t, l, s);
                    }, s.spawnCategory())
                    .sized(s.width, s.height).eyeHeight(s.height * 0.85f).clientTrackingRange(12)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.id(s.id))));
            CREATURES.put(s, type);
            EGGS.put(s, ITEMS.registerItem(s.id + "_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(type.get()))));
        }
        for (Species s : Species.values()) {
            if (!s.flyer()) continue;
            NESTS.put(s, BLOCKS.registerBlock(s.id + "_nest", dev.nez.arksurvivalreturns.feature.flying.NestBlock::new,
                    p -> p.strength(0.4f).noOcclusion().noCollision()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .sound(net.minecraft.world.level.block.SoundType.GRASS)));
            NEST_EGGS.put(s, ITEMS.registerSimpleItem(s.id + "_egg", p -> p.stacksTo(16)));
        }
        for (String id : new String[]{"tintoberry", "amarberry", "azulberry"})
            BERRIES.put(id, ITEMS.registerSimpleItem(id, p -> p.stacksTo(64)));
        // Narcoberry is the baseline sedative: consumed, swung or crafted into a tranquilizer arrow.
        BERRIES.put("narcoberry", ITEMS.registerItem("narcoberry", SedativeItem::new, p -> p.stacksTo(64)));
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.arksurvivalreturns"))
                .icon(() -> BERRIES.get("narcoberry").get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    BERRIES.values().forEach(i -> output.accept(i.get()));
                    output.accept(TRANQUILIZER_ARROW_ITEM.get());
                    output.accept(CONCENTRATED_SEDATIVE.get());
                    output.accept(IMPROVED_TRANQUILIZER_ARROW_ITEM.get());
                    output.accept(COMPANION_WHISTLE.get());
                    output.accept(FIELD_JOURNAL.get());
                    output.accept(PLANT_FIBER.get());
                    dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.displayItems(output);
                    output.accept(PACK_HARNESS.get());
                    output.accept(REINFORCED_HARNESS.get());
                    output.accept(FIBER_BANDAGE.get());
                    output.accept(FLINT_KNIFE.get());
                    output.accept(BEDROLL_ITEM.get());
                    TROUGHS.values().forEach(block -> output.accept(block.get()));
                    output.accept(DRYING_RACK_ITEM.get());
                    output.accept(DRIED_RATION.get());
                    output.accept(COOKING_POT_ITEM.get());
                    output.accept(HEARTY_STEW.get());
                    output.accept(TRAIL_MIX.get());
                    output.accept(ALLOSAUR_HEART.get());
                    output.accept(WORKSHOP_SCHEMATIC.get());
                    output.accept(GUARDIAN_TROPHY.get());
                    EGGS.values().forEach(i -> output.accept(i.get()));
                    NEST_EGGS.values().forEach(i -> output.accept(i.get()));
                }).build());
    }

    /** Client-side menu factory: the entity is resolved from the id the server wrote, never from a load. */
    private static CreatureEntity resolve(net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.network.RegistryFriendlyByteBuf data) {
        int entityId = data.readVarInt();
        return inventory.player.level().getEntity(entityId) instanceof CreatureEntity creature ? creature : null;
    }

    public static Species species(EntityType<?> type) {
        for (var e : CREATURES.entrySet()) if (e.getValue().get() == type) return e.getKey();
        throw new IllegalArgumentException("Unknown creature type: " + type);
    }
    public static void attributes(EntityAttributeCreationEvent event) {
        CREATURES.forEach((species, type) -> event.put(type.get(), CreatureEntity.attributes(species).build()));
        event.put(GUARDIAN_GIGANOTOSAURUS.get(),
                dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity.attributes().build());
    }
    private ModContent() {}
}
