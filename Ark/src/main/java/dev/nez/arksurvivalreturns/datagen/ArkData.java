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
        terralith = loadTerralith();
        tags(); models(); berries(); taming(); journal(); camp(); cargo(); farm(); medicine(); kitchen(); downed(); flying(); spawns(); theme(); tests();
        PrimitiveData.generate(this::put);
        IntegrationData.generate(this::put);
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
    /** Terralith biome -> closest vanilla analog (config/integrations/terralith-biomes.json, tools/build_biome_compat.py). */
    private Map<String, String> terralith = Map.of();

    private Map<String, String> loadTerralith() {
        var path = output.getOutputFolder().getParent().getParent().getParent().resolve("config/integrations/terralith-biomes.json");
        try {
            var root = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().getAsJsonObject("terralith");
            var out = new TreeMap<String, String>();
            root.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsJsonObject().get("analog").getAsString()));
            return out;
        } catch (java.io.IOException | RuntimeException missing) {
            return Map.of();
        }
    }

    /**
     * Biome tags list vanilla biomes, plus every Terralith biome whose analog is listed. Terralith entries
     * are optional, so the tag still loads without Terralith installed.
     */
    private void biomeTag(String name, String... values) {
        var entries = new ArrayList<Object>();
        var vanilla = new HashSet<String>();
        for (String value : values) {
            String id = value.contains(":") ? value : "minecraft:" + value;
            entries.add(id);
            vanilla.add(id);
        }
        terralith.forEach((biome, analog) -> { if (vanilla.contains(analog)) entries.add(Map.of("id", biome, "required", false)); });
        put("data/" + NS + "/tags/worldgen/biome/" + name, Map.of("replace", false, "values", entries));
    }
    private void tag(String name, String... values) {
        put("data/" + NS + "/tags/" + name, Map.of("replace", false, "values",
                Arrays.stream(values).map(v -> v.contains(":") ? v : "minecraft:" + v).toList()));
    }
    /** Snow-covered land: the only home of the cold species, and closed to the warm ones. */
    private static final String[] COLD_BIOMES = {"snowy_plains", "ice_spikes", "snowy_taiga", "snowy_beach", "grove",
            "snowy_slopes", "frozen_peaks", "jagged_peaks", "frozen_river"};
    /** Every other surface biome with natural ground; mushroom fields stay a wildlife-free refuge. */
    private static final String[] TEMPERATE_BIOMES = {"plains", "sunflower_plains", "meadow", "cherry_grove", "forest",
            "flower_forest", "birch_forest", "old_growth_birch_forest", "dark_forest", "taiga", "old_growth_pine_taiga",
            "old_growth_spruce_taiga", "savanna", "savanna_plateau", "windswept_savanna", "windswept_hills",
            "windswept_gravelly_hills", "windswept_forest", "stony_peaks", "jungle", "sparse_jungle", "bamboo_jungle",
            "swamp", "mangrove_swamp", "river", "beach", "stony_shore", "desert", "badlands", "wooded_badlands",
            "eroded_badlands"};

    private void tags() {
        // Habitats replace the old per-species biome lists and the biome difficulty tiers: danger and levels
        // come from the area alone, so a species appears wherever its habitat and the local danger allow.
        biomeTag("habitat/" + Species.Habitat.TEMPERATE.id, TEMPERATE_BIOMES);
        biomeTag("habitat/" + Species.Habitat.WETLAND.id, "swamp", "mangrove_swamp", "river", "jungle", "sparse_jungle", "bamboo_jungle");
        biomeTag("habitat/" + Species.Habitat.COLD.id, COLD_BIOMES);
        biomeTag("habitat/" + Species.Habitat.SEA.id, "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean",
                "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean");
        var land = new ArrayList<>(List.of(TEMPERATE_BIOMES));
        land.addAll(List.of(COLD_BIOMES));
        biomeTag("habitat/" + Species.Habitat.SKY.id, land.toArray(String[]::new));
        // Each species keeps its own tag so a data pack can still narrow or widen one species.
        for (var species : Species.values())
            tag("worldgen/biome/spawns/" + species.id, "#" + NS + ":habitat/" + species.habitat().id);
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
                NS + ":plant_fiber", NS + ":fiber_bandage", NS + ":companion_whistle");
        tag("item/mass/bulk", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry",
                NS + ":field_journal", "sweet_berries", "glow_berries", "egg", "snowball", "paper",
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
        Map<String, String> en = new TreeMap<>(), pt = new TreeMap<>();
        en.put("item." + NS + ".debug_spyglass", "Debug Spyglass");
        pt.put("item." + NS + ".debug_spyglass", "Luneta de depura\u00e7\u00e3o");
        String[] debugKeys = {"title", "searching", "aim", "page", "range"};
        String[] debugEn = {"ARK // DINO INSPECTOR", "SCANNING...", "Aim at a dinosaur to read its state.", "PAGE %s/%s | Mouse wheel to browse", "RANGE %s blocks | Hold use to scan"};
        String[] debugPt = {"ARK // INSPETOR DE DINOS", "BUSCANDO...", "Mire em um dinossauro para ler seu estado.", "P\u00c1GINA %s/%s | Role para navegar", "ALCANCE %s blocos | Segure usar"};
        for (int i = 0; i < debugKeys.length; i++) {
            en.put("debug." + NS + "." + debugKeys[i], debugEn[i]);
            pt.put("debug." + NS + "." + debugKeys[i], debugPt[i]);
        }
        // Preserve the vanilla scope's separate inventory and held models.
        put("assets/" + NS + "/items/debug_spyglass", Map.of("model", Map.of("type", "minecraft:select",
                "property", "minecraft:display_context", "cases", List.of(Map.of("when", List.of("gui", "ground", "fixed", "on_shelf"),
                        "model", Map.of("type", "minecraft:model", "model", "minecraft:item/spyglass"))),
                "fallback", Map.of("type", "minecraft:model", "model", "minecraft:item/spyglass_in_hand"))));
        en.put("itemGroup." + NS, "Ark Survival Returns"); pt.put("itemGroup." + NS, "Ark Survival Returns");
        en.put("map." + NS + ".filter_on", "Difficulty: on"); en.put("map." + NS + ".filter_off", "Difficulty: off");
        pt.put("map." + NS + ".filter_on", "Dificuldade: ligada"); pt.put("map." + NS + ".filter_off", "Dificuldade: desligada");
        String[] mapKeys = {"locked", "locked_short", "unlocked", "status", "legend", "unrated", "cursor", "rank_1", "rank_2", "rank_3", "rank_4", "rank_5"};
        String[] mapEn = {"[ARK] Map locked. Requires taming a creature from a danger-5 region.", "Locked", "Unlocked", "%s: map %s", "Region difficulty", "Unrated dimension", "At cursor: %s/5", "1 - Easy", "2 - Moderate", "3 - Hard", "4 - Severe", "5 - Extreme"};
        String[] mapPt = {"[ARK] Mapa bloqueado. Requer domar uma criatura de uma regi\u00e3o de perigo 5.", "Bloqueado", "Desbloqueado", "%s: mapa %s", "Dificuldade regional", "Dimens\u00e3o sem classifica\u00e7\u00e3o", "No cursor: %s/5", "1 - F\u00e1cil", "2 - Moderada", "3 - Dif\u00edcil", "4 - Severa", "5 - Extrema"};
        for (int i = 0; i < mapKeys.length; i++) {
            en.put("map." + NS + "." + mapKeys[i], mapEn[i]); pt.put("map." + NS + "." + mapKeys[i], mapPt[i]);
        }
        en.put("hud." + NS + ".creature", "%s | Lv. %s | %s / %s HP"); pt.put("hud." + NS + ".creature", "%s | Nv. %s | %s / %s PV");
        en.put("screen." + NS + ".taming", "Taming %s"); pt.put("screen." + NS + ".taming", "Domestica\u00e7\u00e3o %s");
        en.put("screen." + NS + ".hunger", "Hunger %s"); pt.put("screen." + NS + ".hunger", "Fome %s");
        en.put("screen." + NS + ".torpor", "Torpor %s/%s"); pt.put("screen." + NS + ".torpor", "Torpor %s/%s");
        en.put("chat." + NS + ".biome", "[ARK] %s | Danger %s/5 | Wild levels %s-%s");
        pt.put("chat." + NS + ".biome", "[ARK] %s | Perigo %s/5 | N\u00edveis selvagens %s-%s");
        en.put("chat." + NS + ".biome_unrated", "[ARK] %s | Outside Overworld danger zones");
        String[] states = {"roam", "forage", "drink", "rest", "alert", "investigate", "threaten", "hunt", "defend", "flee", "return_home", "feed"};
        String[] stateEn = {"Roaming", "Foraging", "Drinking", "Resting", "Alert", "Investigating", "Warning", "Hunting", "Defending", "Fleeing", "Returning home", "Feeding"};
        String[] statePt = {"Vagando", "Pastando", "Bebendo", "Descansando", "Alerta", "Investigando", "Amea\u00e7ando", "Ca\u00e7ando", "Defendendo", "Fugindo", "Voltando para casa", "Comendo"};
        for (int i = 0; i < states.length; i++) {
            en.put("behavior." + NS + "." + states[i], stateEn[i]); pt.put("behavior." + NS + "." + states[i], statePt[i]);
        }
        en.put("behavior." + NS + ".seek_water", "Seeking water"); pt.put("behavior." + NS + ".seek_water", "Procurando \u00e1gua");
        en.put("behavior." + NS + ".sleep", "Sleeping"); pt.put("behavior." + NS + ".sleep", "Dormindo");
        en.put("behavior." + NS + ".search", "Searching for prey"); pt.put("behavior." + NS + ".search", "Procurando presas");
        en.put("behavior." + NS + ".regroup", "Regrouping"); pt.put("behavior." + NS + ".regroup", "Reagrupando");
        // Body actions follow the state on the target bar ("Hunting / Stalking"); a missing label fails datagen.
        var actions = new EnumMap<BehaviorAction, String[]>(BehaviorAction.class);
        actions.put(BehaviorAction.IDLE, new String[]{"Standing", "Parado"});
        actions.put(BehaviorAction.WALK, new String[]{"Walking", "Andando"});
        actions.put(BehaviorAction.RUN, new String[]{"Running", "Correndo"});
        actions.put(BehaviorAction.TURN, new String[]{"Turning", "Virando"});
        actions.put(BehaviorAction.LOOK, new String[]{"Looking around", "Olhando em volta"});
        actions.put(BehaviorAction.SNIFF, new String[]{"Sniffing", "Farejando"});
        actions.put(BehaviorAction.POOP, new String[]{"Pooping", "Fazendo coc\u00f4"});
        actions.put(BehaviorAction.GRAZE, new String[]{"Grazing", "Pastando"});
        actions.put(BehaviorAction.DRINK, new String[]{"Drinking", "Bebendo"});
        actions.put(BehaviorAction.NOTICE, new String[]{"Noticing", "Percebendo"});
        actions.put(BehaviorAction.ROAR, new String[]{"Roaring", "Rugindo"});
        actions.put(BehaviorAction.THREAT, new String[]{"Threat display", "Intimidando"});
        actions.put(BehaviorAction.STARTLE, new String[]{"Startled", "Assustado"});
        actions.put(BehaviorAction.STALK, new String[]{"Stalking", "Espreitando"});
        actions.put(BehaviorAction.CHASE, new String[]{"Chasing", "Perseguindo"});
        actions.put(BehaviorAction.BOLT, new String[]{"Bolting", "Em disparada"});
        actions.put(BehaviorAction.FEED, new String[]{"Eating", "Comendo"});
        actions.put(BehaviorAction.SETTLE, new String[]{"Settling down", "Deitando"});
        actions.put(BehaviorAction.SLEEP, new String[]{"Asleep", "Dormindo"});
        actions.put(BehaviorAction.WAKE, new String[]{"Waking up", "Acordando"});
        actions.put(BehaviorAction.REST, new String[]{"Resting", "Descansando"});
        for (var action : BehaviorAction.values()) {
            var label = Objects.requireNonNull(actions.get(action), () -> "Missing action label: " + action);
            en.put(action.key(), label[0]); pt.put(action.key(), label[1]);
        }
        pt.put("chat." + NS + ".biome_unrated", "[ARK] %s | Fora das zonas de perigo do mundo normal");
        String[] ids = {"tintoberry", "amarberry", "azulberry", "narcoberry"};
        String[] names = {"Tintoberry", "Amarberry", "Azulberry", "Narcoberry (Sedative)"};
        String[] portuguese = {"Tintoberry", "Amarberry", "Azulberry", "Narcoberry (Sedativa)"};
        for (int i = 0; i < ids.length; i++) {
            model(ids[i]); en.put("item." + NS + "." + ids[i], names[i]); pt.put("item." + NS + "." + ids[i], portuguese[i]);
        }
        // Tranquilizer arrow: reuses the vanilla arrow art, so no new texture is required.
        put("assets/" + NS + "/models/item/tranquilizer_arrow", Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", "minecraft:item/arrow")));
        put("assets/" + NS + "/items/tranquilizer_arrow", Map.of("model", Map.of("type", "minecraft:model",
                "model", NS + ":item/tranquilizer_arrow")));
        en.put("item." + NS + ".tranquilizer_arrow", "Tranquilizer Arrow");
        pt.put("item." + NS + ".tranquilizer_arrow", "Flecha tranquilizante");
        // Companion whistle: reuses the vanilla goat horn art, so no new texture is required.
        put("assets/" + NS + "/models/item/companion_whistle", Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", "minecraft:item/goat_horn")));
        put("assets/" + NS + "/items/companion_whistle", Map.of("model", Map.of("type", "minecraft:model",
                "model", NS + ":item/companion_whistle")));
        en.put("item." + NS + ".companion_whistle", "Companion Whistle");
        pt.put("item." + NS + ".companion_whistle", "Apito de companheiro");
        en.put("companion." + NS + ".order.follow", "Your companion follows you");
        pt.put("companion." + NS + ".order.follow", "Seu companheiro segue voc\u00ea");
        en.put("companion." + NS + ".order.stay", "Your companion stays here");
        pt.put("companion." + NS + ".order.stay", "Seu companheiro fica aqui");
        en.put("companion." + NS + ".order.wander", "Your companion wanders nearby");
        pt.put("companion." + NS + ".order.wander", "Seu companheiro vaga por perto");
        // Field Journal: reuses the vanilla book art, so no new texture is required.
        put("assets/" + NS + "/models/item/field_journal", Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", "minecraft:item/book")));
        put("assets/" + NS + "/items/field_journal", Map.of("model", Map.of("type", "minecraft:model",
                "model", NS + ":item/field_journal")));
        en.put("item." + NS + ".field_journal", "Field Journal");
        pt.put("item." + NS + ".field_journal", "Di\u00e1rio de campo");
        en.put("item." + NS + ".plant_fiber", "Plant Fiber");
        pt.put("item." + NS + ".plant_fiber", "Fibra vegetal");
        en.put("item." + NS + ".pack_harness", "Pack Harness");
        pt.put("item." + NS + ".pack_harness", "Arn\u00eas de carga");
        en.put("item." + NS + ".reinforced_harness", "Reinforced Harness");
        pt.put("item." + NS + ".reinforced_harness", "Arn\u00eas refor\u00e7ado");
        en.put("screen." + NS + ".cargo", "Cargo %s / %s");
        pt.put("screen." + NS + ".cargo", "Carga %s / %s");
        en.put("screen." + NS + ".unload", "Unload");
        pt.put("screen." + NS + ".unload", "Descarregar");
        en.put("screen." + NS + ".load", "Load");
        pt.put("screen." + NS + ".load", "Carregar");
        en.put("cargo." + NS + ".moved", "[ARK] Moved %s items.");
        pt.put("cargo." + NS + ".moved", "[ARK] %s itens movidos.");
        en.put("cargo." + NS + ".none", "[ARK] No nearby storage accepted the transfer.");
        pt.put("cargo." + NS + ".none", "[ARK] Nenhum armazenamento pr\u00f3ximo aceitou a transfer\u00eancia.");
        en.put("item." + NS + ".fiber_bandage", "Fiber Bandage");
        pt.put("item." + NS + ".fiber_bandage", "Bandagem de fibra");
        en.put("item." + NS + ".flint_knife", "Flint Knife");
        pt.put("item." + NS + ".flint_knife", "Faca de s\u00edlex");
        en.put("block." + NS + ".bedroll", "Primitive Bedroll");
        pt.put("block." + NS + ".bedroll", "Rolo de dormir primitivo");
        en.put("item." + NS + ".bedroll", "Primitive Bedroll");
        pt.put("item." + NS + ".bedroll", "Rolo de dormir primitivo");
        en.put("key." + NS + ".journal", "Open Field Journal");
        pt.put("key." + NS + ".journal", "Abrir di\u00e1rio de campo");
        en.put("key.category." + NS + ".keys", "Ark Survival Returns");
        pt.put("key.category." + NS + ".keys", "Ark Survival Returns");
        tamingMessages(en, pt);
        tribeMessages(en, pt);
        campMessages(en, pt);
        downedMessages(en, pt);
        massMessages(en, pt);
        workMessages(en, pt);
        farmMessages(en, pt);
        kitchenMessages(en, pt);
        guardianMessages(en, pt);
        techMessages(en, pt);
        for (Species s : Species.values()) {
            model(s.id + "_spawn_egg");
            en.put("entity." + NS + "." + s.id, s.displayName); pt.put("entity." + NS + "." + s.id, s.displayName);
            en.put("item." + NS + "." + s.id + "_spawn_egg", s.displayName + " Spawn Egg");
            pt.put("item." + NS + "." + s.id + "_spawn_egg", "Ovo gerador de " + s.displayName);
        }
        en.put("map."+NS+".land_on", "Land habitats: on"); en.put("map."+NS+".land_off", "Land habitats: off");
        pt.put("map."+NS+".land_on", "Habitats: ligados"); pt.put("map."+NS+".land_off", "Habitats: desligados");
        en.put("map."+NS+".land_label", "%s | %s, %s, %s | %s"); pt.put("map."+NS+".land_label", "%s | %s, %s, %s | %s");
        en.put("map."+NS+".land_occupied", "Occupied"); pt.put("map."+NS+".land_occupied", "Ocupado");
        en.put("map."+NS+".land_vacant", "Recovering"); pt.put("map."+NS+".land_vacant", "Em recupera\u00e7\u00e3o");
        en.put("map."+NS+".land_invalid", "Seeking water"); pt.put("map."+NS+".land_invalid", "Buscando \u00e1gua");
        en.put("map."+NS+".land_more", "+%s habitats"); pt.put("map."+NS+".land_more", "+%s habitats");
        en.put("map." + NS + ".nests_on", "Nests: on"); en.put("map." + NS + ".nests_off", "Nests: off");
        pt.put("map." + NS + ".nests_on", "Ninhos: ligados"); pt.put("map." + NS + ".nests_off", "Ninhos: desligados");
        en.put("map." + NS + ".nest_label", "%s habitat | %s, %s, %s"); pt.put("map." + NS + ".nest_label", "Habitat de %s | %s, %s, %s");
        for (var bird : Species.values()) {
            if (!bird.flyer()) continue;
            en.put("block." + NS + "." + bird.id + "_nest", bird.displayName + " Nest");
            pt.put("block." + NS + "." + bird.id + "_nest", "Ninho de " + bird.displayName);
            en.put("item." + NS + "." + bird.id + "_egg", bird.displayName + " Egg");
            pt.put("item." + NS + "." + bird.id + "_egg", "Ovo de " + bird.displayName);
        }
        model("allosaur_heart");
        model("workshop_schematic");
        model("guardian_trophy");
        PrimitiveData.lang(en, pt);
        put("assets/" + NS + "/lang/en_us", en); put("assets/" + NS + "/lang/pt_br", pt);
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
        tag("item/taming/sedative", NS + ":narcoberry", NS + ":tranquilizer_arrow");
        tag("item/taming/knockout_food", "#" + NS + ":taming/plant_food", "#" + NS + ":taming/raw_meat",
                "#" + NS + ":taming/fish");
        json("data/" + NS + "/recipe/tranquilizer_arrow", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"tranquilizer_arrow",
                 "ingredients":["minecraft:arrow","minecraft:arrow",
                                "minecraft:arrow","minecraft:arrow",
                                "%s:narcoberry","minecraft:bone"],
                 "result":{"count":4,"id":"%s:tranquilizer_arrow"}}
                """.formatted(NS, NS));
    }

    /** Field Journal crafting and the hidden discovery advancements the quest book reads. */
    private void journal() {
        json("data/" + NS + "/recipe/field_journal", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"field_journal",
                 "ingredients":["minecraft:book","minecraft:leather","minecraft:leather"],
                 "result":{"count":1,"id":"%s:field_journal"}}
                """.formatted(NS));
        // Awarded by the taming code; hidden so it is a discovery record, not a popup.
        json("data/" + NS + "/advancement/journal/first_tame", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        json("data/" + NS + "/advancement/journal/rank5_tame", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        // Awarded by the guardian service when the tribe wins the First Guardian encounter.
        json("data/" + NS + "/advancement/journal/first_guardian", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
    }

    /** Player-facing taming and sedation text, in both shipped locales. */
    private void tamingMessages(Map<String, String> en, Map<String, String> pt) {
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
                "you do not own this creature", "it needs a saddle", "someone is already riding it",
                "there is no room to mount here", "your tribe has not granted you riding permission for %s",
                "your tribe has not granted you cargo access to %s",
                "your tribe has not granted you order permission for %s"};
        String[] portuguese = {"%s foi nocauteado.", "%s est\u00e1 acordando.", "%s come. Progresso de domestica\u00e7\u00e3o %s%%.",
                "%s adora a comida favorita. Progresso de domestica\u00e7\u00e3o %s%%.", "%s recusa: %s",
                "Voc\u00ea n\u00e3o consegue alcan\u00e7ar %s agora.", "Voc\u00ea reivindicou %s.",
                "%s vai tolerar voc\u00ea por um momento.", "%s confia em voc\u00ea agora.", "Voc\u00ea est\u00e1 inconsciente: %s",
                "aceito", "comida errada", "fome insuficiente", "ainda digerindo a \u00faltima refei\u00e7\u00e3o",
                "n\u00e3o est\u00e1 inconsciente", "outro jogador det\u00e9m esta tentativa", "j\u00e1 domesticado", "alvo inv\u00e1lido",
                "voc\u00ea n\u00e3o pode atacar", "voc\u00ea n\u00e3o pode interagir", "voc\u00ea n\u00e3o pode usar itens", "voc\u00ea n\u00e3o pode andar",
                "voc\u00ea n\u00e3o pode montar", "a criatura est\u00e1 inconsciente", "voc\u00ea precisa domar primeiro",
                "voc\u00ea n\u00e3o \u00e9 o dono desta criatura", "precisa de uma sela", "algu\u00e9m j\u00e1 est\u00e1 montado",
                "n\u00e3o h\u00e1 espa\u00e7o para montar aqui", "sua tribo n\u00e3o lhe deu permiss\u00e3o de montaria em %s",
                "sua tribo n\u00e3o lhe deu acesso \u00e0 carga de %s",
                "sua tribo n\u00e3o lhe deu permiss\u00e3o de ordens para %s"};
        for (int i = 0; i < keys.length; i++) {
            en.put("taming." + NS + "." + keys[i], english[i]);
            pt.put("taming." + NS + "." + keys[i], portuguese[i]);
        }
    }
    /** Player-facing tribe permission text, in both shipped locales. */
    private void tribeMessages(Map<String, String> en, Map<String, String> pt) {
        String[] keys = {"status_solo", "status_party", "flag", "permission_set", "permission_cleared",
                "permission_denied", "unknown", "on", "off"};
        String[] english = {"No tribe yet for %s. Create one with /ftbteams party create, then invite your partner.",
                "Tribe %s | %s member(s)", "%s: %s", "%s for %s set to %s.",
                "Tribe permissions for %s reset to defaults.",
                "Only a tribe owner or a gamemaster may change tribe permissions.", "Unknown option: %s", "on", "off"};
        String[] portuguese = {"Ainda sem tribo para %s. Crie uma com /ftbteams party create e convide seu parceiro.",
                "Tribo %s | %s membro(s)", "%s: %s", "%s de %s definida como %s.",
                "Permiss\u00f5es de tribo de %s restauradas ao padr\u00e3o.",
                "Somente o dono da tribo ou um gamemaster pode mudar permiss\u00f5es.", "Op\u00e7\u00e3o desconhecida: %s", "ligada", "desligada"};
        for (int i = 0; i < keys.length; i++) {
            en.put("tribe." + NS + "." + keys[i], english[i]);
            pt.put("tribe." + NS + "." + keys[i], portuguese[i]);
        }
    }

    /**
     * Removals the theme enforces through data: biome spawn lists and features, structure sets,
     * village trades, advancements and the grounded source for bones.
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
        // Replaced advancements keep their file path, so saved progress is simply never granted.
        var unreachable = Map.of("criteria", Map.of("theme_removed", Map.of("trigger", "minecraft:impossible")));
        for (String path : dev.nez.arksurvivalreturns.feature.theme.ThemePolicy.DISABLED_ADVANCEMENTS) {
            put("data/minecraft/advancement/" + path, unreachable);
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
        // No new PNGs: each model reuses a vanilla texture that reads as the primitive equivalent.
        vanillaModel("plant_fiber", "minecraft:item/wheat");
        vanillaModel("fiber_bandage", "minecraft:item/paper");
        vanillaModel("flint_knife", "minecraft:item/flint");
        CampAssetsData.bedroll(this::put);
        // Two halves like a bed: only the head drops the item.
        json("data/" + NS + "/loot_table/blocks/bedroll", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
             {"condition":"minecraft:survives_explosion"},
             {"condition":"minecraft:block_state_property","block":"%s:bedroll","properties":{"part":"head"}}],
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
                 "ingredients":["minecraft:flint","minecraft:stick","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:flint_knife"}}
                """.formatted(NS, NS));
        json("data/" + NS + "/recipe/fiber_bandage", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"fiber_bandage",
                 "ingredients":["%s:plant_fiber","%s:plant_fiber","%s:plant_fiber","minecraft:string"],
                 "result":{"count":2,"id":"%s:fiber_bandage"}}
                """.formatted(NS, NS, NS, NS));
        json("data/" + NS + "/recipe/bedroll", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"bedroll",
                 "ingredients":["%s:plant_fiber","%s:plant_fiber","%s:plant_fiber","%s:plant_fiber",
                                "minecraft:leather"],
                 "result":{"count":1,"id":"%s:bedroll"}}
                """.formatted(NS, NS, NS, NS, NS));
    }

    /** Cargo rigs: the two harness tiers and their primitive recipes. Vanilla textures stand in. */
    private void cargo() {
        vanillaModel("pack_harness", "minecraft:item/saddle");
        vanillaModel("reinforced_harness", "minecraft:item/iron_horse_armor");
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
        vanillaModel("dried_ration", "minecraft:item/bread");
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
        vanillaModel("hearty_stew", "minecraft:item/rabbit_stew");
        vanillaModel("trail_mix", "minecraft:item/cookie");
    }

    /** Concentration without a station: narcoberries and fiber become a stronger dose at the crafting table. */
    private void medicine() {
        vanillaModel("concentrated_sedative", "minecraft:item/gunpowder");
        vanillaModel("improved_tranquilizer_arrow", "minecraft:item/spectral_arrow");
        json("data/" + NS + "/recipe/concentrated_sedative", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"concentrated_sedative",
                 "ingredients":["%s:narcoberry","%s:narcoberry","%s:narcoberry","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:concentrated_sedative"}}
                """.formatted(NS, NS, NS, NS, NS));
        json("data/" + NS + "/recipe/improved_tranquilizer_arrow", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"improved_tranquilizer_arrow",
                 "ingredients":["%s:tranquilizer_arrow","%s:tranquilizer_arrow","%s:tranquilizer_arrow",
                                "%s:tranquilizer_arrow","%s:concentrated_sedative"],
                 "result":{"count":4,"id":"%s:improved_tranquilizer_arrow"}}
                """.formatted(NS, NS, NS, NS, NS, NS));
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

    private void campMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("camp." + NS + ".starter_kit", "A survivor's kit: a bedroll, fiber bandages, plant fiber and a flint knife.");
        pt.put("camp." + NS + ".starter_kit", "Um kit de sobrevivente: rolo de dormir, bandagens de fibra, fibra vegetal e uma faca de s\u00edlex.");
        en.put("camp." + NS + ".bedroll_set", "Respawn point set. It stays here even if the bedroll is destroyed.");
        pt.put("camp." + NS + ".bedroll_set", "Ponto de renascimento definido. Ele permanece mesmo se o rolo for destru\u00eddo.");
        en.put("camp." + NS + ".bedroll_picked", "Bedroll rolled up.");
        pt.put("camp." + NS + ".bedroll_picked", "Rolo de dormir recolhido.");
        en.put("camp." + NS + ".bedroll_disabled", "Setting respawn with a bedroll is disabled on this server.");
        pt.put("camp." + NS + ".bedroll_disabled", "Definir o renascimento com um rolo de dormir est\u00e1 desativado neste servidor.");
        en.put("camp." + NS + ".bandaged", "Bandaged +%s health.");
        pt.put("camp." + NS + ".bandaged", "Bandagem recuperou +%s de vida.");
    }


    private void downedMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("downed." + NS + ".downed", "You are down! %s seconds until you bleed out; a tribe member can revive you with a fiber bandage.");
        pt.put("downed." + NS + ".downed", "Voc\u00ea caiu! %s segundos at\u00e9 sangrar; um membro da tribo pode reanimar voc\u00ea com uma bandagem de fibra.");
        en.put("downed." + NS + ".tribe_down", "%s is down at %s, %s, %s with %s seconds left.");
        pt.put("downed." + NS + ".tribe_down", "%s caiu em %s, %s, %s com %s segundos restantes.");
        en.put("downed." + NS + ".revived", "%s pulled you back up.");
        pt.put("downed." + NS + ".revived", "%s levantou voc\u00ea de volta.");
        en.put("downed." + NS + ".revived_self", "You revived %s.");
        pt.put("downed." + NS + ".revived_self", "Voc\u00ea reanimou %s.");
        en.put("downed." + NS + ".not_tribe", "Only a tribe member can revive them.");
        pt.put("downed." + NS + ".not_tribe", "S\u00f3 um membro da tribo pode reanimar.");
        en.put("downed." + NS + ".denied", "You cannot do that while down.");
        pt.put("downed." + NS + ".denied", "Voc\u00ea n\u00e3o pode fazer isso ca\u00eddo.");
        en.put("hud.downed." + NS + ".title", "DOWNED");
        pt.put("hud.downed." + NS + ".title", "CA\u00cdDO");
        en.put("hud.downed." + NS + ".hint", "A tribe member must use a fiber bandage");
        pt.put("hud.downed." + NS + ".hint", "Um membro da tribo precisa usar uma bandagem de fibra");
        en.put("hud.downed." + NS + ".seconds", "%ss");
        pt.put("hud.downed." + NS + ".seconds", "%ss");
    }

    /** Carried-mass gauge and the warning/overload messages; the warning band itself has no penalty. */
    private void massMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("hud." + NS + ".mass", "Load %s / %s");
        pt.put("hud." + NS + ".mass", "Carga %s / %s");
        en.put("hud." + NS + ".mass.mount", "Mount %s / %s");
        pt.put("hud." + NS + ".mass.mount", "Montaria %s / %s");
        en.put("hud." + NS + ".mass.warn", "[ARK] Heavy load: %s / %s");
        pt.put("hud." + NS + ".mass.warn", "[ARK] Carga pesada: %s / %s");
        en.put("hud." + NS + ".mass.overload", "[ARK] Overburdened: sprint disabled and movement slowing.");
        pt.put("hud." + NS + ".mass.overload", "[ARK] Sobrecarregado: corrida desativada e movimento mais lento.");
        en.put("hud." + NS + ".mass.heavy", "[ARK] Severely overburdened: movement heavily slowed.");
        pt.put("hud." + NS + ".mass.heavy", "[ARK] Muito sobrecarregado: movimento fortemente reduzido.");
        en.put("hud." + NS + ".mass.eased", "[ARK] Load eased.");
        pt.put("hud." + NS + ".mass.eased", "[ARK] Carga aliviada.");
        en.put("hud." + NS + ".overload.takeoff", "[ARK] The load is too heavy for takeoff.");
        pt.put("hud." + NS + ".overload.takeoff", "[ARK] A carga \u00e9 pesada demais para decolar.");
        en.put("hud." + NS + ".overload.descent", "[ARK] Overloaded: descending under control.");
        pt.put("hud." + NS + ".overload.descent", "[ARK] Sobrecarregado: descendo sob controle.");
        en.put("hud." + NS + ".overload.dive", "[ARK] Overloaded: this mount will not dive.");
        pt.put("hud." + NS + ".overload.dive", "[ARK] Sobrecarregado: esta montaria n\u00e3o mergulha.");
        en.put("hud." + NS + ".overload.surface", "[ARK] Surfacing before the rider runs out of air.");
        pt.put("hud." + NS + ".overload.surface", "[ARK] Subindo \u00e0 superf\u00edcie antes que o ar acabe.");
    }

    /** Work orders: the extra companion order, its permission denial and the job feedback. */
    private void workMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("companion." + NS + ".order.work", "Your companion starts working here");
        pt.put("companion." + NS + ".order.work", "Seu companheiro come\u00e7a a trabalhar aqui");
        en.put("taming." + NS + ".denied.work", "[ARK] Your tribe has not granted you work access to %s");
        pt.put("taming." + NS + ".denied.work", "[ARK] Sua tribo n\u00e3o concedeu acesso de trabalho a %s");
        en.put("work." + NS + ".full", "[ARK] %s's hold is full.");
        pt.put("work." + NS + ".full", "[ARK] O compartimento de %s est\u00e1 cheio.");
        en.put("work." + NS + ".blocked", "[ARK] %s pauses: a permitted survivor must stay nearby.");
        pt.put("work." + NS + ".blocked", "[ARK] %s pausa: um sobrevivente autorizado precisa ficar perto.");
    }

    /** Homestead station and crop names. */
    private void farmMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("block." + NS + ".trough", "Oak Feeding Trough");
        pt.put("block." + NS + ".trough", "Cocho de alimenta\u00e7\u00e3o");
        en.put("item." + NS + ".trough", "Oak Feeding Trough");
        pt.put("item." + NS + ".trough", "Cocho de alimenta\u00e7\u00e3o");
        String[][] troughNames = {{"spruce", "Spruce", "pinheiro"}, {"birch", "Birch", "bétula"},
                {"jungle", "Jungle", "selva"}, {"acacia", "Acacia", "acácia"}, {"dark_oak", "Dark Oak", "carvalho escuro"},
                {"mangrove", "Mangrove", "mangue"}, {"cherry", "Cherry", "cerejeira"}, {"pale_oak", "Pale Oak", "carvalho pálido"},
                {"bamboo", "Bamboo", "bambu"}};
        for (String[] wood : troughNames) {
            for (String kind : List.of("block.", "item.")) {
                en.put(kind + NS + "." + wood[0] + "_trough", wood[1] + " Feeding Trough");
                pt.put(kind + NS + "." + wood[0] + "_trough", "Cocho de " + wood[2]);
            }
        }
        en.put("kitchen." + NS + ".heated", "Campfire lit");
        pt.put("kitchen." + NS + ".heated", "Fogueira acesa");
        en.put("kitchen." + NS + ".needs_fire", "Needs a lit campfire below");
        pt.put("kitchen." + NS + ".needs_fire", "Acenda a fogueira abaixo");
        en.put("block." + NS + ".drying_rack", "Drying Rack");
        pt.put("block." + NS + ".drying_rack", "Varal de secagem");
        en.put("item." + NS + ".drying_rack", "Drying Rack");
        pt.put("item." + NS + ".drying_rack", "Varal de secagem");
        en.put("item." + NS + ".dried_ration", "Dried Ration");
        pt.put("item." + NS + ".dried_ration", "Ra\u00e7\u00e3o seca");
        en.put("block." + NS + ".tintoberry_bush", "Tintoberry Bush");
        pt.put("block." + NS + ".tintoberry_bush", "Arbusto de tintoberry");
        en.put("block." + NS + ".amarberry_bush", "Amarberry Bush");
        pt.put("block." + NS + ".amarberry_bush", "Arbusto de amarberry");
        en.put("block." + NS + ".azulberry_bush", "Azulberry Bush");
        pt.put("block." + NS + ".azulberry_bush", "Arbusto de azulberry");
        en.put("block." + NS + ".narcoberry_bush", "Narcoberry Bush");
        pt.put("block." + NS + ".narcoberry_bush", "Arbusto de narcoberry");
        en.put("item." + NS + ".concentrated_sedative", "Concentrated Sedative");
        pt.put("item." + NS + ".concentrated_sedative", "Sedativo concentrado");
        en.put("item." + NS + ".improved_tranquilizer_arrow", "Improved Tranquilizer Arrow");
        pt.put("item." + NS + ".improved_tranquilizer_arrow", "Flecha tranquilizante melhorada");
    }

    /** Kitchen names: the pot and the two prepared meals. */
    private void kitchenMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("block." + NS + ".cooking_pot", "Cooking Pot");
        pt.put("block." + NS + ".cooking_pot", "Panela de cozinha");
        en.put("item." + NS + ".cooking_pot", "Cooking Pot");
        pt.put("item." + NS + ".cooking_pot", "Panela de cozinha");
        en.put("item." + NS + ".hearty_stew", "Hearty Stew");
        pt.put("item." + NS + ".hearty_stew", "Ensopado refor\u00e7ado");
        en.put("item." + NS + ".trail_mix", "Trail Mix");
        pt.put("item." + NS + ".trail_mix", "Mistura de trilha");
    }

    private static Map<String, Object> nestBox(double x, double y, double z, double xx, double yy, double zz, String texture) {
        var faces = new LinkedHashMap<String, Object>();
        for (String side : List.of("north", "south", "east", "west", "up", "down")) faces.put(side, Map.of("texture", "#"+texture));
        return Map.of("from", List.of(x,y,z), "to", List.of(xx,yy,zz), "faces", faces);
    }
    private static String nestRim(Species species) {
        return switch (species) {
            case PTERANODON -> "minecraft:block/sand";
            case ARGENTAVIS -> "minecraft:block/spruce_planks";
            case QUETZAL -> "minecraft:block/oak_planks";
            case ARCHAEOPTERYX -> "minecraft:block/moss_block";
            case DRAGON -> "minecraft:block/polished_blackstone";
            default -> "minecraft:block/oak_planks";
        };
    }
    private void flying() {
        for (var species : Species.values()) {
            if (!species.flyer()) continue;
            String id = species.id + "_nest";
            String rim = nestRim(species);
            var textures = Map.of("rim", rim, "leaf", "minecraft:block/moss_block", "egg", "minecraft:block/turtle_egg", "particle", rim);
            var bowl = new ArrayList<Map<String,Object>>();
            bowl.add(nestBox(2,0,2,14,1,14,"rim"));
            bowl.add(nestBox(1,0,1,15,3,3,"rim")); bowl.add(nestBox(1,0,13,15,3,15,"rim"));
            bowl.add(nestBox(1,0,3,3,3,13,"rim")); bowl.add(nestBox(13,0,3,15,3,13,"rim"));
            if (species != Species.PTERANODON) {
                // Short staggered fronds over a twig ring; all geometry remains within its block.
                for (int i=0;i<3;i++) {
                    bowl.add(nestBox(2+i*3,2,1,4+i*3,3.5,4,"leaf"));
                    bowl.add(nestBox(4+i*3,2,12,6+i*3,3.5,15,"leaf"));
                }
            }
            put("assets/"+NS+"/models/block/"+id+"_empty", Map.of("textures",textures,"elements",bowl));
            var full = new ArrayList<>(bowl);
            full.add(nestBox(6,1,6,10,4,10,"egg"));full.add(nestBox(7,4,7,9,5,9,"egg"));
            put("assets/"+NS+"/models/block/"+id, Map.of("textures",textures,"elements",full));
            put("assets/"+NS+"/blockstates/"+id, Map.of("variants",Map.of("egg=true",Map.of("model",NS+":block/"+id),"egg=false",Map.of("model",NS+":block/"+id+"_empty"))));
            put("assets/"+NS+"/models/item/"+species.id+"_egg",Map.of("parent","minecraft:item/generated","textures",Map.of("layer0","minecraft:item/turtle_egg")));
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
    private void guardianMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("item." + NS + ".allosaur_heart", "Allosaur Heart");
        pt.put("item." + NS + ".allosaur_heart", "Cora\u00e7\u00e3o de Alossauro");
        en.put("item." + NS + ".workshop_schematic", "Workshop Schematic");
        pt.put("item." + NS + ".workshop_schematic", "Esquema da Oficina");
        en.put("item." + NS + ".guardian_trophy", "Guardian Trophy");
        pt.put("item." + NS + ".guardian_trophy", "Trofeu do Guardi\u00e3o");
        en.put("tooltip." + NS + ".allosaur_heart",
                "Encounter key. Offer it at a block beneath an Ancient Remnants monolith to summon the First Guardian.");
        pt.put("tooltip." + NS + ".allosaur_heart",
                "Chave do ritual. Ofere\u00e7a-a em um bloco sob um mon\u00f3lito de Ancient Remnants para invocar o Primeiro Guardi\u00e3o.");
        en.put("tooltip." + NS + ".workshop_schematic",
                "Proof of the First Guardian's defeat. The tribe's workshop research is already recorded; this is a memento.");
        pt.put("tooltip." + NS + ".workshop_schematic",
                "Prova da derrota do Primeiro Guardi\u00e3o. A pesquisa da tribo j\u00e1 est\u00e1 registrada; isto \u00e9 uma lembran\u00e7a.");
        en.put("tooltip." + NS + ".guardian_trophy", "Trophy taken from the Guardian Giganotosaurus.");
        pt.put("tooltip." + NS + ".guardian_trophy", "Trofeu tomado do Giganotossauro Guardi\u00e3o.");
        en.put("guardian." + NS + ".name", "Guardian Giganotosaurus");
        pt.put("guardian." + NS + ".name", "Giganotossauro Guardi\u00e3o");
        en.put("guardian." + NS + ".bar", "%s | %s/%s");
        pt.put("guardian." + NS + ".bar", "%s | %s/%s");
        String[] keys = {"requires_mod", "no_monolith", "active", "resetting", "downed", "chunks", "no_room",
                "failed", "awakened", "reawakened", "retreated", "victory", "status_line", "status_health",
                "status_none", "command.reset", "command.clear", "command.grant"};
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
                "[ARK] Granted the Workshop Schematic to %s."};
        String[] portuguese = {
                "[ARK] O Primeiro Guardi\u00e3o dorme dentro dos mon\u00f3litos de Ancient Remnants. Instale o mod para continuar esta ca\u00e7ada.",
                "[ARK] Ofere\u00e7a o cora\u00e7\u00e3o em um bloco logo abaixo do mon\u00f3lito.",
                "[ARK] Um Guardi\u00e3o j\u00e1 domina este mon\u00f3lito.",
                "[ARK] O mon\u00f3lito ainda est\u00e1 se acalmando. O ritual recome\u00e7a em instantes.",
                "[ARK] Voc\u00ea n\u00e3o pode realizar o ritual ca\u00eddo.",
                "[ARK] A arena ainda n\u00e3o est\u00e1 totalmente carregada.",
                "[ARK] N\u00e3o h\u00e1 terreno livre para o Guardi\u00e3o surgir aqui.",
                "[ARK] O ritual falhou; seu cora\u00e7\u00e3o est\u00e1 intacto.",
                "[ARK] O Cora\u00e7\u00e3o de Alossauro se consome. O Guardi\u00e3o desperta!",
                "[ARK] O ritual brilha de novo. O Guardi\u00e3o retorna!",
                "[ARK] O Guardi\u00e3o perde o interesse e volta a dormir. O ritual continua desbloqueado.",
                "[ARK] O Primeiro Guardi\u00e3o cai. A tribo conquista o Esquema da Oficina.",
                "[ARK] Guardi\u00e3o %s em %s, %s, %s",
                "| %s/%s de vida",
                "[ARK] Nenhum encontro de guardi\u00e3o est\u00e1 registrado para sua tribo.",
                "[ARK] O encontro mais pr\u00f3ximo foi reiniciado para uma nova tentativa gratuita.",
                "[ARK] O registro do encontro mais pr\u00f3ximo foi removido.",
                "[ARK] Esquema da Oficina concedido a %s."};
        for (int i = 0; i < keys.length; i++) {
            en.put("guardian." + NS + "." + keys[i], english[i]);
            pt.put("guardian." + NS + "." + keys[i], portuguese[i]);
        }
    }

    /** Technology tree text: completion and gate announcements plus the /arktech feedback. */
    private void techMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("key." + NS + ".tech_tree", "Open Tribe Chronicle");
        pt.put("key." + NS + ".tech_tree", "Abrir crônica da tribo");
        en.put("key.categories." + NS + ".progression", "Ark Progression");
        pt.put("key.categories." + NS + ".progression", "Progressão Ark");
        String[] uiKeys = {"title", "pan", "quests", "loading", "unavailable", "locked", "available", "complete", "shared", "local"};
        String[] uiEn = {"TRIBE CHRONICLE", "Wheel / drag / arrows", "Quest book", "Opening the chronicle...", "The chronicle is unavailable.",
                "Complete the earlier steps", "Ready to pursue", "Complete", "Shared journal", "Quest link unavailable"};
        String[] uiPt = {"CRÔNICA DA TRIBO", "Roda / arraste / setas", "Missões", "Abrindo a crônica...", "A crônica está indisponível.",
                "Conclua as etapas anteriores", "Pronto para começar", "Concluído", "Diário da tribo", "Missões desconectadas"};
        for (int i = 0; i < uiKeys.length; i++) {
            en.put("tech.gui." + uiKeys[i], uiEn[i]);
            pt.put("tech.gui." + uiKeys[i], uiPt[i]);
        }
        en.put("tech." + NS + ".completed", "[ARK] Technology unlocked: %s");
        pt.put("tech." + NS + ".completed", "[ARK] Tecnologia desbloqueada: %s");
        en.put("tech." + NS + ".gate", "[ARK] The gate %s opens a new age.");
        pt.put("tech." + NS + ".gate", "[ARK] O port\u00e3o %s abre uma nova era.");
        en.put("tech." + NS + ".unavailable", "[ARK] The technology tree is not loaded.");
        pt.put("tech." + NS + ".unavailable", "[ARK] A \u00e1rvore de tecnologia n\u00e3o foi carregada.");
        en.put("tech." + NS + ".status_total", "[ARK] Technology: %s/%s nodes complete.");
        pt.put("tech." + NS + ".status_total", "[ARK] Tecnologia: %s/%s n\u00f3s conclu\u00eddos.");
        en.put("tech." + NS + ".status_age", "[ARK] %s: %s/%s");
        pt.put("tech." + NS + ".status_age", "[ARK] %s: %s/%s");
        en.put("tech." + NS + ".status_available", "[ARK] Available: %s - %s");
        pt.put("tech." + NS + ".status_available", "[ARK] Dispon\u00edvel: %s - %s");
        en.put("tech." + NS + ".command.unknown", "[ARK] Unknown technology node: %s");
        pt.put("tech." + NS + ".command.unknown", "[ARK] N\u00f3 de tecnologia desconhecido: %s");
        en.put("tech." + NS + ".command.unlock", "[ARK] Unlocked technology node %s.");
        pt.put("tech." + NS + ".command.unlock", "[ARK] N\u00f3 de tecnologia desbloqueado: %s.");
        en.put("tech." + NS + ".command.reset", "[ARK] Technology progress reset for your tribe.");
        pt.put("tech." + NS + ".command.reset", "[ARK] O progresso de tecnologia da sua tribo foi reiniciado.");
        en.put("tech." + NS + ".command.reset_empty", "[ARK] Your tribe has no technology progress to reset.");
        pt.put("tech." + NS + ".command.reset_empty",
                "[ARK] Sua tribo n\u00e3o tem progresso de tecnologia para reiniciar.");
    }

    /**
     * Summed vanilla spawn-list weight a habitat adds to each of its biomes. A habitat spans dozens of
     * biomes, so a species' list weight is its share of this budget: a biome's Ark entries weigh about
     * what they did under the old per-species biome lists, and vanilla animals keep their share of
     * chunk-generation spawns. The population budget still picks by the species' own weight.
     */
    private static final Map<Species.Habitat, Integer> HABITAT_LIST_WEIGHT = Map.of(
            Species.Habitat.TEMPERATE, 30, Species.Habitat.SKY, 8, Species.Habitat.WETLAND, 20,
            Species.Habitat.COLD, 20, Species.Habitat.SEA, 18);

    /** Oversized bodies break vanilla's single-chunk spawn clamp; the population budget spawns them. */
    private static boolean vanillaListed(Species species) { return species.weight > 0 && species.chunkSpawnSafe(); }

    private void spawns() {
        var habitatWeight = new EnumMap<Species.Habitat, Integer>(Species.Habitat.class);
        for (var species : Species.values())
            if (vanillaListed(species)) habitatWeight.merge(species.habitat(), species.weight, Integer::sum);
        for (var species : Species.values()) {
            if (!vanillaListed(species)) continue;
            int weight = Math.max(1, (int) Math.round(species.weight
                    * (double) HABITAT_LIST_WEIGHT.get(species.habitat()) / habitatWeight.get(species.habitat())));
            put("data/" + NS + "/neoforge/biome_modifier/spawn_" + species.id, Map.of(
                    "type", "neoforge:add_spawns",
                    "biomes", "#" + NS + ":spawns/" + species.id,
                    "spawners", Map.of(
                            "type", NS + ":" + species.id,
                            "weight", weight,
                            "minCount", species.minGroup,
                            "maxCount", species.maxGroup)));
        }
    }
    private void tests() {
        var spawningRules = Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", true));
        put("data/" + NS + "/test_environment/empty", spawningRules);
        put("data/" + NS + "/test_environment/collection", spawningRules);
        for (String name : List.of("levels_persist", "packs_and_damage", "spawn_rules", "grass_berries", "progression", "behavior", "combat_timing", "creature_expansion", "mass_load", "cargo_load", "cargo_transfer", "overload_flight", "overload_swim", "work_harvest", "farm_batch", "medicine_dose", "kitchen_cook"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_environment/population", spawningRules);
        // Guardian tests own a small batch: the encounter spawns an oversized boss and must not
        // contend with the timing-sensitive combat suite in the shared empty batch.
        put("data/" + NS + "/test_environment/guardian", spawningRules);
        put("data/" + NS + "/test_environment/tech", spawningRules);
        put("data/" + NS + "/test_instance/population", Map.of("type", "minecraft:function", "function", NS + ":population",
                "environment", NS + ":population", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("flying_ecology", "flying_pteranodon", "flying_argentavis", "land_ecology", "land_movement"))
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
                "journal_taming_unlock", "camp_starter_kit", "camp_bedroll_spawn",
                "downed_revive"))
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
        for (String name : List.of("guardian_heart", "guardian_policy", "guardian_registration",
                "guardian_persistence", "guardian_rewards"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":guardian",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
        for (String name : List.of("primitive_rocks", "primitive_fire", "primitive_forge", "primitive_curing", "primitive_gates",
                "primitive_tall_stations", "primitive_keratin",
                "integration_curios", "integration_toms_storage", "integration_terralith", "integration_better_combat"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":empty",
                    "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("tech_tree", "tech_progress", "tech_triggers", "tech_flow", "tech_future"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function",
                    "function", NS + ":" + name, "environment", NS + ":tech",
                    "structure", NS + ":test_population", "max_ticks", 300, "sky_access", true));
    }
}
