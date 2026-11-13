package dev.nez.arksurvivalreturns.feature.testground;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.camp.TallBlocks;
import dev.nez.arksurvivalreturns.feature.creature.CreatureVariants;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Lays the testing ground out around a point, on its ground level:
 * <ul>
 * <li>east, the gallery: a Specimen Post for every species in every colour, frozen, each named on a sign; the sea
 * species in glass tanks of water;</li>
 * <li>north, the taming bays: rooted, hungry specimens beside a crate of taming supplies;</li>
 * <li>west, the arena: a glass pen under a barrier roof with a post outside each wall that puts its inmate inside,
 * a button on each post;</li>
 * <li>south, the workshop: every Ark workstation, the ones with a process loaded and kept running
 * ({@link TestGround#keep}).</li>
 * </ul>
 * Specimens come out once their post first ticks (a player nearby), so building loads no entity into land
 * nobody is in.
 */
public final class TestGroundBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /** The gallery's lanes wrap into a new column past this depth. */
    private static final int COLUMN_DEPTH = 220;

    private final ServerLevel level;
    private final BlockPos origin;
    private final int ground;
    private int posts, specimens;
    final List<BlockPos> stations = new ArrayList<>();

    public TestGroundBuilder(ServerLevel level, BlockPos at) {
        this.level = level;
        this.ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
        this.origin = new BlockPos(at.getX(), ground, at.getZ());
    }

    /** Builds every section; returns a line for the player. */
    public Component buildAll() {
        hub();
        gallery();
        bays();
        arena();
        workshop();
        return Component.literal("Testing ground built at " + origin.getX() + ", " + origin.getZ() + ": " + posts + " posts, "
                + specimens + " specimens to come out as you walk up to them. Gallery east, arena west, taming bays north, workshop south. /arktest for commands.");
    }

    // ------------------------------------------------------------------ sections

    private void hub() {
        fill(origin.offset(-3, 0, -3), origin.offset(3, 0, 3), Blocks.POLISHED_ANDESITE.defaultBlockState());
        sign(origin.offset(3, 1, 0), 4, "Gallery", "every creature", "and colour", "east ->");
        sign(origin.offset(-3, 1, 0), 12, "Arena", "glass pen", "fights, hunger", "<- west");
        sign(origin.offset(0, 1, -3), 0, "Taming bays", "rooted, hungry", "supplies crate", "north");
        sign(origin.offset(0, 1, 3), 8, "Workshop", "every station", "processes running", "south");
    }

    private void gallery() {
        int x0 = origin.getX() + 80, z0 = origin.getZ() - COLUMN_DEPTH / 2;
        int columnX = x0, z = z0, columnLength = 0;
        for (Species species : Species.values()) {
            int cell = cell(species);
            var variants = CreatureVariants.of(species);
            int count = Math.max(1, variants.size());
            if (z + cell > z0 + COLUMN_DEPTH && z > z0) {
                columnX += columnLength + 12; z = z0; columnLength = 0;
            }
            columnLength = Math.max(columnLength, count * cell);
            // A walkway runs along the lane's north edge; the specimens face it.
            fill(new BlockPos(columnX, ground, z), new BlockPos(columnX + count * cell - 1, ground, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int i = 0; i < count; i++) {
                int px = columnX + i * cell + cell / 2, pz = z + cell / 2 + 1;
                BlockPos post = new BlockPos(px, ground, pz);
                if (species.realm() == Species.Realm.WATER) tank(post, cell / 2 - 2, (int) Math.ceil(species.height) + 3);
                post(post, species, i, 1, Mode.FROZEN, 50, false, 180f, null, true);
                sign(new BlockPos(px, ground + 1, z + 1), 8, species.displayName, variants.isEmpty() ? "" : variants.get(i), "frozen", "");
            }
            z += cell;
        }
    }

    private void bays() {
        Species[] tames = {Species.DILOPHOSAUR, Species.PARASAUR, Species.TRICERATOPS, Species.VELOCIRAPTOR,
                Species.TYRANNOSAURUS, Species.PTERANODON, Species.DIREWOLF, Species.MAMMOTH};
        int z = origin.getZ() - 44, x = origin.getX() - (tames.length * 16) / 2;
        for (Species species : tames) {
            BlockPos post = new BlockPos(x + 8, ground, z);
            fill(post.offset(-6, 0, -6), post.offset(6, 0, 6), Blocks.COARSE_DIRT.defaultBlockState());
            post(post, species, 0, 1, Mode.ROOTED, 100, true, 0f, null, true);
            sign(post.offset(-2, 1, 7), 0, species.displayName, "rooted, hungry", "screen: knock out,", "tame, kill");
            crate(post.offset(2, 1, 7), supplies());
            x += 16;
        }
    }

    private void arena() {
        int half = 16, height = 20;
        BlockPos centre = origin.offset(-30 - half, 0, 0);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int dy = 1; dy <= height; dy++) {
            for (int d = -half - 1; d <= half + 1; d++) {
                set(centre.offset(d, dy, -half - 1), glass); set(centre.offset(d, dy, half + 1), glass);
                set(centre.offset(-half - 1, dy, d), glass); set(centre.offset(half + 1, dy, d), glass);
            }
        }
        fill(centre.offset(-half - 1, height + 1, -half - 1), centre.offset(half + 1, height + 1, half + 1), Blocks.BARRIER.defaultBlockState());
        fill(centre.offset(-half, 0, -half), centre.offset(half, 0, half), Blocks.GRASS_BLOCK.defaultBlockState());
        // A post outside each wall puts its inmate eight blocks inside, facing the middle, rooted until Fight lets it go;
        // a button on top puts out a new one. East and west start with theirs out; north and south wait for the button.
        record Gate(Direction side, Species species, int hunger, boolean out) {}
        for (var gate : List.of(new Gate(Direction.EAST, Species.TYRANNOSAURUS, 90, true), new Gate(Direction.WEST, Species.TRICERATOPS, 30, true),
                new Gate(Direction.NORTH, Species.VELOCIRAPTOR, 90, false), new Gate(Direction.SOUTH, Species.PARASAUR, 30, false))) {
            var step = gate.side().getUnitVec3i();
            BlockPos post = centre.offset(step.getX() * (half + 3), 0, step.getZ() * (half + 3));
            Vec3 inside = Vec3.atBottomCenterOf(centre.offset(step.getX() * (half - 8), 1, step.getZ() * (half - 8)));
            Vec3 offset = inside.subtract(Vec3.atBottomCenterOf(post));
            float yaw = SpecimenPostBlockEntity.yawTowards(inside, Vec3.atBottomCenterOf(centre));
            post(post, gate.species(), 0, 1, Mode.ROOTED, gate.hunger(), false, yaw, offset, gate.out());
            set(post.above(), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
            sign(post.offset(step.getX() * 2, 1, step.getZ() * 2), rotationFacing(gate.side()), gate.species().displayName,
                    "button: new inmate", "screen: Fight", "hunger, swap");
        }
    }

    private void workshop() {
        int z = origin.getZ() + 16, x = origin.getX() - 30;
        fill(new BlockPos(x - 2, ground, z - 3), new BlockPos(x + TestGround.WORKSHOP.get().size() * 4, ground, z + 3), Blocks.SMOOTH_STONE.defaultBlockState());
        for (var block : TestGround.WORKSHOP.get()) {
            BlockPos at = new BlockPos(x, ground + 1, z);
            if (block instanceof dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock) {
                // The pot cooks over a lit fire: the fire goes first, the pot on top.
                place(at, dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.STONE_FIRE.get().defaultBlockState());
                place(at.above(), block.defaultBlockState());
                stations.add(at.above());
            } else place(at, block.defaultBlockState());
            stations.add(at);
            sign(at.offset(0, 0, -2), 8, block.getName().getString(), "", "", "");
            x += 4;
        }
        TestGround.keep(level, stations);
    }

    // ------------------------------------------------------------------ pieces

    /** Side of a species' gallery cell: room for the body's length (about 1.7 times its width) and a margin. */
    static int cell(Species species) {
        return Math.max(6, Mth.ceil(species.width * 1.7) + 4);
    }

    private void post(BlockPos pos, Species species, int variant, int lvl, Mode mode, int hunger, boolean respawn, float yaw, Vec3 offset,
            boolean out) {
        set(pos, TestGroundContent.SPECIMEN_POST.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof SpecimenPostBlockEntity post)) return;
        post.configure(species, variant, lvl, mode, hunger, respawn);
        if (offset != null) post.place(offset.x, offset.y, offset.z, yaw);
        else post.setYaw(yaw);
        posts++;
        if (out) { post.arm(); specimens++; }
    }

    private void tank(BlockPos floor, int radius, int height) {
        BlockState glass = Blocks.GLASS.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        fill(floor.offset(-radius - 1, 0, -radius - 1), floor.offset(radius + 1, 0, radius + 1), Blocks.SAND.defaultBlockState());
        for (int dy = 1; dy <= height; dy++)
            for (int dx = -radius - 1; dx <= radius + 1; dx++)
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    boolean wall = Math.abs(dx) == radius + 1 || Math.abs(dz) == radius + 1;
                    set(floor.offset(dx, dy, dz), wall ? glass : water);
                }
    }

    private void crate(BlockPos pos, List<ItemStack> items) {
        set(pos, dev.nez.arksurvivalreturns.feature.station.StationContent.STORAGE_CRATE.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof Container container)
            for (int i = 0; i < Math.min(items.size(), container.getContainerSize()); i++) container.setItem(i, items.get(i));
    }

    private static List<ItemStack> supplies() {
        var items = new ArrayList<ItemStack>();
        items.add(new ItemStack(net.minecraft.world.item.Items.BOW));
        items.add(new ItemStack(dev.nez.arksurvivalreturns.registry.ModContent.TRANQUILIZER_ARROW_ITEM.get(), 64));
        items.add(new ItemStack(dev.nez.arksurvivalreturns.registry.ModContent.IMPROVED_TRANQUILIZER_ARROW_ITEM.get(), 64));
        items.add(new ItemStack(dev.nez.arksurvivalreturns.registry.ModContent.NARCOTICS.get(), 64));
        dev.nez.arksurvivalreturns.registry.ModContent.BERRIES.values().forEach(berry -> items.add(new ItemStack(berry.get(), 64)));
        dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.RAW_MEAT.values().forEach(meat -> items.add(new ItemStack(meat.get(), 64)));
        items.add(new ItemStack(net.minecraft.world.item.Items.BEEF, 64));
        items.add(new ItemStack(net.minecraft.world.item.Items.WHEAT, 64));
        items.add(new ItemStack(net.minecraft.world.item.Items.CARROT, 64));
        items.add(new ItemStack(dev.nez.arksurvivalreturns.feature.debug.DebugSpyglass.item()));
        return items;
    }

    private void sign(BlockPos pos, int rotation, String... lines) {
        set(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        var text = sign.getText(true);
        for (int i = 0; i < Math.min(4, lines.length); i++) {
            String line = lines[i].length() > 15 ? lines[i].substring(0, 15) : lines[i];
            text = text.setMessage(i, Component.literal(line));
        }
        sign.setText(text, true);
        sign.setWaxed(true);
    }

    /** The standing-sign rotation whose text faces this way. */
    private static int rotationFacing(Direction side) {
        return switch (side) { case SOUTH -> 0; case WEST -> 4; case NORTH -> 8; default -> 12; };
    }

    /** A block, both halves of a tall one, its front facing north. */
    private void place(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        if (state.hasProperty(TallBlocks.HALF)) {
            level.setBlock(pos, state.setValue(TallBlocks.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
            level.setBlock(pos.above(), state.setValue(TallBlocks.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        } else level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private void fill(BlockPos a, BlockPos b, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) set(pos, state);
    }

    private void set(BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, FLAGS);
    }
}
