package dev.nez.arksurvivalreturns.feature.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.feature.work.WorkProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Server side of the command wheel (F22): the player whistles, and the tame under the crosshair, or every own tame
 * within whistle range, takes the order.
 *
 * <p>Every request is checked here: the tame's owner, or a tribe member with order permission (work permission for
 * WORK), within range and conscious. A group whistle reaches only the player's own tames. CALL keeps each tame's
 * order and moves its anchor to a spot round the caller, so a staying herd walks over and stays there, a wandering one
 * wanders there and a worker works there. Everyone near hears the whistle; each tame that took the order shows a
 * note above its head.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class CompanionWhistle {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ArkSurvivalReturns.MOD_ID);
    private static final Map<CompanionCommand, DeferredHolder<SoundEvent, SoundEvent>> WHISTLES = new EnumMap<>(CompanionCommand.class);
    static {
        for (var command : CompanionCommand.values()) {
            String name = "companion.whistle." + command.key();
            WHISTLES.put(command, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ArkSurvivalReturns.id(name))));
        }
    }
    /** Reach of a pet, in blocks beyond the tame's half width. */
    public static final double PET_REACH = 6.0;
    private static final int COOLDOWN_TICKS = 6;
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();

    public static void register(IEventBus bus) { SOUNDS.register(bus); }

    public static double range() { return Config.COMPANION_WHISTLE_RANGE.get(); }

    public static boolean workCapable(CreatureEntity creature) {
        return WorkProfiles.of(creature.species()).job() != WorkProfiles.Job.NONE;
    }

    @SubscribeEvent static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CompanionCommandPayload.TYPE, CompanionCommandPayload.STREAM_CODEC, CompanionWhistle::handle);
    }

    private static void handle(CompanionCommandPayload packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return;
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST_REQUEST.put(player.getUUID(), now);
        if (packet.entityId() == CompanionCommandPayload.ALL) whistleAll(player, packet.command());
        else if (player.level().getEntity(packet.entityId()) instanceof CreatureEntity creature) whistleOne(player, creature, packet.command());
    }

    /** One tame, chosen by the crosshair. */
    public static void whistleOne(ServerPlayer player, CreatureEntity creature, CompanionCommand command) {
        if (!creature.isAlive() || !creature.isTamed()) return;
        boolean owner = creature.isOwnedBy(player);
        if (command == CompanionCommand.PET) {
            if (!owner && !TribeService.isTribeMember(creature, player)) return;
            if (player.distanceTo(creature) > PET_REACH + creature.getBbWidth() / 2) {
                tell(player, "command.pet_far", creature.getDisplayName());
                return;
            }
            whistle(player, command);
            creature.pet(player);
            return;
        }
        whistle(player, command);
        if (player.distanceTo(creature) > range() + creature.getBbWidth() / 2) {
            tell(player, "command.far", creature.getDisplayName());
            return;
        }
        if (!owner && !TribeService.canCommand(creature, player)) {
            if (TribeService.isTribeMember(creature, player)) player.sendSystemMessage(Component.translatable(
                    "taming.arksurvivalreturns.denied.command", creature.getDisplayName()), true);
            return;
        }
        if (TorporService.restricted(creature)) {
            tell(player, "command.unconscious", creature.getDisplayName());
            return;
        }
        if (command == CompanionCommand.WORK && !owner && !TribeService.canWork(creature, player)) {
            player.sendSystemMessage(Component.translatable("taming.arksurvivalreturns.denied.work", creature.getDisplayName()), true);
            return;
        }
        if (!apply(player, creature, command, 0)) {
            tell(player, "command.cannot_work", creature.getDisplayName());
            return;
        }
        tell(player, "command." + command.key(), creature.getDisplayName());
    }

    /** Every own tame within whistle range, nearest first. */
    public static void whistleAll(ServerPlayer player, CompanionCommand command) {
        if (command == CompanionCommand.PET) return;
        whistle(player, command);
        double range = range();
        var tames = new ArrayList<>(player.level().getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(range),
                creature -> creature.isAlive() && creature.isTamed() && creature.isOwnedBy(player)
                        && !TorporService.restricted(creature) && creature.distanceTo(player) <= range));
        if (tames.isEmpty()) {
            tell(player, "command.none", (int) range);
            return;
        }
        tames.sort(Comparator.comparingDouble(creature -> creature.distanceToSqr(player)));
        int done = 0;
        for (var creature : tames) if (apply(player, creature, command, done)) done++;
        if (done == 0) tell(player, "command.group.cannot_work");
        else tell(player, "command.group." + command.key(), done);
    }

    /** Gives one command to one tame the player may command; false when it cannot take it (WORK without a job). */
    private static boolean apply(ServerPlayer player, CreatureEntity creature, CompanionCommand command, int index) {
        switch (command) {
            case WORK -> {
                if (!workCapable(creature) || !Config.WORK_ENABLED.get()) return false;
                CompanionService.setOrder(creature, CompanionOrder.WORK);
            }
            case CALL -> CompanionService.call(creature, callSpot(player, creature, index));
            case PET -> { return false; }
            default -> CompanionService.setOrder(creature, command.order());
        }
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.NOTE, creature.getX(), creature.getY() + creature.getBbHeight() + 0.5,
                    creature.getZ(), 0, command.note() / 24.0, 0, 0, 1);
        }
        return true;
    }

    /**
     * A spot round the caller for the index-th tame called: a sunflower spiral spaced by body width, so a called herd
     * does not crowd one block. Land tames get a spot to stand on near the caller's height, flyers and swimmers the
     * caller's own height.
     */
    private static BlockPos callSpot(ServerPlayer player, CreatureEntity creature, int index) {
        double turn = index * 2.39996;
        double radius = 1.5 + creature.getBbWidth() * 0.75 + 1.2 * Math.sqrt(index);
        BlockPos around = BlockPos.containing(player.getX() + Math.cos(turn) * radius, player.getY(),
                player.getZ() + Math.sin(turn) * radius);
        if (!creature.species().landHabitat() || creature.isInWater() || !(player.level() instanceof ServerLevel level)) return around;
        return standingSpot(level, around);
    }

    /** The first open block with firm ground under it, from a little above the caller down; never loads a chunk. */
    private static BlockPos standingSpot(ServerLevel level, BlockPos around) {
        if (!level.isLoaded(around)) return around;
        var cursor = around.mutable();
        for (int dy = 2; dy >= -24; dy--) {
            cursor.setY(around.getY() + dy);
            BlockPos below = cursor.below();
            if (level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return cursor.immutable();
        }
        // A caller high in the air: the ground under the spot.
        return new BlockPos(around.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.getX(), around.getZ()),
                around.getZ());
    }

    private static void whistle(ServerPlayer player, CompanionCommand command) {
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), WHISTLES.get(command).get(),
                SoundSource.PLAYERS, command == CompanionCommand.PET ? 0.6f : 1.0f, Mth.randomBetween(player.getRandom(), 0.96f, 1.04f));
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("companion.arksurvivalreturns." + key, args), true);
    }

    @SubscribeEvent static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_REQUEST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent static void stopped(ServerStoppedEvent event) {
        LAST_REQUEST.clear();
    }

    private CompanionWhistle() {}
}
