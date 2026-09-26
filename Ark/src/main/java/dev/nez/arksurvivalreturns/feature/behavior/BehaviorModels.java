package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The behaviour models as documentation data for the project showcase (tools/build_showcase.py).
 *
 * <p>Land and water matrices are not typed by hand: they are recorded by driving the real
 * {@link WildlifeMind} through seeded random encounters, so every cell names the rule that actually
 * fired. Bridges are recorded from the real {@link Choreographer}, tier-2 odds from
 * {@link AmbientRoutine}. Only the flight phases, which live in the flyer entity, are declared here.
 * Export runs in datagen (ShowcaseData) and writes Ark/design/showcase/behavior.json.
 */
public final class BehaviorModels {
    private static final long SEED = 0x5EED_B3A7L;

    public static JsonObject export(BehaviorTier.Radii radii, int nightStart, int nightEnd, double carnivoreDaySleep) {
        var root = new JsonObject();
        var tiers = new JsonArray();
        tiers.add(tier("FULL", radii.full(), "Full behaviour: senses, needs (hunger, thirst, fatigue), hunting, fleeing, "
                + "drinking and the timed bridges between states. Decisions twice a second."));
        tiers.add(tier("AMBIENT", radii.ambient(), "Seen from afar: short walks, turns, stops, looking around, grazing, "
                + "sniffing, pooping and the daily sleep schedule. One cheap decision every few seconds; needs frozen."));
        tiers.add(tier("DORMANT", radii.dormant(), "Culled: no routine. Within this radius the pose still follows the "
                + "schedule (asleep or standing) on a slow timer; beyond it nothing runs."));
        root.add("tiers", tiers);
        root.addProperty("margin", radii.margin());
        var schedule = new JsonObject();
        schedule.addProperty("nightStart", nightStart);
        schedule.addProperty("nightEnd", nightEnd);
        schedule.addProperty("carnivoreDaySleep", carnivoreDaySleep);
        schedule.addProperty("carnivore", "Night: hunt. Morning: sleep. Afternoon: roam and drink.");
        schedule.addProperty("herbivore", "Night: sleep. Day: graze, drink and roam.");
        schedule.addProperty("flyer", "No schedule: flyers loop around their nest, land, perch and take off.");
        root.add("schedule", schedule);
        var models = new JsonArray();
        models.add(landModel(false));
        models.add(landModel(true));
        models.add(flyerModel());
        models.add(aquaticModel());
        root.add("models", models);
        var actions = new JsonArray();
        for (var action : BehaviorAction.values()) {
            var o = new JsonObject();
            o.addProperty("id", action.name());
            o.addProperty("motion", action.motion().name());
            o.addProperty("cue", action.cue() == null ? "" : action.cue().name());
            actions.add(o);
        }
        root.add("actions", actions);
        return root;
    }

    private static JsonObject tier(String id, int radius, String summary) {
        var o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("radius", radius);
        o.addProperty("summary", summary);
        return o;
    }

    // ------------------------------------------------------------------------------------ land

    private static JsonObject landModel(boolean carnivore) {
        var o = new JsonObject();
        o.addProperty("id", carnivore ? "land_carnivore" : "land_herbivore");
        o.addProperty("title", carnivore ? "Land carnivore" : "Land herbivore");
        o.addProperty("realms", "land amphibious");
        o.addProperty("predator", carnivore);
        o.addProperty("summary", carnivore
                ? "Hunts at night, sleeps through the morning and roams in the afternoon. Warns before it charges: "
                  + "big carnivores notice, face and roar; stalkers creep in low. Pack mates react with their own delay."
                : "Grazes, drinks and roams by day, sleeps at night. Startles before it bolts; defensive herds stand "
                  + "together at night. Alarms ripple through the herd instead of moving it in lockstep.");
        var full = mindMatrix(carnivore, false);
        var tiers = new JsonArray();
        tiers.add(tierMatrix("FULL", full, "Decision model (WildlifeMind), recorded from seeded encounters. "
                + "Cells list the rule that chose the new state; every change plays a bridge first."));
        tiers.add(tierMatrix("AMBIENT", ambientMatrix(carnivore), "Ambient routine (AmbientRoutine): odds of the next "
                + "step, plus the schedule. A player within the full radius, or any hit, promotes to FULL."));
        tiers.add(tierMatrix("DORMANT", dormantMatrix(), "Pose only, on the schedule; beyond the outer radius nothing runs."));
        o.add("tiers", tiers);
        o.add("bridges", carnivore ? carnivoreBridges() : herbivoreBridges());
        return o;
    }

    /** Transitions of the real decision model: from -> to -> rules that fired, with counts. */
    static Map<String, Map<String, TreeSet<String>>> mindMatrix(boolean predator, boolean aquatic) {
        var matrix = new TreeMap<String, Map<String, TreeSet<String>>>();
        var random = new SplittableRandom(SEED + (predator ? 1 : 0) + (aquatic ? 2 : 0));
        for (int episode = 0; episode < 4000; episode++) {
            // Water: mostly hunters plus the drifting, timid Cnidaria. Land: a quarter of herbivores are timid.
            boolean hunter = aquatic ? random.nextInt(5) != 0 : predator;
            boolean timid = !hunter && (aquatic || random.nextInt(4) == 0);
            var mind = new WildlifeMind(hunter, timid, aquatic);
            mind.restoreNeeds(random.nextDouble(), random.nextDouble(), random.nextDouble());
            var scene = Scene.values()[random.nextInt(Scene.values().length)];
            boolean night = random.nextBoolean();
            double health = random.nextInt(8) == 0 ? 0.2 : 1;
            for (int step = 0; step < 160; step++) {
                if (random.nextInt(8) == 0) scene = Scene.values()[random.nextInt(Scene.values().length)];
                if (random.nextInt(60) == 0) night = !night;
                if (random.nextInt(40) == 0) health = random.nextInt(4) == 0 ? 0.2 : 1;
                if (random.nextInt(50) == 0) mind.restoreNeeds(random.nextDouble(), random.nextDouble(), random.nextDouble());
                var before = mind.state();
                if (before.sleeping() && (scene == Scene.ATTACKED || scene == Scene.CLOSE_INTRUDER) && !aquatic) {
                    mind.interruptSleep(200);
                    record(matrix, before, mind.state(), "woken by a hit, a noise or a close player");
                    continue;
                }
                // A landed killing strike feeds the mind directly (onStrikeKill), outside a decision step.
                if (before.combat() && hunter && scene == Scene.PREY_IN_SIGHT && random.nextInt(6) == 0) {
                    mind.ate();
                    scene = Scene.QUIET; // the prey is dead, nothing left to sense
                }
                if (aquatic) mind.quench(); // AquaticGoal quenches before every step
                var observation = scene.observe(hunter, night, health, aquatic);
                boolean sleepWanted = !aquatic && (hunter ? !night && random.nextBoolean() : night);
                var routine = new WildlifeMind.Routine(true, night, sleepWanted, true, scene.danger(), !aquatic && random.nextBoolean(),
                        random.nextInt(20) == 0, 200, 2, !aquatic && !hunter && night && random.nextInt(30) == 0);
                mind.step(observation, 10, routine, null);
                if (mind.state() != before) record(matrix, before, mind.state(), mind.reason().label());
            }
        }
        return matrix;
    }

    private static void record(Map<String, Map<String, TreeSet<String>>> matrix, BehaviorState from, BehaviorState to, String why) {
        if (from == to) return;
        matrix.computeIfAbsent(from.name(), k -> new TreeMap<>()).computeIfAbsent(to.name(), k -> new TreeSet<>()).add(why);
    }

    /** Encounter situations the recorder cycles through; each holds for a few decisions. */
    private enum Scene {
        QUIET, PREY_IN_SIGHT, INTRUDER, CLOSE_INTRUDER, ATTACKED, SOUND, BIG_PREDATOR, FAR_FROM_HOME, WATER, GRAZING;

        WildlifeMind.Observation observe(boolean predator, boolean night, double health, boolean aquatic) {
            boolean dark = night;
            return switch (this) {
                case QUIET -> new WildlifeMind.Observation(0, false, false, false, false, false, false, false, false, dark, health);
                case PREY_IN_SIGHT -> new WildlifeMind.Observation(1, true, predator, false, false, false, false, false, false, dark, health);
                case INTRUDER -> new WildlifeMind.Observation(1, true, predator, true, false, false, false, false, false, dark, health);
                case CLOSE_INTRUDER -> new WildlifeMind.Observation(1, true, predator, true, false, false, false, false, false, dark, health);
                case ATTACKED -> new WildlifeMind.Observation(1, true, predator, true, true, false, false, false, false, dark, health);
                case SOUND -> new WildlifeMind.Observation(0.65, false, false, false, false, false, false, false, false, dark, health);
                case BIG_PREDATOR -> new WildlifeMind.Observation(1, true, false, !predator, false, !predator, false, false, false, dark, health);
                case FAR_FROM_HOME -> new WildlifeMind.Observation(0, false, false, false, false, false, true, false, false, dark, health);
                case WATER -> new WildlifeMind.Observation(0, false, false, false, false, false, false, !aquatic, false, dark, health);
                case GRAZING -> new WildlifeMind.Observation(0, false, false, false, false, false, false, false, !aquatic && !predator, dark, health);
            };
        }

        boolean danger() { return this == ATTACKED || this == BIG_PREDATOR || this == CLOSE_INTRUDER; }
    }

    private static Map<String, Map<String, TreeSet<String>>> ambientMatrix(boolean carnivore) {
        var profile = carnivore ? CANONICAL_CARNIVORE : CANONICAL_HERBIVORE;
        var matrix = new TreeMap<String, Map<String, TreeSet<String>>>();
        var odds = new EnumMap<DailySchedule.Phase, Map<AmbientRoutine.Step, Integer>>(DailySchedule.Phase.class);
        int samples = 20000;
        for (var phase : DailySchedule.Phase.values()) {
            if (phase == DailySchedule.Phase.SLEEP || phase == DailySchedule.Phase.HUNT && !carnivore) continue;
            var random = new SplittableRandom(SEED + phase.ordinal());
            var counts = new EnumMap<AmbientRoutine.Step, Integer>(AmbientRoutine.Step.class);
            for (int i = 0; i < samples; i++) counts.merge(AmbientRoutine.next(phase, profile, random, false).step(), 1, Integer::sum);
            odds.put(phase, counts);
        }
        // Rows only for steps this model can take: a carnivore never grazes.
        var steps = EnumSet.of(AmbientRoutine.Step.SLEEP);
        odds.values().forEach(counts -> steps.addAll(counts.keySet()));
        for (var from : steps) {
            if (from == AmbientRoutine.Step.SLEEP) {
                add(matrix, "SLEEP", "STAND", "schedule wakes it");
                continue;
            }
            add(matrix, from.name(), "SLEEP", "schedule turns to sleep");
            odds.forEach((phase, counts) -> {
                String when = phase == DailySchedule.Phase.HUNT ? "night" : carnivore ? "afternoon" : "day";
                counts.forEach((to, count) -> {
                    int percent = (int) Math.round(count * 100.0 / samples);
                    if (percent > 0) add(matrix, from.name(), to.name(), when + " " + percent + "%");
                });
            });
        }
        add(matrix, "WALK", "WALK", "home-range edge: walk back");
        return matrix;
    }

    private static Map<String, Map<String, TreeSet<String>>> dormantMatrix() {
        var matrix = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(matrix, "STAND", "SLEEP", "schedule turns to sleep (checked every 5 s)");
        add(matrix, "SLEEP", "STAND", "schedule wakes it (checked every 5 s)");
        return matrix;
    }

    private static void add(Map<String, Map<String, TreeSet<String>>> matrix, String from, String to, String why) {
        matrix.computeIfAbsent(from, k -> new TreeMap<>()).computeIfAbsent(to, k -> new TreeSet<>()).add(why);
    }

    private static JsonObject tierMatrix(String tier, Map<String, Map<String, TreeSet<String>>> matrix, String note) {
        var o = new JsonObject();
        o.addProperty("tier", tier);
        o.addProperty("note", note);
        var states = new TreeSet<String>();
        matrix.forEach((from, row) -> { states.add(from); states.addAll(row.keySet()); });
        var list = new JsonArray();
        states.forEach(list::add);
        o.add("states", list);
        var cells = new JsonObject();
        matrix.forEach((from, row) -> {
            var r = new JsonObject();
            row.forEach((to, reasons) -> {
                var why = new JsonArray();
                reasons.forEach(why::add);
                r.add(to, why);
            });
            cells.add(from, r);
        });
        o.add("matrix", cells);
        return o;
    }

    // -------------------------------------------------------------------------------- bridges

    private static final BehaviorProfile CANONICAL_HERBIVORE = new BehaviorProfile("parasaur", false, false, true, false,
            true, 4, "Para-Roar-Alert", BehaviorClips.of("parasaur"));
    private static final BehaviorProfile CANONICAL_CARNIVORE = new BehaviorProfile("tyrannosaurus", true, false, false, false,
            false, 13.5, "Rex-Roar", BehaviorClips.of("tyrannosaurus"));
    private static final BehaviorProfile CANONICAL_STALKER = new BehaviorProfile("sabertooth", true, false, false, true,
            false, 1.6, "Saber-Startled", BehaviorClips.of("sabertooth"));

    private static JsonArray herbivoreBridges() {
        var out = new JsonArray();
        var p = CANONICAL_HERBIVORE;
        out.add(bridge(p, BehaviorState.ROAM, BehaviorState.ALERT, false, "a sound, scent or glimpse"));
        out.add(bridge(p, BehaviorState.ROAM, BehaviorState.FLEE, false, "a predator in sight"));
        out.add(bridge(p, BehaviorState.FORAGE, BehaviorState.FLEE, true, "hit while grazing"));
        out.add(bridge(p, BehaviorState.SLEEP, BehaviorState.FLEE, false, "woken by a predator at night"));
        out.add(bridge(p, List.of(BehaviorState.ROAM), BehaviorState.ALERT, BehaviorState.THREATEN, false, "an intruder comes close"));
        out.add(bridge(p, List.of(BehaviorState.ALERT), BehaviorState.THREATEN, BehaviorState.DEFEND, false, "the intruder ignored the warning"));
        out.add(bridge(p, List.of(BehaviorState.ROAM), BehaviorState.FLEE, BehaviorState.REGROUP, false, "threat gone, herd scattered"));
        out.add(bridge(p, BehaviorState.ROAM, BehaviorState.SLEEP, false, "night falls"));
        out.add(bridge(p, BehaviorState.SLEEP, BehaviorState.ROAM, false, "dawn"));
        return out;
    }

    private static JsonArray carnivoreBridges() {
        var out = new JsonArray();
        var p = CANONICAL_CARNIVORE;
        out.add(bridge(p, BehaviorState.SEARCH, BehaviorState.ALERT, false, "prey glimpsed at night"));
        out.add(bridge(p, List.of(BehaviorState.SEARCH), BehaviorState.ALERT, BehaviorState.THREATEN, false, "prey or intruder in clear sight"));
        out.add(bridge(p, List.of(BehaviorState.ALERT), BehaviorState.THREATEN, BehaviorState.HUNT, false, "the warning was held"));
        out.add(bridge(p, BehaviorState.SEARCH, BehaviorState.HUNT, false, "prey already tracked"));
        out.add(bridge(CANONICAL_STALKER, BehaviorState.SEARCH, BehaviorState.HUNT, false, "stalker (Sabertooth)"));
        out.add(bridge(p, BehaviorState.SLEEP, BehaviorState.DEFEND, true, "hit while asleep"));
        out.add(bridge(p, List.of(BehaviorState.THREATEN), BehaviorState.HUNT, BehaviorState.RETURN_HOME, false, "chase too long or too far"));
        out.add(bridge(p, BehaviorState.ROAM, BehaviorState.SLEEP, false, "morning"));
        out.add(bridge(p, BehaviorState.SLEEP, BehaviorState.ROAM, false, "afternoon"));
        return out;
    }

    private static JsonObject bridge(BehaviorProfile profile, BehaviorState from, BehaviorState to, boolean urgent, String when) {
        return bridge(profile, List.of(), from, to, urgent, when);
    }

    /** Records the beats of one change; {@code before} replays earlier states so a warning already given counts. */
    private static JsonObject bridge(BehaviorProfile profile, List<BehaviorState> before, BehaviorState from, BehaviorState to,
            boolean urgent, String when) {
        var choreo = new Choreographer(profile, SEED);
        var path = new ArrayList<>(before);
        path.add(from);
        choreo.reset(path.getFirst());
        for (int i = 1; i < path.size(); i++) choreo.enter(path.get(i - 1), path.get(i), false);
        choreo.enter(from, to, urgent);
        var beats = new JsonArray();
        var first = new JsonObject();
        first.addProperty("action", choreo.action().name());
        first.addProperty("ticks", choreo.remaining());
        beats.add(first);
        for (var beat : choreo.queued()) {
            var b = new JsonObject();
            b.addProperty("action", beat.action().name());
            b.addProperty("ticks", beat.ticks());
            beats.add(b);
        }
        var steady = new Choreographer(profile, SEED);
        steady.reset(to);
        steady.advance(1, switch (to) {
            case ROAM, RETURN_HOME, REGROUP, INVESTIGATE, SEARCH, SEEK_WATER -> true;
            default -> false;
        });
        var last = new JsonObject();
        last.addProperty("action", steady.action().name());
        last.addProperty("ticks", 0);
        if (beats.isEmpty() || !beats.get(beats.size() - 1).getAsJsonObject().get("action").getAsString().equals(steady.action().name())) beats.add(last);
        var o = new JsonObject();
        o.addProperty("species", profile.species());
        o.addProperty("from", from.name());
        o.addProperty("to", to.name());
        o.addProperty("when", when);
        o.addProperty("urgent", urgent);
        o.add("beats", beats);
        return o;
    }

    // --------------------------------------------------------------------------------- flyers

    private static JsonObject flyerModel() {
        var o = new JsonObject();
        o.addProperty("id", "flyer");
        o.addProperty("title", "Flyer");
        o.addProperty("realms", "air");
        o.addProperty("predator", false);
        o.addProperty("summary", "No day schedule, hunger or thirst. Each bird flies smooth laps around its nest (wide loops, "
                + "figure eights over the nest, thermal circles that gain height), then lands, perches, looks around and takes "
                + "off again. Egg thieves and, for apex flyers, intruders are circled and swooped.");
        var tiers = new JsonArray();
        var full = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(full, "ROAM", "SOAR", "lap finished, a thermal was chosen");
        add(full, "SOAR", "ROAM", "thermal lap finished");
        add(full, "ROAM", "RETURN_HOME", "beyond the roam radius");
        add(full, "SOAR", "RETURN_HOME", "beyond the roam radius");
        add(full, "RETURN_HOME", "ROAM", "back within 10 blocks of home");
        add(full, "ROAM", "LAND", "perch timer due, nest safe and reachable");
        add(full, "SOAR", "LAND", "perch timer due, nest safe and reachable");
        add(full, "LAND", "PERCH", "touched down on the nest");
        add(full, "LAND", "TAKEOFF", "nest blocked or gone, water, or 10 s without touchdown");
        add(full, "PERCH", "TAKEOFF", "rest over (5 to 15 s), hurt, or the nest became unsafe");
        add(full, "TAKEOFF", "ROAM", "climbed clear");
        for (var from : new String[]{"ROAM", "SOAR", "RETURN_HOME", "LAND", "TAKEOFF"})
            add(full, from, "DEFENSE_CIRCLE", "egg stolen, or an intruder in an apex flyer's airspace");
        add(full, "PERCH", "DEFENSE_CIRCLE", "egg stolen while perched (takes off into the defense)");
        add(full, "DEFENSE_CIRCLE", "SWOOP", "swoop cooldown over and a clear line to the thief");
        add(full, "SWOOP", "DEFENSE_CIRCLE", "bite landed or missed, 5 s, or a collision");
        add(full, "DEFENSE_CIRCLE", "RETURN_HOME", "thief gone, out of the leash, unseen for 3 s, or timeout");
        add(full, "SWOOP", "RETURN_HOME", "thief gone, out of the leash, or timeout");
        tiers.add(tierMatrix("FULL", full, "Flight phases (FlyingCreatureEntity). Clips follow the motion: flap when climbing, "
                + "glide when level or descending, bank left or right in turns, hover on approach."));
        var ambient = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(ambient, "ROAM", "SOAR", "lap finished, a thermal was chosen");
        add(ambient, "SOAR", "ROAM", "thermal lap finished");
        add(ambient, "ROAM", "LAND", "perch timer due");
        add(ambient, "LAND", "PERCH", "touched down");
        add(ambient, "PERCH", "TAKEOFF", "rest over");
        add(ambient, "TAKEOFF", "ROAM", "climbed clear");
        tiers.add(tierMatrix("AMBIENT", ambient, "Same laps and perching without territory scans; collision checks every 3 ticks."));
        var dormant = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(dormant, "PERCH", "PERCH", "stays perched");
        add(dormant, "ROAM", "ROAM", "keeps its lap, collision checks every 10 ticks");
        tiers.add(tierMatrix("DORMANT", dormant, "A perched bird stays on its nest; one in the air finishes its lap cheaply."));
        o.add("tiers", tiers);
        o.add("bridges", new JsonArray());
        o.add("curves", sampleLaps());
        return o;
    }

    /** One sample lap per curve kind around a nest at the origin: {x, y, z} offsets in blocks, a point per 3.75 degrees. */
    private static JsonArray sampleLaps() {
        var curves = new JsonArray();
        var random = new SplittableRandom(SEED);
        for (var kind : FlightPath.Kind.values()) {
            FlightPath.Shape shape;
            do shape = FlightPath.pick(random, 48, 14, true); while (shape.kind() != kind);
            var points = new JsonArray();
            for (int i = 0; i <= 96; i++) {
                var point = new JsonArray();
                for (double v : FlightPath.offset(shape, Math.PI * 2 * i / 96)) point.add(Math.round(v * 10) / 10.0);
                points.add(point);
            }
            var curve = new JsonObject();
            curve.addProperty("kind", kind.name());
            curve.add("points", points);
            curves.add(curve);
        }
        return curves;
    }

    // -------------------------------------------------------------------------------- aquatic

    private static JsonObject aquaticModel() {
        var o = new JsonObject();
        o.addProperty("id", "aquatic");
        o.addProperty("title", "Aquatic");
        o.addProperty("realms", "water");
        o.addProperty("predator", true);
        o.addProperty("summary", "Kept simple for now: cruise inside the home pool, investigate, warn, hunt and feed, or flee "
                + "(Cnidaria), with no sleep, thirst or grazing. Swimming banks use the rig's left and right swim clips.");
        var tiers = new JsonArray();
        tiers.add(tierMatrix("FULL", mindMatrix(true, true), "Same decision model as land, without sleep, water or grazing."));
        var ambient = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(ambient, "ROAM", "REST", "cruise leg finished: hover a while");
        add(ambient, "REST", "ROAM", "hover over: next cruise leg");
        tiers.add(tierMatrix("AMBIENT", ambient, "Slow cruise legs inside the water column, no senses."));
        var dormant = new TreeMap<String, Map<String, TreeSet<String>>>();
        add(dormant, "REST", "REST", "hovers in place");
        tiers.add(tierMatrix("DORMANT", dormant, "Hovers; containment inside the water column still runs."));
        o.add("tiers", tiers);
        o.add("bridges", new JsonArray());
        return o;
    }

    /** Entry point for the showcase export outside datagen: prints the document with the default radii. */
    public static void main(String[] args) {
        var json = export(new BehaviorTier.Radii(64, 128, 256, 8), 13000, 23000, 0.5);
        System.out.println(new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(json));
    }

    private BehaviorModels() {}
}
