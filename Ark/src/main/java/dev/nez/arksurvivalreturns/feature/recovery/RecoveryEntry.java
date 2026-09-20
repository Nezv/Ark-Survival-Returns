package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One recoverable inventory. The items live here, not in the world block: the marker is only a
 * visible place to walk back to, and destroying it never deletes anything.
 */
public record RecoveryEntry(UUID id, ResourceKey<Level> dimension, Optional<BlockPos> pos, List<ItemStack> items) {
    public static final Codec<RecoveryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RecoveryEntry::id),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(RecoveryEntry::dimension),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(RecoveryEntry::pos),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(RecoveryEntry::items)
    ).apply(instance, RecoveryEntry::new));

    public boolean placed() {
        return pos.isPresent();
    }

    public RecoveryEntry unplaced() {
        return new RecoveryEntry(id, dimension, Optional.empty(), items);
    }

    public RecoveryEntry withItems(List<ItemStack> replacement) {
        return new RecoveryEntry(id, dimension, pos, List.copyOf(replacement));
    }
}
