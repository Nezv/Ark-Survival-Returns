package dev.nez.arksurvivalreturns.feature.taming;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only diagnostics for torpor, taming and riding, registered in the vanilla command framework.
 *
 * <p>Mutation commands always go through {@link TorporService} and {@link TamingService}, so exercising a
 * transition here is the same code path a sedative or a meal takes. Nothing in this class is reachable by
 * an ordinary player: the whole tree requires the gamemaster permission level.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TamingCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arktaming")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> inspect(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("torpor")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> setTorpor(context.getSource(),
                                                EntityArgument.getEntity(context, "target"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("hunger")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100))
                                        .executes(context -> setHunger(context.getSource(),
                                                EntityArgument.getEntity(context, "target"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> reset(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("mount")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> mount(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("roster").executes(context -> roster(context.getSource())))
                .then(Commands.literal("log")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> log(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static int inspect(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Not a living entity: " + target.getType().toShortString()));
            return 0;
        }
        var torpor = TorporService.of(living);
        send(source, "entity=" + target.getType().getDescription().getString()
                + " id=" + TorporService.describe(living));
        send(source, "consciousness=" + torpor.phase() + " torpor=" + TorporService.round(torpor.torpor())
                + "/" + TorporService.round(torpor.maximum())
                + " ratio=" + TorporService.round(torpor.ratio() * 100) + "%"
                + " wakeBelow=" + TorporService.round(torpor.wakeThreshold())
                + " recoveryDelay=" + torpor.recoveryDelay());
        if (target instanceof CreatureEntity creature) {
            var profile = creature.profile();
            send(source, "profile=" + profile.species().id
                    + " size=" + profile.size()
                    + " method=" + profile.method()
                    + " target=" + profile.targetSeconds() + "s"
                    + " meals=" + profile.mealsPerTame()
                    + " progressPerMeal=" + TorporService.round(profile.progressPerMeal()) + "%"
                    + " resistance=" + profile.sedativeResistance());
            send(source, "food=" + profile.describeFood());
            send(source, "taming=" + TamingService.summary(creature).getString());
            var seat = creature.rideProfile();
            if (seat != null) {
                send(source, "ride=" + seat.mode() + " bone=" + seat.bone() + " pose=" + seat.riderPose()
                        + " saddle=" + creature.isSaddled());
            }
        } else {
            send(source, "taming=not tameable (only registered creatures can be tamed)");
        }
        send(source, "riders=" + target.getPassengers().size()
                + " controllingPassenger=" + (target.getControllingPassenger() == null ? "-"
                        : target.getControllingPassenger().getUUID().toString()));
        return 1;
    }

    private static int setTorpor(CommandSourceStack source, Entity target, double value) {
        if (!(target instanceof LivingEntity living)) return fail(source, "Not a living entity");
        if (!TorporService.setTorpor(living, value))
            return fail(source, "No dose applied: the target is not a legal sedation target (dead, spectator, creative or an armor stand)");
        var state = TorporService.of(living);
        send(source, "torpor set to " + TorporService.round(state.torpor()) + "/"
                + TorporService.round(state.maximum()) + " phase=" + state.phase());
        return 1;
    }

    private static int setHunger(CommandSourceStack source, Entity target, double value) {
        if (!(target instanceof CreatureEntity creature)) return fail(source, "Not a registered creature");
        TamingService.of(creature).setHunger(value);
        send(source, "feeding hunger set to " + TorporService.round(TamingService.of(creature).hunger()));
        return 1;
    }

    private static int reset(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) return fail(source, "Not a living entity");
        TorporService.clear(living);
        if (living instanceof CreatureEntity creature) {
            TamingService.of(creature).resetAttempt();
            TamingService.of(creature).setHunger(Config.WILD_HUNGER_MAX.get());
        }
        send(source, "sedation cleared and attempt reset");
        return 1;
    }

    private static int mount(CommandSourceStack source, Entity target) {
        if (!(target instanceof CreatureEntity creature)) return fail(source, "Not a registered creature");
        var seat = creature.rideProfile();
        var position = creature.seatPosition();
        send(source, "controller=" + (creature.getControllingPassenger() == null ? "-"
                : creature.getControllingPassenger().getName().getString()));
        send(source, "movementMode=" + (seat == null ? "-" : seat.mode())
                + " seat=" + TorporService.round(position.x) + "," + TorporService.round(position.y)
                + "," + TorporService.round(position.z));
        send(source, "seatLocal=" + seat.seatX() + "," + seat.seatY() + "," + seat.seatZ()
                + " yawOffset=" + seat.yawOffset());
        send(source, "dismount=" + seat.groundDismountX() + "," + seat.groundDismountY() + ","
                + seat.groundDismountZ() + " alt=" + seat.altDismountX() + "," + seat.altDismountY() + ","
                + seat.altDismountZ());
        for (var passenger : creature.getPassengers()) {
            send(source, "rider=" + passenger.getUUID() + " (" + passenger.getType().toShortString() + ")");
        }
        return 1;
    }

    private static int roster(CommandSourceStack source) {
        var staticProblems = CreatureProfileRegistry.validate();
        var itemProblems = CreatureProfileRegistry.validateItems();
        send(source, "registered species=" + Species.values().length
                + " profiles=" + java.util.Arrays.stream(Species.values())
                        .filter(s -> CreatureProfileRegistry.of(s) != null).count());
        if (staticProblems.isEmpty() && itemProblems.isEmpty()) {
            send(source, "roster complete: every registered creature has a profile, food and ride seat");
            return 1;
        }
        staticProblems.forEach(problem -> source.sendFailure(Component.literal("profile: " + problem)));
        itemProblems.forEach(problem -> source.sendFailure(Component.literal("food: " + problem)));
        return 0;
    }

    private static int log(CommandSourceStack source, boolean enabled) {
        Config.TAMING_DEBUG_LOG.set(enabled);
        send(source, "taming debug log " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private TamingCommands() {}
}
