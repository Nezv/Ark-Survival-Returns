package dev.nez.arksurvivalreturns.feature.drop;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The four supply drops, rarest last. The colour is the parachute's, the crate's paint, the beam's and the
 * chat line's (tools/build_drop_assets.py repeats it); a drop is rolled by weight.
 */
public enum SupplyTier implements StringRepresentable {
    WHITE("white", 3, 0xECEADF, 50),
    GREEN("green", 5, 0x4A9646, 30),
    BLUE("blue", 7, 0x3A6EBE, 15),
    PURPLE("purple", 9, 0x8442AC, 5);

    public static final StringRepresentable.EnumCodec<SupplyTier> CODEC = StringRepresentable.fromEnum(SupplyTier::values);

    public final String id;
    public final int slots, colour, weight;
    public final ResourceKey<LootTable> loot;

    SupplyTier(String id, int slots, int colour, int weight) {
        this.id = id;
        this.slots = slots;
        this.colour = colour;
        this.weight = weight;
        this.loot = ResourceKey.create(Registries.LOOT_TABLE, ArkSurvivalReturns.id("chests/supply_drop/" + id));
    }

    @Override public String getSerializedName() { return id; }

    /** "White Loot Crate" and so on, in the tier's colour. */
    public MutableComponent crateName() {
        return Component.translatable("container.arksurvivalreturns.loot_crate." + id).withColor(colour);
    }

    public static SupplyTier byId(String id) {
        return CODEC.byName(id, WHITE);
    }

    public static SupplyTier roll(RandomSource random) {
        int total = 0;
        for (SupplyTier tier : values()) total += tier.weight;
        int pick = random.nextInt(total);
        for (SupplyTier tier : values()) if ((pick -= tier.weight) < 0) return tier;
        return WHITE;
    }
}
