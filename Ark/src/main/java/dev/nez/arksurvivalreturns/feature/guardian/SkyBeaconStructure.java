package dev.nez.arksurvivalreturns.feature.guardian;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureSounds;
import dev.nez.arksurvivalreturns.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One template per start, with no dragon in it: a mark on the nest stands for the guardian, which comes when a
 * player first climbs into the eye. Terrain sampling never requests live chunks.
 */
public final class SkyBeaconStructure extends Structure {
    public static final Identifier ID = ArkSurvivalReturns.id("sky_beacon");
    public static final String[] VARIANTS = {"red", "white", "black"};
    /** Template bounds, the nest in the monolith's eye and the crate beside it, as tools/build_sky_beacons.py writes them. */
    public static final Vec3i SIZE = new Vec3i(33, 100, 33);
    public static final BlockPos NEST = new BlockPos(16, 63, 16), CRATE = new BlockPos(12, 63, 15);
    /** The core is the eye: its middle stands this far over the nest, and a player this near is inside or on its lip. */
    public static final int CORE_HEIGHT = 3;
    public static final double CORE_REACH = 7.5;
    /** Tag of the marker entity the template leaves on the nest, and the stem of its palette tag. */
    public static final String MARK = "arksurvivalreturns.sky_beacon";
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

    /** The saved name of one beacon, shared by its arrival and its victory record. */
    public static String key(ServerLevel level, BlockPos nest) {
        return "sky_beacon|" + level.dimension().identifier() + "|" + nest.asLong();
    }

    public static boolean inCore(BlockPos nest, Vec3 position) {
        return position.distanceToSqr(Vec3.atCenterOf(nest.above(CORE_HEIGHT))) <= CORE_REACH * CORE_REACH;
    }

    /** Once a second: a player who has climbed into a beacon's eye brings its dragon. */
    public static void tick(MinecraftServer server) {
        if (!Config.GUARDIAN_ENABLED.get() || !Config.NATURAL_SPAWNS.get()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.isAlive() && !player.isSpectator() && player.level().getGameRules().get(GameRules.SPAWN_MOBS)) watch(player);
    }

    /** Public for tests. Looks only among the loaded entities beside the player; the mark goes when its dragon comes. */
    public static void watch(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Marker mark : level.getEntities(EntityType.MARKER, player.getBoundingBox().inflate(CORE_REACH + CORE_HEIGHT),
                marker -> marker.entityTags().contains(MARK))) {
            BlockPos nest = mark.blockPosition();
            int variant = 0;
            for (int i = 0; i < VARIANTS.length; i++) if (mark.entityTags().contains(MARK + "." + VARIANTS[i])) variant = i;
            if (inCore(nest, player.position()) && wake(level, nest, variant).isPresent()) mark.discard();
        }
    }

    /**
     * Brings a beacon's dragon: it arrives on its flight path round the head, clear of the stone, and never in a
     * chunk that is not loaded. Empty when there is no room for it yet. Public for tests.
     */
    public static Optional<GuardianDragonEntity> wake(ServerLevel level, BlockPos nest, int variant) {
        GuardianDragonEntity dragon = ModContent.GUARDIAN_DRAGON.get().create(level, EntitySpawnReason.STRUCTURE);
        if (dragon == null) return Optional.empty();
        dragon.setBeaconVariant(variant);
        dragon.anchorAt(nest);
        Vec3 at = dragon.patrol();
        dragon.snapTo(at.x, at.y, at.z, (float)(Math.atan2(nest.getZ() + .5 - at.z, nest.getX() + .5 - at.x) * 180 / Math.PI - 90), 0);
        for (int lift = 0; lift < 16 && !level.noCollision(dragon); lift++) dragon.setPos(dragon.position().add(0, 2, 0));
        if (!GuardianStructure.arenaLoaded(level, dragon.blockPosition(), 4) || !level.noCollision(dragon)) return Optional.empty();
        level.addFreshEntity(dragon);
        dragon.playCreatureSound(CreatureSounds.Role.WARN, 6f);
        return Optional.of(dragon);
    }

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
            return new StructurePlaceSettings().setKnownShape(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }
        /** The dragon's palette, by the template this piece was built from. */
        public int variant() {
            return Math.max(0, java.util.List.of(VARIANTS).indexOf(templateName.substring(templateName.lastIndexOf('/') + 1)));
        }
        @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                RandomSource random, BoundingBox box) {}
    }
}
