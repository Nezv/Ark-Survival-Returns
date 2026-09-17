package dev.nez.arksurvivalreturns.feature.theme;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single source for the vanilla content the dinosaur theme removes.
 *
 * <p>The identifiers also drive data generation, so the runtime guards and the generated
 * data pack cannot drift apart. Vanilla variants present in this game version are named
 * individually, including the ones the directive lists only by family.
 */
public final class ThemePolicy {
    /** Every removed creature family, with the targeted version's variants spelled out. */
    public static final List<String> REMOVED_ENTITIES = List.of(
            // Zombies, including the horse, camel, aquatic and Nether variants.
            "zombie", "husk", "drowned", "zombie_villager", "zombie_horse", "camel_husk", "zombie_nautilus", "zombified_piglin", "giant",
            // Skeletons, including the swamp, desert, frozen and horse variants.
            "skeleton", "stray", "bogged", "parched", "wither_skeleton", "skeleton_horse",
            "creeper",
            // The End, including its boss, crystals, shells and the buried insect.
            "enderman", "endermite", "ender_dragon", "end_crystal", "shulker",
            "witch",
            // Illagers and their beasts.
            "pillager", "vindicator", "evoker", "illusioner", "vex", "ravager",
            "phantom",
            // Cube monsters: slimes, magma cubes and the sulfur variant.
            "slime", "magma_cube", "sulfur_cube",
            "guardian", "elder_guardian",
            "blaze", "breeze", "ghast", "happy_ghast",
            "piglin", "piglin_brute", "hoglin", "zoglin",
            "warden",
            // The pale garden's living heartwood.
            "creaking",
            // Silverfish only exist to guard infested blocks and monster rooms.
            "silverfish",
            // Cave spiders are the separate dungeon variant; ordinary spiders stay.
            "cave_spider",
            // Constructed living servants.
            "iron_golem", "snow_golem", "copper_golem",
            "wither");

    /**
     * Items whose acquisition routes are removed. Used to strip recipes, loot, trades and
     * item use. Ordinary materials such as bone, string, gunpowder and slime balls are
     * deliberately absent: they keep a grounded source.
     */
    public static final List<String> REMOVED_ITEMS = List.of(
            // Enchanting and magical consumables.
            "enchanting_table", "enchanted_book", "enchanted_golden_apple", "golden_apple",
            // Brewing, potions and supernatural effects.
            "brewing_stand", "potion", "splash_potion", "lingering_potion", "tipped_arrow",
            "nether_wart", "blaze_rod", "blaze_powder", "fermented_spider_eye", "glistering_melon_slice",
            "dragon_breath", "ghast_tear", "magma_cream",
            // Teleportation.
            "ender_pearl", "ender_eye", "ender_chest", "chorus_fruit", "popped_chorus_fruit",
            // Resurrection, souls and the Wither build.
            "totem_of_undying", "soul_torch", "soul_lantern", "soul_campfire", "soul_sand", "soul_soil",
            "wither_skeleton_skull",
            // Magical infrastructure.
            "beacon", "conduit", "respawn_anchor", "lodestone",
            // Fantasy flight.
            "elytra", "firework_rocket",
            // Nether and End progression tiers.
            "nether_star", "netherite_ingot", "netherite_scrap", "netherite_upgrade_smithing_template",
            "ancient_debris", "dragon_egg", "echo_shard", "shulker_shell",
            // Trial chamber rewards.
            "mace", "heavy_core", "breeze_rod", "wind_charge", "ominous_bottle",
            "trial_key", "ominous_trial_key",
            // Drops of removed creatures with no grounded role left.
            "rotten_flesh", "phantom_membrane");

    /**
     * Blocks whose interaction and placement are refused: the mechanic they exist for is
     * removed even in worlds that already contain them.
     */
    public static final List<String> DISABLED_BLOCKS = List.of(
            "enchanting_table", "brewing_stand", "beacon", "conduit", "ender_chest", "respawn_anchor",
            "end_portal_frame", "trial_spawner", "vault",
            "sculk_catalyst", "sculk_shrieker", "sculk_sensor", "calibrated_sculk_sensor",
            "creaking_heart");

    /** Structure sets emptied in newly generated terrain. */
    public static final List<String> DISABLED_STRUCTURE_SETS = List.of(
            // Dimension access.
            "strongholds", "ruined_portals", "end_cities", "nether_complexes", "nether_fossils",
            // Underground challenges and fantasy structures.
            "ancient_cities", "trial_chambers", "woodland_mansions", "pillager_outposts",
            "swamp_huts", "ocean_monuments");

    /** Structures referenced by the disabled sets, kept for map and loot filtering. */
    public static final Set<String> DISABLED_STRUCTURES = Set.of(
            "minecraft:stronghold", "minecraft:monument", "minecraft:mansion", "minecraft:ancient_city",
            "minecraft:trial_chambers", "minecraft:pillager_outpost", "minecraft:swamp_hut",
            "minecraft:end_city", "minecraft:fortress", "minecraft:bastion_remnant", "minecraft:nether_fossil");

    /** Placed features removed from every biome: monster rooms, infested ore and sculk. */
    public static final List<String> REMOVED_FEATURES = List.of(
            "minecraft:monster_room", "minecraft:monster_room_deep", "minecraft:ore_infested",
            "minecraft:sculk_patch_deep_dark", "minecraft:sculk_patch_ancient_city", "minecraft:sculk_vein");

    /**
     * Advancements replaced by an unobtainable placeholder. Covers every dimension gate, the
     * Wither and the Ender Dragon, and the objectives of the removed mechanics that can no
     * longer be completed.
     */
    public static final List<String> DISABLED_ADVANCEMENTS = List.of(
            "nether/root", "nether/all_effects", "nether/all_potions", "nether/brew_potion",
            "nether/charge_respawn_anchor", "nether/create_beacon", "nether/create_full_beacon",
            "nether/distract_piglin", "nether/explore_nether", "nether/fast_travel", "nether/find_bastion",
            "nether/find_fortress", "nether/get_wither_skull", "nether/loot_bastion", "nether/netherite_armor",
            "nether/obtain_ancient_debris", "nether/obtain_blaze_rod", "nether/obtain_crying_obsidian",
            "nether/return_to_sender", "nether/ride_strider", "nether/ride_strider_in_overworld_lava",
            "nether/summon_wither", "nether/uneasy_alliance",
            "end/root", "end/dragon_breath", "end/dragon_egg", "end/elytra", "end/enter_end_gateway",
            "end/find_end_city", "end/kill_dragon", "end/levitate", "end/respawn_dragon",
            "story/enter_the_nether", "story/enter_the_end", "story/follow_ender_eye", "story/form_obsidian",
            "story/enchant_item", "story/cure_zombie_villager",
            "adventure/avoid_vibration", "adventure/blowback", "adventure/heart_transplanter",
            "adventure/hero_of_the_village", "adventure/kill_all_mobs", "adventure/kill_mob_near_sculk_catalyst",
            "adventure/minecraft_trials_edition", "adventure/overoverkill", "adventure/revaulting",
            "adventure/sniper_duel", "adventure/spyglass_at_dragon", "adventure/spyglass_at_ghast",
            "adventure/summon_iron_golem", "adventure/totem_of_undying",
            "adventure/trim_with_all_exclusive_armor_patterns", "adventure/two_birds_one_arrow",
            "adventure/under_lock_and_key", "adventure/use_lodestone", "adventure/very_very_frightening",
            "adventure/voluntary_exile", "adventure/who_needs_rockets", "adventure/whos_the_pillager_now",
            "husbandry/balanced_diet", "husbandry/bred_all_animals", "husbandry/froglights",
            "husbandry/obtain_netherite_hoe", "husbandry/place_dried_ghast_in_water", "husbandry/silk_touch_nest");

    /**
     * Trade tags that keep only their surviving entries. Enchantment offers, Nether and End
     * offers, removed-mob purchases and maps to disabled structures are dropped.
     */
    public static final Map<String, List<String>> TRADE_TAGS = Map.ofEntries(
            Map.entry("armorer/level_4", List.of("#minecraft:common_smith/level_4")),
            Map.entry("armorer/level_5", List.of("#minecraft:common_smith/level_5")),
            Map.entry("cartographer/level_2", List.of(
                    "minecraft:cartographer/2/glass_pane_emerald",
                    "minecraft:cartographer/2/emerald_and_compass_village_taiga_map",
                    "minecraft:cartographer/2/emerald_and_compass_village_snowy_map",
                    "minecraft:cartographer/2/emerald_and_compass_village_savanna_map",
                    "minecraft:cartographer/2/emerald_and_compass_village_plains_map",
                    "minecraft:cartographer/2/emerald_and_compass_explorer_jungle_map",
                    "minecraft:cartographer/2/emerald_and_compass_village_desert_map")),
            Map.entry("cartographer/level_3", List.of("minecraft:cartographer/3/compass_emerald")),
            Map.entry("cartographer/level_5", List.of("minecraft:cartographer/5/emerald_globe_banner_pattern")),
            Map.entry("cleric/level_1", List.of("minecraft:cleric/1/emerald_redstone")),
            Map.entry("cleric/level_4", List.of(
                    "minecraft:cleric/4/turtle_scute_emerald", "minecraft:cleric/4/glass_bottle_emerald")),
            Map.entry("cleric/level_5", List.of("minecraft:cleric/5/emerald_experience_bottle")),
            Map.entry("farmer/level_5", List.of("minecraft:farmer/5/emerald_golden_carrot")),
            Map.entry("fisherman/level_3", List.of("minecraft:fisherman/3/salmon_emerald")),
            Map.entry("fletcher/level_4", List.of("minecraft:fletcher/4/feather_emerald")),
            Map.entry("fletcher/level_5", List.of("minecraft:fletcher/5/tripwire_hook_emerald")),
            Map.entry("librarian/level_1", List.of(
                    "minecraft:librarian/1/paper_emerald", "minecraft:librarian/1/emerald_bookshelf")),
            Map.entry("librarian/level_2", List.of(
                    "minecraft:librarian/2/book_emerald", "minecraft:librarian/2/emerald_lantern")),
            Map.entry("librarian/level_3", List.of(
                    "minecraft:librarian/3/ink_sac_emerald", "minecraft:librarian/3/emerald_glass")),
            Map.entry("librarian/level_4", List.of(
                    "minecraft:librarian/4/writable_book_emerald", "minecraft:librarian/4/emerald_clock",
                    "minecraft:librarian/4/emerald_compass")),
            Map.entry("toolsmith/level_3", List.of(
                    "#minecraft:common_smith/level_3", "minecraft:toolsmith/3/flint_emerald",
                    "minecraft:toolsmith/3/emerald_diamond_hoe")),
            Map.entry("toolsmith/level_4", List.of(
                    "#minecraft:common_smith/level_4", "minecraft:toolsmith/4/diamond_emerald")),
            Map.entry("toolsmith/level_5", List.of("#minecraft:common_smith/level_5")),
            Map.entry("wandering_trader/uncommon", List.of(
                    "minecraft:wandering_trader/emerald_packed_ice", "minecraft:wandering_trader/emerald_blue_ice",
                    "minecraft:wandering_trader/emerald_gunpowder", "minecraft:wandering_trader/emerald_podzol",
                    "minecraft:wandering_trader/emerald_acacia_log", "minecraft:wandering_trader/emerald_birch_log",
                    "minecraft:wandering_trader/emerald_dark_oak_log", "minecraft:wandering_trader/emerald_jungle_log",
                    "minecraft:wandering_trader/emerald_oak_log", "minecraft:wandering_trader/emerald_spruce_log",
                    "minecraft:wandering_trader/emerald_cherry_log", "minecraft:wandering_trader/emerald_mangrove_log",
                    "minecraft:wandering_trader/emerald_pale_oak_log")),
            Map.entry("weaponsmith/level_1", List.of(
                    "#minecraft:common_smith/level_1", "minecraft:weaponsmith/1/emerald_iron_axe")),
            Map.entry("weaponsmith/level_4", List.of(
                    "#minecraft:common_smith/level_4", "minecraft:weaponsmith/4/diamond_emerald")),
            Map.entry("weaponsmith/level_5", List.of("#minecraft:common_smith/level_5")));

    /** Loot table ids whose enchantment-producing functions are stripped. */
    public static final Set<String> ENCHANT_LOOT_FUNCTIONS = Set.of(
            "minecraft:enchant_randomly", "minecraft:enchant_with_levels", "minecraft:set_enchantments");

    /**
     * Vanilla animals whose carcass supplies bones now that skeletons are gone. Bones keep
     * their mundane role: bone meal, wolf taming and bone blocks.
     */
    public static final List<String> BONE_ANIMALS = List.of(
            "cow", "mooshroom", "pig", "sheep", "chicken", "rabbit", "goat", "horse", "donkey", "mule",
            "llama", "trader_llama", "camel", "wolf", "fox", "ocelot", "cat", "polar_bear", "panda",
            "armadillo", "sniffer", "turtle", "dolphin");

    private static Set<EntityType<?>> removedTypes;
    private static Set<Item> removedItemSet;
    private static Set<Block> disabledBlocks;

    /** Removed item ids in the full {@code namespace:path} form used by data and signatures. */
    public static final Set<String> REMOVED_ITEM_IDS = qualified(REMOVED_ITEMS);

    /** Removed entity ids in the full {@code namespace:path} form used by data and signatures. */
    public static final Set<String> REMOVED_ENTITY_IDS = qualified(REMOVED_ENTITIES);

    private ThemePolicy() {}

    /** True when the creature family is removed from survival gameplay. */
    public static boolean removed(EntityType<?> type) {
        if (removedTypes == null) {
            var found = new LinkedHashSet<EntityType<?>>();
            for (String id : REMOVED_ENTITIES) {
                var type2 = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
                if (type2 != null) found.add(type2);
            }
            removedTypes = Set.copyOf(found);
        }
        return removedTypes.contains(type);
    }

    /** True when the stack is one of the items whose acquisition routes are removed. */
    public static boolean removed(ItemStack stack) {
        return !stack.isEmpty() && removed(stack.getItem());
    }

    public static boolean removed(Item item) {
        if (removedItemSet == null) removedItemSet = resolve(BuiltInRegistries.ITEM, REMOVED_ITEMS);
        return removedItemSet.contains(item);
    }

    /** True when the block's mechanic is disabled, in existing worlds as well as new ones. */
    public static boolean disabled(BlockState state) {
        if (disabledBlocks == null) disabledBlocks = resolve(BuiltInRegistries.BLOCK, DISABLED_BLOCKS);
        return disabledBlocks.contains(state.getBlock());
    }

    public static boolean disabled(Block block) {
        if (disabledBlocks == null) disabledBlocks = resolve(BuiltInRegistries.BLOCK, DISABLED_BLOCKS);
        return disabledBlocks.contains(block);
    }

    /** Both removed dimensions keep their saved data and ids; only access is refused. */
    public static boolean removedDimension(net.minecraft.resources.ResourceKey<Level> dimension) {
        return dimension == Level.NETHER || dimension == Level.END;
    }

    /** True when a placed pumpkin on this base would build an iron, snow or copper golem. */
    public static boolean golemBase(BlockState state) {
        return state.is(Blocks.IRON_BLOCK) || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.COPPER);
    }

    /** True when placing this block would build the Wither. */
    public static boolean witherBase(BlockState state) {
        return state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    public static boolean isGolemHead(BlockState state) {
        return state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN);
    }

    /** Expands bare ids to the {@code minecraft} namespace. */
    public static Set<String> qualified(List<String> ids) {
        var qualified = new LinkedHashSet<String>();
        for (String id : ids) qualified.add(id.contains(":") ? id : "minecraft:" + id);
        return Set.copyOf(qualified);
    }

    private static <T> Set<T> resolve(net.minecraft.core.Registry<T> registry, List<String> ids) {
        var found = new LinkedHashSet<T>();
        for (String id : ids) {
            var value = registry.getValue(Identifier.parse(id));
            if (value != null) found.add(value);
        }
        return Set.copyOf(found);
    }
}
