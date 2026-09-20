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
    public static final DeferredItem<SedativeArrowItem> TRANQUILIZER_ARROW_ITEM = ITEMS.registerItem(
            "tranquilizer_arrow", SedativeArrowItem::new, p -> p.stacksTo(64));
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
    /** Spear: extra reach at the cost of swing speed. */
    public static final DeferredItem<Item> SPEAR = ITEMS.registerItem("spear", Item::new,
            dev.nez.arksurvivalreturns.feature.camp.CampMaterials::spear);
    /** Bedroll: sets the personal respawn point; the saved point survives the block. */
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.camp.BedrollBlock> BEDROLL =
            BLOCKS.registerBlock("bedroll", dev.nez.arksurvivalreturns.feature.camp.BedrollBlock::new,
                    p -> p.strength(0.4f).noOcclusion().noCollision()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                            .sound(net.minecraft.world.level.block.SoundType.WOOL));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BEDROLL_ITEM =
            ITEMS.registerSimpleBlockItem(BEDROLL, p -> p.stacksTo(1));
    /** Recovery cache marker: the death haul lives in world SavedData, not in this block. */
    public static final DeferredBlock<dev.nez.arksurvivalreturns.feature.recovery.RecoveryCacheBlock> RECOVERY_CACHE =
            BLOCKS.registerBlock("recovery_cache", dev.nez.arksurvivalreturns.feature.recovery.RecoveryCacheBlock::new,
                    p -> p.strength(1.5f).explosionResistance(1200f)
                            .sound(net.minecraft.world.level.block.SoundType.WOOD));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RECOVERY_CACHE_ITEM =
            ITEMS.registerSimpleBlockItem(RECOVERY_CACHE, p -> p.stacksTo(1));

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
                    output.accept(COMPANION_WHISTLE.get());
                    output.accept(FIELD_JOURNAL.get());
                    output.accept(PLANT_FIBER.get());
                    output.accept(PACK_HARNESS.get());
                    output.accept(REINFORCED_HARNESS.get());
                    output.accept(FIBER_BANDAGE.get());
                    output.accept(FLINT_KNIFE.get());
                    output.accept(SPEAR.get());
                    output.accept(BEDROLL_ITEM.get());
                    output.accept(RECOVERY_CACHE_ITEM.get());
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
    }
    private ModContent() {}
}
