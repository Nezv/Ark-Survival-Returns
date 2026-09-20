package dev.nez.arksurvivalreturns.feature.mass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server-side player load state: event-driven recomputation with a bounded open-menu watchdog.
 *
 * <p>Inventory changes mark the player dirty and one pass per tick at most recomputes the load;
 * the only recurring cost is a one-second recheck while a container menu is open, so inventories
 * are never scanned every tick. Effects scale movement and deny sprint; nothing blocks item moves.
 */
public final class MassService {
    public record Load(double mass, double capacity, MassRules.Band band) {
        public double ratio() {
            return capacity <= 0.0 ? 0.0 : mass / capacity;
        }
    }

    public static final Identifier OVERLOAD_MODIFIER = ArkSurvivalReturns.id("mass_overload");
    private static final Map<UUID, Load> LOADS = new HashMap<>();
    private static final Map<UUID, Double> SENT_MASS = new HashMap<>();
    private static final Map<UUID, Long> SENT_TICK = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static int rulesHash;

    public static void markDirty(ServerPlayer player) {
        DIRTY.add(player.getUUID());
    }

    public static Load load(ServerPlayer player) {
        return LOADS.getOrDefault(player.getUUID(), emptyLoad());
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
            if (tracked != null && !MassRules.sprintAllowed(tracked.ratio()) && player.isSprinting()) player.setSprinting(false);
        }
        DIRTY.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Recomputes one player, applies the overload modifier and syncs when the value or band moved. */
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
        double mass = MassCalculator.playerMass(player);
        double capacity = MassRules.playerCapacity();
        double ratio = capacity <= 0.0 ? 0.0 : mass / capacity;
        Load load = new Load(mass, capacity, MassRules.band(ratio));
        Load previous = LOADS.put(id, load);
        applyModifier(player, MassRules.speedFactor(ratio));
        if (previous != null && previous.band() != load.band()) notifyBand(player, previous.band(), load.band(), load);
        boolean bandChanged = previous == null || previous.band() != load.band();
        long tick = player.level().getServer() == null ? 0 : player.level().getServer().getTickCount();
        long last = SENT_TICK.getOrDefault(id, Long.MIN_VALUE);
        double sent = SENT_MASS.getOrDefault(id, Double.NaN);
        boolean valueChanged = Double.isNaN(sent) || Math.abs(sent - mass) >= 0.05;
        if (bandChanged || (tick - last >= 10 && valueChanged)) {
            SENT_MASS.put(id, mass);
            SENT_TICK.put(id, tick);
            MassSync.send(player, load, Config.MASS_GAUGE.get());
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

    private static void applyModifier(ServerPlayer player, double factor) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (factor >= 0.9995) {
            attribute.removeModifier(OVERLOAD_MODIFIER);
            return;
        }
        attribute.addOrUpdateTransientModifier(
                new AttributeModifier(OVERLOAD_MODIFIER, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeModifier(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(OVERLOAD_MODIFIER);
    }

    /** Re-applies the load when mass settings change, at most once every five seconds. */
    private static void checkRules(MinecraftServer server) {
        int hash = Objects.hash(Config.MASS_ENABLED.get(), Config.MASS_PRESET.get(), Config.MASS_PLAYER_CAPACITY.get(),
                Config.MASS_WARNING_RATIO.get(), Config.MASS_SLOW_RATIO.get(), Config.MASS_HEAVY_RATIO.get(),
                Config.MASS_SPEED_FLOOR.get(), Config.MASS_MULTIPLIER.get(), Config.MASS_UNKNOWN_CONTAINER.get(),
                Config.MASS_CONTAINER_CONTENT_CAP.get(), Config.MASS_GAUGE.get());
        if (hash == rulesHash) return;
        rulesHash = hash;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) DIRTY.add(player.getUUID());
    }

    private static Load emptyLoad() {
        return new Load(0.0, MassRules.playerCapacity(), MassRules.Band.NORMAL);
    }

    private MassService() {}
}
