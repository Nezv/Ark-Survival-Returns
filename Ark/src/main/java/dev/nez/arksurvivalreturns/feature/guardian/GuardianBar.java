package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * One server-controlled boss event per active encounter, shown to participants and to any nearby
 * player. The bar follows the boss health, disappears on reset, death and disconnect, and returns
 * after a reconnect because the encounter tick re-syncs the viewers.
 */
public final class GuardianBar {
    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

    public static void show(String key, GuardianGiganotosaurusEntity boss) {
        BARS.computeIfAbsent(key, ignored -> new ServerBossEvent(UUID.randomUUID(),
                name(boss), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
        update(key, boss);
    }

    public static void update(String key, GuardianGiganotosaurusEntity boss) {
        ServerBossEvent bar = BARS.get(key);
        if (bar == null) return;
        bar.setName(name(boss));
        bar.setProgress(Math.clamp(boss.getHealth() / Math.max(1f, boss.getMaxHealth()), 0f, 1f));
    }

    /** Adds missing viewers and removes everyone else; called at a bounded cadence by the service. */
    public static void sync(String key, Collection<ServerPlayer> viewers) {
        ServerBossEvent bar = BARS.get(key);
        if (bar == null) return;
        Set<ServerPlayer> desired = new HashSet<>(viewers);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!desired.contains(player)) bar.removePlayer(player);
        }
        Set<ServerPlayer> current = new HashSet<>(bar.getPlayers());
        for (ServerPlayer player : desired) {
            if (!current.contains(player)) bar.addPlayer(player);
        }
    }

    public static void hide(String key) {
        ServerBossEvent bar = BARS.remove(key);
        if (bar != null) bar.removeAllPlayers();
    }

    public static void hideAll() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /** Drops one disconnected viewer from every encounter bar. */
    public static void removePlayer(ServerPlayer player) {
        BARS.values().forEach(bar -> bar.removePlayer(player));
    }

    private static Component name(GuardianGiganotosaurusEntity boss) {
        return Component.translatable("guardian.arksurvivalreturns.bar", boss.getDisplayName(),
                (int) Math.ceil(boss.getHealth()), (int) Math.ceil(boss.getMaxHealth()));
    }

    private GuardianBar() {}
}
