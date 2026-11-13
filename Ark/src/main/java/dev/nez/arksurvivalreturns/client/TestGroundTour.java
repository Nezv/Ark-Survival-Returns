package dev.nez.arksurvivalreturns.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Action;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The Ark Testing Ground walked with nobody at the keyboard ({@code tools/testground_tour.py}). Off unless the client
 * was launched with {@code -Darksurvivalreturns.testground=<output folder>} into a new Testing Ground world. It flies
 * the player to each section, takes a picture there (the folder's screenshots/), opens a Specimen Post's screen, and
 * runs the posts' own actions on the bays and the arena: a knock-out and a tame, a hit and a kill, a fight. What the
 * posts timed, the workshop's stations and every post's status go to the log as "Testground tour:" lines; then the
 * game closes.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class TestGroundTour {
    private static final String OUT = System.getProperty("arksurvivalreturns.testground", "");
    private static final int VIEW = 100;

    private record Step(int after, Runnable run) {}

    private static final List<Step> steps = new ArrayList<>();
    private static int index = -1, ticks;
    private static BlockPos origin = BlockPos.ZERO;
    private static volatile BlockPos screenPost;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (OUT.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (index < 0) {
            if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null || mc.getSingleplayerServer() == null) return;
            plan(mc);
            index = 0;
            return;
        }
        if (index >= steps.size() || mc.player == null) return;
        mc.getToastManager().clear();
        if (!mc.player.getAbilities().flying) { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); }
        if (++ticks < steps.get(index).after()) return;
        ticks = 0;
        steps.get(index++).run().run();
    }

    private static void plan(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var spawn = server.getRespawnData().pos();
        int ground = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
        origin = new BlockPos(spawn.getX(), ground, spawn.getZ());
        ArkSurvivalReturns.LOGGER.info("Testground tour: started at {}, output {}", origin, OUT);
        // From above: the hub, the arena west, the bays north, the workshop south.
        fly(0, 110, 0, 180, 90); shot("overview");
        fly(0, 22, -8, 180, 28); shot("bays");
        // A bay's screen, then its own actions: a knock-out and a tame on the Parasaur, a hit and a kill on the Dilophosaur.
        server(40, level -> screenPost = post(level, Species.PARASAUR, origin.offset(0, 0, -44)).getBlockPos());
        client(10, () -> { if (screenPost != null) mc.setScreen(new SpecimenPostScreen(screenPost)); });
        shot("screen");
        client(5, () -> mc.setScreen(null));
        server(5, level -> {
            post(level, Species.PARASAUR, origin.offset(0, 0, -44)).act(Action.KNOCK_OUT, level.players().getFirst());
            var doomed = post(level, Species.DILOPHOSAUR, origin.offset(0, 0, -44));
            for (var creature : level.getEntitiesOfClass(CreatureEntity.class, new AABB(doomed.getBlockPos()).inflate(6),
                    c -> c.species() == Species.DILOPHOSAUR && c.entityTags().contains(SpecimenPostBlockEntity.TAG)))
                creature.hurtServer(level, level.damageSources().generic(), 3f);
        });
        server(60, level -> post(level, Species.DILOPHOSAUR, origin.offset(0, 0, -44)).act(Action.KILL, level.players().getFirst()));
        shot("bays_down");
        server(60, level -> post(level, Species.PARASAUR, origin.offset(0, 0, -44)).act(Action.TAME, level.players().getFirst()));
        server(120, level -> posts(level, "bays"));
        // The arena from inside, under its roof: the two inmates rooted, then let at each other.
        fly(-62, 20, 30, 180, 38); shot("arena");
        server(5, level -> post(level, Species.TYRANNOSAURUS, origin.offset(-62, 0, 0)).act(Action.FIGHT, level.players().getFirst()));
        client(400, () -> {}); shot("arena_fight");
        server(5, level -> posts(level, "arena"));
        fly(2, 10, 5, 0, 38); shot("workshop");
        server(5, TestGroundTour::stations);
        server(300, TestGroundTour::stations);
        // The gallery: its first column from above, then lanes from their walkway: small, large, winged, sea, the giant.
        fly(92, 70, -40, -90, 45); shot("gallery");
        for (Species species : List.of(Species.VELOCIRAPTOR, Species.TRICERATOPS, Species.TYRANNOSAURUS, Species.PTERANODON,
                Species.QUETZAL, Species.MEGALODON, Species.PLESIOSAUR, Species.MOSASAURUS, Species.DRAGON, Species.TITANOSAUR)) {
            lane(species);
            shot("lane_" + species.id);
            server(5, level -> posts(level, "lane " + species.id));
        }
        client(40, mc::stop);
    }

    // ------------------------------------------------------------------ steps

    private static void client(int wait, Runnable run) { steps.add(new Step(wait, run)); }

    private static void server(int wait, Consumer<ServerLevel> work) {
        client(wait, () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) server.execute(() -> {
                try { work.accept(server.overworld()); }
                catch (RuntimeException e) { ArkSurvivalReturns.LOGGER.error("Testground tour: step failed", e); }
            });
        });
    }

    /** Stands the player in the air at an offset from the hub, looking this way. */
    private static void fly(double dx, double dy, double dz, float yaw, float pitch) {
        server(10, level -> command(level, "tp @a " + (origin.getX() + 0.5 + dx) + " " + (origin.getY() + 1 + dy) + " "
                + (origin.getZ() + 0.5 + dz) + " " + yaw + " " + pitch));
    }

    /** Stands the player on a species' lane, back from its walkway by the size of the body, looking at its colours. */
    private static void lane(Species species) {
        server(10, level -> {
            var table = TestGroundBuilder.extents(level);
            var body = TestGroundBuilder.extent(table, species);
            var slots = TestGroundBuilder.gallery(table, origin).stream().filter(slot -> slot.species() == species).toList();
            if (slots.isEmpty()) { ArkSurvivalReturns.LOGGER.info("Testground tour: no lane for {}", species.displayName); return; }
            double x = (slots.getFirst().post().getX() + slots.getLast().post().getX()) / 2.0 + 0.5;
            double span = slots.getLast().post().getX() - slots.getFirst().post().getX() + 2 * slots.getFirst().halfX();
            double back = Math.max(6, Math.max(span * 0.45, body.height() * 1.2));
            double up = Math.max(3, Math.max(body.height() * 0.7, back * 0.35));
            ArkSurvivalReturns.LOGGER.info("Testground tour: lane of {} at {} to {}, {} colours, body {}", species.displayName,
                    slots.getFirst().post().subtract(origin), slots.getLast().post().subtract(origin), slots.size(), body);
            command(level, "tp @a " + x + " " + (origin.getY() + 1 + up) + " " + (slots.getFirst().laneZ() + 0.5 - back) + " 0 " + (float) Math.toDegrees(Math.atan2(up * 0.6, back + body.front())));
        });
    }

    private static void shot(String name) {
        client(VIEW, () -> {
            var mc = Minecraft.getInstance();
            Screenshot.grab(new File(OUT), name + ".png", mc.getMainRenderTarget(), 1, message -> {});
            ArkSurvivalReturns.LOGGER.info("Testground tour: {}.png", name);
        });
    }

    private static void command(ServerLevel level, String command) {
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "execute in minecraft:overworld run " + command);
    }

    // ------------------------------------------------------------------ what it reads

    private static List<SpecimenPostBlockEntity> loaded(ServerLevel level, BlockPos centre, int chunks) {
        var found = new ArrayList<SpecimenPostBlockEntity>();
        for (int cx = (centre.getX() >> 4) - chunks; cx <= (centre.getX() >> 4) + chunks; cx++)
            for (int cz = (centre.getZ() >> 4) - chunks; cz <= (centre.getZ() >> 4) + chunks; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) if (entity instanceof SpecimenPostBlockEntity post) found.add(post);
            }
        return found;
    }

    private static SpecimenPostBlockEntity post(ServerLevel level, Species species, BlockPos near) {
        SpecimenPostBlockEntity best = null;
        for (var post : loaded(level, near, 6))
            if (post.species() == species && (best == null || post.getBlockPos().distSqr(near) < best.getBlockPos().distSqr(near))) best = post;
        if (best == null) throw new IllegalStateException("No " + species.displayName + " post near " + near);
        return best;
    }

    /** Every loaded post around the player: how many hold a specimen, how many bodies are out, and what the posts timed. */
    private static void posts(ServerLevel level, String where) {
        var here = level.players().getFirst().blockPosition();
        var posts = loaded(level, here, 10);
        long holding = posts.stream().filter(SpecimenPostBlockEntity::hasSpecimen).count();
        int bodies = level.getEntitiesOfClass(CreatureEntity.class, new AABB(here).inflate(260),
                c -> c.entityTags().contains(SpecimenPostBlockEntity.TAG)).size();
        ArkSurvivalReturns.LOGGER.info("Testground tour: {}: {} posts loaded, {} hold a specimen, {} specimen bodies", where, posts.size(), holding, bodies);
        boolean gallery = where.startsWith("lane");
        for (var post : posts) {
            if (!post.reports().isEmpty() || !gallery && post.getBlockPos().distSqr(here) < 70 * 70)
                ArkSurvivalReturns.LOGGER.info("Testground tour: {} post {} | {} | {}", where, post.getBlockPos().subtract(origin), post.status(), post.reports());
            else if (gallery && !post.hasSpecimen())
                ArkSurvivalReturns.LOGGER.info("Testground tour: {} post {} has no specimen | {}", where, post.getBlockPos().subtract(origin), post.status());
        }
    }

    /** The workshop's stations as they are saved: what is in them and how far their work is. */
    private static void stations(ServerLevel level) {
        for (int dx = -26; dx <= 28; dx++) for (int dy = 1; dy <= 2; dy++) {
            var pos = origin.offset(dx, dy, 16);
            var entity = level.getBlockEntity(pos);
            var state = level.getBlockState(pos);
            if (state.isAir() || entity == null) continue;
            String data = entity == null ? "" : entity.saveCustomOnly(level.registryAccess()).toString();
            ArkSurvivalReturns.LOGGER.info("Testground tour: station {} {} {}", pos.subtract(origin), state, data.length() > 420 ? data.substring(0, 420) : data);
        }
    }

    private TestGroundTour() {}
}
