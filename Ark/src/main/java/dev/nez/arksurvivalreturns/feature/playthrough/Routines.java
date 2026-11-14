package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.List;

import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;

/**
 * The routines the director can run. The stages follow the tech tree's lanes, Prehistoric to Bronze, then the
 * Armoury's gold and diamond gear: each one has the player earn what its nodes ask ({@link Gathering#obtain}: break,
 * hunt, process, craft), and every stage that brings a new weapon or armour set ends with a kill streak and a damage
 * test against every species at the stage's creature level. Then the sweeps: every weapon (melee and ranged), armour
 * set and recipe earned, every block, screen and supply-drop tier once; then creatures set on each other. Nothing is
 * given; a level gate the play has not reached is lifted and written down ({@link Gathering#lift}), never ground for.
 * {@code smoke} is the same with the three smallest species and few loot rolls, to check the run itself.
 */
public final class Routines {
    public static final List<String> NAMES = List.of("standard", "smoke", "stages", "sweeps", "fights", "recipes", "economy");
    private static final String NS = "arksurvivalreturns:";

    static List<Step> get(String name) {
        return switch (name) {
            case "standard" -> build(Integer.MAX_VALUE, 50, true, true, true);
            case "smoke" -> build(3, 5, true, true, false);
            case "stages" -> build(Integer.MAX_VALUE, 0, true, false, false);
            case "sweeps" -> build(Integer.MAX_VALUE, 50, false, true, false);
            case "fights" -> fightsOnly();
            // The whole recipe tree: every item any bench makes, vanilla's too; long.
            case "recipes" -> recipesOnly();
            // The stages and the recipe sweep with no measurements: the GameTest's run.
            case "economy" -> economy();
            default -> null;
        };
    }

    private static List<Step> start() {
        var steps = new ArrayList<Step>();
        steps.add(Verbs.command("gamemode survival @a"));
        // A safety net, reported: a player who dies anyway keeps the bags, so the run can go on and say so.
        steps.add(Verbs.command("gamerule keepInventory true"));
        steps.add(Verbs.checkpoint("start"));
        return steps;
    }

    private static List<Step> build(int species, int lootRolls, boolean stages, boolean sweeps, boolean fights) {
        var steps = start();
        if (stages) stages(steps, species);
        if (sweeps) sweeps(steps, lootRolls);
        if (fights) { steps.add(Verbs.stage("FIGHTS")); steps.add(CombatSteps.fights(15)); steps.add(Verbs.land(0, 300)); }
        steps.add(Sweeps.tech("end"));
        steps.add(Verbs.checkpoint("end"));
        return steps;
    }

    private static List<Step> fightsOnly() {
        var steps = start();
        steps.add(Verbs.stage("FIGHTS"));
        steps.add(CombatSteps.fights(15));
        steps.add(Verbs.land(0, 300));
        return steps;
    }

    private static List<Step> recipesOnly() {
        var steps = start();
        stages(steps, 0);
        steps.add(Verbs.stage("RECIPES"));
        steps.add(Sweeps.recipes(true));
        steps.add(Sweeps.tech("end"));
        steps.add(Verbs.checkpoint("end"));
        return steps;
    }

    private static List<Step> economy() {
        var steps = new ArrayList<Step>();
        steps.add(Verbs.checkpoint("start"));
        stages(steps, 0);
        steps.add(Verbs.stage("RECIPES"));
        steps.add(Sweeps.recipes());
        steps.add(Sweeps.tech("end"));
        steps.add(Verbs.checkpoint("end"));
        return steps;
    }

    private static void stages(List<Step> steps, int species) {
        // R01, Prehistoric: rock, berries, rock tools; hide from the first hunts.
        stage(steps, "R01", "rock 4", "stone_hatchet", "tintoberry 3", "amarberry 1", "azulberry 1", "narcoberry 1", "rock_pickaxe",
                "rock_sword", "rock_shovel", "dried_meat 3", "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots");
        // The first preserved meal, eaten (Dried Meat III, which a tech node asks for, takes three game days on the rack).
        steps.add(Verbs.eat(NS + "dried_meat"));
        measure(steps, "R01", NS + "rock_sword", hide(), 1, species);

        // R02: flint, keratin, the spear and the keratin set.
        stage(steps, "R02", "flint_knife", "keratin 3", "keratin_spear", "keratin_helmet", "keratin_chestplate", "keratin_leggings", "keratin_boots");
        measure(steps, "R02", NS + "keratin_spear", set("keratin"), 5, species);

        // R03: camp and companions: saddlery, mattress, a fire, dishes, narcotics, the first tame (passive, fed by hand).
        // The fire's spit cooks each meat before the pot is seated on it for the dishes.
        stage(steps, "R03", "saddlery", "mattress", "stone_fire", "cooked_herbivore_meat 2", "cooked_carnivore_meat 1", "cooked_bird_meat 1",
                "hearty_stew", "trail_mix");
        // While the fire still burns under the pot: a stick lit on it.
        steps.add(Sweeps.torch());
        goals(steps, "dried_ration 1", "narcotics 1");
        steps.add(Sweeps.sleep(NS + "mattress"));
        steps.add(Sweeps.tamed(Species.PARASAUR, 5, NS + "azulberry", NS + "narcotics", false));
        // A worker: a Triceratops knocked out with the player's narcotics, kept for a tame's life (work, a kill, the trough).
        steps.add(Sweeps.tamed(Species.TRICERATOPS, 5, "minecraft:carrot", NS + "narcotics", true));
        steps.add(Sweeps.companion());

        // R04: the Prehistoric finale; the bow and the first tranquilizer arrows.
        stage(steps, "R04", "primitive_forge", "minecraft:bow", "tranquilizer_arrow 8");
        steps.add(Sweeps.tech("R04"));

        // R05, Bronze: ingots, tools, longsword, hammer and the bronze set.
        stage(steps, "R05", "bronze_ingot 4", "bronze_pickaxe", "bronze_axe", "bronze_shovel", "bronze_longsword", "bronze_hammer",
                "bronze_helmet", "bronze_chestplate", "bronze_leggings", "bronze_boots");
        measure(steps, "R05", NS + "bronze_longsword", set("bronze"), 15, species);

        // R06: hearth, minerals, field medicine; the Therizinosaurus tame (knocked out with the player's narcotics).
        stage(steps, "R06", "bedroll", "minecraft:coal 2", "minecraft:iron_ingot 2", "sulphur 2", "minecraft:gunpowder 2",
                "minecraft:glass 2", "medicine_bench", "herbal_bandage", "vitamins");
        steps.add(Sweeps.sleep(NS + "bedroll"));
        steps.add(Sweeps.tamed(Species.THERIZINOSAURUS, 5, "minecraft:carrot", NS + "narcotics", false));

        // R07: the Bronze finale.
        stage(steps, "R07", "explosive_arrow");
        steps.add(Sweeps.tech("R07"));

        // R08: the Armoury's gold (the Iron Age is not registered yet, F17).
        stage(steps, "R08", "minecraft:gold_ingot 8", "minecraft:golden_sword", "minecraft:golden_pickaxe",
                "minecraft:golden_helmet", "minecraft:golden_chestplate", "minecraft:golden_leggings", "minecraft:golden_boots");
        measure(steps, "R08", "minecraft:golden_sword", vanilla("golden"), 25, species);

        // R09: the Armoury's diamond.
        stage(steps, "R09", "minecraft:diamond 8", "minecraft:diamond_sword", "minecraft:diamond_pickaxe",
                "minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots");
        measure(steps, "R09", "minecraft:diamond_sword", vanilla("diamond"), 40, species);
    }

    private static void sweeps(List<Step> steps, int lootRolls) {
        // Everything here is earned too; what the stages left is used first.
        steps.add(Verbs.stage("WEAPONS"));
        steps.add(Sweeps.weapons());
        steps.add(Verbs.stage("ARMOUR"));
        steps.add(Sweeps.armour());
        steps.add(Verbs.stage("RECIPES"));
        steps.add(Sweeps.recipes());
        // What the crusher grinds besides gunpowder, once each.
        steps.add(Verbs.stage("STATIONS"));
        for (String ground : List.of("minecraft:bone_meal", "minecraft:sugar", "minecraft:red_dye", "minecraft:yellow_dye")) steps.add(Gathering.obtain(ground, 1));
        steps.add(Verbs.stage("BLOCKS"));
        steps.add(Sweeps.blocks());
        steps.add(Verbs.stage("SCREENS"));
        steps.add(Sweeps.screens());
        steps.add(Verbs.stage("DROPS"));
        steps.add(Sweeps.drops());
        steps.add(Verbs.stage("LOOT"));
        for (var tier : SupplyTier.values()) steps.add(Verbs.lootRolls(tier, lootRolls));
    }

    /** A stage: each goal earned in order ("item" or "item count"; ids without a namespace are Ark's), then a snapshot. */
    private static void stage(List<Step> steps, String name, String... goals) {
        steps.add(Verbs.stage(name));
        goals(steps, goals);
        steps.add(Verbs.checkpoint(name));
        steps.add(Sweeps.tech(name));
    }

    private static void goals(List<Step> steps, String... goals) {
        for (String goal : goals) {
            var parts = goal.split(" ");
            String item = parts[0].contains(":") ? parts[0] : NS + parts[0];
            steps.add(Gathering.obtain(item, parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
        }
    }

    /** The stage's kill streak and damage test, with creatures of the stage's level, at the normal tick rate. */
    private static void measure(List<Step> steps, String stage, String weapon, String[] armour, int creatureLevel, int species) {
        if (species <= 0) return;
        steps.add(Verbs.wield(weapon));
        steps.add(Verbs.unequip());
        steps.add(Verbs.equip(armour));
        steps.add(Verbs.teleport(CombatSteps.LANE_X, 0, CombatSteps.LANE_Z + 8, 180, 10));
        steps.add(Verbs.shot(stage + "_lane", 40));
        steps.add(CombatSteps.all(stage + " " + weapon.substring(weapon.indexOf(':') + 1), creatureLevel, true, species));
        steps.add(Verbs.wield(weapon));
        steps.add(CombatSteps.all(stage + " " + armour[0].substring(armour[0].indexOf(':') + 1).replaceAll("_[a-z]+$", ""), creatureLevel, false, species));
    }

    private static String[] hide() { return vanilla("leather"); }

    private static String[] vanilla(String material) {
        return new String[]{"minecraft:" + material + "_helmet", "minecraft:" + material + "_chestplate", "minecraft:" + material + "_leggings", "minecraft:" + material + "_boots"};
    }

    private static String[] set(String material) {
        return new String[]{NS + material + "_helmet", NS + material + "_chestplate", NS + material + "_leggings", NS + material + "_boots"};
    }

    private Routines() {}
}
