package dev.nez.arksurvivalreturns.feature.testground;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
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
 * <li>east, the gallery: a Specimen Post for every species in every colour, frozen; each post is a pedestal on the
 * lane's walkway with the name on a sign on top, its creature on the ground behind it, the sea species in glass
 * tanks of water;</li>
 * <li>north, the taming bays: rooted, hungry specimens beside a crate of taming supplies;</li>
 * <li>west, the arena: a glass pen under a barrier roof with a post outside each wall that puts its inmate inside,
 * a button on each post;</li>
 * <li>south, the workshop: every Ark workstation, the ones with a process loaded and kept running
 * ({@link TestGround#keep}).</li>
 * </ul>
 * Every place is sized by the creature's drawn body ({@link Extent}), which is several times its hitbox. Specimens
 * come out once their post first ticks (a player nearby), so building loads no entity into land nobody is in.
 */
public final class TestGroundBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /** The gallery's lanes wrap into a new column past this depth. */
    private static final int COLUMN_DEPTH = 220;
    /** Where the sections sit from the hub: the gallery's west edge, the bays' row, the arena's half side and its gap. */
    private static final int GALLERY_X = 100, BAYS_Z = -50, ARENA_HALF = 32, ARENA_GAP = 30, WORKSHOP_Z = 16;

    /**
     * A creature's drawn body in blocks from its feet: to either side, ahead, behind and up. Measured from the models by
     * {@code tools/build_testground_extents.py} into {@code data/arksurvivalreturns/testground/extents.json}.
     */
    public record Extent(double halfWidth, double front, double back, double height) {
        /** How far the body reaches whichever way it turns. */
        public double reach() { return Math.max(halfWidth, Math.max(front, back)); }
    }

    /**
     * One gallery place: the post on the lane's walkway edge, and the room its creature has behind it (blocks to
     * either side, ahead and behind, margins included). The creature stands {@code front + 1} blocks south of the post.
     */
    public record Slot(Species species, int variant, BlockPos post, int laneZ, int halfX, int front, int back) {
        public Vec3 body() { return Vec3.atBottomCenterOf(post.offset(0, 0, front + 1)); }
    }

    private final ServerLevel level;
    private final BlockPos origin;
    private final int ground;
    private final Map<String, Extent> extents;
    private int posts, specimens;
    final List<BlockPos> stations = new ArrayList<>();

    public TestGroundBuilder(ServerLevel level, BlockPos at) {
        this.level = level;
        this.ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
        this.origin = new BlockPos(at.getX(), ground, at.getZ());
        this.extents = extents(level);
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

    // ------------------------------------------------------------------ body sizes

    /** The measured bodies by species id; empty when the table is missing. */
    public static Map<String, Extent> extents(ServerLevel level) {
        var table = new HashMap<String, Extent>();
        var resource = level.getServer().getResourceManager().getResource(ArkSurvivalReturns.id("testground/extents.json"));
        if (resource.isEmpty()) return table;
        try (var reader = resource.get().openAsReader()) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                var body = entry.getValue().getAsJsonObject();
                table.put(entry.getKey(), new Extent(body.get("half_width").getAsDouble(), body.get("front").getAsDouble(),
                        body.get("back").getAsDouble(), body.get("height").getAsDouble()));
            }
        } catch (IOException | RuntimeException e) {
            ArkSurvivalReturns.LOGGER.warn("Testing ground: the body sizes could not be read, guessing from hitboxes", e);
        }
        return table;
    }

    /** A species' body from the table, or a guess from its hitbox (bodies run about five hitbox widths long). */
    public static Extent extent(Map<String, Extent> table, Species species) {
        var known = table.get(species.id);
        return known != null ? known : new Extent(species.width * 1.5 + 0.5, species.width * 4, species.width * 6, species.height * 2.5);
    }

    // ------------------------------------------------------------------ sections

    private void hub() {
        fill(origin.offset(-3, 0, -3), origin.offset(3, 0, 3), Blocks.POLISHED_ANDESITE.defaultBlockState());
        sign(origin.offset(3, 1, 0), 4, "Gallery", "every creature", "and colour", "east ->");
        sign(origin.offset(-3, 1, 0), 12, "Arena", "glass pen", "fights, hunger", "<- west");
        sign(origin.offset(0, 1, -3), 0, "Taming bays", "rooted, hungry", "supplies crate", "north");
        sign(origin.offset(0, 1, 3), 8, "Workshop", "every station", "processes running", "south");
    }

    /**
     * The gallery's places around a hub at ground level. Lanes run east, one species each with its colours side by
     * side, a walkway along the north edge; lanes stack south and wrap into a new column to the east.
     */
    public static List<Slot> gallery(Map<String, Extent> table, BlockPos origin) {
        var slots = new ArrayList<Slot>();
        int z0 = origin.getZ() - COLUMN_DEPTH / 2, columnX = origin.getX() + GALLERY_X, z = z0, columnWidth = 0;
        for (Species species : Species.values()) {
            var body = extent(table, species);
            // A tank's wall and a block of water around a sea body; a block of air around the others.
            int margin = species.realm() == Species.Realm.WATER ? 2 : 1;
            int halfX = Mth.ceil(body.halfWidth()) + margin, front = Mth.ceil(body.front()) + margin, back = Mth.ceil(body.back()) + margin;
            // A lane: the walkway, the row of posts, the body, a gap before the next walkway.
            int cell = 2 * halfX + 3, depth = front + back + 4, count = Math.max(1, CreatureVariants.of(species).size());
            if (z + depth > z0 + COLUMN_DEPTH && z > z0) { columnX += columnWidth + 8; z = z0; columnWidth = 0; }
            columnWidth = Math.max(columnWidth, count * cell);
            for (int i = 0; i < count; i++)
                slots.add(new Slot(species, i, new BlockPos(columnX + i * cell + halfX + 1, origin.getY() + 1, z + 1), z, halfX, front, back));
            z += depth;
        }
        return slots;
    }

    private void gallery() {
        for (var slot : gallery(extents, origin)) {
            var post = slot.post();
            var species = slot.species();
            fill(new BlockPos(post.getX() - slot.halfX() - 1, ground, slot.laneZ()), new BlockPos(post.getX() + slot.halfX() + 1, ground, slot.laneZ()),
                    Blocks.POLISHED_ANDESITE.defaultBlockState());
            if (species.realm() == Species.Realm.WATER)
                tank(new BlockPos(post.getX(), ground, post.getZ() + slot.front() + 1), slot.halfX() - 1, slot.front() - 1, slot.back() - 1,
                        Mth.ceil(extent(extents, species).height()) + 2);
            // The creature faces the walkway from the ground behind its post.
            post(post, species, slot.variant(), 1, Mode.FROZEN, 50, false, 180f, new Vec3(0, 0, slot.front() + 1), true);
            var variants = CreatureVariants.of(species);
            sign(post.above(), 8, species.displayName, variants.isEmpty() ? "" : variants.get(slot.variant()), "frozen", "");
        }
    }

    private void bays() {
        Species[] tames = {Species.DILOPHOSAUR, Species.PARASAUR, Species.TRICERATOPS, Species.VELOCIRAPTOR,
                Species.TYRANNOSAURUS, Species.PTERANODON, Species.DIREWOLF, Species.MAMMOTH};
        // A rooted specimen turns on the spot: its bay is as wide as its body is long.
        int[] radius = new int[tames.length];
        int total = 0;
        for (int i = 0; i < tames.length; i++) {
            radius[i] = Math.max(6, Mth.ceil(extent(extents, tames[i]).reach()) + 2);
            total += 2 * radius[i] + 3;
        }
        int z = origin.getZ() + BAYS_Z, x = origin.getX() - total / 2;
        for (int i = 0; i < tames.length; i++) {
            int r = radius[i];
            BlockPos post = new BlockPos(x + r + 1, ground, z);
            fill(post.offset(-r, 0, -r), post.offset(r, 0, r), Blocks.COARSE_DIRT.defaultBlockState());
            post(post, tames[i], 0, 1, Mode.ROOTED, 100, true, 0f, null, true);
            sign(post.offset(-2, 1, r + 1), 0, tames[i].displayName, "rooted, hungry", "screen: knock out,", "tame, kill");
            crate(post.offset(2, 1, r + 1), supplies());
            x += 2 * r + 3;
        }
    }

    private void arena() {
        int half = ARENA_HALF, height = 24;
        BlockPos centre = origin.offset(-ARENA_GAP - half, 0, 0);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int dy = 1; dy <= height; dy++) {
            for (int d = -half - 1; d <= half + 1; d++) {
                set(centre.offset(d, dy, -half - 1), glass); set(centre.offset(d, dy, half + 1), glass);
                set(centre.offset(-half - 1, dy, d), glass); set(centre.offset(half + 1, dy, d), glass);
            }
        }
        fill(centre.offset(-half - 1, height + 1, -half - 1), centre.offset(half + 1, height + 1, half + 1), Blocks.BARRIER.defaultBlockState());
        fill(centre.offset(-half, 0, -half), centre.offset(half, 0, half), Blocks.GRASS_BLOCK.defaultBlockState());
        // A post outside each wall puts its inmate twelve blocks inside, facing the middle, rooted until Fight lets it go;
        // a button on top puts out a new one. East and west start with theirs out; north and south wait for the button.
        record Gate(Direction side, Species species, int hunger, boolean out) {}
        for (var gate : List.of(new Gate(Direction.EAST, Species.TYRANNOSAURUS, 90, true), new Gate(Direction.WEST, Species.TRICERATOPS, 30, true),
                new Gate(Direction.NORTH, Species.VELOCIRAPTOR, 90, false), new Gate(Direction.SOUTH, Species.PARASAUR, 30, false))) {
            var step = gate.side().getUnitVec3i();
            BlockPos post = centre.offset(step.getX() * (half + 3), 0, step.getZ() * (half + 3));
            Vec3 inside = Vec3.atBottomCenterOf(centre.offset(step.getX() * (half - 12), 1, step.getZ() * (half - 12)));
            Vec3 offset = inside.subtract(Vec3.atBottomCenterOf(post));
            float yaw = SpecimenPostBlockEntity.yawTowards(inside, Vec3.atBottomCenterOf(centre));
            post(post, gate.species(), 0, 1, Mode.ROOTED, gate.hunger(), false, yaw, offset, gate.out());
            set(post.above(), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
            sign(post.offset(step.getX() * 2, 1, step.getZ() * 2), rotationFacing(gate.side()), gate.species().displayName,
                    "button: new inmate", "screen: Fight", "hunger, swap");
        }
    }

    private void workshop() {
        // Centred on the hub: clear of the arena, whose east wall and post stand 27 to 29 blocks west of it.
        int z = origin.getZ() + WORKSHOP_Z, x = origin.getX() - TestGround.WORKSHOP.get().size() * 2 + 2;
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

    /** A glass tank of water on a sand floor: its inside reaches this far from the body's place to either side, ahead (north) and behind. */
    private void tank(BlockPos floor, int halfX, int front, int back, int height) {
        BlockState glass = Blocks.GLASS.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        fill(floor.offset(-halfX - 1, 0, -front - 1), floor.offset(halfX + 1, 0, back + 1), Blocks.SAND.defaultBlockState());
        for (int dy = 1; dy <= height; dy++)
            for (int dx = -halfX - 1; dx <= halfX + 1; dx++)
                for (int dz = -front - 1; dz <= back + 1; dz++) {
                    boolean wall = Math.abs(dx) == halfX + 1 || dz == -front - 1 || dz == back + 1;
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
