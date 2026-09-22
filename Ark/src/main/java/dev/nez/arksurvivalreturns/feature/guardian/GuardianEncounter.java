package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Persistent state of one ritual anchor, keyed by dimension, structure instance and structure id.
 *
 * <p>The record is immutable; every transition produces a new value and stores it through
 * {@link GuardianData#put}. A server restart or chunk unload therefore never duplicates the boss,
 * consumes a heart twice or issues rewards twice.
 */
public record GuardianEncounter(
        String key,
        ResourceKey<Level> dimension,
        BlockPos anchor,
        Identifier structure,
        UUID tribe,
        GuardianState state,
        Optional<UUID> boss,
        Set<UUID> participants,
        Set<UUID> tames,
        double bossMaxHealth,
        boolean rewardsIssued,
        long resetAt,
        long emptySince
) {
    public static final Codec<GuardianEncounter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(GuardianEncounter::key),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(GuardianEncounter::dimension),
            BlockPos.CODEC.fieldOf("anchor").forGetter(GuardianEncounter::anchor),
            Identifier.CODEC.fieldOf("structure").forGetter(GuardianEncounter::structure),
            UUIDUtil.STRING_CODEC.fieldOf("tribe").forGetter(GuardianEncounter::tribe),
            GuardianState.CODEC.optionalFieldOf("state", GuardianState.LOCKED).forGetter(GuardianEncounter::state),
            UUIDUtil.STRING_CODEC.optionalFieldOf("boss").forGetter(GuardianEncounter::boss),
            uuidSetCodec().optionalFieldOf("participants", Set.of()).forGetter(GuardianEncounter::participants),
            uuidSetCodec().optionalFieldOf("tames", Set.of()).forGetter(GuardianEncounter::tames),
            Codec.DOUBLE.optionalFieldOf("boss_max_health", 0.0).forGetter(GuardianEncounter::bossMaxHealth),
            Codec.BOOL.optionalFieldOf("rewards_issued", false).forGetter(GuardianEncounter::rewardsIssued),
            Codec.LONG.optionalFieldOf("reset_at", 0L).forGetter(GuardianEncounter::resetAt),
            Codec.LONG.optionalFieldOf("empty_since", 0L).forGetter(GuardianEncounter::emptySince)
    ).apply(instance, GuardianEncounter::new));

    private static Codec<Set<UUID>> uuidSetCodec() {
        return UUIDUtil.STRING_CODEC.listOf().xmap(LinkedHashSet::new, List::copyOf);
    }

    /** Stable instance key: dimension, the structure start chunk and the structure id. */
    public static String key(ResourceKey<Level> dimension, Identifier structure, StructureStart start) {
        return dimension.identifier() + "|" + start.getChunkPos().pack() + "|" + structure;
    }

    public GuardianEncounter withState(GuardianState newState) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, newState, boss, participants,
                tames, bossMaxHealth, rewardsIssued, resetAt, emptySince);
    }

    public GuardianEncounter withBoss(UUID newBoss) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, Optional.ofNullable(newBoss),
                participants, tames, bossMaxHealth, rewardsIssued, resetAt, emptySince);
    }

    public GuardianEncounter withParticipants(Set<UUID> newParticipants) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, Set.copyOf(newParticipants),
                tames, bossMaxHealth, rewardsIssued, resetAt, emptySince);
    }

    public GuardianEncounter withTames(Set<UUID> newTames) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, participants,
                Set.copyOf(newTames), bossMaxHealth, rewardsIssued, resetAt, emptySince);
    }

    public GuardianEncounter withBossMaxHealth(double health) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, participants,
                tames, health, rewardsIssued, resetAt, emptySince);
    }

    public GuardianEncounter withRewardsIssued(boolean issued) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, participants,
                tames, bossMaxHealth, issued, resetAt, emptySince);
    }

    public GuardianEncounter withResetAt(long time) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, participants,
                tames, bossMaxHealth, rewardsIssued, time, emptySince);
    }

    public GuardianEncounter withEmptySince(long time) {
        return new GuardianEncounter(key, dimension, anchor, structure, tribe, state, boss, participants,
                tames, bossMaxHealth, rewardsIssued, resetAt, time);
    }

    /** True when this encounter's record owns the given boss entity. */
    public boolean owns(UUID entity) {
        return state == GuardianState.ACTIVE && boss.filter(entity::equals).isPresent();
    }
}
