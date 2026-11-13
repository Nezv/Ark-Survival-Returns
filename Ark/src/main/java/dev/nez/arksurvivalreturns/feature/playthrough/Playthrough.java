package dev.nez.arksurvivalreturns.feature.playthrough;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.testground.TestGround;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The playthrough director (F43): drives one survival player through a routine of steps ({@link Routines}) on the
 * server tick and writes what happened to {@code events.jsonl} for the report. Started by {@code /arkplay start
 * <routine>}, or on joining a Testing Ground world when the client was launched with
 * {@code -Darksurvivalreturns.playthrough=<folder>} (tools/playthrough_run.py).
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class Playthrough {
    private static final String FOLDER = System.getProperty("arksurvivalreturns.playthrough", "");
    private static final String ROUTINE = System.getProperty("arksurvivalreturns.playthrough.routine", "standard");
    private static PlayRun run;
    private static CombatSteps.Fight watched;
    private static boolean autostarted;

    private static volatile String request, clientScreen = "";

    public static PlayRun current() { return run; }

    /**
     * What the client is asked to do next: "shot:<name>" (a picture into the run's screenshots), "camera:third" or
     * "camera:first", "screen:tech", "screen:inventory" or "screen:close". The client clears it once done and says
     * which screen was open.
     */
    public static String pendingRequest() { return request; }
    public static void requestDone(String screen) { clientScreen = screen; request = null; }
    static void request(String what) { request = what; }
    static String clientScreen() { return clientScreen; }
    /** Whether a run was launched by tools/playthrough_run.py, which wants the client closed at its end. */
    public static boolean launched() { return !FOLDER.isEmpty(); }

    static void watch(CombatSteps.Fight fight) { watched = fight; }

    public static String start(ServerPlayer player, String routine, Path folder) {
        if (run != null && !run.finished()) return "A playthrough is already running: " + run.progress();
        var steps = Routines.get(routine);
        if (steps == null) return "No routine '" + routine + "'; known: " + String.join(", ", Routines.NAMES);
        var level = player.level().getServer().overworld();
        var spawn = level.getRespawnData().pos();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
        if (folder == null) folder = level.getServer().getServerDirectory().resolve("diagnostics/playthrough")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        try { run = new PlayRun(level.getServer(), player, new BlockPos(spawn.getX(), ground, spawn.getZ()), folder, routine, steps); }
        catch (IOException e) { return "Could not open " + folder + ": " + e.getMessage(); }
        ArkSurvivalReturns.LOGGER.info("Playthrough: started {} with {} steps, writing to {}", routine, steps.size(), folder);
        return "Playthrough " + routine + " started (" + steps.size() + " steps), writing to " + folder;
    }

    public static String stop() {
        if (run == null || run.finished()) return "No playthrough is running";
        run.stop("stopped");
        watched = null;
        return "Playthrough stopped";
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (run != null && !run.finished()) run.tick();
    }

    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (FOLDER.isEmpty() || autostarted || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TestGround.isTestingWorld(player.level().getServer().overworld())) return;
        autostarted = true;
        player.sendSystemMessage(Component.literal(start(player, ROUTINE, Path.of(FOLDER))));
    }

    // ------------------------------------------------------------------ the damage test's hooks

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (watched != null && run != null && event.getEntity().getUUID().equals(run.playerId)) watched.incoming(event.getAmount());
    }

    /** What is left after armour is the measure; the blow itself is then cancelled so the test can go on. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        if (watched == null || run == null || !event.getEntity().getUUID().equals(run.playerId)) return;
        watched.struck(run, event.getNewDamage());
        event.setNewDamage(0);
    }

    // ------------------------------------------------------------------ command

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arkplay")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").then(Commands.argument("routine", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Routines.NAMES, builder))
                        .executes(context -> send(context.getSource(), start(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "routine"), null)))))
                .then(Commands.literal("stop").executes(context -> send(context.getSource(), stop())))
                .then(Commands.literal("status").executes(context -> send(context.getSource(),
                        run == null ? "No playthrough has run" : run.stage() + " " + run.progress() + " -> " + run.folder))));
    }

    private static int send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private Playthrough() {}
}
