package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Every Ark accessory: the Curios slot it fits, its group and its attribute bonuses. Behaviour that is not
 * an attribute (gliding, climbing, ambush...) lives in {@link AccessoryEffects} and reads {@link Worn}.
 *
 * <p>Primitive gadgets are the tools prehistoric people actually wore: slit goggles, a pack frame, snowshoes,
 * a stone wristguard, bone skates. Relics are the mystical set: amulets, rings, a crown and creature totems.
 * The look (icon and worn model) comes from tools/build_accessories.py.
 */
public enum Accessory {
    // ------------------------------------------------------------------ primitive gadgets
    BONE_SNOW_GOGGLES(Slot.HEAD, Group.PRIMITIVE),
    PELT_HOOD(Slot.HEAD, Group.PRIMITIVE,
            bonus(() -> AccessoryAttributes.SCENT, -1.0, Operation.ADD_MULTIPLIED_BASE),
            bonus(() -> AccessoryAttributes.VISIBILITY, -0.1, Operation.ADD_MULTIPLIED_BASE)),
    ANTLER_FRONTLET(Slot.HEAD, Group.PRIMITIVE),
    FANG_NECKLACE(Slot.NECKLACE, Group.PRIMITIVE),
    MEMBRANE_GLIDER(Slot.BACK, Group.PRIMITIVE),
    PACK_FRAME(Slot.BACK, Group.PRIMITIVE,
            bonus(() -> AccessoryAttributes.CARRY_CAPACITY, 0.4, Operation.ADD_MULTIPLIED_BASE)),
    HIDE_QUIVER(Slot.BACK, Group.PRIMITIVE),
    FUR_MANTLE(Slot.BACK, Group.PRIMITIVE, bonus(() -> Attributes.ARMOR, 1.0, Operation.ADD_VALUE)),
    GHILLIE_WRAP(Slot.BODY, Group.PRIMITIVE,
            bonus(() -> AccessoryAttributes.VISIBILITY, -0.4, Operation.ADD_MULTIPLIED_BASE)),
    BONE_VEST(Slot.BODY, Group.PRIMITIVE,
            bonus(() -> Attributes.ARMOR, 3.0, Operation.ADD_VALUE),
            bonus(() -> Attributes.KNOCKBACK_RESISTANCE, 0.1, Operation.ADD_VALUE),
            bonus(() -> Attributes.MOVEMENT_SPEED, -0.05, Operation.ADD_MULTIPLIED_TOTAL)),
    THORNPROOF_WRAPS(Slot.LEGS, Group.PRIMITIVE),
    CROC_WADERS(Slot.LEGS, Group.PRIMITIVE,
            bonus(() -> Attributes.WATER_MOVEMENT_EFFICIENCY, 0.4, Operation.ADD_VALUE),
            bonus(() -> Attributes.MOVEMENT_EFFICIENCY, 0.35, Operation.ADD_VALUE)),
    SNOWSHOES(Slot.FEET, Group.PRIMITIVE),
    BONE_SKATES(Slot.FEET, Group.PRIMITIVE),
    STALKER_MOCCASINS(Slot.FEET, Group.PRIMITIVE,
            bonus(() -> AccessoryAttributes.NOISE, -0.7, Operation.ADD_MULTIPLIED_BASE),
            bonus(() -> Attributes.SNEAKING_SPEED, 0.15, Operation.ADD_VALUE)),
    FLIPPER_SANDALS(Slot.FEET, Group.PRIMITIVE,
            bonus(() -> NeoForgeMod.SWIM_SPEED, 0.6, Operation.ADD_MULTIPLIED_BASE),
            bonus(() -> Attributes.MOVEMENT_SPEED, -0.1, Operation.ADD_MULTIPLIED_TOTAL)),
    CLIMBING_CLAWS(Slot.HANDS, Group.PRIMITIVE),
    SCYTHE_CLAWS(Slot.HANDS, Group.PRIMITIVE),
    ARCHER_WRISTGUARD(Slot.BRACELET, Group.PRIMITIVE),
    THUMB_RING(Slot.RING, Group.PRIMITIVE),
    EMBER_CARRIER(Slot.BELT, Group.PRIMITIVE),
    HERBAL_POUCH(Slot.BELT, Group.PRIMITIVE),
    DIVER_STONES(Slot.BELT, Group.PRIMITIVE, bonus(() -> Attributes.OXYGEN_BONUS, 2.0, Operation.ADD_VALUE)),
    TALLY_BONE(Slot.CHARM, Group.PRIMITIVE),
    // ----------------------------------------------------------------------------- relics
    AMBER_AMULET(Slot.NECKLACE, Group.RELIC),
    ALICORN_PENDANT(Slot.NECKLACE, Group.RELIC),
    QUETZAL_MANTLE(Slot.BACK, Group.RELIC, bonus(() -> Attributes.SAFE_FALL_DISTANCE, 4.0, Operation.ADD_VALUE)),
    GUARDIAN_CROWN(Slot.HEAD, Group.RELIC, bonus(() -> Attributes.ARMOR, 2.0, Operation.ADD_VALUE)),
    OBSIDIAN_BAND(Slot.RING, Group.RELIC, bonus(() -> Attributes.BURNING_TIME, -0.75, Operation.ADD_MULTIPLIED_BASE)),
    AMBER_RING(Slot.RING, Group.RELIC),
    SERPENT_RING(Slot.RING, Group.RELIC),
    KINSHIP_BRACELET(Slot.BRACELET, Group.RELIC),
    RAPTOR_TOTEM(Slot.CHARM, Group.RELIC,
            bonus(() -> Attributes.MOVEMENT_SPEED, 0.1, Operation.ADD_MULTIPLIED_BASE),
            bonus(() -> Attributes.STEP_HEIGHT, 0.5, Operation.ADD_VALUE)),
    REX_TOTEM(Slot.CHARM, Group.RELIC, bonus(() -> Attributes.ATTACK_DAMAGE, 2.0, Operation.ADD_VALUE)),
    ARGENTAVIS_TOTEM(Slot.CHARM, Group.RELIC),
    MEGALODON_TOTEM(Slot.CHARM, Group.RELIC, bonus(() -> NeoForgeMod.SWIM_SPEED, 0.3, Operation.ADD_MULTIPLIED_BASE));

    /** Curios slot types, in the order the Ark inventory shows them. */
    public enum Slot {
        HEAD, NECKLACE, BACK, BODY, BRACELET, HANDS, RING, BELT, LEGS, FEET, CHARM;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Primitive gadgets are camp crafts; relics are the mystical, rarer set. */
    public enum Group { PRIMITIVE, RELIC }

    public record Bonus(Supplier<Holder<Attribute>> attribute, double amount, Operation operation) {}

    public final String id = name().toLowerCase(Locale.ROOT);
    public final Slot slot;
    public final Group group;
    public final List<Bonus> bonuses;

    Accessory(Slot slot, Group group, Bonus... bonuses) {
        this.slot = slot;
        this.group = group;
        this.bonuses = List.of(bonuses);
    }

    private static Bonus bonus(Supplier<Holder<Attribute>> attribute, double amount, Operation operation) {
        return new Bonus(attribute, amount, operation);
    }

    public String effectKey() { return "accessory.arksurvivalreturns." + id + ".effect"; }

    public String loreKey() { return "accessory.arksurvivalreturns." + id + ".lore"; }

    public net.minecraft.world.item.Item item() { return AccessoryContent.ITEMS.get(this).get(); }

    public static Accessory of(net.minecraft.world.item.Item item) {
        return item instanceof AccessoryItem accessory ? accessory.accessory() : null;
    }
}
