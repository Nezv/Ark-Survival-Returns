package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/**
 * Optional Ancient Remnants integration.
 *
 * <p>Ancient Remnants has no activation block: its monoliths are jigsaw structures whose floating
 * centre is the invulnerable {@code ancient_remnants:elderheart} entity. The encounter therefore
 * anchors on an existing block that is verified to be inside the designated structure and close to
 * that monolith. Nothing about the third-party structure is edited or copied.
 *
 * <p>Every method fails closed when the mod is absent, so Ark loads and plays without it.
 */
public final class GuardianStructure {
    public static final String MOD_ID = "ancient_remnants";
    public static final Identifier ELDERHEART = Identifier.fromNamespaceAndPath(MOD_ID, "elderheart");

    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** The configured structure ids, parsed and de-duplicated. */
    public static List<Identifier> structures() {
        List<Identifier> ids = new ArrayList<>();
        for (String value : Config.GUARDIAN_STRUCTURES.get()) {
            Identifier id = Identifier.tryParse(value);
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    public static Optional<Structure> structure(ServerLevel level, Identifier id) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return registry.get(id).map(Holder.Reference::value);
    }

    /** The valid structure start that owns the position, without ever loading a chunk. */
    public static Optional<StructureStart> startAt(ServerLevel level, BlockPos pos, Identifier id) {
        if (!available() || !level.isLoaded(pos)) return Optional.empty();
        return structure(level, id)
                .map(structure -> level.structureManager().getStructureWithPieceAt(pos, structure))
                .filter(StructureStart::isValid);
    }

    /** True when a floating monolith of the mod stands within the radius of a block. */
    public static boolean monolithNear(ServerLevel level, BlockPos pos, double radius) {
        if (!available()) return false;
        AABB box = new AABB(pos).inflate(radius);
        return !level.getEntitiesOfClass(Entity.class, box,
                entity -> ELDERHEART.equals(EntityType.getKey(entity.getType()))).isEmpty();
    }

    /** Every loaded chunk the arena circle touches must already be loaded; nothing is force-loaded. */
    public static boolean arenaLoaded(ServerLevel level, BlockPos center, int radius) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunkAt(new BlockPos(chunkX << 4, center.getY(), chunkZ << 4))) return false;
            }
        }
        return true;
    }

    private GuardianStructure() {}
}
