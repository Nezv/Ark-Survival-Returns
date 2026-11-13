package dev.nez.arksurvivalreturns.feature.testground;

import java.util.Locale;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Action;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /arktest}, operators only:
 * <ul>
 * <li>{@code build} lays the testing ground out around you;</li>
 * <li>{@code quiet} turns spawning, the clock and the weather off in this world;</li>
 * <li>{@code post <pos> <action>} runs one post's action (spawn, clear, kill, knock_out, wake, tame, fight);</li>
 * <li>{@code posts <action> [radius]} runs it on every post around you (default 64 blocks);</li>
 * <li>{@code clear [radius]} removes every specimen around you without a death.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TestGroundCommand {
    private static final String[] ACTIONS = {"spawn", "clear", "kill", "knock_out", "wake", "tame", "fight"};

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arktest")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("build").executes(context -> build(context.getSource())))
                .then(Commands.literal("quiet").executes(context -> {
                    TestGround.quiet(context.getSource().getLevel());
                    send(context.getSource(), "Spawning, the day cycle and the weather are off");
                    return 1;
                }))
                .then(Commands.literal("post").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ACTIONS, builder))
                                .executes(context -> post(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "action"))))))
                .then(Commands.literal("posts").then(Commands.argument("action", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ACTIONS, builder))
                        .executes(context -> posts(context.getSource(), StringArgumentType.getString(context, "action"), 64))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                .executes(context -> posts(context.getSource(), StringArgumentType.getString(context, "action"),
                                        IntegerArgumentType.getInteger(context, "radius"))))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource(), 256))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
                                .executes(context -> clear(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int help(CommandSourceStack source) {
        send(source, "/arktest build | quiet | post <pos> <action> | posts <action> [radius] | clear [radius]");
        send(source, "Actions: " + String.join(", ", ACTIONS) + ". Right-click a Specimen Post for its screen; a redstone pulse spawns its specimen.");
        return 1;
    }

    private static int build(CommandSourceStack source) {
        var builder = new TestGroundBuilder(source.getLevel(), BlockPos.containing(source.getPosition()));
        source.sendSuccess(builder::buildAll, true);
        return 1;
    }

    private static Action action(String name) {
        try { return Action.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static int post(CommandSourceStack source, BlockPos pos, String name) {
        var action = action(name);
        if (action == null) return fail(source, "Unknown action " + name);
        if (!(source.getLevel().getBlockEntity(pos) instanceof SpecimenPostBlockEntity post)) return fail(source, "No Specimen Post there");
        post.act(action, source.getEntity() instanceof Player player ? player : null);
        send(source, post.status());
        return 1;
    }

    private static int posts(CommandSourceStack source, String name, int radius) {
        var action = action(name);
        if (action == null) return fail(source, "Unknown action " + name);
        ServerLevel level = source.getLevel();
        BlockPos centre = BlockPos.containing(source.getPosition());
        int done = 0;
        for (int cx = (centre.getX() - radius) >> 4; cx <= (centre.getX() + radius) >> 4; cx++)
            for (int cz = (centre.getZ() - radius) >> 4; cz <= (centre.getZ() + radius) >> 4; cz++) {
                // Only land that is already loaded: the command never loads chunks.
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof SpecimenPostBlockEntity post && post.getBlockPos().closerThan(centre, radius)) {
                        post.act(action, source.getEntity() instanceof Player player ? player : null);
                        done++;
                    }
                }
            }
        int count = done;
        send(source, name + " on " + count + " posts");
        return count;
    }

    private static int clear(CommandSourceStack source, int radius) {
        Vec3 at = source.getPosition();
        var found = source.getLevel().getEntitiesOfClass(CreatureEntity.class, new AABB(at, at).inflate(radius),
                creature -> creature.entityTags().contains(SpecimenPostBlockEntity.TAG));
        found.forEach(CreatureEntity::discard);
        send(source, "Removed " + found.size() + " specimens");
        return found.size();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private TestGroundCommand() {}
}
