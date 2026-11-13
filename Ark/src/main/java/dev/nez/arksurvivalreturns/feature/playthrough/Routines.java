package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.List;

import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import net.minecraft.world.level.block.Blocks;

/**
 * The routines the director can run. Each stage gathers and crafts what the tech tree asks at that point, then ends
 * with a kill streak (the stage's weapon) and a damage test (the stage's armour). {@code smoke} is the same with the
 * three smallest species only, to check the run itself in a few minutes.
 *
 * <p>Hand-written for now; tools/build_playthrough.py is to generate the gather and craft lists from the tech tree and
 * the workstation catalogue (F43, build order step 2).
 */
public final class Routines {
    public static final List<String> NAMES = List.of("standard", "smoke");
    private static final String NS = "arksurvivalreturns:";
    /** Measurement segments run faster than real time; game ticks are what is recorded, so the numbers hold. */
    private static final int FAST_RATE = 60;

    static List<Step> get(String name) {
        return switch (name) {
            case "standard" -> build(Integer.MAX_VALUE);
            case "smoke" -> build(3);
            default -> null;
        };
    }

    private static List<Step> build(int species) {
        var steps = new ArrayList<Step>();
        steps.add(Verbs.command("gamemode survival @a"));
        steps.add(Verbs.command("clear @a"));
        steps.add(Verbs.command("arklevel set @a 1"));
        steps.add(Verbs.checkpoint("start"));

        // R01: rock and wood, the first tools.
        steps.add(Verbs.stage("R01"));
        steps.add(Verbs.breakBlock(PrimitiveContent.LOOSE_ROCK.get(), 4, 4, 16));
        steps.add(Verbs.breakBlock(Blocks.OAK_LOG, 6, 4, 8));
        steps.add(Verbs.breakBlock(Blocks.SHORT_GRASS, 8, 4, 24));
        steps.add(Verbs.craft("minecraft:oak_planks", 4));
        steps.add(Verbs.craft(NS + "stone_hatchet", 1));
        steps.add(Verbs.craft(NS + "rock_pickaxe", 1));
        steps.add(Verbs.craft(NS + "rock_sword", 1));
        steps.add(Verbs.craft(NS + "flint_knife", 1));
        steps.add(Verbs.checkpoint("R01 crafted"));
        measure(steps, "R01", NS + "rock_sword", new String[0], species);

        // R02: keratin arms and armour.
        steps.add(Verbs.stage("R02"));
        steps.add(Verbs.craft(NS + "keratin_spear", 1));
        for (String piece : keratin()) steps.add(Verbs.craft(piece, 1));
        steps.add(Verbs.checkpoint("R02 crafted"));
        measure(steps, "R02", NS + "keratin_spear", keratin(), species);

        // R03: bronze arms and armour.
        steps.add(Verbs.stage("R03"));
        steps.add(Verbs.craft(NS + "bronze_longsword", 1));
        for (String piece : bronze()) steps.add(Verbs.craft(piece, 1));
        steps.add(Verbs.checkpoint("R03 crafted"));
        measure(steps, "R03", NS + "bronze_longsword", bronze(), species);

        // Supply drops: every tier's table rolled, the contents written for the report to rate.
        steps.add(Verbs.stage("LOOT"));
        for (var tier : SupplyTier.values()) steps.add(Verbs.lootRolls(tier, species == Integer.MAX_VALUE ? 50 : 5));
        steps.add(Verbs.checkpoint("end"));
        return steps;
    }

    /** The stage's kill streak and damage test, sped up. */
    private static void measure(List<Step> steps, String stage, String weapon, String[] armour, int species) {
        steps.add(Verbs.wield(weapon));
        steps.add(Verbs.unequip());
        if (armour.length > 0) steps.add(Verbs.equip(armour));
        steps.add(Verbs.teleport(CombatSteps.LANE_X, 0, CombatSteps.LANE_Z + 8, 180, 10));
        steps.add(Verbs.shot(stage + "_lane", 40));
        steps.add(Verbs.command("tick rate " + FAST_RATE));
        steps.add(CombatSteps.all(stage + " " + weapon.substring(weapon.indexOf(':') + 1), 1, true, species));
        steps.add(CombatSteps.all(stage + " " + (armour.length == 0 ? "no armour" : armour[0].substring(armour[0].indexOf(':') + 1)), 1, false, species));
        steps.add(Verbs.command("tick rate 20"));
    }

    private static String[] keratin() { return set("keratin"); }
    private static String[] bronze() { return set("bronze"); }
    private static String[] set(String material) {
        return new String[]{NS + material + "_helmet", NS + material + "_chestplate", NS + material + "_leggings", NS + material + "_boots"};
    }

    private Routines() {}
}
