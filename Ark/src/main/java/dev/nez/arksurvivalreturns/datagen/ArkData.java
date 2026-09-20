package dev.nez.arksurvivalreturns.datagen;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
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
        tags(); models(); berries(); taming(); journal(); camp(); recovery(); flying(); spawns(); theme(); tests();
        return CompletableFuture.allOf(files.entrySet().stream().map(e -> DataProvider.saveStable(cache, e.getValue(),
                output.getOutputFolder().resolve(e.getKey()))).toArray(CompletableFuture[]::new));
    }
    private void put(String path, Object value) { files.put(path + ".json", new Gson().toJsonTree(value)); }
    private void json(String path, String value) { files.put(path + ".json", JsonParser.parseString(value)); }
    private void biomeTag(String name, String... values) { tag("worldgen/biome/" + name, values); }
    private void tag(String name, String... values) {
        put("data/" + NS + "/tags/" + name, Map.of("replace", false, "values",
                Arrays.stream(values).map(v -> v.contains(":") ? v : "minecraft:" + v).toList()));
    }
    private void tags() {
        biomeTag("difficulty/easy", "plains", "sunflower_plains", "beach", "birch_forest", "old_growth_birch_forest", "cherry_grove", "river", "mushroom_fields");
        biomeTag("difficulty/moderate", "forest", "flower_forest", "savanna", "savanna_plateau", "windswept_savanna", "meadow", "taiga", "old_growth_pine_taiga", "ocean", "lukewarm_ocean", "warm_ocean");
        biomeTag("difficulty/hard", "jungle", "sparse_jungle", "bamboo_jungle", "swamp", "mangrove_swamp", "dark_forest", "old_growth_spruce_taiga", "desert", "badlands", "wooded_badlands", "eroded_badlands", "windswept_forest", "stony_shore", "cold_ocean", "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "lush_caves", "dripstone_caves");
        biomeTag("difficulty/extreme", "snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "windswept_hills", "windswept_gravelly_hills", "frozen_river", "snowy_beach", "frozen_ocean", "deep_frozen_ocean", "deep_dark");
        biomeTag("difficulty/severe", "eroded_badlands", "jagged_peaks", "frozen_peaks");
        biomeTag("spawns/pteranodon", "beach", "river", "plains", "sunflower_plains", "savanna");
        biomeTag("spawns/velociraptor", "forest", "flower_forest", "savanna", "savanna_plateau", "jungle", "sparse_jungle");
        biomeTag("spawns/argentavis", "taiga", "old_growth_pine_taiga", "windswept_forest", "windswept_hills", "windswept_gravelly_hills", "stony_peaks", "snowy_taiga");
        biomeTag("spawns/triceratops", "plains", "sunflower_plains", "savanna", "savanna_plateau", "meadow");
        biomeTag("spawns/therizinosaurus", "jungle", "sparse_jungle", "dark_forest", "swamp", "old_growth_spruce_taiga");
        biomeTag("spawns/brontosaurus", "savanna", "savanna_plateau", "sparse_jungle");
        biomeTag("spawns/tyrannosaurus", "badlands", "wooded_badlands", "desert", "sparse_jungle", "windswept_forest");
        biomeTag("spawns/giganotosaurus", "windswept_hills", "windswept_gravelly_hills", "stony_peaks", "jagged_peaks");
        biomeTag("spawns/titanosaur", "stony_peaks", "windswept_gravelly_hills");
        biomeTag("spawns/spinosaurus", "river", "swamp", "mangrove_swamp", "sparse_jungle");
        biomeTag("spawns/parasaur", "plains", "sunflower_plains", "savanna", "forest", "river");
        biomeTag("spawns/ceratosaurus", "forest", "savanna", "badlands", "sparse_jungle");
        biomeTag("spawns/dilophosaur", "beach", "forest", "jungle", "sparse_jungle", "swamp");
        biomeTag("spawns/acrocanthosaurus", "windswept_hills", "windswept_forest", "wooded_badlands", "jagged_peaks");
        biomeTag("spawns/allosaurus", "savanna", "savanna_plateau", "windswept_forest", "sparse_jungle");
        biomeTag("spawns/ankylosaurus", "plains", "savanna", "taiga", "windswept_hills", "meadow");
        biomeTag("spawns/carnotaurus", "forest", "savanna", "badlands", "sparse_jungle");
        biomeTag("spawns/pegomastax", "beach", "forest", "birch_forest", "jungle");
        biomeTag("spawns/lystrosaurus", "plains", "sunflower_plains", "beach", "forest", "meadow");
        // Collection: water-bound species prefer open and deep water, semi-aquatic species the swamp.
        biomeTag("spawns/cnidaria", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean");
        biomeTag("spawns/plesiosaur", "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "river");
        biomeTag("spawns/megalodon", "ocean", "deep_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean");
        biomeTag("spawns/liopleurodon", "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean");
        biomeTag("spawns/mosasaurus", "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean");
        biomeTag("spawns/tusoteuthis", "deep_ocean", "deep_cold_ocean", "deep_frozen_ocean");
        biomeTag("spawns/kaprosuchus", "swamp", "mangrove_swamp", "river", "jungle", "sparse_jungle", "lush_caves");
        biomeTag("spawns/sarco", "swamp", "mangrove_swamp", "river", "jungle");
        biomeTag("spawns/deinosuchus", "swamp", "mangrove_swamp", "river", "jungle", "sparse_jungle");
        biomeTag("spawns/titanoboa", "swamp", "mangrove_swamp", "jungle", "sparse_jungle", "dark_forest");
        // Collection: cold species are gated to snow and mountain biomes, never to warm high ground.
        biomeTag("spawns/megalocerus", "snowy_taiga", "snowy_plains", "grove", "taiga");
        biomeTag("spawns/unicorn", "snowy_plains", "snowy_taiga", "grove", "ice_spikes");
        biomeTag("spawns/mammoth", "snowy_plains", "snowy_taiga", "snowy_beach", "grove", "frozen_river");
        biomeTag("spawns/direwolf", "snowy_taiga", "snowy_plains", "grove", "taiga", "frozen_river");
        biomeTag("spawns/sabertooth", "snowy_taiga", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks");
        biomeTag("spawns/megapithecus", "jagged_peaks", "frozen_peaks", "snowy_slopes", "stony_peaks");
        // Collection: remaining warm land and flying species reuse existing habitat families.
        biomeTag("spawns/paraceratherium", "plains", "sunflower_plains", "savanna", "meadow", "forest");
        biomeTag("spawns/terrorbird", "savanna", "plains", "jungle", "sparse_jungle", "badlands");
        biomeTag("spawns/ravager", "dark_forest", "old_growth_pine_taiga", "taiga", "windswept_forest", "forest");
        biomeTag("spawns/archaeopteryx", "forest", "dark_forest", "jungle", "sparse_jungle", "birch_forest", "old_growth_birch_forest");
        biomeTag("spawns/quetzal", "windswept_hills", "windswept_gravelly_hills", "stony_peaks", "savanna_plateau", "badlands", "jagged_peaks");
        biomeTag("spawns/dragon", "jagged_peaks", "frozen_peaks", "stony_peaks", "snowy_slopes", "windswept_gravelly_hills");
        tag("block/spawn_surfaces", "#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta",
                "grass_block", "podzol", "mycelium",
                "stone", "granite", "diorite", "andesite", "gravel", "snow", "snow_block", "ice", "packed_ice", "blue_ice",
                "mud", "packed_mud", "clay", "moss_block", "pale_moss_block", "calcite", "tuff", "deepslate");
        for (Species s : Species.values()) {
            put("data/" + NS + "/loot_table/entities/" + s.id, Map.of("type", "minecraft:entity", "pools", List.of()));
        }
        tag("item/berries", NS + ":tintoberry", NS + ":amarberry", NS + ":azulberry", NS + ":narcoberry");
        tag("item/sedative_berries", NS + ":narcoberry");
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
        en.put("item." + NS + ".fiber_bandage", "Fiber Bandage");
        pt.put("item." + NS + ".fiber_bandage", "Bandagem de fibra");
        en.put("item." + NS + ".flint_knife", "Flint Knife");
        pt.put("item." + NS + ".flint_knife", "Faca de s\u00edlex");
        en.put("item." + NS + ".spear", "Flint Spear");
        pt.put("item." + NS + ".spear", "Lan\u00e7a de s\u00edlex");
        en.put("block." + NS + ".bedroll", "Field Bedroll");
        pt.put("block." + NS + ".bedroll", "Rolo de dormir");
        en.put("item." + NS + ".bedroll", "Field Bedroll");
        pt.put("item." + NS + ".bedroll", "Rolo de dormir");
        en.put("key." + NS + ".journal", "Open Field Journal");
        pt.put("key." + NS + ".journal", "Abrir di\u00e1rio de campo");
        en.put("key.category." + NS + ".keys", "Ark Survival Returns");
        pt.put("key.category." + NS + ".keys", "Ark Survival Returns");
        tamingMessages(en, pt);
        tribeMessages(en, pt);
        campMessages(en, pt);
        recoveryMessages(en, pt);
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
        tag("item/taming/raw_meat", "beef", "porkchop", "chicken", "rabbit", "mutton");
        tag("item/taming/fish", "cod", "salmon");
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
        vanillaModel("spear", "minecraft:item/trident");
        // Bedroll: a two-pixel wool mat. The block has no properties, so one state is enough.
        put("assets/" + NS + "/models/block/bedroll", Map.of("textures",
                Map.of("wool", "minecraft:block/red_wool", "particle", "minecraft:block/red_wool"),
                "elements", List.of(nestBox(0, 0, 0, 16, 2, 16, "wool"))));
        put("assets/" + NS + "/blockstates/bedroll", Map.of("variants", Map.of("", Map.of("model", NS + ":block/bedroll"))));
        put("assets/" + NS + "/models/item/bedroll", Map.of("parent", NS + ":block/bedroll"));
        put("assets/" + NS + "/items/bedroll", Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":block/bedroll")));
        json("data/" + NS + "/loot_table/blocks/bedroll", """
            {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[
             {"condition":"minecraft:survives_explosion"}],
             "entries":[{"type":"minecraft:item","name":"%s:bedroll"}]}]}
            """.formatted(NS));
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
        json("data/" + NS + "/recipe/spear", """
                {"type":"minecraft:crafting_shapeless","category":"equipment","group":"spear",
                 "ingredients":["minecraft:stick","minecraft:stick","minecraft:flint","%s:plant_fiber"],
                 "result":{"count":1,"id":"%s:spear"}}
                """.formatted(NS, NS));
        json("data/" + NS + "/recipe/fiber_bandage", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"fiber_bandage",
                 "ingredients":["%s:plant_fiber","%s:plant_fiber","%s:plant_fiber","minecraft:string"],
                 "result":{"count":2,"id":"%s:fiber_bandage"}}
                """.formatted(NS, NS, NS, NS));
        json("data/" + NS + "/recipe/bedroll", """
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"bedroll",
                 "ingredients":["minecraft:red_wool","minecraft:red_wool","minecraft:red_wool",
                                "minecraft:string","minecraft:string"],
                 "result":{"count":1,"id":"%s:bedroll"}}
                """.formatted(NS));
    }

    /** Recovery cache visuals and the two hidden discovery advancements. */
    private void recovery() {
        // A crate-like marker; no new PNGs, the barrel texture reads as a survivor's cache.
        var faces = new LinkedHashMap<String, Object>();
        for (String side : List.of("north", "south", "east", "west")) faces.put(side, Map.of("texture", "#side"));
        faces.put("up", Map.of("texture", "#top"));
        faces.put("down", Map.of("texture", "#side"));
        put("assets/" + NS + "/models/block/recovery_cache", Map.of(
                "textures", Map.of("side", "minecraft:block/barrel_side", "top", "minecraft:block/barrel_top",
                        "particle", "minecraft:block/barrel_side"),
                "elements", List.of(Map.of("from", List.of(2, 0, 2), "to", List.of(14, 10, 14), "faces", faces))));
        put("assets/" + NS + "/blockstates/recovery_cache", Map.of("variants", Map.of("", Map.of("model", NS + ":block/recovery_cache"))));
        put("assets/" + NS + "/models/item/recovery_cache", Map.of("parent", NS + ":block/recovery_cache"));
        put("assets/" + NS + "/items/recovery_cache", Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":block/recovery_cache")));
        // The marker drops nothing: the items live in world SavedData until collected.
        json("data/" + NS + "/loot_table/blocks/recovery_cache", """
                {"type":"minecraft:block","pools":[]}
                """);
        json("data/" + NS + "/advancement/journal/first_loss", """
                {"criteria":{"discovered":{"trigger":"minecraft:impossible"}},
                 "requirements":[["discovered"]]}
                """);
        json("data/" + NS + "/advancement/journal/first_recovery", """
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

    private void recoveryMessages(Map<String, String> en, Map<String, String> pt) {
        en.put("block." + NS + ".recovery_cache", "Recovery Cache");
        pt.put("block." + NS + ".recovery_cache", "Cache de recupera\u00e7\u00e3o");
        en.put("item." + NS + ".recovery_cache", "Recovery Cache");
        pt.put("item." + NS + ".recovery_cache", "Cache de recupera\u00e7\u00e3o");
        en.put("recovery." + NS + ".position", "%s (%s, %s, %s)");
        pt.put("recovery." + NS + ".position", "%s (%s, %s, %s)");
        en.put("recovery." + NS + ".unplaced", "unplaced");
        pt.put("recovery." + NS + ".unplaced", "sem local");
        en.put("recovery." + NS + ".own_cache", "Your gear is waiting at %s. It stays until collected.");
        pt.put("recovery." + NS + ".own_cache", "Seus itens esperam em %s. Eles ficam at\u00e9 serem recolhidos.");
        en.put("recovery." + NS + ".tribe_cache", "%s died; their gear is at %s.");
        pt.put("recovery." + NS + ".tribe_cache", "%s morreu; os itens est\u00e3o em %s.");
        en.put("recovery." + NS + ".collected", "Recovered %s stack(s).");
        pt.put("recovery." + NS + ".collected", "Recuperou %s pilha(s).");
        en.put("recovery." + NS + ".collected_owner", "%s recovered your cache.");
        pt.put("recovery." + NS + ".collected_owner", "%s recolheu seu cache.");
        en.put("recovery." + NS + ".not_yours", "Only the owner or their tribe may recover this cache.");
        pt.put("recovery." + NS + ".not_yours", "S\u00f3 o dono ou a tribo dele pode recolher este cache.");
        en.put("recovery." + NS + ".reminder", "%s cache(s) still waiting. Use /arkrecover list.");
        pt.put("recovery." + NS + ".reminder", "%s cache(s) ainda esperando. Use /arkrecover list.");
        en.put("recovery." + NS + ".list_empty", "No caches outstanding.");
        pt.put("recovery." + NS + ".list_empty", "Nenhum cache pendente.");
        en.put("recovery." + NS + ".list_entry", "%s. %s - %s stack(s)");
        pt.put("recovery." + NS + ".list_entry", "%s. %s - %s pilha(s)");
        en.put("recovery." + NS + ".must_travel", "This cache is placed in the world; travel to it or use /arkrecover list.");
        pt.put("recovery." + NS + ".must_travel", "Este cache est\u00e1 no mundo; v\u00e1 at\u00e9 ele ou use /arkrecover list.");
        en.put("recovery." + NS + ".cleared", "Cleared %s cache(s) for %s.");
        pt.put("recovery." + NS + ".cleared", "Removeu %s cache(s) de %s.");
        en.put("recovery." + NS + ".invalid_index", "No cache with index %s.");
        pt.put("recovery." + NS + ".invalid_index", "Nenhum cache com \u00edndice %s.");
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
    private void spawns() {
        for (var species : Species.values()) {
            // Oversized bodies break vanilla's single-chunk spawn clamp; the population budget spawns them.
            if (species.weight <= 0 || !species.chunkSpawnSafe()) continue;
            put("data/" + NS + "/neoforge/biome_modifier/spawn_" + species.id, Map.of(
                    "type", "neoforge:add_spawns",
                    "biomes", "#" + NS + ":spawns/" + species.id,
                    "spawners", Map.of(
                            "type", NS + ":" + species.id,
                            "weight", species.weight,
                            "minCount", species.minGroup,
                            "maxCount", species.maxGroup)));
        }
    }
    private void tests() {
        var spawningRules = Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", true));
        put("data/" + NS + "/test_environment/empty", spawningRules);
        put("data/" + NS + "/test_environment/collection", spawningRules);
        for (String name : List.of("levels_persist", "packs_and_damage", "spawn_rules", "grass_berries", "progression", "behavior", "combat_timing", "creature_expansion"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_environment/population", spawningRules);
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
                "journal_taming_unlock", "camp_starter_kit", "camp_bedroll_spawn", "recovery_cache"))
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
    }
}
