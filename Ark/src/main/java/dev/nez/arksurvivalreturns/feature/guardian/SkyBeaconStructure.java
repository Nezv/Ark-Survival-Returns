package dev.nez.arksurvivalreturns.feature.guardian;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One template and its saved guardian per start; terrain sampling never requests live chunks. */
public final class SkyBeaconStructure extends Structure {
    public static final Identifier ID = ArkSurvivalReturns.id("sky_beacon");
    public static final String[] VARIANTS = {"red", "white", "black"};
    /** Template bounds, the nest in the monolith's eye and the crate beside it, as tools/build_sky_beacons.py writes them. */
    public static final Vec3i SIZE = new Vec3i(33, 100, 33);
    public static final BlockPos NEST = new BlockPos(16, 63, 16), CRATE = new BlockPos(12, 63, 15);
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ArkSurvivalReturns.id("chests/sky_beacon"));
    public static final MapCodec<SkyBeaconStructure> CODEC = simpleCodec(SkyBeaconStructure::new);
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ArkSurvivalReturns.MOD_ID);
    public static final java.util.function.Supplier<StructureType<SkyBeaconStructure>> TYPE =
            TYPES.register("sky_beacon", () -> () -> CODEC);
    public static final java.util.function.Supplier<StructurePieceType> PIECE =
            PIECES.register("sky_beacon", () -> (context, tag) -> new Piece(context.structureTemplateManager(), tag));

    public SkyBeaconStructure(StructureSettings settings) { super(settings); }

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        int ground = context.chunkGenerator().getSeaLevel();
        // The monolith hangs just above the ground or the sea. It widens by a block for every three it rises,
        // so the ground away from its axis may stand that much higher than the ground under the tip.
        for (int dx = -16; dx <= 16; dx += 4) for (int dz = -16; dz <= 16; dz += 4)
            ground = Math.max(ground, context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz,
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState())
                    - Math.max(0, Math.max(Math.abs(dx), Math.abs(dz)) - 4) * 3);
        // The template's lowest rows hold only trailing vines; never truncate the crown at build height.
        if (ground + SIZE.getY() >= context.heightAccessor().getMaxY()) return Optional.empty();
        BlockPos origin = new BlockPos(x - NEST.getX(), ground + 1, z - NEST.getZ());
        String variant = VARIANTS[context.random().nextInt(VARIANTS.length)];
        return Optional.of(new GenerationStub(origin.offset(NEST), builder ->
                builder.addPiece(new Piece(context.structureTemplateManager(), origin, variant))));
    }

    @Override public StructureType<?> type() { return TYPE.get(); }

    public static final class Piece extends TemplateStructurePiece {
        public Piece(StructureTemplateManager manager, BlockPos origin, String variant) {
            super(PIECE.get(), 0, manager, ArkSurvivalReturns.id("sky_beacon/" + variant),
                    ArkSurvivalReturns.id("sky_beacon/" + variant).toString(), settings(), origin);
        }
        public Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(PIECE.get(), tag, manager, ignored -> settings());
        }
        private static StructurePlaceSettings settings() {
            // Known shape: the vines are authored with their faces, and a chunk-by-chunk shape update would drop
            // the ones whose wall lies in a chunk that is not placed yet.
            return new StructurePlaceSettings().setIgnoreEntities(false).setKnownShape(true)
                    .setFinalizeEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }
        @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level,
                net.minecraft.world.level.StructureManager structures, net.minecraft.world.level.chunk.ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBox, net.minecraft.world.level.ChunkPos chunk, BlockPos reference) {
            placeSettings.setIgnoreEntities(!dev.nez.arksurvivalreturns.Config.GUARDIAN_ENABLED.get()
                    || !dev.nez.arksurvivalreturns.Config.NATURAL_SPAWNS.get()
                    || !level.getLevel().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS));
            super.postProcess(level, structures, generator, random, chunkBox, chunk, reference);
        }
        @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                RandomSource random, BoundingBox box) {}
    }
}
