package dev.nez.arksurvivalreturns.feature.mass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.cargo.CargoProfiles;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Server-side load state for players and their mounts: event-driven recomputation with a bounded
 * open-menu watchdog.
 *
 * <p>Carried items and cargo changes mark the owner dirty and one pass per tick at most recomputes
 * the load; the only recurring cost is a one-second recheck while a container menu is open, so
 * inventories are never scanned every tick. Effects scale movement and deny sprint; nothing blocks
 * item moves, so overloading stays a deliberate player choice.
 */
public final class MassService {
    public record Load(double mass, double capacity, MassRules.Band band) {
        public double ratio() {
            return capacity <= 0.0 ? 0.0 : mass / capacity;
        }
    }

    public static final Identifier OVERLOAD_MODIFIER = ArkSurvivalReturns.id("mass_overload");
    private static final Map<UUID, Load> LOADS = new HashMap<>();
    private static final Map<UUID, Load> CREATURE_LOADS = new HashMap<>();
    private static final Map<UUID, Double> SENT_MASS = new HashMap<>();
    private static final Map<UUID, Long> SENT_TICK = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Set<UUID> DIRTY_CREATURES = new HashSet<>();
    private static final Map<UUID, Long> WARN_TICKS = new HashMap<>();
    private static int rulesHash;

    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer serverPlayer) DIRTY.add(serverPlayer.getUUID());
    }

    public static void markDirty(CreatureEntity creature) {
        if (!creature.level().isClientSide()) DIRTY_CREATURES.add(creature.getUUID());
    }

    public static Load load(ServerPlayer player) {
        return LOADS.getOrDefault(player.getUUID(), emptyLoad());
    }

    public static Load creatureLoad(CreatureEntity creature) {
        return CREATURE_LOADS.getOrDefault(creature.getUUID(), emptyLoad());
    }

    /** Effective capacity for the creature's fitted harness; bare allowance without the required tier. */
    public static double creatureCapacity(CreatureEntity creature) {
        return CargoProfiles.capacity(creature.species(), creature.harnessTier());
    }

    /** True when the creature's tracked load is at or past its overload line. */
    public static boolean overloaded(CreatureEntity creature) {
        return MassRules.enabled() && MassRules.overloaded(creatureLoad(creature).ratio());
    }

    /** Rate-limited rider warning for overload behavior; at most one every three seconds. */
    public static void warn(CreatureEntity mount, String key) {
        if (!(mount.getFirstPassenger() instanceof ServerPlayer rider)) return;
        long last = WARN_TICKS.getOrDefault(mount.getUUID(), Long.MIN_VALUE);
        if (mount.tickCount - last < 60) return;
        WARN_TICKS.put(mount.getUUID(), (long) mount.tickCount);
        rider.sendSystemMessage(Component.translatable(key), true);
    }

    /** Drops all tracked state and the movement modifier, e.g. on logout or with mass disabled. */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        DIRTY.remove(id);
        boolean tracked = LOADS.remove(id) != null;
        SENT_MASS.remove(id);
        SENT_TICK.remove(id);
        removeModifier(player);
        if (tracked) MassSync.clear(player);
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 100 == 0) checkRules(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (DIRTY.remove(id) || (tick % 20 == 0 && player.containerMenu != player.inventoryMenu)) refresh(player);
            Load tracked = LOADS.get(id);
            if (tracked != null && !MassRules.sprintAllowed(tracked.ratio()) && player.isSprinting()
                    && !(player.getVehicle() instanceof CreatureEntity)) {
                player.setSprinting(false);
            }
        }
        DIRTY.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        resolveDirtyCreatures(server);
    }

    /** Recomputes one player, applies the overload effect and syncs when the value or band moved. */
    public static void refresh(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!MassRules.enabled()) {
            boolean tracked = LOADS.remove(id) != null;
            SENT_MASS.remove(id);
            SENT_TICK.remove(id);
            removeModifier(player);
            if (tracked) MassSync.clear(player);
            return;
        }
        if (player.getVehicle() instanceof CreatureEntity mount && mount.level() == player.level()) {
            // While mounted the rider's carried load counts on the mount, so the on-foot penalty is off.
            Load mountLoad = mountLoad(mount, player);
            CREATURE_LOADS.put(mount.getUUID(), mountLoad);
            applyModifier(mount, MassRules.speedFactor(mountLoad.ratio()));
            removeModifier(player);
            Load previous = LOADS.put(id, mountLoad);
            boolean bandChanged = previous == null || previous.band() != mountLoad.band();
            if (previous != null && previous.band() != mountLoad.band()) notifyBand(player, previous.band(), mountLoad.band(), mountLoad);
            sync(player, mountLoad, mountLoad, bandChanged);
            return;
        }
        double mass = MassCalculator.playerMass(player);
        // Pack frames and other gear scale the base allowance (carry_capacity attribute).
        double capacity = MassRules.playerCapacity() * dev.nez.arksurvivalreturns.feature.accessory.AccessoryAttributes.value(
                player, dev.nez.arksurvivalreturns.feature.accessory.AccessoryAttributes.CARRY_CAPACITY);
        double ratio = capacity <= 0.0 ? 0.0 : mass / capacity;
        Load load = new Load(mass, capacity, MassRules.band(ratio));
        Load previous = LOADS.put(id, load);
        applyModifier(player, MassRules.speedFactor(ratio));
        if (previous != null && previous.band() != load.band()) notifyBand(player, previous.band(), load.band(), load);
        sync(player, load, null, previous == null || previous.band() != load.band());
    }

    /** Recomputes one creature: cargo mass plus a rider's carried load, against its cargo capacity. */
    public static void refreshCreature(CreatureEntity creature) {
        UUID id = creature.getUUID();
        if (!MassRules.enabled()) {
            CREATURE_LOADS.remove(id);
            removeModifier(creature);
            return;
        }
        Load load = mountLoad(creature, creature.getFirstPassenger() instanceof Player rider ? rider : null);
        CREATURE_LOADS.put(id, load);
        applyModifier(creature, MassRules.speedFactor(load.ratio()));
    }

    private static void resolveDirtyCreatures(MinecraftServer server) {
        if (DIRTY_CREATURES.isEmpty()) return;
        Set<UUID> unresolved = new HashSet<>(DIRTY_CREATURES);
        for (ServerLevel level : server.getAllLevels()) {
            if (unresolved.isEmpty()) break;
            var iterator = unresolved.iterator();
            while (iterator.hasNext()) {
                if (level.getEntity(iterator.next()) instanceof CreatureEntity creature) {
                    iterator.remove();
                    refreshCreature(creature);
                }
            }
        }
        // Unloaded creatures recompute when they join a level again; stale marks are dropped.
        DIRTY_CREATURES.clear();
    }

    /** Cargo plus a rider's carried load; the rider's body and the creature's body stay abstracted. */
    public static Load mountLoad(CreatureEntity creature, @Nullable Player rider) {
        double mass = MassCalculator.cargoMass(creature.tamingInventory())
                + MassCalculator.massOf(creature.harnessSlot().getItem(0));
        if (rider != null) mass += MassCalculator.playerMass(rider);
        double capacity = creatureCapacity(creature);
        double ratio = capacity <= 0.0 ? 0.0 : mass / capacity;
        return new Load(mass, capacity, MassRules.band(ratio));
    }

    private static void sync(ServerPlayer player, Load displayed, @Nullable Load mount, boolean force) {
        UUID id = player.getUUID();
        long tick = player.level().getServer() == null ? 0 : player.level().getServer().getTickCount();
        long last = SENT_TICK.getOrDefault(id, Long.MIN_VALUE);
        double sent = SENT_MASS.getOrDefault(id, Double.NaN);
        boolean valueChanged = Double.isNaN(sent) || Math.abs(sent - displayed.mass()) >= 0.05;
        if (force || (tick - last >= 10 && valueChanged)) {
            SENT_MASS.put(id, displayed.mass());
            SENT_TICK.put(id, tick);
            MassSync.send(player, displayed, mount != null, Config.MASS_GAUGE.get());
        }
    }

    /** Warns on an upward band crossing and confirms when a load eases back below overload. */
    private static void notifyBand(ServerPlayer player, MassRules.Band from, MassRules.Band to, Load load) {
        if (to.ordinal() > from.ordinal()) {
            String key = switch (to) {
                case WARN -> "hud.arksurvivalreturns.mass.warn";
                case OVERLOAD -> "hud.arksurvivalreturns.mass.overload";
                case HEAVY -> "hud.arksurvivalreturns.mass.heavy";
                case NORMAL -> null;
            };
            if (key != null) {
                player.sendSystemMessage(Component.translatable(key, Math.round(load.mass()), Math.round(load.capacity())), true);
            }
        } else if (to.ordinal() < from.ordinal() && to != MassRules.Band.WARN) {
            player.sendSystemMessage(Component.translatable("hud.arksurvivalreturns.mass.eased"), true);
        }
    }

    private static void applyModifier(LivingEntity entity, double factor) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (factor >= 0.9995) {
            attribute.removeModifier(OVERLOAD_MODIFIER);
            return;
        }
        attribute.addOrUpdateTransientModifier(
                new AttributeModifier(OVERLOAD_MODIFIER, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeModifier(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(OVERLOAD_MODIFIER);
    }

    /** Re-applies the load when mass settings change, at most once every five seconds. */
    private static void checkRules(MinecraftServer server) {
        int hash = Objects.hash(Config.MASS_ENABLED.get(), Config.MASS_PRESET.get(), Config.MASS_PLAYER_CAPACITY.get(),
                Config.MASS_WARNING_RATIO.get(), Config.MASS_SLOW_RATIO.get(), Config.MASS_HEAVY_RATIO.get(),
                Config.MASS_SPEED_FLOOR.get(), Config.MASS_MULTIPLIER.get(), Config.MASS_UNKNOWN_CONTAINER.get(),
                Config.MASS_CONTAINER_CONTENT_CAP.get(), Config.MASS_GAUGE.get(), Config.MASS_BARE_CAPACITY.get());
        if (hash == rulesHash) return;
        rulesHash = hash;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DIRTY.add(player.getUUID());
            if (player.getVehicle() instanceof CreatureEntity mount) DIRTY_CREATURES.add(mount.getUUID());
        }
    }

    private static Load emptyLoad() {
        return new Load(0.0, MassRules.playerCapacity(), MassRules.Band.NORMAL);
    }

    private MassService() {}
}
