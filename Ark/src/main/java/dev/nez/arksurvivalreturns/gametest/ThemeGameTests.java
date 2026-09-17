package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.feature.theme.DimensionGuard;
import dev.nez.arksurvivalreturns.feature.theme.ThemePolicy;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;

/**
 * Headless checks for the theme alignment: registered data, loaded registries and the runtime
 * guards, exercised through real registry lookups and real entities.
 */
final class ThemeGameTests {
    /** Trade tags that must lose their removed offers while keeping the surviving ones. */
    private static final Map<String, String> TRADE_ABSENT = Map.of(
            "minecraft:librarian/level_1", "minecraft:librarian/1/emerald_and_book_enchanted_book",
            "minecraft:cartographer/level_3", "minecraft:cartographer/3/emerald_and_compass_ocean_explorer_map",
            "minecraft:cartographer/level_5", "minecraft:cartographer/5/emerald_and_compass_woodland_mansion_map",
            "minecraft:cleric/level_4", "minecraft:cleric/4/emerald_ender_pearl",
            "minecraft:cleric/level_5", "minecraft:cleric/5/nether_wart_emerald",
            "minecraft:fletcher/level_5", "minecraft:fletcher/5/arrow_and_emerald_tipped_arrow",
            "minecraft:wandering_trader/uncommon", "minecraft:wandering_trader/emerald_long_invisibility_potion");

    private static final Map<String, String> TRADE_PRESENT = Map.of(
            "minecraft:librarian/level_1", "minecraft:librarian/1/paper_emerald",
            "minecraft:cartographer/level_3", "minecraft:cartographer/3/compass_emerald",
            "minecraft:cleric/level_4", "minecraft:cleric/4/glass_bottle_emerald",
            "minecraft:cartographer/level_2", "minecraft:cartographer/2/emerald_and_compass_explorer_jungle_map",
            "minecraft:wandering_trader/uncommon", "minecraft:wandering_trader/emerald_gunpowder");

    static void run(GameTestHelper h) {
        var world = h.getLevel();
        var server = world.getServer();
        var registries = server.registryAccess();

        // Every listed creature id must resolve in this game version; a typo would silently
        // leave a family alive, so the list itself is checked.
        for (String id : ThemePolicy.REMOVED_ENTITIES) {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
            h.assertTrue(type != null, "Unknown removed creature id: " + id);
            h.assertTrue(ThemePolicy.removed(type), "Removal policy lost " + id);
        }
        for (String id : ThemePolicy.REMOVED_ITEMS) {
            h.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)) != null, "Unknown removed item id: " + id);
        }
        for (String id : ThemePolicy.DISABLED_BLOCKS) {
            h.assertTrue(BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)) != null, "Unknown disabled block id: " + id);
        }

        // A control animal joins, every removed family is refused at the level boundary.
        var control = EntityTypes.COW.create(world, EntitySpawnReason.COMMAND);
        control.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 3, 6))));
        h.assertTrue(world.addFreshEntity(control), "Control animal could not join the level");
        control.discard();
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(
                EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.ENDERMAN,
                EntityTypes.CAVE_SPIDER, EntityTypes.PHANTOM, EntityTypes.SHULKER, EntityTypes.WARDEN,
                EntityTypes.CREAKING, EntityTypes.IRON_GOLEM)) {
            var mob = type.create(world, EntitySpawnReason.COMMAND);
            h.assertTrue(mob != null, "Removed creature could not be constructed: " + type);
            mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 3, 6))));
            h.assertFalse(world.addFreshEntity(mob), "Removed creature joined the level: " + type);
            mob.discard();
        }

        // Biome data: no removed spawner and no removed feature survives in either dimension.
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        for (String id : List.of("plains", "desert", "snowy_taiga", "deep_dark", "lush_caves",
                "nether_wastes", "basalt_deltas", "the_end")) {
            var biome = biomes.getValue(Identifier.parse("minecraft:" + id));
            h.assertTrue(biome != null, "Unknown biome " + id);
            for (var category : MobCategory.values()) {
                for (var entry : biome.getMobSettings().getMobs(category).unwrap()) {
                    h.assertFalse(ThemePolicy.removed(entry.value().type()),
                            "Biome " + id + " still spawns " + entry.value().type());
                }
            }
            for (var step : biome.getGenerationSettings().features()) {
                for (var feature : step) {
                    var key = feature.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                    h.assertFalse(ThemePolicy.REMOVED_FEATURES.contains(key), "Biome " + id + " still generates " + key);
                }
            }
        }

        // Structure sets: the disabled ones are empty, ordinary ones still place.
        var sets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        for (String id : ThemePolicy.DISABLED_STRUCTURE_SETS) {
            var set = sets.getValue(Identifier.parse("minecraft:" + id));
            h.assertTrue(set != null, "Unknown structure set " + id);
            h.assertTrue(set.structures().isEmpty(), "Structure set still places " + id);
        }
        for (String id : List.of("villages", "mineshafts", "ocean_ruins", "shipwrecks")) {
            var set = sets.getValue(Identifier.parse("minecraft:" + id));
            h.assertTrue(set != null && !set.structures().isEmpty(), "Ordinary structure set was emptied: " + id);
        }

        recipes(h, server.getRecipeManager());
        loot(h, server, registries);
        trades(h, registries);
        advancements(h, server);
        mechanics(h, world);
        dimensions(h, world);
        bones(h, world);
        h.succeed();
    }

    private static void recipes(GameTestHelper h, RecipeManager manager) {
        for (String id : List.of("beacon", "enchanting_table", "brewing_stand", "ender_chest", "respawn_anchor",
                "conduit", "ender_eye", "golden_apple", "popped_chorus_fruit", "firework_rocket_simple",
                "netherite_sword_smithing", "netherite_upgrade_smithing_template", "netherite_ingot")) {
            h.assertTrue(manager.byKey(key(Registries.RECIPE, "minecraft:" + id)).isEmpty(), "Recipe survived: " + id);
        }
        for (String id : List.of("crafting_table", "iron_pickaxe", "bread", "bookshelf", "bone_meal",
                "copper_bulb", "waxed_oxidized_copper_bulb", "waxed_copper_bulb_from_honeycomb")) {
            h.assertTrue(manager.byKey(key(Registries.RECIPE, "minecraft:" + id)).isPresent(), "Recipe was lost: " + id);
        }
    }

    private static void loot(GameTestHelper h, MinecraftServer server, RegistryAccess registries) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var reloadable = server.reloadableRegistries();
        for (String id : List.of("minecraft:chests/simple_dungeon", "minecraft:chests/end_city_treasure",
                "minecraft:gameplay/fishing/treasure", "minecraft:chests/ancient_city")) {
            var table = reloadable.getLootTable(key(Registries.LOOT_TABLE, id));
            h.assertTrue(table != LootTable.EMPTY, "Missing loot table " + id);
            var json = LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow();
            h.assertFalse(containsEnchantFunction(json), "Enchantment function survived in " + id);
            var removed = new ArrayList<String>();
            collectRemovedItems(json, removed);
            h.assertTrue(removed.isEmpty(), "Removed items survived in " + id + ": " + removed);
            h.assertTrue(hasItemEntry(json), "Loot table was emptied: " + id);
        }
    }

    private static void trades(GameTestHelper h, RegistryAccess registries) {
        var trades = registries.lookupOrThrow(Registries.VILLAGER_TRADE);
        TRADE_ABSENT.forEach((tag, trade) -> h.assertFalse(tagged(trades, tag).contains(trade),
                "Trade survived in " + tag + ": " + trade));
        TRADE_PRESENT.forEach((tag, trade) -> h.assertTrue(tagged(trades, tag).contains(trade),
                "Trade was lost in " + tag + ": " + trade));
    }

    private static void advancements(GameTestHelper h, MinecraftServer server) {
        for (String id : List.of("minecraft:story/enter_the_nether", "minecraft:story/enter_the_end",
                "minecraft:end/kill_dragon", "minecraft:nether/summon_wither", "minecraft:story/enchant_item",
                "minecraft:adventure/totem_of_undying", "minecraft:husbandry/balanced_diet")) {
            var holder = server.getAdvancements().get(Identifier.parse(id));
            h.assertTrue(holder != null, "Missing advancement " + id);
            var criteria = holder.value().criteria();
            h.assertFalse(criteria.isEmpty(), "Advancement has no criteria " + id);
            for (var criterion : criteria.values()) {
                h.assertTrue(trigger(criterion), "Advancement is still reachable: " + id);
            }
        }
        var reachable = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
        h.assertTrue(reachable != null && reachable.value().criteria().values().stream().noneMatch(ThemeGameTests::trigger),
                "An unrelated advancement was disabled");
    }

    private static void mechanics(GameTestHelper h, ServerLevel world) {
        for (var block : List.of(Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND, Blocks.BEACON, Blocks.CONDUIT,
                Blocks.ENDER_CHEST, Blocks.RESPAWN_ANCHOR, Blocks.END_PORTAL_FRAME, Blocks.TRIAL_SPAWNER,
                Blocks.VAULT, Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER, Blocks.CREAKING_HEART)) {
            h.assertTrue(ThemePolicy.disabled(block.defaultBlockState()), "Mechanic block still usable: " + block);
        }
        for (var block : List.of(Blocks.CHEST, Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.GRINDSTONE,
                Blocks.FURNACE, Blocks.SMITHING_TABLE, Blocks.SCULK, Blocks.SPAWNER)) {
            h.assertFalse(ThemePolicy.disabled(block.defaultBlockState()), "Ordinary block was disabled: " + block);
        }
        h.assertTrue(ThemePolicy.isGolemHead(Blocks.CARVED_PUMPKIN.defaultBlockState())
                && ThemePolicy.golemBase(Blocks.IRON_BLOCK.defaultBlockState())
                && ThemePolicy.golemBase(Blocks.SNOW_BLOCK.defaultBlockState())
                && ThemePolicy.golemBase(Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState()),
                "Golem build detection missed a base");
        h.assertFalse(ThemePolicy.golemBase(Blocks.STONE.defaultBlockState()), "Ordinary base treated as a golem build");
        h.assertTrue(ThemePolicy.witherBase(Blocks.SOUL_SAND.defaultBlockState())
                && ThemePolicy.witherBase(Blocks.SOUL_SOIL.defaultBlockState()), "Wither build detection missed soul blocks");

        var player = h.makeMockPlayer(GameType.SURVIVAL);
        world.addFreshEntity(player);
        try {
            var totem = new LivingUseTotemEvent(player, world.damageSources().generic(),
                    new ItemStack(Items.TOTEM_OF_UNDYING), InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(totem);
            h.assertTrue(totem.isCanceled(), "Totem of undying still protected its holder");
        } finally {
            player.discard();
        }
    }

    private static void dimensions(GameTestHelper h, ServerLevel world) {
        var rules = world.getGameRules();
        h.assertFalse(rules.get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS), "Nether portals are still enabled");
        h.assertFalse(rules.get(GameRules.RAIDS), "Raids are still enabled");
        h.assertFalse(rules.get(GameRules.SPAWN_PATROLS), "Patrols are still enabled");
        h.assertFalse(rules.get(GameRules.SPAWN_PHANTOMS), "Phantoms are still enabled");
        h.assertFalse(rules.get(GameRules.SPAWN_WARDENS), "Wardens are still enabled");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (var target : List.of(Level.NETHER, Level.END)) {
            var event = new EntityTravelToDimensionEvent(player, target);
            NeoForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled(), "Travel to " + target.identifier() + " was allowed");
        }
        var overworld = new EntityTravelToDimensionEvent(player, Level.OVERWORLD);
        NeoForge.EVENT_BUS.post(overworld);
        h.assertFalse(overworld.isCanceled(), "Ordinary Overworld travel was blocked");

        // A character saved inside a removed dimension is returned to safe Overworld ground.
        // The test installs its own platform and respawn point so the result is deterministic.
        var original = world.getServer().getRespawnData();
        h.setBlock(12, 1, 12, Blocks.STONE);
        var platform = h.absolutePos(new BlockPos(12, 2, 12));
        world.getServer().setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, platform, 0.0F, 0.0F));
        var saved = h.makeMockPlayer(GameType.SURVIVAL);
        try {
            var landing = DimensionGuard.returnPosition(world, saved, new Vec3(0.5, 64.0, 0.5), Level.END);
            h.assertTrue(landing != null, "No safe Overworld return position for " + platform);
            h.assertTrue(landing.getX() == platform.getX() && landing.getZ() == platform.getZ(),
                    "Recorded respawn point was not reused: " + landing);
            h.assertTrue(world.getFluidState(landing).isEmpty() && world.getBlockState(landing.below()).blocksMotion(),
                    "Return position is not safe ground: " + landing);
            // A legacy respawn point inside the Nether maps the walk-in coordinates instead.
            world.getServer().setRespawnData(LevelData.RespawnData.of(Level.NETHER, new BlockPos(0, 64, 0), 0.0F, 0.0F));
            var mapped = DimensionGuard.returnPosition(world, saved, new Vec3(0.5, 64.0, 0.5), Level.NETHER);
            h.assertTrue(mapped == null || (world.getFluidState(mapped).isEmpty()
                    && world.getBlockState(mapped.below()).blocksMotion()), "Mapped return position is not safe: " + mapped);
        } finally {
            world.getServer().setRespawnData(original);
            saved.discard();
        }
    }

    /** Bones move from skeletons to animal carcasses, so bone meal survives their removal. */
    private static void bones(GameTestHelper h, ServerLevel world) {
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        int spawned = 0;
        for (int i = 0; i < 10; i++) {
            Cow cow = EntityTypes.COW.create(world, EntitySpawnReason.COMMAND);
            h.assertTrue(cow != null, "Cow could not be created");
            cow.setNoAi(true);
            cow.setPos(Vec3.atBottomCenterOf(pos));
            h.assertTrue(world.addFreshEntity(cow), "Cow could not join the level");
            cow.hurtServer(world, world.damageSources().generic(), 1000.0F);
            spawned++;
        }
        var bones = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(24),
                item -> item.getItem().is(Items.BONE));
        h.assertTrue(spawned == 10 && !bones.isEmpty(), "Animal carcasses dropped no bones");
    }

    private static List<String> tagged(net.minecraft.core.Registry<VillagerTrade> trades, String tag) {
        var found = new ArrayList<String>();
        for (var holder : trades.getTagOrEmpty(TagKey.create(Registries.VILLAGER_TRADE, Identifier.parse(tag)))) {
            holder.unwrapKey().ifPresent(key -> found.add(key.identifier().toString()));
        }
        return found;
    }

    private static boolean trigger(Criterion<?> criterion) {
        var id = BuiltInRegistries.TRIGGER_TYPES.getKey(criterion.trigger());
        return id != null && id.toString().equals("minecraft:impossible");
    }

    private static boolean containsEnchantFunction(JsonElement element) {
        if (element instanceof JsonArray array) {
            for (var child : array) if (containsEnchantFunction(child)) return true;
            return false;
        }
        if (!(element instanceof JsonObject object)) return false;
        var function = object.get("function");
        if (function != null && function.isJsonPrimitive()
                && ThemePolicy.ENCHANT_LOOT_FUNCTIONS.contains(function.getAsString())) return true;
        for (var child : object.entrySet()) if (containsEnchantFunction(child.getValue())) return true;
        return false;
    }

    private static void collectRemovedItems(JsonElement element, List<String> found) {
        if (element instanceof JsonArray array) {
            for (var child : array) collectRemovedItems(child, found);
            return;
        }
        if (!(element instanceof JsonObject object)) return;
        var type = object.get("type");
        var name = object.get("name");
        if (type != null && type.isJsonPrimitive() && "minecraft:item".equals(type.getAsString())
                && name != null && name.isJsonPrimitive() && ThemePolicy.REMOVED_ITEM_IDS.contains(name.getAsString())) {
            found.add(name.getAsString());
        }
        for (var child : object.entrySet()) collectRemovedItems(child.getValue(), found);
    }

    private static boolean hasItemEntry(JsonElement element) {
        if (element instanceof JsonArray array) {
            for (var child : array) if (hasItemEntry(child)) return true;
            return false;
        }
        if (!(element instanceof JsonObject object)) return false;
        var type = object.get("type");
        if (type != null && type.isJsonPrimitive() && "minecraft:item".equals(type.getAsString())) return true;
        for (var child : object.entrySet()) if (hasItemEntry(child.getValue())) return true;
        return false;
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String id) {
        return ResourceKey.create(registry, Identifier.parse(id));
    }

    private ThemeGameTests() {}
}
