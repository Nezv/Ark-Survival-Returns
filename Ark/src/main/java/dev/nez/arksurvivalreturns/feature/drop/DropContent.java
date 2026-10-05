package dev.nez.arksurvivalreturns.feature.drop;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Supply drops: a {@link SupplyDropEntity} comes down under a parachute and leaves a {@link LootCrateBlock}, which
 * has no item form and is never crafted. {@link SupplyDrops} sends them; the models are tools/build_drop_assets.py's.
 */
public final class DropContent {
    public static final DeferredBlock<LootCrateBlock> LOOT_CRATE = ModContent.BLOCKS.registerBlock("loot_crate",
            LootCrateBlock::new, p -> p.strength(2.5f).sound(SoundType.WOOD).noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 7));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootCrateBlockEntity>> LOOT_CRATE_BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("loot_crate",
                    () -> new BlockEntityType<>(LootCrateBlockEntity::new, LOOT_CRATE.get()));
    /** The server writes the crate's slot count; the client lays out that many. */
    public static final DeferredHolder<MenuType<?>, MenuType<LootCrateMenu>> LOOT_CRATE_MENU = ModContent.MENUS.register(
            "loot_crate", () -> IMenuTypeExtension.create((id, inventory, data) -> new LootCrateMenu(id, inventory, data.readVarInt())));
    /** Seen from far off and from below: the tracking range is in chunks. */
    public static final DeferredHolder<EntityType<?>, EntityType<SupplyDropEntity>> SUPPLY_DROP = ModContent.ENTITIES.register(
            "supply_drop", () -> EntityType.Builder.<SupplyDropEntity>of(SupplyDropEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(10).fireImmune().noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ArkSurvivalReturns.id("supply_drop"))));

    public static void register(IEventBus bus) {
        // Everything registers through the shared ModContent registers; calling this loads the fields above in time.
    }

    private DropContent() {}
}
