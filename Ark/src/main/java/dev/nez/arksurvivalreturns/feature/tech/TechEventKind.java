package dev.nez.arksurvivalreturns.feature.tech;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What happened, for the trigger engine. The event carries only the context a node can care about. */
public enum TechEventKind implements StringRepresentable {
    OBTAIN("obtain"),
    CONSUME("consume"),
    PLACE_BLOCK("place_block"),
    SLEEP("sleep"),
    TAME("tame"),
    TROUGH_FEED("trough_feed"),
    TAME_WORK("tame_work"),
    TAME_KILL("tame_kill"),
    CRAFT("craft"),
    DAMAGE_CREATURE("damage_creature"),
    LIGHT_TORCH("light_torch");

    public static final Codec<TechEventKind> CODEC = StringRepresentable.fromEnum(TechEventKind::values);
    private final String name;

    TechEventKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
