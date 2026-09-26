package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Creature parts: a player kill rolls loot_table/trophies/&lt;species&gt; on top of the carcass table
 * (meat, hide, bone). Amber turns up now and then in dug gravel and clay.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TrophyDrops {
    public record Drop(String trophy, double chance, int min, int max) {}

    public static final Map<Species, List<Drop>> TABLE = new EnumMap<>(Species.class);

    static {
        put(Species.VELOCIRAPTOR, new Drop("sickle_claw", 0.5, 1, 1));
        put(Species.THERIZINOSAURUS, new Drop("scythe_claw", 0.8, 1, 2));
        put(Species.SABERTOOTH, new Drop("fang", 1.0, 1, 2), new Drop("thick_pelt", 0.6, 1, 1));
        put(Species.DIREWOLF, new Drop("fang", 0.5, 1, 1), new Drop("thick_pelt", 0.8, 1, 2));
        put(Species.RAVAGER, new Drop("fang", 0.5, 1, 1), new Drop("thick_pelt", 0.6, 1, 1));
        put(Species.MAMMOTH, new Drop("thick_pelt", 1.0, 2, 3));
        put(Species.MEGALOCERUS, new Drop("giant_antler", 0.7, 1, 2), new Drop("thick_pelt", 0.5, 1, 1));
        put(Species.MEGAPITHECUS, new Drop("thick_pelt", 1.0, 2, 3));
        put(Species.PTERANODON, new Drop("wing_membrane", 0.8, 1, 2));
        put(Species.QUETZAL, new Drop("wing_membrane", 1.0, 2, 3), new Drop("quetzal_feather", 1.0, 2, 4));
        put(Species.SARCO, new Drop("croc_scute", 0.8, 1, 3));
        put(Species.DEINOSUCHUS, new Drop("croc_scute", 0.9, 2, 3));
        put(Species.KAPROSUCHUS, new Drop("croc_scute", 0.7, 1, 2));
        put(Species.PLESIOSAUR, new Drop("marine_flipper", 0.7, 1, 2));
        put(Species.LIOPLEURODON, new Drop("marine_flipper", 0.7, 1, 2));
        put(Species.MOSASAURUS, new Drop("marine_flipper", 0.9, 2, 2));
        put(Species.MEGALODON, new Drop("megalodon_tooth", 1.0, 1, 2));
        put(Species.UNICORN, new Drop("unicorn_horn", 1.0, 1, 1));
        put(Species.TITANOBOA, new Drop("venom_fang", 0.8, 1, 2));
        put(Species.TYRANNOSAURUS, new Drop("tyrant_tooth", 1.0, 1, 3));
        put(Species.GIGANOTOSAURUS, new Drop("tyrant_tooth", 1.0, 2, 3));
        put(Species.ARGENTAVIS, new Drop("argentavis_talon", 0.8, 1, 2));
    }

    private static void put(Species species, Drop... drops) {
        TABLE.put(species, List.of(drops));
    }

    public static ResourceKey<LootTable> table(Species species) {
        return ResourceKey.create(Registries.LOOT_TABLE, ArkSurvivalReturns.id("trophies/" + species.id));
    }

    @SubscribeEvent static void creatureDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof CreatureEntity creature) || !(creature.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof Player killer) || !TABLE.containsKey(creature.species())) return;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, creature)
                .withParameter(LootContextParams.ORIGIN, creature.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        Vec3 at = creature.position();
        level.getServer().reloadableRegistries().getLootTable(table(creature.species())).getRandomItems(params, stack ->
                event.getDrops().add(new ItemEntity(level, at.x, at.y + 0.5, at.z, stack)));
    }

    /** Amber: fossil resin, found in about one of every seventy blocks of dug gravel or clay. */
    @SubscribeEvent static void dig(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player) || event.getState().is(Blocks.SAND)) return;
        if (!event.getState().is(Blocks.GRAVEL) && !event.getState().is(Blocks.CLAY)) return;
        var level = event.getLevel();
        if (EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), event.getTool()) > 0) return;
        if (level.getRandom().nextFloat() >= 0.015f) return;
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                AccessoryContent.trophy("amber").getDefaultInstance()));
    }

    private TrophyDrops() {}
}
