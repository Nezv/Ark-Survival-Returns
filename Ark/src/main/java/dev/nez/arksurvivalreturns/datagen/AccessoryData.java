package dev.nez.arksurvivalreturns.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryContent;
import dev.nez.arksurvivalreturns.feature.accessory.TrophyDrops;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Accessories (P13 / I07): item models, recipes, Curios slot tags, trophy loot tables, GameTest instances.
 * Translations are merged into ArkData's language files through {@link #lang}. Kept apart from ArkData so
 * the accessory set can be regenerated on its own.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class AccessoryData implements DataProvider {
    private static final String NS = ArkSurvivalReturns.MOD_ID;
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private final PackOutput output;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();
    /** Recipe ingredients per accessory, for the showcase export. */
    private final Map<String, List<String>> made = new LinkedHashMap<>();

    public AccessoryData(PackOutput output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent.Client event) { event.createProvider(AccessoryData::new); }

    @Override public String getName() { return "Ark accessories"; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        files.clear();
        models();
        recipes();
        tags();
        trophyLoot();
        tests();
        var saves = new ArrayList<CompletableFuture<?>>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        // Showcase facts live beside the design sources (Ark/design/showcase), never in the shipped resources.
        var showcase = output.getOutputFolder().getParent().getParent().getParent().resolve("design/showcase");
        saves.add(DataProvider.saveStable(cache, new Gson().toJsonTree(showcase()), showcase.resolve("accessories.json")));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    /** Slot, group, bonuses, ingredients and creature sources of every accessory, for build_showcase.py. */
    private Map<String, Object> showcase() {
        var accessories = new ArrayList<Object>();
        for (Accessory accessory : Accessory.values()) {
            var bonuses = new ArrayList<Object>();
            for (Accessory.Bonus bonus : accessory.bonuses) {
                bonuses.add(Map.of("attribute", bonus.attribute().get().getRegisteredName(), "amount", bonus.amount(),
                        "operation", bonus.operation().getSerializedName()));
            }
            accessories.add(Map.of("id", accessory.id, "slot", accessory.slot.id(), "group", accessory.group.name().toLowerCase(),
                    "bonuses", bonuses, "ingredients", made.getOrDefault(accessory.id, List.of())));
        }
        var trophies = new LinkedHashMap<String, List<Object>>();
        TrophyDrops.TABLE.forEach((species, drops) -> {
            for (TrophyDrops.Drop drop : drops) {
                trophies.computeIfAbsent(drop.trophy(), k -> new ArrayList<>()).add(Map.of("species", species.id,
                        "chance", drop.chance(), "min", drop.min(), "max", drop.max()));
            }
        });
        trophies.computeIfAbsent("amber", k -> new ArrayList<>()).add(Map.of("species", "digging", "chance", 0.015, "min", 1, "max", 1));
        return Map.of("accessories", accessories, "trophies", trophies);
    }

    private void put(String path, Object value) { files.put(path + ".json", new Gson().toJsonTree(value)); }

    // ------------------------------------------------------------------------------ models

    private void models() {
        for (Accessory accessory : Accessory.values()) flat(accessory.id, NS + ":item/accessory/" + accessory.id);
        for (String trophy : AccessoryContent.TROPHY_IDS) flat(trophy, NS + ":item/trophy/" + trophy);
    }

    private void flat(String id, String texture) {
        put(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", texture)));
        put(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    // ----------------------------------------------------------------------------- recipes

    private static final String FIBER = NS + ":plant_fiber", ROCK = NS + ":rock", OCHRE = NS + ":red_ochre";

    private void recipes() {
        made.clear();
        shapeless("bone_snow_goggles", "bone", "bone", "leather", FIBER);
        shapeless("pelt_hood", t("thick_pelt"), t("thick_pelt"), t("fang"), FIBER);
        shapeless("antler_frontlet", t("giant_antler"), t("giant_antler"), "leather", FIBER);
        shapeless("fang_necklace", t("fang"), t("fang"), t("fang"), FIBER);
        shapeless("membrane_glider", t("wing_membrane"), t("wing_membrane"), "bone", "bone", "leather", FIBER);
        shaped("pack_frame", List.of("S S", "SLS", "SFS"), Map.of("S", "minecraft:stick", "L", "minecraft:leather", "F", FIBER));
        shapeless("hide_quiver", "leather", "leather", "leather", "bone", FIBER);
        shapeless("fur_mantle", t("thick_pelt"), t("thick_pelt"), t("thick_pelt"), FIBER);
        shapeless("ghillie_wrap", "#minecraft:leaves", "#minecraft:leaves", "#minecraft:leaves", "#minecraft:leaves",
                FIBER, FIBER, FIBER, "leather");
        shaped("bone_vest", List.of("B B", "BLB", "BBB"), Map.of("B", "minecraft:bone", "L", "minecraft:leather"));
        shapeless("thornproof_wraps", "leather", "leather", FIBER, FIBER);
        shapeless("croc_waders", t("croc_scute"), t("croc_scute"), t("croc_scute"), t("croc_scute"), "leather", "leather");
        shapeless("snowshoes", "stick", "stick", "stick", "stick", FIBER, FIBER, FIBER, "leather");
        shapeless("bone_skates", "bone", "bone", "leather", FIBER);
        shapeless("stalker_moccasins", "leather", "leather", FIBER, "feather");
        shapeless("flipper_sandals", t("marine_flipper"), t("marine_flipper"), "leather", FIBER);
        shapeless("climbing_claws", t("sickle_claw"), t("sickle_claw"), "leather", "leather", FIBER);
        shapeless("scythe_claws", t("scythe_claw"), t("scythe_claw"), "leather", FIBER);
        shapeless("archer_wristguard", ROCK, ROCK, "leather", FIBER);
        shapeless("thumb_ring", "bone", "flint");
        shapeless("ember_carrier", "clay_ball", "clay_ball", NS + ":fire_starter", FIBER);
        shapeless("herbal_pouch", "leather", FIBER, "#" + NS + ":berries", "#" + NS + ":berries", "#" + NS + ":berries");
        shapeless("diver_stones", ROCK, ROCK, ROCK, FIBER, FIBER);
        shapeless("tally_bone", "bone", ROCK);
        shapeless("amber_amulet", t("amber"), t("amber"), t("amber"), FIBER, OCHRE);
        shapeless("alicorn_pendant", t("unicorn_horn"), "gold_nugget", "gold_nugget", FIBER, OCHRE);
        shapeless("quetzal_mantle", t("quetzal_feather"), t("quetzal_feather"), t("quetzal_feather"), t("quetzal_feather"),
                t("thick_pelt"), FIBER, OCHRE);
        shapeless("guardian_crown", NS + ":guardian_trophy", "gold_ingot", "gold_ingot", "gold_ingot", OCHRE);
        shapeless("obsidian_band", "obsidian", "flint", "flint", OCHRE);
        shapeless("amber_ring", t("amber"), "bone", OCHRE);
        shapeless("serpent_ring", t("venom_fang"), "bone", OCHRE);
        shapeless("kinship_bracelet", t("thick_pelt"), "feather", FIBER, FIBER, OCHRE);
        shapeless("raptor_totem", "#minecraft:logs", t("sickle_claw"), OCHRE);
        shapeless("rex_totem", "#minecraft:logs", t("tyrant_tooth"), OCHRE);
        shapeless("argentavis_totem", "#minecraft:logs", t("argentavis_talon"), OCHRE);
        shapeless("megalodon_totem", "#minecraft:logs", t("megalodon_tooth"), OCHRE);
        put(DATA + "recipe/red_ochre", Map.of("type", "minecraft:crafting_shapeless", "category", "misc", "group", "red_ochre",
                "ingredients", List.of("minecraft:clay_ball", ROCK, "minecraft:red_dye"),
                "result", Map.of("count", 2, "id", OCHRE)));
    }

    private static String t(String trophy) { return NS + ":" + trophy; }

    private static String ingredient(String id) {
        if (id.startsWith("#") || id.contains(":")) return id;
        return "minecraft:" + id;
    }

    private void shapeless(String id, String... ingredients) {
        made.put(id, java.util.Arrays.stream(ingredients).map(AccessoryData::ingredient).toList());
        put(DATA + "recipe/accessory/" + id, Map.of("type", "minecraft:crafting_shapeless", "category", "equipment",
                "group", "ark_accessory", "ingredients", java.util.Arrays.stream(ingredients).map(AccessoryData::ingredient).toList(),
                "result", Map.of("count", 1, "id", NS + ":" + id)));
    }

    private void shaped(String id, List<String> pattern, Map<String, String> key) {
        var ingredients = new ArrayList<String>();
        for (String row : pattern) for (char c : row.toCharArray()) if (c != ' ') ingredients.add(key.get(String.valueOf(c)));
        made.put(id, ingredients);
        put(DATA + "recipe/accessory/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "equipment",
                "group", "ark_accessory", "pattern", pattern, "key", key, "result", Map.of("count", 1, "id", NS + ":" + id)));
    }

    // -------------------------------------------------------------------------------- tags

    private void tags() {
        Map<String, List<String>> slots = new LinkedHashMap<>();
        for (Accessory accessory : Accessory.values()) {
            slots.computeIfAbsent(accessory.slot.id(), k -> new ArrayList<>()).add(NS + ":" + accessory.id);
        }
        slots.forEach((slot, items) -> put("data/curios/tags/item/" + slot, Map.of("replace", false, "values", items)));
        put("data/minecraft/tags/item/freeze_immune_wearables", Map.of("replace", false, "values", List.of(NS + ":fur_mantle")));
        put(DATA + "tags/block/thorny", Map.of("replace", false, "values", List.of("minecraft:sweet_berry_bush",
                NS + ":tintoberry_bush", NS + ":amarberry_bush", NS + ":azulberry_bush", NS + ":narcoberry_bush")));
        put(DATA + "tags/item/accessories", Map.of("replace", false, "values",
                java.util.Arrays.stream(Accessory.values()).map(a -> NS + ":" + a.id).toList()));
        put(DATA + "tags/item/trophies", Map.of("replace", false, "values",
                AccessoryContent.TROPHY_IDS.stream().map(id -> NS + ":" + id).toList()));
    }

    // ------------------------------------------------------------------------ trophy loot

    /** Creature parts, rolled on a player kill in addition to the carcass table (TrophyDrops). */
    private void trophyLoot() {
        TrophyDrops.TABLE.forEach((species, drops) -> {
            var pools = new ArrayList<Object>();
            for (TrophyDrops.Drop drop : drops) {
                pools.add(Map.of("rolls", 1,
                        "conditions", List.of(Map.of("condition", "minecraft:random_chance", "chance", drop.chance())),
                        "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + drop.trophy(), "functions", List.of(
                                Map.of("function", "minecraft:set_count", "count",
                                        Map.of("type", "minecraft:uniform", "min", drop.min(), "max", drop.max())),
                                Map.of("function", "minecraft:enchanted_count_increase", "enchantment", "minecraft:looting",
                                        "count", Map.of("type", "minecraft:uniform", "min", 0, "max", 1)))))));
            }
            put(DATA + "loot_table/trophies/" + species.id, Map.of("type", "minecraft:entity", "pools", pools));
        });
    }

    // ------------------------------------------------------------------------------- tests

    private void tests() {
        for (String name : List.of("accessory_catalog", "accessory_curios", "accessory_senses", "accessory_trophies"))
            put(DATA + "test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
    }

    // -------------------------------------------------------------------------------- lang

    /** Names, effect lines and lore. Called from ArkData's language pass. */
    static void lang(Map<String, String> en) {
        String[][] rows = {
            // id, name, effect, lore
            {"bone_snow_goggles", "Bone Snow Goggles", "No Blindness or Darkness. Sneak to squint: the view narrows like a spyglass.",
                "Carved slits cut the glare off the snow."},
            {"pelt_hood", "Stalker's Pelt Hood", "Stalker set: creatures cannot smell you.",
                "Hunters wore their quarry's skin to walk downwind unnoticed."},
            {"antler_frontlet", "Antler Frontlet", "Herbivores take you for one of the herd until you strike a creature.",
                "Stag skull and antlers, worn at Star Carr eleven thousand years ago."},
            {"fang_necklace", "Fang Necklace", "Ambush: double damage to creatures that have not noticed you.",
                "Every fang a hunt that went right."},
            {"membrane_glider", "Membrane Glider", "Hold Jump while falling to glide; look down to dive faster. No fall damage while gliding.",
                "Pteranodon skin stretched over bone spars."},
            {"pack_frame", "Pack Frame", "Carry 40% more before the load slows you.",
                "A bent hazel frame like the one the Iceman carried over the Alps."},
            {"hide_quiver", "Hide Quiver", "30% chance to keep each arrow you shoot, tranquilizer arrows included.",
                "Arrows at the shoulder, not in the hand."},
            {"fur_mantle", "Fur Mantle", "You never freeze in powder snow.",
                "Mammoth wool against the long winter."},
            {"ghillie_wrap", "Ghillie Wrap", "Stalker set: creatures see you from much closer.",
                "Leaves and fibre until the hunter is part of the thicket."},
            {"bone_vest", "Bone Lamellar Vest", "Rows of laced bone plates.",
                "Arctic hunters laced armour from bone and ivory long before metal."},
            {"thornproof_wraps", "Thornproof Leg Wraps", "Thorns, cacti and berry bushes neither hurt nor hold you.",
                "Hide strips wound from knee to ankle."},
            {"croc_waders", "Croc-Scale Waders", "Wade through water and mud with little slowdown.",
                "Scutes shed by the river's oldest hunters."},
            {"snowshoes", "Snowshoes", "Walk on powder snow; faster on snow.",
                "Bent wood and rawhide lacing spread the weight."},
            {"bone_skates", "Bone Skates", "Glide across ice much faster.",
                "Polished leg bones, the oldest skates ever found."},
            {"stalker_moccasins", "Stalker Moccasins", "Stalker set: creatures barely hear you; sneak faster.",
                "Soft soles for soft steps."},
            {"flipper_sandals", "Flipper Sandals", "Swim much faster; waddle on land.",
                "Plesiosaur paddles tied under the foot."},
            {"climbing_claws", "Climbing Claws", "Climb walls: walk into one to climb, sneak to cling.",
                "Raptor sickle claws strapped to the palms."},
            {"scythe_claws", "Scythe Claws", "Cut plants and leaves at a touch; grass gives more fibre, leaves more sticks.",
                "Therizinosaurus claws, made for stripping branches."},
            {"archer_wristguard", "Stone Archer's Wristguard", "Archer set: your arrows fly 25% faster.",
                "Ground greenstone, like the Beaker bowmen's bracers."},
            {"thumb_ring", "Bone Thumb Ring", "Archer set: bows and crossbows draw a third faster.",
                "The string rolls off bone, not skin."},
            {"ember_carrier", "Ember Carrier", "Sneak and use an empty hand on a Stone Fire, campfire or candle to light it.",
                "A clay pot of smouldering tinder fungus."},
            {"herbal_pouch", "Herbal Pouch", "Below a third of your health you chew the herbs: Regeneration II. Refills in 90 seconds.",
                "Bitter leaves and red berries, for bad days."},
            {"diver_stones", "Diver's Stones", "Sneak in water to sink like a stone; hold your breath longer.",
                "Pearl divers went down on a weighted cord."},
            {"tally_bone", "Tally Bone", "+1 Looting against creatures.",
                "A notched bone keeps count of every hunt."},
            {"amber_amulet", "Amber Amulet", "A fatal blow cracks the amber instead: you stay up at 40% health. Heals in a day.",
                "Something ancient sleeps inside, and will not let you join it yet."},
            {"alicorn_pendant", "Alicorn Pendant", "Poison, wither, nausea and hunger fade from you.",
                "They said a unicorn's horn found the poison in any cup."},
            {"quetzal_mantle", "Quetzal Feather Mantle", "Jump again in mid-air, once per leap.",
                "The feathered serpent lends a little of its wind."},
            {"guardian_crown", "Guardian Crown", "Chieftain: your tribe's tames nearby grow stronger and heal; your tribe resists.",
                "Giganotosaurus teeth set in gold: the tribe follows whoever wears it."},
            {"obsidian_band", "Obsidian Band", "Fire and lava hurt 40% less; burning ends sooner.",
                "Volcanic glass remembers the heat and refuses it."},
            {"amber_ring", "Amber Ring", "See in the dark while you stand in it.",
                "Sunlight caught in resin, for the long nights."},
            {"serpent_ring", "Serpent Ring", "Your melee hits poison the target.",
                "Titanoboa venom never quite dries."},
            {"kinship_bracelet", "Kinship Bracelet", "Your tames nearby heal slowly and take 15% less damage.",
                "Braided from fur and feathers they shed for you."},
            {"raptor_totem", "Raptor Totem", "Swift: faster, and you step up whole blocks.",
                "Carved with the sickle claw it honours."},
            {"rex_totem", "Rex Totem", "Tyrant: hit harder; small creatures flee from you.",
                "Small things know the shape of that jaw."},
            {"argentavis_totem", "Argentavis Totem", "Keen eye: the creature you look at within 48 blocks is outlined.",
                "The great bird sees the whole valley at once."},
            {"megalodon_totem", "Megalodon Totem", "Deep: breathe underwater and swim faster.",
                "The sea gives back what the shark allows."},
        };
        for (String[] r : rows) {
            en.put("item." + NS + "." + r[0], r[1]);
            en.put("accessory." + NS + "." + r[0] + ".effect", r[2]);
            en.put("accessory." + NS + "." + r[0] + ".lore", r[3]);
        }
        String[][] trophies = {
            {"thick_pelt", "Thick Pelt"}, {"giant_antler", "Giant Antler"},
            {"fang", "Fang"}, {"wing_membrane", "Wing Membrane"},
            {"sickle_claw", "Sickle Claw"}, {"scythe_claw", "Scythe Claw"},
            {"croc_scute", "Croc Scute"}, {"marine_flipper", "Marine Flipper"},
            {"megalodon_tooth", "Megalodon Tooth"}, {"unicorn_horn", "Unicorn Horn"},
            {"quetzal_feather", "Quetzal Feather"}, {"venom_fang", "Venom Fang"},
            {"tyrant_tooth", "Tyrant Tooth"}, {"argentavis_talon", "Argentavis Talon"},
            {"amber", "Amber"}, {"red_ochre", "Red Ochre"}};
        for (String[] r : trophies) {
            en.put("item." + NS + "." + r[0], r[1]);
        }
        en.put("itemGroup." + NS + ".accessories", "Ark Accessories");
        en.put("accessory." + NS + ".recharging", "Recharging: %s min");
        en.put("accessory." + NS + ".amber_amulet.cracked", "The amber cracks and holds you up.");
        String[][] attributes = {{"visibility", "Visibility to Creatures"},
            {"noise", "Noise"}, {"scent", "Scent"}, {"carry_capacity", "Carry Capacity"}};
        for (String[] r : attributes) {
            en.put("attribute.name." + NS + "." + r[0], r[1]);
        }
        en.put("curios.identifier.legs", "Legs");
    }
}
