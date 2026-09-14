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
        tags(); models(); berries(); flying(); tests();
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
        String[] mapEn = {"[ARK] Map locked. Requires taming a creature from a danger-5 region. Taming is not available yet.", "Locked", "Unlocked", "%s: map %s", "Region difficulty", "Unrated dimension", "At cursor: %s/5", "1 - Easy", "2 - Moderate", "3 - Hard", "4 - Extreme", "5 - Severe"};
        String[] mapPt = {"[ARK] Mapa bloqueado. Requer domar uma criatura de uma regiÃ£o de perigo 5. DomesticaÃ§Ã£o ainda indisponÃ­vel.", "Bloqueado", "Desbloqueado", "%s: mapa %s", "Dificuldade regional", "DimensÃ£o sem classificaÃ§Ã£o", "No cursor: %s/5", "1 - FÃ¡cil", "2 - Moderada", "3 - DifÃ­cil", "4 - Extrema", "5 - Severa"};
        for (int i = 0; i < mapKeys.length; i++) {
            en.put("map." + NS + "." + mapKeys[i], mapEn[i]); pt.put("map." + NS + "." + mapKeys[i], mapPt[i]);
        }
        en.put("hud." + NS + ".creature", "%s | Lv. %s | %s / %s HP"); pt.put("hud." + NS + ".creature", "%s | Nv. %s | %s / %s PV");
        en.put("chat." + NS + ".biome", "[ARK] %s | Danger %s/5 | Wild levels %s-%s");
        pt.put("chat." + NS + ".biome", "[ARK] %s | Perigo %s/5 | NÃ­veis selvagens %s-%s");
        en.put("chat." + NS + ".biome_unrated", "[ARK] %s | Outside Overworld danger zones");
        String[] states = {"roam", "forage", "drink", "rest", "alert", "investigate", "threaten", "hunt", "defend", "flee", "return_home", "feed"};
        String[] stateEn = {"Roaming", "Foraging", "Drinking", "Resting", "Alert", "Investigating", "Warning", "Hunting", "Defending", "Fleeing", "Returning home", "Feeding"};
        String[] statePt = {"Vagando", "Pastando", "Bebendo", "Descansando", "Alerta", "Investigando", "AmeaÃ§ando", "CaÃ§ando", "Defendendo", "Fugindo", "Voltando para casa", "Comendo"};
        for (int i = 0; i < states.length; i++) {
            en.put("behavior." + NS + "." + states[i], stateEn[i]); pt.put("behavior." + NS + "." + states[i], statePt[i]);
        }
        en.put("behavior." + NS + ".seek_water", "Seeking water"); pt.put("behavior." + NS + ".seek_water", "Procurando Ã¡gua");
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
        for (var bird : new Species[]{Species.PTERANODON, Species.ARGENTAVIS}) {
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
    private static Map<String, Object> nestBox(double x, double y, double z, double xx, double yy, double zz, String texture) {
        var faces = new LinkedHashMap<String, Object>();
        for (String side : List.of("north", "south", "east", "west", "up", "down")) faces.put(side, Map.of("texture", "#"+texture));
        return Map.of("from", List.of(x,y,z), "to", List.of(xx,yy,zz), "faces", faces);
    }
    private void flying() {
        for (var species : new Species[]{Species.PTERANODON, Species.ARGENTAVIS}) {
            String id = species.id + "_nest";
            String rim = species == Species.ARGENTAVIS ? "minecraft:block/spruce_planks" : "minecraft:block/sand";
            var textures = Map.of("rim", rim, "leaf", "minecraft:block/moss_block", "egg", "minecraft:block/turtle_egg", "particle", rim);
            var bowl = new ArrayList<Map<String,Object>>();
            bowl.add(nestBox(2,0,2,14,1,14,"rim"));
            bowl.add(nestBox(1,0,1,15,3,3,"rim")); bowl.add(nestBox(1,0,13,15,3,15,"rim"));
            bowl.add(nestBox(1,0,3,3,3,13,"rim")); bowl.add(nestBox(13,0,3,15,3,13,"rim"));
            if (species == Species.ARGENTAVIS) {
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
    private void tests() {
        var spawningRules = Map.of("type", "minecraft:game_rules", "rules", Map.of("minecraft:spawn_mobs", true));
        put("data/" + NS + "/test_environment/empty", spawningRules);
        for (String name : List.of("levels_persist", "packs_and_damage", "spawn_rules", "grass_berries", "progression", "behavior", "creature_expansion"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                    "environment", NS + ":empty", "structure", NS + ":test_empty", "max_ticks", 100, "sky_access", true));
        put("data/" + NS + "/test_environment/population", spawningRules);
        put("data/" + NS + "/test_instance/population", Map.of("type", "minecraft:function", "function", NS + ":population",
                "environment", NS + ":population", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        for (String name : List.of("flying_ecology", "flying_pteranodon", "flying_argentavis", "land_ecology", "land_movement"))
            put("data/" + NS + "/test_instance/" + name, Map.of("type", "minecraft:function", "function", NS + ":" + name,
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 500, "sky_access", true));
        put("data/" + NS + "/test_instance/nighttime", Map.of("type", "minecraft:function", "function", NS + ":nighttime",
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 200, "sky_access", true));
        put("data/" + NS + "/test_instance/debug_spyglass", Map.of("type", "minecraft:function", "function", NS + ":debug_spyglass",
                "environment", NS + ":empty", "structure", NS + ":test_population", "max_ticks", 100, "sky_access", true));
    }
}
