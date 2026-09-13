package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.debug.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DinoDebugGameTests {
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, ArkSurvivalReturns.id("debug_spyglass"), () -> DinoDebugGameTests::run);
    }
    private static void run(GameTestHelper h) {
        var world = h.getLevel();
        var entities = new ArrayList<Entity>();
        try {
            // Serialized state is a broad regression guard against an inspector mutating gameplay.
            for (var species : Species.values()) {
                var dino = create(h, species, 8); entities.add(dino);
                dino.initializeLevel(40); dino.setHealth(dino.getMaxHealth() * 0.4f);
                dino.wildlife().mind().restoreNeeds(0.7, 0.6, 0.5);
                var before = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
                dino.saveWithoutId(before);
                var lines = DinoDebugSnapshot.capture(dino);
                h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("level = 40")), "Missing level for " + species);
                h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("runtime.wildlife.mind.hunger = 0.7")), "Missing server needs");
                h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("saved.CreatureLevel")), "Missing saved data");
                h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("runtime.wildlife.failedPaths")), "Missing transient AI field");
                if (species.flyer()) h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("runtime.phaseTicks")), "Missing flight state");
                h.assertTrue(lines.size() <= DinoDebugSnapshot.MAX_LINES && lines.stream().allMatch(s -> s.length() <= 58), "Unbounded debug snapshot");
                var after = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
                dino.saveWithoutId(after);
                h.assertTrue(before.buildResult().equals(after.buildResult()), "Inspection mutated " + species);
                var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), world.registryAccess());
                try {
                    var packet = new DinoDebugPayload(dino.getUUID(), world.dimension().identifier().toString(), species.displayName,
                            0, 1, lines.subList(0, DinoDebugSnapshot.PAGE_LINES));
                    DinoDebugPayload.STREAM_CODEC.encode(buffer, packet);
                    h.assertTrue(buffer.readableBytes() < 4096, "Debug page exceeded network budget");
                    h.assertTrue(packet.equals(DinoDebugPayload.STREAM_CODEC.decode(buffer)), "Debug packet round trip failed");
                    buffer.clear();
                    var turn = new DinoDebugPayload.TurnPage(dino.getUUID(), -1);
                    DinoDebugPayload.TurnPage.STREAM_CODEC.encode(buffer, turn);
                    h.assertTrue(turn.equals(DinoDebugPayload.TurnPage.STREAM_CODEC.decode(buffer)), "Page turn round trip failed");
                    buffer.clear();
                    buffer.writeUUID(dino.getUUID()); buffer.writeUtf("minecraft:overworld"); buffer.writeUtf("test");
                    buffer.writeVarInt(0); buffer.writeVarInt(1); buffer.writeVarInt(999);
                    boolean rejected = false;
                    try { DinoDebugPayload.STREAM_CODEC.decode(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
                    h.assertTrue(rejected, "Oversized row count accepted");
                } finally { buffer.release(); }
            }
            var player = h.makeMockPlayer(GameType.SURVIVAL); entities.add(player);
            player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(56, 2, 51))));
            player.setXRot(0); player.setYRot(0); player.setYHeadRot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, DebugSpyglass.item().getDefaultInstance());
            player.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(DebugSpyglass.using(player) && player.isScoping(), "Debug Spyglass does not zoom");
            player.stopUsingItem();
            h.assertFalse(DebugSpyglass.using(player), "Inspector active after release");
            player.setItemInHand(InteractionHand.MAIN_HAND, Items.SPYGLASS.getDefaultInstance());
            player.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertFalse(DebugSpyglass.using(player), "Vanilla scope activated inspector");
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.OFF_HAND, DebugSpyglass.item().getDefaultInstance());
            player.startUsingItem(InteractionHand.OFF_HAND);
            h.assertTrue(DebugSpyglass.using(player) && player.isScoping(), "Offhand debug scope failed");
            var near = create(h, Species.VELOCIRAPTOR, 8); entities.add(near); world.addFreshEntity(near);
            var far = create(h, Species.VELOCIRAPTOR, 12); entities.add(far); world.addFreshEntity(far);
            h.assertTrue(DinoDebugSync.target(player) == near, "Ray failed to pick nearest dinosaur");
            near.setInvisible(true);
            h.assertTrue(DinoDebugSync.target(player) == far, "Invisible dinosaur selected");
            near.setInvisible(false);
            for (int x = 54; x <= 58; x++) for (int y = 2; y <= 6; y++) h.setBlock(x, y, 54, Blocks.STONE);
            h.assertTrue(DinoDebugSync.target(player) == null, "Debug ray penetrated wall");
            for (int x = 54; x <= 58; x++) for (int y = 2; y <= 6; y++) h.setBlock(x, y, 54, Blocks.AIR);
            player.setYRot(180); player.setYHeadRot(180);
            // Other concurrently running test structures can contain dinosaurs behind this fixture.
            var away = DinoDebugSync.target(player);
            h.assertTrue(away != near && away != far, "Looking away retained target");
            player.setYRot(0); player.setYHeadRot(0); player.setPos(player.position().add(0, 0, -DinoDebugSync.RANGE));
            int chunks = world.getChunkSource().getLoadedChunksCount();
            var distant = DinoDebugSync.target(player);
            h.assertTrue(distant != near && distant != far, "Out-of-range dinosaur selected");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Debug ray loaded chunks");
        } finally { entities.forEach(Entity::discard); }
        h.succeed();
    }
    private static CreatureEntity create(GameTestHelper h, Species species, int z) {
        var dino = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        dino.setNoAi(true); dino.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(56, 2, z + 48))));
        return dino;
    }
    private DinoDebugGameTests() {}
}
