package dev.nez.arksurvivalreturns.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.primitive.DinoMeat;
import dev.nez.arksurvivalreturns.feature.taming.CreatureProfileRegistry;

/**
 * Creature facts for the project showcase (tools/build_showcase.py), exported from the live enums so the
 * page never drifts from the mod. Written to Ark/design/showcase/species.json, outside the shipped resources.
 */
final class ShowcaseData {
    static JsonObject species() {
        JsonArray list = new JsonArray();
        for (Species s : Species.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("name", s.displayName);
            o.addProperty("realm", s.realm().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("predator", s.predator);
            o.addProperty("apex", s.apex());
            o.addProperty("cold", s.coldAdapted());
            o.addProperty("health", s.health);
            o.addProperty("damage", s.damage);
            o.addProperty("width", s.width);
            o.addProperty("height", s.height);
            o.addProperty("groupMin", s.minGroup);
            o.addProperty("groupMax", s.maxGroup);
            o.addProperty("danger", s.minimumDanger());
            DinoMeat meat = DinoMeat.of(s);
            o.addProperty("meat", meat == null ? "none" : meat.id());
            var taming = CreatureProfileRegistry.of(s);
            o.addProperty("taming", taming.method().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("size", taming.size().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("note", taming.note());
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("species", list);
        return root;
    }

    private ShowcaseData() {}
}
