package dev.nez.arksurvivalreturns.feature.tech;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** A node's role in the tree: the three main paths, the age-changing gates and optional side quests. */
public enum TechNodeKind implements StringRepresentable {
    MAIN("main"),
    GATE("gate"),
    SIDE("side");

    public static final Codec<TechNodeKind> CODEC = StringRepresentable.fromEnum(TechNodeKind::values);
    private final String name;

    TechNodeKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
