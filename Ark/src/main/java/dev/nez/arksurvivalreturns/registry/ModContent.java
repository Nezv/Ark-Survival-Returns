package dev.nez.arksurvivalreturns.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;

public final class ModContent {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArkSurvivalReturns.MOD_ID);
    public static final EnumMap<Species, DeferredBlock<dev.nez.arksurvivalreturns.feature.flying.NestBlock>> NESTS = new EnumMap<>(Species.class);
    public static final EnumMap<Species, DeferredItem<Item>> NEST_EGGS = new EnumMap<>(Species.class);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArkSurvivalReturns.MOD_ID);
    public static final EnumMap<Species, DeferredHolder<EntityType<?>, EntityType<CreatureEntity>>> CREATURES = new EnumMap<>(Species.class);
    public static final Map<String, DeferredItem<Item>> BERRIES = new LinkedHashMap<>();
    public static final EnumMap<Species, DeferredItem<SpawnEggItem>> EGGS = new EnumMap<>(Species.class);
    static {
        for (Species s : Species.values()) {
            var type = ENTITIES.register(s.id, () -> EntityType.Builder
                    .<CreatureEntity>of((t, l) -> switch (s.realm()) {
                        case AIR -> new dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity(t, l, s);
                        case WATER -> new dev.nez.arksurvivalreturns.feature.aquatic.AquaticCreatureEntity(t, l, s);
                        case AMPHIBIOUS -> new dev.nez.arksurvivalreturns.feature.creature.AmphibiousCreatureEntity(t, l, s);
                        case LAND -> new CreatureEntity(t, l, s);
                    }, MobCategory.CREATURE)
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
        for (String id : new String[]{"tintoberry", "amarberry", "azulberry", "narcoberry"})
            BERRIES.put(id, ITEMS.registerSimpleItem(id, p -> p.stacksTo(64)));
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.arksurvivalreturns"))
                .icon(() -> BERRIES.get("narcoberry").get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    BERRIES.values().forEach(i -> output.accept(i.get()));
                    EGGS.values().forEach(i -> output.accept(i.get()));
                    NEST_EGGS.values().forEach(i -> output.accept(i.get()));
                }).build());
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
