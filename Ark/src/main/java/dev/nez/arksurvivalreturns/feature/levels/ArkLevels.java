package dev.nez.arksurvivalreturns.feature.levels;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
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

    /** All the XP a player has earned towards their level: every level's cost below it, and the XP into the next. */
    public static long totalXp(ArkProgress progress) {
        long total = progress.xp();
        for (int level = 0; level < progress.level(); level++) total += xpForNextLevel(level);
        return total;
    }

    /** XP still missing before the player stands at {@code level}; zero when they already do. */
    public static long xpToReach(Player player, int level) {
        var progress = get(player);
        long missing = -progress.xp();
        for (int at = progress.level(); at < Math.min(level, Config.PLAYER_LEVEL_CAP.get()); at++) missing += xpForNextLevel(at);
        return Math.max(0L, missing);
    }

    /** A species' body against a 50-health animal, between a quarter and four times: bigger animals are worth more. */
    public static double body(Species species) {
        return Math.clamp(species.health / 50.0, 0.25, 4.0);
    }

    /** A kill: level and body, and a carnivore's worth many times a plant eater's (the hunt is the danger). */
    public static long killXp(CreatureEntity creature) {
        var species = creature.species();
        double diet = species.predator ? Config.PLAYER_XP_CARNIVORE.get() : 1.0;
        return Math.round(Config.PLAYER_XP_KILL.get() * creature.creatureLevel() * body(species) * diet);
    }

    /** A finished tame: level and body. */
    public static long tameXp(CreatureEntity creature) {
        return Math.round(Config.PLAYER_XP_TAME.get() * creature.creatureLevel() * body(creature.species()));
    }

    /** A tech node: the base for the Prehistoric age, twice for Bronze, three times for Iron. */
    public static long techNodeXp(String age) {
        int step = switch (age) { case "bronze" -> 2; case "iron" -> 3; default -> 1; };
        return (long) Config.PLAYER_XP_TECH_NODE.get() * step;
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
    public static void crafted(ServerPlayer player, Identifier recipe) { crafted(player, recipe, 0); }

    /** A first craft at a workstation is worth more the higher the level its recipe asks. */
    public static void crafted(ServerPlayer player, Identifier recipe, int recipeLevel) {
        if (get(player).markCrafted(recipe)) addXp(player, firstCraftXp(recipeLevel), Source.FIRST_CRAFT);
    }

    public static long firstCraftXp(int recipeLevel) {
        return Config.PLAYER_XP_FIRST_CRAFT.get() + (long) Config.PLAYER_XP_FIRST_CRAFT_LEVEL.get() * Math.max(0, recipeLevel);
    }

    private ArkLevels() {}
}
