package dev.nez.arksurvivalreturns.datagen;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.data.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Single reproducible source for data-pack defaults, item models and translations. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class ArkData implements DataProvider {
    private static final String NS = ArkSurvivalReturns.MOD_ID;
    private final PackOutput output;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();
    public ArkData(PackOutput output) { this.output = output; }
    @SubscribeEvent public static void gather(GatherDataEvent.Client event) { event.createProvider(ArkData::new); }
    @Override public String getName() { return "Ark wildlife, berries and biome progression"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        files.clear();
        profiles = loadProfiles();
        tags(); models(); berries(); taming(); journal(); camp(); cargo(); farm(); medicine(); kitchen(); downed(); flying(); spawns(); theme(); tests();
        PrimitiveData.generate(this::put);
        BronzeData.generate(this::put);
        IntegrationData.generate(this::put);
        StationData.generate(this::put);
        DropData.generate(this::put);
        StationData.itemModels(this::put);
        SulphurData.generate(this::put);
        var saves = new ArrayList<CompletableFuture<?>>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        // Showcase facts live beside the design sources (Ark/design/showcase), never in the shipped resources.
        var showcase = output.getOutputFolder().getParent().getParent().getParent().resolve("design/showcase");
        saves.add(DataProvider.saveStable(cache, ShowcaseData.species(), showcase.resolve("species.json")));
        saves.add(DataProvider.saveStable(cache, ShowcaseData.behavior(), showcase.resolve("behavior.json")));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }
    private void put(String path, Object value) { files.put(path + ".json", new Gson().toJsonTree(value)); }
    private void json(String path, String value) { files.put(path + ".json", JsonParser.parseString(value)); }
    /**
     * The profile of every biome the pack ships, as the running game classifies it
     * (config/integrations/biome-profiles.json, written by the spawn_ranges GameTest). Datagen has no biome
     * tags to classify with, so the species range tags are generated from this copy and the test keeps the two
     * in step.
     */
    private Map<String, dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile> profiles = Map.of();

    private Map<String, dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile> loadProfiles() {
        var path = output.getOutputFolder().getParent().getParent().getParent().resolve("config/integrations/biome-profiles.json");
        try {
            var root = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().getAsJsonObject("biomes");
            var out = new TreeMap<String, dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile>();
            root.entrySet().forEach(e -> {
                var o = e.getValue().getAsJsonObject();
                out.put(e.getKey(), new dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile(e.getKey(),
                        dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Type.valueOf(o.get("type").getAsString().toUpperCase(java.util.Locale.ROOT)),
                        dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Climate.valueOf(o.get("climate").getAsString().toUpperCase(java.util.Locale.ROOT)),
                        dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Moisture.valueOf(o.get("moisture").getAsString().toUpperCase(java.util.Locale.ROOT)),
                        o.get("mountainous").getAsBoolean(), o.get("snowy").getAsBoolean(),
                        dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.TreeCover.valueOf(o.get("trees").getAsString().toUpperCase(java.util.Locale.ROOT))));
            });
            return out;
        } catch (java.io.IOException | RuntimeException missing) {
            return Map.of();
        }
    }

    private void tag(String name, String... values) {
        put("data/" + NS + "/tags/" + name, Map.of("replace", false, "values",
                Arrays.stream(values).map(v -> v.contains(":") ? v : "minecraft:" + v).toList()));
    }
    private void tags() {
        // One range tag per species, from its range over the shipped biomes (SpeciesRange). The game itself
        // asks the range, so a biome this list has not seen yet still gets its animals; the tag is the readable
        // copy for the showcase and the place where a data pack adds a biome.
        for (var species : Species.values()) {
            var entries = new ArrayList<Object>();
            profiles.forEach((id, profile) -> {
                if (dev.nez.arksurvivalreturns.feature.spawn.SpeciesRange.lives(species, profile))
                    entries.add(id.startsWith("minecraft:") ? id : Map.of("id", id, "required", false));
            });
            put("data/" + NS + "/tags/worldgen/biome/spawns/" + species.id, Map.of("replace", false, "values", entries));
        }
        tag("block/spawn_surfaces", "#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta",
                "grass_block", "podzol", "mycelium",
                "stone", "granite", "diorite", "andesite", "gravel", "snow", "snow_block", "ice", "packed_ice", "blue_ice",
                "mud", "packed_mud", "clay", "moss_block", "pale_moss_block", "calcite", "tuff", "deepslate");
        // Creature loot tables (meat, hide, bone) are generated by PrimitiveData.
        tag("item/berries", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry");
        tag("item/sedative_berries", NS + ":narcoberry");
        // Carried-mass categories. NeoForge common groups cover whole material families; block items
        // without a category default to one unit and everything else scales with its stack size.
        tag("item/mass/light", "string", "feather", "torch", "stick", "flint", "bone",
                NS + ":plant_fiber", NS + ":fiber_bandage");
        tag("item/mass/bulk", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry",
                "sweet_berries", "glow_berries", "egg", "snowball", "paper",
                "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds", "pitcher_pod");
        tag("item/mass/food", "#c:foods");
        tag("item/mass/unit", "minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "tnt_minecart",
                "bucket", "water_bucket", "lava_bucket", "milk_bucket", "powder_snow_bucket");
        tag("item/mass/ore", "#c:ores", "#c:raw_materials", "#c:ingots", "coal", "charcoal", "diamond", "emerald",
                "lapis_lazuli", "redstone", "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot",
                "ancient_debris");
        tag("item/mass/tool", "#c:tools", "shears", "flint_and_steel", "fishing_rod", "bow", "crossbow", "trident",
                NS + ":flint_knife", NS + ":keratin_spear");
        tag("item/mass/armor", "#c:armors", "shield", "elytra", "leather_horse_armor", "iron_horse_armor",
                "golden_horse_armor", "diamond_horse_armor", "wolf_armor");
        // Work job targets, so a data pack can retune what each worker harvests.
        tag("block/work/forage", "grass_block", "short_grass", "tall_grass", "fern", "large_fern", "sweet_berry_bush");
        tag("block/work/mineral", "#c:ores", "#c:stones");
    }
    private void models() {
        Map<String, String> en = new TreeMap<>();
        en.put("item." + NS + ".debug_spyglass", "Debug Spyglass");
        String[] debugKeys = {"title", "searching", "aim", "page", "range"};
        String[] debugEn = {"ARK // DINO INSPECTOR", "SCANNING...", "Aim at a dinosaur to read its state.", "PAGE %s/%s | Mouse wheel to browse", "RANGE %s blocks | Hold use to scan"};
        for (int i = 0; i < debugKeys.length; i++) {
            en.put("debug." + NS + "." + debugKeys[i], debugEn[i]);
        }
        // BetterF3 looks its module and line names up under its own keys (client/betterf3/ArkModule).
        String[] f3Keys = {"zone", "creatures", "wildlife", "nearby", "threads", "target", "mass", "level", "shaders"};
        String[] f3En = {"Danger zone", "Creatures", "Wildlife", "Nearby", "Threads", "Aimed at", "Load", "Ark level", "Shaders"};
        en.put("text.betterf3.module.ark", "Ark");
        for (int i = 0; i < f3Keys.length; i++) {
            en.put("text.betterf3.line.ark_" + f3Keys[i], f3En[i]);
        }
        // The inventory icon is the Ark sprite (tools/build_cosy_items.py); in hand it stays the vanilla scope.
        put("assets/" + NS + "/models/item/debug_spyglass", Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", NS + ":item/debug_spyglass")));
        put("assets/" + NS + "/items/debug_spyglass", Map.of("model", Map.of("type", "minecraft:select",
                "property", "minecraft:display_context", "cases", List.of(Map.of("when", List.of("gui", "ground", "fixed", "on_shelf"),
                        "model", Map.of("type", "minecraft:model", "model", NS + ":item/debug_spyglass"))),
                "fallback", Map.of("type", "minecraft:model", "model", "minecraft:item/spyglass_in_hand"))));
        en.put("itemGroup." + NS, "Ark Survival Returns");
        en.put("itemGroup." + NS + ".eggs", "Ark Creature Eggs");
        en.put("map." + NS + ".filter_on", "Difficulty: on"); en.put("map." + NS + ".filter_off", "Difficulty: off");
        String[] mapKeys = {"locked", "locked_short", "unlocked", "status", "legend", "unrated", "cursor", "rank_1", "rank_2", "rank_3"};
        String[] mapEn = {"[ARK] Map locked. Requires taming a creature from a danger-3 region.", "Locked", "Unlocked", "%s: map %s", "Region difficulty", "Unrated dimension", "At cursor: %s/3", "1 - Easy", "2 - Dangerous", "3 - Deadly"};
        for (int i = 0; i < mapKeys.length; i++) {
            en.put("map." + NS + "." + mapKeys[i], mapEn[i]);
        }
        en.put("hud." + NS + ".creature", "%s | Lv. %s | %s / %s HP");
        en.put("screen." + NS + ".taming", "Taming %s");
        en.put("screen." + NS + ".hunger", "Hunger %s");
        en.put("screen." + NS + ".torpor", "Torpor %s/%s");
        en.put("chat." + NS + ".biome", "[ARK] %s | Danger %s/3 | Wild levels %s-%s");
        en.put("chat." + NS + ".biome_unrated", "[ARK] %s | Outside Overworld danger zones");
        String[] states = {"roam", "forage", "drink", "rest", "alert", "investigate", "threaten", "hunt", "defend", "flee", "return_home", "feed"};
        String[] stateEn = {"Roaming", "Foraging", "Drinking", "Resting", "Alert", "Investigating", "Warning", "Hunting", "Defending", "Fleeing", "Returning home", "Feeding"};
        for (int i = 0; i < states.length; i++) {
            en.put("behavior." + NS + "." + states[i], stateEn[i]);
        }
        en.put("behavior." + NS + ".seek_water", "Seeking water");
        en.put("behavior." + NS + ".sleep", "Sleeping");
        en.put("behavior." + NS + ".search", "Searching for prey");
        en.put("behavior." + NS + ".regroup", "Regrouping");
        // Body actions follow the state on the target bar ("Hunting / Stalking"); a missing label fails datagen.
        var actions = new EnumMap<BehaviorAction, String>(BehaviorAction.class);
        actions.put(BehaviorAction.IDLE, "Standing");
        actions.put(BehaviorAction.WALK, "Walking");
        actions.put(BehaviorAction.RUN, "Running");
        actions.put(BehaviorAction.TURN, "Turning");
        actions.put(BehaviorAction.LOOK, "Looking around");
        actions.put(BehaviorAction.SNIFF, "Sniffing");
        actions.put(BehaviorAction.POOP, "Pooping");
        actions.put(BehaviorAction.GRAZE, "Grazing");
        actions.put(BehaviorAction.DRINK, "Drinking");
        actions.put(BehaviorAction.NOTICE, "Noticing");
        actions.put(BehaviorAction.ROAR, "Roaring");
        actions.put(BehaviorAction.THREAT, "Threat display");
        actions.put(BehaviorAction.STARTLE, "Startled");
        actions.put(BehaviorAction.STALK, "Stalking");
        actions.put(BehaviorAction.CHASE, "Chasing");
        actions.put(BehaviorAction.BOLT, "Bolting");
        actions.put(BehaviorAction.FEED, "Eating");
        actions.put(BehaviorAction.SETTLE, "Settling down");
        actions.put(BehaviorAction.SLEEP, "Asleep");
        actions.put(BehaviorAction.WAKE, "Waking up");
        actions.put(BehaviorAction.REST, "Resting");
        for (var action : BehaviorAction.values()) {
            var label = Objects.requireNonNull(actions.get(action), () -> "Missing action label: " + action);
            en.put(action.key(), label);
        }
        String[] ids = {"tintoberry", "amarberry", "azulberry", "narcoberry"};
        // Old ids, new names (recipe gates Q5): existing worlds keep their berries.
        String[] names = {"Redberry", "Yellowberry", "Blueberry", "Blackberry"};
        for (int i = 0; i < ids.length; i++) {
            model(ids[i]); en.put("item." + NS + "." + ids[i], names[i]);
        }
        // Tranquilizer arrow: the vanilla arrow with a narcotic head (tools/build_cosy_items.py).
        put("assets/" + NS + "/models/item/tranquilizer_arrow", Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", NS + ":item/tranquilizer_arrow")));
        put("assets/" + NS + "/items/tranquilizer_arrow", Map.of("model", Map.of("type", "minecraft:model",
                "model", NS + ":item/tranquilizer_arrow")));
        en.put("item." + NS + ".tranquilizer_arrow", "Tranquilizer Arrow");
        en.put("companion." + NS + ".order.follow", "Your companion follows you");
        en.put("companion." + NS + ".order.stay", "Your companion stays here");
        en.put("companion." + NS + ".order.wander", "Your companion wanders nearby");
        commandWheel(en);
        en.put("item." + NS + ".plant_fiber", "Plant Fiber");
        en.put("item." + NS + ".pack_harness", "Pack Harness");
        en.put("item." + NS + ".reinforced_harness", "Reinforced Harness");
        en.put("screen." + NS + ".cargo", "Cargo %s / %s");
        en.put("screen." + NS + ".unload", "Unload");
        en.put("screen." + NS + ".load", "Load");
        en.put("cargo." + NS + ".moved", "[ARK] Moved %s items.");
        en.put("cargo." + NS + ".none", "[ARK] No nearby storage accepted the transfer.");
        en.put("item." + NS + ".fiber_bandage", "Fiber Bandage");
        en.put("item." + NS + ".flint_knife", "Flint Knife");
        en.put("block." + NS + ".mattress", "Mattress");
        en.put("item." + NS + ".mattress", "Mattress");
        en.put("block." + NS + ".bedroll", "Bedroll");
        en.put("item." + NS + ".bedroll", "Bedroll");
        en.put("key." + NS + ".journal", "Open Journal");
        en.put("key.category." + NS + ".keys", "Ark Survival Returns");
        // The tech-tree key (P) has its own Controls category (TechClient).
        en.put("key.category." + NS + ".progression", "Ark Survival Returns: Progression");
        // The session recorder's marker key (SessionRecorderClient).
        en.put("key." + NS + ".session_mark", "Mark Recorded Moment");
        en.put("key.category." + NS + ".debug", "Ark Survival Returns: Debug");
        // Entities without a spawn egg still show their type name in death messages, statistics and recipe viewers.
        en.put("entity." + NS + ".guardian_dragon", "Beacon Guardian");
        en.put("container." + NS + ".loot_crate", "Loot Crate");
        en.put("entity." + NS + ".guardian_giganotosaurus", "Guardian Giganotosaurus");
        en.put("entity." + NS + ".tranquilizer_arrow", "Tranquilizer Arrow");
        tamingMessages(en);
        tribeMessages(en);
        campMessages(en);
        StationData.messages(en);
        DropData.messages(en);
        TitleData.messages(en);
        downedMessages(en);
        massMessages(en);
        workMessages(en);
        farmMessages(en);
        kitchenMessages(en);
        guardianMessages(en);
        techMessages(en);
        en.put("levels." + NS + ".up", "[ARK] Level up! Ark level %s");
        en.put("levels." + NS + ".status", "[ARK] %s: level %s, XP %s/%s");
        en.put("levels." + NS + ".cap", "[ARK] The maximum Ark level is %s.");
        for (Species s : Species.values()) {
            model(s.id + "_spawn_egg");
            en.put("entity." + NS + "." + s.id, s.displayName);
            en.put("item." + NS + "." + s.id + "_spawn_egg", s.displayName + " Spawn Egg");
            // The sprites come from tools/build_saddle_items.py, by the tier and seat of the concept saddle (F28).
            model(s.id + "_saddle");
            en.put("item." + NS + "." + s.id + "_saddle", s.displayName + " Saddle");
        }
        en.put("map."+NS+".land_on", "Land habitats: on"); en.put("map."+NS+".land_off", "Land habitats: off");
        en.put("map."+NS+".land_label", "%s | %s, %s, %s | %s");
        en.put("map."+NS+".land_occupied", "Occupied");
        en.put("map."+NS+".land_vacant", "Recovering");
        en.put("map."+NS+".land_invalid", "Seeking water");
        en.put("map."+NS+".land_more", "+%s habitats");
        en.put("map." + NS + ".nests_on", "Nests: on"); en.put("map." + NS + ".nests_off", "Nests: off");
        en.put("map." + NS + ".nest_label", "%s habitat | %s, %s, %s");
        for (var bird : Species.values()) {
            if (!bird.flyer()) continue;
            en.put("block." + NS + "." + bird.id + "_nest", bird.displayName + " Nest");
            en.put("item." + NS + "." + bird.id + "_egg", bird.displayName + " Egg");
        }
        model("allosaur_heart");
        model("workshop_schematic");
        model("guardian_trophy");
        PrimitiveData.lang(en);
        BronzeData.lang(en);
        AccessoryData.lang(en);
        SulphurData.lang(en);
        put("assets/" + NS + "/lang/en_us", en);
    }
    private void model(String id) {
        put("assets/" + NS + "/models/item/" + id, Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", NS + ":item/" + id)));
        put("assets/" + NS + "/items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    /**
     * Taming food categories as data tags, so a data pack can retune every diet without a code change.
     * Accepted food is tag based; the per-species favourite stays a single item in the profile table.
     */
    private void taming() {
        tag("item/taming/small_plant_food", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", "#minecraft:leaves");
        tag("item/taming/high_quality_plant_food", "carrot", "apple", "wheat");
        tag("item/taming/plant_food", "#" + NS + ":taming/small_plant_food",
                "#" + NS + ":taming/high_quality_plant_food");
        var rawMeat = new ArrayList<>(List.of("beef", "porkchop", "chicken", "rabbit", "mutton"));
        for (var meat : dev.nez.arksurvivalreturns.feature.primitive.DinoMeat.values()) {
            if (meat != dev.nez.arksurvivalreturns.feature.primitive.DinoMeat.MARINE) rawMeat.add(NS + ":" + meat.rawId());
        }
        tag("item/taming/raw_meat", rawMeat.toArray(String[]::new));
        tag("item/taming/fish", "cod", "salmon", NS + ":" + dev.nez.arksurvivalreturns.feature.primitive.DinoMeat.MARINE.rawId());
        tag("item/taming/sedative", NS + ":narcoberry", NS + ":narcotics", NS + ":tranquilizer_arrow",
                NS + ":improved_tranquilizer_arrow");
        tag("item/taming/knockout_food", "#" + NS + ":taming/plant_food", "#" + NS + ":taming/raw_meat",
                "#" + NS + ":taming/fish");
        json("data/" + NS + "/recipe/tranquilizer_arrow", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"tranquilizer_arrow",
                 "ingredients":["minecraft:arrow","minecraft:arrow",
                                "minecraft:arrow","minecraft:arrow",
                                "%s:narcotics","minecraft:bone"],
                 "result":{"count":4,"id":"%s:tranquilizer_arrow"}}
                """.formatted(NS, NS));
    }

    /** The hidden discovery advancements the quest book reads. */
    private void journal() {
        // Awarded by the taming code; hidden so it is a discovery record, not a popup.
        json("data/" + NS + "/advancement/journal/first_tame", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        json("data/" + NS + "/advancement/journal/top_zone_tame", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        // Awarded by the guardian service when the tribe wins the First Guardian encounter.
        json("data/" + NS + "/advancement/journal/first_guardian", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
    }

    /** The command wheel (F22): its key, wedges, hints and the whistle's answers on the action bar. */
    private void commandWheel(Map<String, String> en) {
        String c = "companion." + NS + ".command.", w = "wheel." + NS + ".";
        String[][] text = {
                {"key." + NS + ".command_wheel", "Command Wheel"},
                {"subtitles." + NS + ".whistle", "Whistle"},
                {c + "follow", "%s follows you"},
                {c + "stay", "%s stays here"},
                {c + "wander", "%s wanders around here"},
                {c + "work", "%s starts working here"},
                {c + "call", "%s comes to you"},
                {c + "group.follow", "%s tames follow you"},
                {c + "group.stay", "%s tames stay where they are"},
                {c + "group.wander", "%s tames wander around where they are"},
                {c + "group.work", "%s tames start working"},
                {c + "group.call", "%s tames come to you"},
                {c + "cannot_work", "%s has no work to do"},
                {c + "group.cannot_work", "None of these tames can work"},
                {c + "none", "No tame of yours within %s blocks hears you"},
                {c + "far", "%s is too far away to hear you"},
                {c + "unconscious", "%s is unconscious and cannot hear you"},
                {c + "pet_far", "Come closer to pet %s"},
                {w + "order.follow", "Follow"},
                {w + "order.stay", "Stay"},
                {w + "order.wander", "Wander"},
                {w + "order.work", "Work"},
                {w + "order.pet", "Pet"},
                {w + "order.call", "Call"},
                {w + "hint.follow", "Walks after you wherever you go"},
                {w + "hint.stay", "Holds its ground where it stands"},
                {w + "hint.wander", "Roams freely around where it stands"},
                {w + "hint.work", "Works the land around where it stands"},
                {w + "hint.pet", "Shows it some affection"},
                {w + "hint.call", "Comes to you and keeps its order there"},
                {w + "why.unconscious", "It is unconscious"},
                {w + "why.pet_far", "Come closer to pet it"},
                {w + "why.pet_group", "Aim at one tame to pet it"},
                {w + "why.none", "No tame of yours within %s blocks"},
                {w + "status", "Level %s · %s"},
                {w + "state.follow", "following"},
                {w + "state.stay", "staying"},
                {w + "state.wander", "wandering"},
                {w + "state.work", "working"},
                {w + "state.unconscious", "unconscious"},
                {w + "group", "Your tames"},
                {w + "group.count", "%s within %s blocks"},
                {w + "tames", "tames"},
                {w + "help.hold", "Release %s to give the order · Esc to cancel"},
                {w + "help.click", "Click an order or press 1 to 6 · Esc to cancel"}};
        for (String[] line : text) {
            en.put(line[0], line[1]);
        }
    }

    /** Player-facing taming and sedation text. */
    private void tamingMessages(Map<String, String> en) {
        String[] keys = {"knockout", "wake", "fed", "fed_favourite", "denied", "denied_access", "claim",
                "truce", "tamed", "unconscious", "accepted", "wrong_food", "not_hungry", "cooldown",
                "wrong_state", "not_claimant", "already_tamed", "excluded", "denied.attack", "denied.interact",
                "denied.use", "denied.move", "denied.mount", "mount.unconscious", "mount.wild", "mount.notowner",
                "mount.unsaddled", "mount.occupied", "mount.noroom", "mount.permission", "denied.cargo",
                "denied.command"};
        String[] english = {"%s has been knocked out.", "%s is waking up.", "%s eats. Taming progress %s%%.",
                "%s relishes the favourite food. Taming progress %s%%.", "%s refuses: %s",
                "You cannot reach %s right now.", "You are the claimant for %s.",
                "%s will tolerate you for a moment.", "%s trusts you now.", "You are unconscious: %s",
                "accepted", "wrong food", "not hungry enough", "still digesting the last meal",
                "not unconscious", "another player holds this attempt", "already tamed", "not a valid target",
                "you cannot attack", "you cannot interact", "you cannot use items", "you cannot walk",
                "you cannot mount", "the creature is unconscious", "you must tame it first",
                "you do not own this creature", "it needs its own saddle", "someone is already riding it",
                "there is no room to mount here", "your tribe has not granted you riding permission for %s",
                "your tribe has not granted you cargo access to %s",
                "your tribe has not granted you order permission for %s"};
        for (int i = 0; i < keys.length; i++) {
            en.put("taming." + NS + "." + keys[i], english[i]);
        }
    }
    /** Player-facing tribe permission text. */
    private void tribeMessages(Map<String, String> en) {
        String[] keys = {"status_solo", "status_party", "flag", "permission_set", "permission_cleared",
                "permission_denied", "unknown", "on", "off"};
        String[] english = {"No tribe yet for %s. Create one with /ftbteams party create, then invite your partner.",
                "Tribe %s | %s member(s)", "%s: %s", "%s for %s set to %s.",
                "Tribe permissions for %s reset to defaults.",
                "Only a tribe owner or a gamemaster may change tribe permissions.", "Unknown option: %s", "on", "off"};
        for (int i = 0; i < keys.length; i++) {
            en.put("tribe." + NS + "." + keys[i], english[i]);
        }
    }

    /**
     * Removals the theme enforces through data: biome spawn lists and features, structure sets,
     * village trades and the grounded source for bones.
     */
    private void theme() {
        tag("entity_type/theme/removed", dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.presentRemovedEntities().toArray(String[]::new));
        // One tag covers every vanilla dimension; modded biomes rely on the runtime guards.
        tag("worldgen/biome/theme/all_dimensions", "#minecraft:is_overworld", "#minecraft:is_nether", "#minecraft:is_end");
        put("data/" + NS + "/neoforge/biome_modifier/remove_fantasy_spawns", Map.of(
                "type", "neoforge:remove_spawns",
                "biomes", "#" + NS + ":theme/all_dimensions",
                "entity_types", "#" + NS + ":theme/removed"));
        put("data/" + NS + "/neoforge/biome_modifier/remove_fantasy_features", Map.of(
                "type", "neoforge:remove_features",
                "biomes", "#" + NS + ":theme/all_dimensions",
                "features", dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.REMOVED_FEATURES));
        // An empty structure list removes the set from world generation entirely, so the
        // replacement placement is never read; it only has to stay schema valid.
        var emptySet = Map.of("structures", List.of(), "placement", Map.of(
                "type", "minecraft:random_spread", "spacing", 32, "separation", 8, "salt", 0));
        for (String set : dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.DISABLED_STRUCTURE_SETS) {
            put("data/minecraft/worldgen/structure_set/" + set, emptySet);
        }
        dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.TRADE_TAGS.forEach((path, values) -> put(
                "data/minecraft/tags/villager_trade/" + path, Map.of("replace", true, "values", values)));
        // The copper bulb is an ordinary copper and redstone block whose vanilla recipe needs a
        // blaze rod. A torch keeps every variant craftable from Overworld materials.
        for (String copper : List.of("copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
                "waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper")) {
            String bulb = copper.replace("_block", "") + "_bulb";
            json("data/minecraft/recipe/" + bulb, """
                {"type":"minecraft:crafting_shaped","category":"redstone","group":"%s",
                 "key":{"C":"minecraft:%s","T":"minecraft:torch","R":"minecraft:redstone"},
                 "pattern":[" C ","CTC"," R "],"result":{"count":4,"id":"minecraft:%s"}}
                """.formatted(bulb, copper, bulb));
        }
        bones();
    }

    /** Bones move from skeletons to animal carcasses, keeping bone meal and wolf taming usable. */
    private void bones() {
        var terms = new ArrayList<Map<String, Object>>();
        for (String animal : dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.BONE_ANIMALS) {
            terms.add(Map.of("condition", "neoforge:loot_table_id", "loot_table_id", "minecraft:entities/" + animal));
        }
        put("data/" + NS + "/loot_modifiers/animal_bones", Map.of(
                "type", "neoforge:add_table",
                "table", NS + ":gameplay/animal_bones",
                "conditions", List.of(Map.of("condition", "minecraft:any_of", "terms", terms))));
        json("data/" + NS + "/loot_table/gameplay/animal_bones", """
            {"type":"minecraft:entity","pools":[{"rolls":1,"conditions":[
                {"condition":"minecraft:random_chance","chance":0.75}],
                "entries":[{"type":"minecraft:item","name":"minecraft:bone"}],
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":2}}]}]}
            """);
    }

    private void berries() {
        // NeoForge 26.2 discovers each modifier directly; no legacy global list file.
        json("data/" + NS + "/loot_modifiers/grass_berries", """
            {"type":"neoforge:add_table","table":"arksurvivalreturns:gameplay/grass_berries","conditions":[
                {"condition":"minecraft:any_of","terms":[
                    {"condition":"neoforge:loot_table_id","loot_table_id":"minecraft:blocks/short_grass"},
                    {"condition":"neoforge:loot_table_id","loot_table_id":"minecraft:blocks/tall_grass"}]},
                {"condition":"minecraft:any_of","terms":[
                    {"condition":"minecraft:block_state_property","block":"minecraft:short_grass"},
                    {"condition":"minecraft:block_state_property","block":"minecraft:tall_grass","properties":{"half":"lower"}}]},
                {"condition":"minecraft:inverted","term":{"condition":"minecraft:match_tool","predicate":{"items":"minecraft:shears"}}}
            ]}
            """);
        json("data/" + NS + "/loot_table/gameplay/grass_berries", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
                {"condition":"minecraft:random_chance","chance":0.35},{"condition":"minecraft:survives_explosion"}],
                "entries":[
                    {"type":"minecraft:item","name":"arksurvivalreturns:tintoberry","weight":30},
                    {"type":"minecraft:item","name":"arksurvivalreturns:amarberry","weight":30},
                    {"type":"minecraft:item","name":"arksurvivalreturns:azulberry","weight":30},
                    {"type":"minecraft:item","name":"arksurvivalreturns:narcoberry","weight":10}],
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":2}}]}]}
            """);
    }

    /** Camp gear: bedroll, field medicine, primitive tools and the plant fiber route. */
    private void camp() {
        // Hand-drawn sprites (tools/build_cosy_items.py): a tied sheaf of fibre and a plain woven roll.
        vanillaModel("plant_fiber", NS + ":item/plant_fiber");
        vanillaModel("fiber_bandage", NS + ":item/fiber_bandage");
        // Knapped flint blade bound to a haft (tools/build_prehistoric_icons.py), held like a tool.
        vanillaModel("flint_knife", NS + ":item/flint_knife");
        put("assets/" + NS + "/models/item/flint_knife", Map.of("parent", "minecraft:item/handheld",
                "textures", Map.of("layer0", NS + ":item/flint_knife")));
        // Painted flat icons (item/mattress.png, item/bedroll.png), not the 3D block models.
        vanillaModel("mattress", NS + ":item/mattress");
        vanillaModel("bedroll", NS + ":item/bedroll");
        CampAssetsData.mattress(this::put);
        CampAssetsData.bedroll(this::put);
        // Disposable: two halves like a bed; only the head drops (AbstractSleepingBlock#playerWillDestroy).
        json("data/" + NS + "/loot_table/blocks/mattress", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
             {"condition":"minecraft:survives_explosion"},
             {"condition":"minecraft:block_state_property","block":"%s:mattress","properties":{"part":"head"}}],
             "entries":[{"type":"minecraft:item","name":"%s:mattress"}]}]}
            """.formatted(NS, NS));
        // Reusable: four cells (2x2); only the head-left anchor drops, same rule as the mattress.
        json("data/" + NS + "/loot_table/blocks/bedroll", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
             {"condition":"minecraft:survives_explosion"},
             {"condition":"minecraft:block_state_property","block":"%s:bedroll","properties":{"part":"head","side":"left"}}],
             "entries":[{"type":"minecraft:item","name":"%s:bedroll"}]}]}
            """.formatted(NS, NS));
        // Plant fiber shares the grass route with the berries; shears still suppress it.
        json("data/" + NS + "/loot_modifiers/grass_fiber", """
            {"type":"neoforge:add_table","table":"arksurvivalreturns:gameplay/grass_fiber","conditions":[
                {"condition":"minecraft:any_of","terms":[
                    {"condition":"neoforge:loot_table_id","loot_table_id":"minecraft:blocks/short_grass"},
                    {"condition":"neoforge:loot_table_id","loot_table_id":"minecraft:blocks/tall_grass"}]},
                {"condition":"minecraft:any_of","terms":[
                    {"condition":"minecraft:block_state_property","block":"minecraft:short_grass"},
                    {"condition":"minecraft:block_state_property","block":"minecraft:tall_grass","properties":{"half":"lower"}}]},
                {"condition":"minecraft:inverted","term":{"condition":"minecraft:match_tool","predicate":{"items":"minecraft:shears"}}}
            ]}
            """);
        json("data/" + NS + "/loot_table/gameplay/grass_fiber", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
                {"condition":"minecraft:random_chance","chance":0.45},{"condition":"minecraft:survives_explosion"}],
                "entries":[{"type":"minecraft:item","name":"%s:plant_fiber"}],
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":2}}]}]}
            """.formatted(NS));
        tag("item/camp/flint_materials", "flint");
        json("data/" + NS + "/recipe/flint_knife", """
                {"type":"minecraft:crafting_shapeless","category":"equipment","group":"flint_knife",
                 "ingredients":["%s:sharp_rock","minecraft:stick","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:flint_knife"}}
                """.formatted(NS, NS, NS));
        json("data/" + NS + "/recipe/fiber_bandage", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"fiber_bandage",
                 "ingredients":["%s:plant_fiber","%s:plant_fiber","%s:plant_fiber","minecraft:string"],
                 "result":{"count":2,"id":"%s:fiber_bandage"}}
                """.formatted(NS, NS, NS, NS));
        // Nine fiber: only the Working Station's 3x3 grid takes it. Unchanged from the old bedroll recipe.
        json("data/" + NS + "/recipe/mattress", """
                {"type":"minecraft:crafting_shaped","category":"misc","group":"mattress",
                 "pattern":["FFF","FFF","FFF"],"key":{"F":"%s:plant_fiber"},
                 "result":{"count":1,"id":"%s:mattress"}}
                """.formatted(NS, NS));
        // A mattress becomes reusable with hide and more fiber; the 3-wide top row still forces the station.
        json("data/" + NS + "/recipe/bedroll", """
                {"type":"minecraft:crafting_shaped","category":"misc","group":"bedroll",
                 "pattern":["LLL","FMF"],"key":{"L":"#c:leathers","F":"%s:plant_fiber","M":"%s:mattress"},
                 "result":{"count":1,"id":"%s:bedroll"}}
                """.formatted(NS, NS, NS));
    }

    /** Cargo rigs: the two harness tiers and their primitive recipes (sprites: tools/build_cosy_items.py). */
    private void cargo() {
        vanillaModel("pack_harness", NS + ":item/pack_harness");
        vanillaModel("reinforced_harness", NS + ":item/reinforced_harness");
        json("data/" + NS + "/recipe/pack_harness", """
                {"type":"minecraft:crafting_shapeless","category":"equipment","group":"pack_harness",
                 "ingredients":["minecraft:leather","minecraft:leather","minecraft:leather","%s:plant_fiber","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:pack_harness"}}
                """.formatted(NS, NS, NS));
        json("data/" + NS + "/recipe/reinforced_harness", """
                {"type":"minecraft:crafting_shapeless","category":"equipment","group":"reinforced_harness",
                 "ingredients":["%s:pack_harness","minecraft:leather","minecraft:flint","%s:plant_fiber","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:reinforced_harness"}}
                """.formatted(NS, NS, NS, NS));
    }

    /** Authored camp stations and the four plantable berry bushes. */
    private void farm() {
        CampAssetsData.farm(this::put);
        put("data/" + NS + "/loot_table/blocks/drying_rack", PrimitiveData.lowerHalfLoot("drying_rack"));
        vanillaModel("dried_ration", NS + ":item/dried_ration");
        json("data/" + NS + "/recipe/drying_rack", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"drying_rack",
                 "ingredients":["minecraft:stick","minecraft:stick","minecraft:stick","minecraft:stick","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:drying_rack"}}
                """.formatted(NS, NS));
        tag("item/farm/trough_food", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry",
                "wheat", "wheat_seeds", "carrot", "potato", "beetroot", "#minecraft:meat", "#minecraft:fishes");
        tag("item/farm/drying_inputs", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry",
                "sweet_berries", "#minecraft:meat", "#minecraft:fishes");
        tag("block/farm/plantable_on", "dirt", "grass_block", "farmland", "coarse_dirt", "rooted_dirt", "podzol",
                "mycelium", "moss_block");
        // The four bushes share vanilla stage art; the berry field on the block decides what they yield.
        for (String id : List.of("tintoberry", "amarberry", "azulberry", "narcoberry")) {
            for (int age = 0; age < 4; age++) {
                put("assets/" + NS + "/models/block/" + id + "_bush_stage" + age, Map.of(
                        "parent", "minecraft:block/cross",
                        "textures", Map.of("cross", "minecraft:block/sweet_berry_bush_stage" + age)));
            }
            var stages = new LinkedHashMap<String, Object>();
            for (int age = 0; age < 4; age++) stages.put("age=" + age, Map.of("model", NS + ":block/" + id + "_bush_stage" + age));
            put("assets/" + NS + "/blockstates/" + id + "_bush", Map.of("variants", stages));
            json("data/" + NS + "/loot_table/blocks/" + id + "_bush", """
                {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
                 {"condition":"minecraft:block_state_property","block":"%s:%s_bush","properties":{"age":"3"}}],
                 "entries":[{"type":"minecraft:item","name":"%s:%s"}],
                 "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":2,"max":3}}]}]}
                """.formatted(NS, id, NS, id));
        }
    }

    private static Map<String, Object> boxWithFaces(double x, double y, double z, double xx, double yy, double zz,
            String side, String top, String bottom) {
        var faces = new LinkedHashMap<String, Object>();
        for (String direction : List.of("north", "south", "east", "west")) faces.put(direction, Map.of("texture", "#" + side));
        faces.put("up", Map.of("texture", "#" + top));
        faces.put("down", Map.of("texture", "#" + bottom));
        return Map.of("from", List.of(x, y, z), "to", List.of(xx, yy, zz), "faces", faces);
    }

    /** The campfire cooking pot and its two prepared meals. */
    private void kitchen() {
        CampAssetsData.pot(this::put);
        json("data/" + NS + "/loot_table/blocks/cooking_pot", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
             {"condition":"minecraft:survives_explosion"}],
             "entries":[{"type":"minecraft:item","name":"%s:cooking_pot"}]}]}
            """.formatted(NS));
        json("data/" + NS + "/recipe/cooking_pot", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"cooking_pot",
                 "ingredients":["minecraft:cobblestone","minecraft:cobblestone","minecraft:cobblestone",
                                "minecraft:cobblestone","%s:plant_fiber","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:cooking_pot"}}
                """.formatted(NS, NS, NS));
        vanillaModel("hearty_stew", NS + ":item/hearty_stew");
        vanillaModel("trail_mix", NS + ":item/trail_mix");
    }

    /** Narcotics come from the Mortar & Pestle (StationData); the Armoury makes both tranquilizer arrows. */
    private void medicine() {
        model("narcotics");
        vanillaModel("improved_tranquilizer_arrow", NS + ":item/improved_tranquilizer_arrow");
        // The recipe gates rename the vanilla Stick: the leaves drop it, and it is the twig of the spec.
        // Leather is the Hide a carcass gives (F31), and so is the armour sewn from it.
        put("assets/minecraft/lang/en_us", Map.of("item.minecraft.stick", "Twig", "item.minecraft.leather", "Hide",
                "item.minecraft.leather_helmet", "Hide Cap", "item.minecraft.leather_chestplate", "Hide Tunic",
                "item.minecraft.leather_leggings", "Hide Pants", "item.minecraft.leather_boots", "Hide Boots",
                "item.minecraft.leather_horse_armor", "Hide Horse Armor",
                // No advancement tab is loaded (C04): the pause menu's button and the L key open the Tribe Chronicle.
                "gui.advancements", "Tribe Chronicle", "key.advancements", "Tribe Chronicle"));
    }

    /** The downed state's two hidden discovery advancements. */
    private void downed() {
        json("data/" + NS + "/advancement/journal/first_downed", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        json("data/" + NS + "/advancement/journal/first_revive", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
    }

    private void vanillaModel(String id, String texture) {
        put("assets/" + NS + "/models/item/" + id, Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", texture)));
        put("assets/" + NS + "/items/" + id, Map.of("model", Map.of("type", "minecraft:model",
                "model", NS + ":item/" + id)));
    }

    private void campMessages(Map<String, String> en) {
        en.put("camp." + NS + ".starter_kit", "A survivor's kit: a bedroll, fiber bandages, plant fiber and a flint knife.");
        en.put("camp." + NS + ".bedroll_set", "Respawn point set. It stays here even if the bedroll is destroyed.");
        en.put("camp." + NS + ".rolled_up", "%s rolled up.");
        en.put("camp." + NS + ".bedroll_disabled", "Setting respawn with a bedroll is disabled on this server.");
        en.put("camp." + NS + ".bandaged", "Bandaged +%s health.");
    }


    private void downedMessages(Map<String, String> en) {
        en.put("downed." + NS + ".downed", "You are down! %s seconds until you bleed out; a tribe member can revive you with a fiber bandage.");
        en.put("downed." + NS + ".tribe_down", "%s is down at %s, %s, %s with %s seconds left.");
        en.put("downed." + NS + ".revived", "%s pulled you back up.");
        en.put("downed." + NS + ".revived_self", "You revived %s.");
        en.put("downed." + NS + ".not_tribe", "Only a tribe member can revive them.");
        en.put("downed." + NS + ".denied", "You cannot do that while down.");
        en.put("hud.downed." + NS + ".title", "DOWNED");
        en.put("hud.downed." + NS + ".hint", "A tribe member must use a fiber bandage");
        en.put("hud.downed." + NS + ".seconds", "%ss");
    }

    /** Carried-mass gauge and the warning/overload messages; the warning band itself has no penalty. */
    private void massMessages(Map<String, String> en) {
        en.put("hud." + NS + ".mass", "Load %s / %s");
        en.put("hud." + NS + ".mass.mount", "Mount %s / %s");
        en.put("hud." + NS + ".mass.warn", "[ARK] Heavy load: %s / %s");
        en.put("hud." + NS + ".mass.overload", "[ARK] Overburdened: sprint disabled and movement slowing.");
        en.put("hud." + NS + ".mass.heavy", "[ARK] Severely overburdened: movement heavily slowed.");
        en.put("hud." + NS + ".mass.eased", "[ARK] Load eased.");
        en.put("hud." + NS + ".overload.takeoff", "[ARK] The load is too heavy for takeoff.");
        en.put("hud." + NS + ".overload.descent", "[ARK] Overloaded: descending under control.");
        en.put("hud." + NS + ".overload.dive", "[ARK] Overloaded: this mount will not dive.");
        en.put("hud." + NS + ".overload.surface", "[ARK] Surfacing before the rider runs out of air.");
    }

    /** Work orders: the extra companion order, its permission denial and the job feedback. */
    private void workMessages(Map<String, String> en) {
        en.put("companion." + NS + ".order.work", "Your companion starts working here");
        en.put("taming." + NS + ".denied.work", "[ARK] Your tribe has not granted you work access to %s");
        en.put("work." + NS + ".full", "[ARK] %s's hold is full.");
        en.put("work." + NS + ".blocked", "[ARK] %s pauses: a permitted survivor must stay nearby.");
    }

    /** Homestead station and crop names. */
    private void farmMessages(Map<String, String> en) {
        en.put("block." + NS + ".trough", "Oak Feeding Trough");
        en.put("item." + NS + ".trough", "Oak Feeding Trough");
        String[][] troughNames = {{"spruce", "Spruce"}, {"birch", "Birch"},
                {"jungle", "Jungle"}, {"acacia", "Acacia"}, {"dark_oak", "Dark Oak"},
                {"mangrove", "Mangrove"}, {"cherry", "Cherry"}, {"pale_oak", "Pale Oak"},
                {"bamboo", "Bamboo"}};
        for (String[] wood : troughNames) {
            for (String kind : List.of("block.", "item.")) {
                en.put(kind + NS + "." + wood[0] + "_trough", wood[1] + " Feeding Trough");
            }
        }
        en.put("kitchen." + NS + ".heated", "Fire lit");
        en.put("kitchen." + NS + ".needs_fire", "Needs a lit fire below");
        en.put("block." + NS + ".drying_rack", "Drying Rack");
        en.put("item." + NS + ".drying_rack", "Drying Rack");
        en.put("item." + NS + ".dried_ration", "Dried Ration");
        en.put("block." + NS + ".tintoberry_bush", "Redberry Bush");
        en.put("block." + NS + ".amarberry_bush", "Yellowberry Bush");
        en.put("block." + NS + ".azulberry_bush", "Blueberry Bush");
        en.put("block." + NS + ".narcoberry_bush", "Blackberry Bush");
        en.put("item." + NS + ".narcotics", "Narcotics");
        en.put("item." + NS + ".improved_tranquilizer_arrow", "Improved Tranquilizer Arrow");
    }

    /** Kitchen names: the pot and the two prepared meals. */
    private void kitchenMessages(Map<String, String> en) {
        en.put("block." + NS + ".cooking_pot", "Cooking Pot");
        en.put("item." + NS + ".cooking_pot", "Cooking Pot");
        en.put("item." + NS + ".hearty_stew", "Hearty Stew");
        en.put("item." + NS + ".trail_mix", "Trail Mix");
    }

    private static Map<String, Object> nestBox(double x, double y, double z, double xx, double yy, double zz, String texture) {
        var faces = new LinkedHashMap<String, Object>();
        for (String side : List.of("north", "south", "east", "west", "up", "down")) faces.put(side, Map.of("texture", "#"+texture));
        return Map.of("from", List.of(x,y,z), "to", List.of(xx,yy,zz), "faces", faces);
    }

    /** A twig tilted about its own middle, one end resting on the rim. */
    private static Map<String, Object> nestStick(double x, double y, double z, double xx, double yy, double zz, String axis, double angle) {
        var stick = new LinkedHashMap<>(nestBox(x, y, z, xx, yy, zz, "rim"));
        stick.put("rotation", Map.of("origin", List.of((x + xx) / 2, (y + yy) / 2, (z + zz) / 2), "axis", axis, "angle", angle));
        return stick;
    }

    /** An egg standing in the lining: a narrow foot, a body of the given height, a shoulder and a crown. */
    private static List<Map<String, Object>> nestEgg(double cx, double cz, double width, double body) {
        double r = width / 2;
        return List.of(nestBox(cx - r + 0.5, 1, cz - r + 0.5, cx + r - 0.5, 1.75, cz + r - 0.5, "egg"),
                nestBox(cx - r, 1.75, cz - r, cx + r, 1.75 + body, cz + r, "egg"),
                nestBox(cx - r + 0.5, 1.75 + body, cz - r + 0.5, cx + r - 0.5, 2.75 + body, cz + r - 0.5, "egg"),
                nestBox(cx - r + 1, 2.75 + body, cz - r + 1, cx + r - 1, 3.25 + body, cz + r - 1, "egg"));
    }
    /** The twigs a species builds with (tools/build_nest_blocks.py NEST_WOOD keeps the same table). */
    private static String nestWood(Species species) {
        return switch (species) {
            case PTERANODON -> "drift";
            case ARGENTAVIS -> "spruce";
            case ARCHAEOPTERYX -> "mossy";
            case DRAGON -> "charred";
            default -> "oak";
        };
    }
    private void flying() {
        for (var species : Species.values()) {
            if (!species.flyer()) continue;
            String id = species.id + "_nest";
            // Own twig, lining and shell textures (tools/build_nest_blocks.py, F21).
            String rim = NS + ":block/nest/twigs_" + nestWood(species);
            var textures = Map.of("rim", rim, "lining", NS + ":block/nest/" + (species == Species.DRAGON ? "lining_ash" : "lining"),
                    "leaf", "minecraft:block/moss_block", "egg", NS + ":block/nest/egg_" + species.id, "particle", rim);
            var bowl = new ArrayList<Map<String,Object>>();
            bowl.add(nestBox(2,0,2,14,1,14,"lining"));
            bowl.add(nestBox(1,0,1,15,2.5,3,"rim")); bowl.add(nestBox(1,0,13,15,2.5,15,"rim"));
            bowl.add(nestBox(1,0,3,3,2.5,13,"rim")); bowl.add(nestBox(13,0,3,15,2.5,13,"rim"));
            // An uneven top, and a loose stick laid aslant along each side.
            bowl.add(nestBox(2,2.5,1,8,3.5,3,"rim")); bowl.add(nestBox(9,2.5,13,14,3.25,15,"rim"));
            bowl.add(nestBox(1,2.5,4,3,3.25,10,"rim")); bowl.add(nestBox(13,2.5,6,15,3.5,12,"rim"));
            bowl.add(nestStick(3,2.75,1.6,13,3.5,2.4,"z",22.5)); bowl.add(nestStick(3,2.5,13.6,13,3.25,14.4,"z",-22.5));
            bowl.add(nestStick(1.6,2.5,3,2.4,3.25,13,"x",-22.5)); bowl.add(nestStick(13.6,2.75,3,14.4,3.5,13,"x",22.5));
            if (species != Species.PTERANODON && species != Species.DRAGON) {
                // Short staggered fronds over a twig ring; all geometry remains within its block.
                for (int i=0;i<3;i++) {
                    bowl.add(nestBox(2+i*3,2,1,4+i*3,3.5,4,"leaf"));
                    bowl.add(nestBox(4+i*3,2,12,6+i*3,3.5,15,"leaf"));
                }
            }
            put("assets/"+NS+"/models/block/"+id+"_empty", Map.of("textures",textures,"elements",bowl));
            var full = new ArrayList<>(bowl);
            if (species == Species.ARCHAEOPTERYX) { full.addAll(nestEgg(6.5, 7.5, 3, 1)); full.addAll(nestEgg(9.5, 8.5, 3, 1)); }
            else if (species == Species.DRAGON) full.addAll(nestEgg(8, 8, 5, 3));
            else full.addAll(nestEgg(8, 8, 4, species == Species.PTERANODON ? 3.5 : 2.5));
            put("assets/"+NS+"/models/block/"+id, Map.of("textures",textures,"elements",full));
            put("assets/"+NS+"/blockstates/"+id, Map.of("variants",Map.of("egg=true",Map.of("model",NS+":block/"+id),"egg=false",Map.of("model",NS+":block/"+id+"_empty"))));
            put("assets/"+NS+"/models/item/"+species.id+"_egg",Map.of("parent","minecraft:item/generated","textures",Map.of("layer0",NS+":item/"+species.id+"_egg")));
            put("assets/"+NS+"/items/"+species.id+"_egg",Map.of("model",Map.of("type","minecraft:model","model",NS+":item/"+species.id+"_egg")));
            json("data/"+NS+"/loot_table/blocks/"+id, """
                {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
                 {"condition":"minecraft:survives_explosion"},
                 {"condition":"minecraft:block_state_property","block":"%s:%s","properties":{"egg":"true"}}],
                 "entries":[{"type":"minecraft:item","name":"%s:%s_egg"}]}]}
                """.formatted(NS,id,NS,species.id));
        }
    }
    /**
     * Vanilla-style biome spawn lists. The compiled weight and group size are baked here; the
     * runtime placement predicate still applies the danger/level gate.
     */
    /** First Guardian encounter text: the item names, the bar and every activation outcome. */
    private void guardianMessages(Map<String, String> en) {
        en.put("item." + NS + ".allosaur_heart", "Allosaur Heart");
        en.put("item." + NS + ".workshop_schematic", "Workshop Schematic");
        en.put("item." + NS + ".guardian_trophy", "Guardian Trophy");
        en.put("tooltip." + NS + ".allosaur_heart",
                "Encounter key. Offer it at a block beneath an Ancient Remnants monolith to summon the First Guardian.");
        en.put("tooltip." + NS + ".workshop_schematic",
                "Proof of the First Guardian's defeat. The tribe's workshop research is already recorded; this is a memento.");
        en.put("tooltip." + NS + ".guardian_trophy", "Trophy taken from a Beacon Guardian.");
        en.put("guardian." + NS + ".name", "Guardian Giganotosaurus");
        en.put("guardian." + NS + ".bar", "%s | %s/%s");
        String[] keys = {"requires_mod", "no_monolith", "active", "resetting", "downed", "chunks", "no_room",
                "failed", "awakened", "reawakened", "retreated", "victory", "status_line", "status_health",
                "status_none", "command.reset", "command.clear", "command.grant", "claimed"};
        String[] english = {
                "[ARK] The First Guardian sleeps inside Ancient Remnants monoliths. Install the mod to continue this hunt.",
                "[ARK] Offer the heart at a block directly beneath the monolith.",
                "[ARK] A Guardian already owns this monolith.",
                "[ARK] The monolith is still calming. The rite resumes shortly.",
                "[ARK] You cannot perform the rite while downed.",
                "[ARK] The arena is not fully loaded yet.",
                "[ARK] There is no clear ground for the Guardian to rise here.",
                "[ARK] The rite failed; your heart is untouched.",
                "[ARK] The Allosaur Heart burns away. The Guardian awakens!",
                "[ARK] The rite flares again. The Guardian returns!",
                "[ARK] The Guardian loses interest and returns to its slumber. The rite remains unlocked.",
                "[ARK] The First Guardian falls. The tribe earns the Workshop Schematic.",
                "[ARK] Guardian %s at %s, %s, %s",
                "| %s/%s HP",
                "[ARK] No guardian encounter is recorded for your tribe.",
                "[ARK] The nearest encounter was reset to a free retry.",
                "[ARK] The nearest encounter record was removed.",
                "[ARK] Granted the Workshop Schematic to %s.",
                "[ARK] Another tribe paid for this rite. Only they can wake the Guardian again."};
        for (int i = 0; i < keys.length; i++) {
            en.put("guardian." + NS + "." + keys[i], english[i]);
        }
    }

    /** Technology tree text: completion and gate announcements plus the /arktech feedback. */
    private void techMessages(Map<String, String> en) {
        en.put("key." + NS + ".tech_tree", "Open Tribe Chronicle");
        en.put("key.categories." + NS + ".progression", "Ark Progression");
        String[] uiKeys = {"title", "pan", "quests", "loading", "unavailable", "locked", "available", "complete", "shared", "local"};
        String[] uiEn = {"TRIBE CHRONICLE", "Wheel / drag / arrows", "Quest book", "Opening the chronicle...", "The chronicle is unavailable.",
                "Complete the earlier steps", "Ready to pursue", "Complete", "Shared journal", "Quest link unavailable"};
        for (int i = 0; i < uiKeys.length; i++) {
            en.put("tech.gui." + uiKeys[i], uiEn[i]);
        }
        en.put("tech." + NS + ".completed", "[ARK] Technology unlocked: %s");
        en.put("tech." + NS + ".gate", "[ARK] The gate %s opens a new age.");
        en.put("tech." + NS + ".unavailable", "[ARK] The technology tree is not loaded.");
        en.put("tech." + NS + ".status_total", "[ARK] Technology: %s/%s nodes complete.");
        en.put("tech." + NS + ".status_age", "[ARK] %s: %s/%s");
        en.put("tech." + NS + ".status_available", "[ARK] Available: %s - %s");
        en.put("tech." + NS + ".command.unknown", "[ARK] Unknown technology node: %s");
        en.put("tech." + NS + ".command.unlock", "[ARK] Unlocked technology node %s.");
        en.put("tech." + NS + ".command.reset", "[ARK] Technology progress reset for your tribe.");
        en.put("tech." + NS + ".command.reset_empty", "[ARK] Your tribe has no technology progress to reset.");
    }

    /**
     * Ark creatures are in no vanilla spawn list: the population budget is the only thing that places them, so
     * its spacing, ranges and group caps hold everywhere, chunk generation included.
     */
    private void spawns() {}
    private void tests() {
        put("data/" + NS + "/test_environment/regression", Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false)));
        put("data/" + NS + "/test_environment/regression_navigation", Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false)));
        put("data/" + NS + "/test_environment/regression_flying", Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false)));
        put("data/" + NS + "/test_environment/wildlife_regression", Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false)));
        // A fixed morning hour inside a grazer's feeding time, so the calm routine is the same on every run.
        put("data/" + NS + "/test_environment/wildlife_morning", Map.of("type", "minecraft:all_of", "definitions", List.of(
                Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false, "minecraft:advance_time", false)),
                Map.of("type", "minecraft:clock_time", "clock", "minecraft:overworld", "time", 2000))));
        for (String name : List.of("wildlife_grazing_day", "wildlife_body_weight", "reaction_mind_rules", "reaction_creative_hit", "reaction_apex_mobbed", "reaction_herd_glance"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":wildlife_morning", "structure", NS + ":test_population", "max_ticks", 600, "sky_access", true));
        for (String name : List.of("wildlife_packet_hearing", "wildlife_partial_cover", "wildlife_home_recovery",
                "wildlife_navigation_turn", "wildlife_water_navigation", "wildlife_wide_pursuit",
                "wildlife_tree_pursuit", "wildlife_log_wall", "wildlife_slope_escape", "wildlife_far_bank", "wildlife_dry_range",
                "wildlife_downhill_bank", "wildlife_bank_arrival", "wildlife_giant_path"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":wildlife_regression", "structure", NS + ":test_population",
                    "max_ticks", name.equals("wildlife_dry_range") ? 1000 : 500, "sky_access", true));
        for (String name : List.of("pack_runtime_contract", "tech_real_event_flow", "tech_lighting_flow", "progression_restart", "tribe_party_flow",
                "flying_ground_pteranodon", "flying_ground_argentavis", "companion_live_navigation", "companion_blocked_route", "work_live_harvest",
                "cargo_request_guards", "cargo_storage_guards", "cargo_scan_limits", "cargo_unloaded_boundary", "cargo_toms_network"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + (name.startsWith("companion_") || name.equals("work_live_harvest") ? ":regression_navigation"
                            : name.startsWith("flying_ground_") ? ":regression_flying" : ":regression"), "structure", NS + ":test_population", "max_ticks", 400, "sky_access", true));
        // The recorder test follows one scripted encounter for 260 ticks under its own day clock, so it runs alone. Its
        // real-time deadline check lasts 300 ms, however many ticks the test server fits into that.
        put("data/" + NS + "/test_environment/session_recorder", Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", false)));
        put("data/" + NS + "/test_instance/session_recorder", Map.of("type", "minecraft:function", "function", NS + ":session_recorder",
                "environment", NS + ":session_recorder", "structure", NS + ":test_population", "max_ticks", 12000, "sky_access", true));
        for (String name : List.of("sky_beacon_assets", "sky_beacon_placement", "sky_beacon_persistence", "sky_beacon_rewards"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 100, "sky_access", true));
        for (String name : List.of("supply_drop_loot", "supply_drop_landing", "supply_drop_expiry", "supply_drop_aim"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 200, "sky_access", true));
        var spawningRules = Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", true));
        put("data/" + NS + "/test_environment/empty", spawningRules);
        put("data/" + NS + "/test_environment/collection", spawningRules);
        put("data/" + NS + "/test_environment/player_levels", spawningRules);
        for (String name : List.of("player_levels", "player_levels_persistence", "player_levels_sources", "player_levels_share", "player_levels_commands"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":player_levels", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
        for (String name : List.of("levels_persist", "packs_and_damage", "spawn_rules", "grass_berries", "progression", "behavior", "combat_timing", "creature_expansion", "mass_load", "cargo_load", "cargo_transfer", "cargo_death_and_cull", "overload_flight", "overload_swim", "work_harvest", "farm_batch", "medicine_dose", "kitchen_cook"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_instance/cargo_reach", Map.of("type", "minecraft:function", "function", NS + ":cargo_reach",
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_environment/population", spawningRules);
        // Guardian tests own a small batch: the encounter spawns an oversized boss and must not
        // contend with the timing-sensitive combat suite in the shared empty batch.
        put("data/" + NS + "/test_environment/guardian", spawningRules);
        put("data/" + NS + "/test_environment/tech", spawningRules);
        put("data/" + NS + "/test_instance/population", Map.of("type", "minecraft:function", "function", NS + ":population",
                "environment", NS + ":population", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("flying_ecology", "flying_pteranodon", "flying_argentavis", "land_ecology", "land_movement", "land_vigilance"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 500, "sky_access", true));
        // The collection tests own their own batch: they share the per-dimension terrain budgets with
        // the land and flying suites, and running them apart keeps those budgets predictable.
        for (String name : List.of("aquatic_ecology", "collection_registration", "collection_cold"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                "environment", NS + ":collection", "structure", NS + ":test_population", "max_ticks", 500, "sky_access", true));
        put("data/" + NS + "/test_instance/nighttime", Map.of("type", "minecraft:function", "function", NS + ":nighttime",
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        put("data/" + NS + "/test_instance/debug_spyglass", Map.of("type", "minecraft:function", "function", NS + ":debug_spyglass",
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_instance/theme_alignment", Map.of("type", "minecraft:function", "function", NS + ":theme_alignment",
                "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 200, "sky_access", true));
        // Taming: the roster and rule checks are quick, the riding and feeding suites own the large plot.
        for (String name : List.of("taming_roster", "taming_torpor", "taming_passive_feeding",
                "taming_knockout_feeding", "taming_wake_before_completion", "taming_persistence",
                "taming_player_sedation", "taming_aerial_feeding", "taming_completion",
                "taming_claim_expiry", "taming_ordinary_mob", "companion", "tribe_permissions", "journal_pack",
                "journal_taming_unlock", "camp_starter_kit", "camp_bedroll_spawn", "camp_mattress_sleep",
                "downed_revive", "downed_bleed_out", "downed_mitigated"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":empty",
                    "structure", NS + ":test_population", "max_ticks", 400, "sky_access", true));
        put("data/" + NS + "/test_instance/taming_riding", Map.of("type", "minecraft:function",
                "function", NS + ":taming_riding", "environment", NS + ":empty",
                "structure", NS + ":test_population", "max_ticks", 400, "sky_access", true));
        put("data/" + NS + "/test_instance/spawn_pipeline", Map.of("type", "minecraft:function",
                "function", NS + ":spawn_pipeline", "environment", NS + ":empty",
                "structure", NS + ":test_empty", "max_ticks", 200, "sky_access", true));
        put("data/" + NS + "/test_instance/spawn_budget", Map.of("type", "minecraft:function",
                "function", NS + ":spawn_budget", "environment", NS + ":population",
                "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
        put("data/" + NS + "/test_instance/spawn_apex", Map.of("type", "minecraft:function",
                "function", NS + ":spawn_apex", "environment", NS + ":empty",
                "structure", NS + ":test_population", "max_ticks", 400, "sky_access", true));
        for (String name : List.of("spawn_ledger_density", "spawn_ledger_feedback", "spawn_ranges", "spawn_sight", "spawn_register", "spawn_biome", "spawn_silent"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":empty",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
        for (String name : List.of("guardian_heart", "guardian_policy", "guardian_registration",
                "guardian_persistence", "guardian_rewards", "guardian_hit_and_save", "guardian_orphan"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":guardian",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
        for (String name : List.of("primitive_rocks", "primitive_fire", "primitive_forge", "primitive_curing", "primitive_gates",
                "primitive_tall_stations", "primitive_keratin", "stations", "station_guards", "recipe_decisions",
                "integration_curios", "integration_toms_storage", "integration_terralith", "integration_better_combat"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":empty",
                    "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("sulphur_growth", "sulphur_cluster_drop", "sulphur_worldgen", "crusher_gunpowder", "explosive_arrow_blast"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("tech_tree", "tech_progress", "tech_triggers", "tech_flow", "tech_future",
                "tech_bronze_wiring", "tech_bronze_flow"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":tech",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
        for (String name : List.of("bronze_tin_ore", "bronze_smelting", "bronze_recipes", "bronze_armor", "bronze_weapons"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":empty",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
    }
}
