package dev.nez.arksurvivalreturns.feature.levels;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Server-authoritative progression. Vanilla XP is never read or spent. */
public final class ArkLevels {
    public enum Source { KILL, TAME, FIRST_CRAFT, TECH_NODE, BOSS, COMMAND, TRIBE }

    public static ArkProgress get(Player player) { return player.getData(LevelAttachments.PROGRESS); }
    public static boolean requires(Player player, int level) { return get(player).level() >= level; }

    /** Cost of advancing from this level. Level zero costs the configured base XP. */
    public static long xpForNextLevel(int level) {
        return Math.max(1L, (long) Math.ceil(Config.PLAYER_LEVEL_BASE_XP.get()
                * Math.pow((double) Math.max(0, level) + 1, Config.PLAYER_LEVEL_EXPONENT.get())));
    }

    /** Each nearby party member earns a bonus; shared and administrative XP never share again. */
    public static void addXp(Player player, long amount, Source source) {
        if (!(player instanceof ServerPlayer server) || amount <= 0L) return;
        award(server, amount);
        if (source == Source.TRIBE || source == Source.COMMAND) return;
        long shared = (long) Math.floor(amount * Config.PLAYER_XP_TRIBE_SHARE.get());
        if (shared <= 0L) return;
        TribeService.team(server).filter(team -> team.isPartyTeam()).ifPresent(team -> {
            double radius = Config.PLAYER_XP_TRIBE_RADIUS.get();
            for (ServerPlayer member : server.level().getServer().getPlayerList().getPlayers()) {
                if (member == server || member.level() != server.level()
                        || !team.getMembers().contains(member.getUUID()) || member.distanceToSqr(server) > radius * radius) continue;
                award(member, shared);
            }
        });
    }

    private static void award(ServerPlayer player, long amount) {
        ArkProgress progress = get(player);
        int before = progress.level();
        int level = before;
        int cap = Config.PLAYER_LEVEL_CAP.get();
        if (level >= cap) return;
        long xp = amount > Long.MAX_VALUE - progress.xp() ? Long.MAX_VALUE : progress.xp() + amount;
        while (level < cap) {
            long cost = xpForNextLevel(level);
            if (xp < cost) break;
            xp -= cost;
            level++;
        }
        progress.set(level, level >= cap ? 0L : xp);
        player.syncData(LevelAttachments.PROGRESS);
        if (level > before) {
            player.sendSystemMessage(Component.translatable("levels.arksurvivalreturns.up", level), true);
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.0F);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.75F, 1.0F, player.getRandom().nextLong()));
        }
    }

    public static void setLevel(ServerPlayer player, int level) {
        get(player).set(Math.clamp(level, 0, Config.PLAYER_LEVEL_CAP.get()), 0L);
        player.syncData(LevelAttachments.PROGRESS);
    }

    /** Called only after a successful craft, using the recipe rather than its output item. */
    public static void crafted(ServerPlayer player, Identifier recipe) {
        if (get(player).markCrafted(recipe)) addXp(player, Config.PLAYER_XP_FIRST_CRAFT.get(), Source.FIRST_CRAFT);
    }

    private ArkLevels() {}
}
