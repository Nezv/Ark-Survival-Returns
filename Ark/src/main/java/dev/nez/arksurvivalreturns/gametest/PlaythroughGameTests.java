package dev.nez.arksurvivalreturns.gametest;

import java.nio.file.Files;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.playthrough.Playthrough;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * The playthrough's economy run headless (F43): a real survival player, in the player list and ticking, earns every
 * stage's goals and every workstation item with nothing given. It passes when the run ends without a step error; what
 * could not be earned is in the run's events file (python tools/playthrough_report.py) and in the log.
 */
final class PlaythroughGameTests {
    static void economy(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ArkPlaythrough"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        // A test world has no ground out there: the player keeps its height by flying, which changes nothing it earns.
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Playthrough.headless = true;
        // Far from the other tests, so the yard, camp and wall touch no one's structure.
        var at = h.absolutePos(BlockPos.ZERO).offset(4000, 0, 4000);
        var folder = server.getServerDirectory().resolve("diagnostics/playthrough/gametest-economy");
        String started = Playthrough.start(player, "economy", folder, at);
        ArkSurvivalReturns.LOGGER.info("Playthrough GameTest: {}", started);
        h.succeedWhen(() -> {
            var run = Playthrough.current();
            h.assertTrue(run != null && run.finished(), "The playthrough is still running: " + (run == null ? "" : run.progress()));
            try {
                var events = Files.readAllLines(folder.resolve("events.jsonl"));
                long errors = events.stream().filter(line -> line.contains("\"type\":\"step_error\"") || line.contains("\"type\":\"step_timeout\"")).count();
                long blocked = events.stream().filter(line -> line.contains("\"type\":\"blocked\"")).count();
                ArkSurvivalReturns.LOGGER.info("Playthrough GameTest: {} events, {} step errors or timeouts, {} goals blocked", events.size(), errors, blocked);
                server.getPlayerList().remove(player);
                Playthrough.headless = false;
                h.assertTrue(errors == 0, errors + " playthrough steps failed or timed out (see " + folder + ")");
            } catch (java.io.IOException e) {
                h.fail("No events file: " + e.getMessage());
            }
        });
    }

    private PlaythroughGameTests() {}
}
