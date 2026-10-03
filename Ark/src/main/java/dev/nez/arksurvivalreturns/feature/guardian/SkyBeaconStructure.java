package dev.nez.arksurvivalreturns.feature.guardian;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One template and its saved guardian per start; terrain sampling never requests live chunks. */
public final class SkyBeaconStructure extends Structure {
    public static final Identifier ID = ArkSurvivalReturns.id("sky_beacon");
    public static final String[] VARIANTS = {"red", "white", "black"};
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
        int terrain = context.chunkGenerator().getSeaLevel();
        // Sample the whole 49-block footprint, including the high corners under the floating wings.
        for (int dx = -24; dx <= 24; dx += 8) for (int dz = -24; dz <= 24; dz += 8)
            terrain = Math.max(terrain, context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz,
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
        int bottom = Math.max(144, terrain + 32);
        // Never bury the tip in a peak or truncate the crown at build height.
        if (bottom + 97 >= context.heightAccessor().getMaxY()) return Optional.empty();
        BlockPos origin = new BlockPos(x - 24, bottom, z - 24);
        String variant = VARIANTS[context.random().nextInt(VARIANTS.length)];
        return Optional.of(new GenerationStub(origin.offset(24, 50, 24), builder ->
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
            return new StructurePlaceSettings().setIgnoreEntities(false)
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
