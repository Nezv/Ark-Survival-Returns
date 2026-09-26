package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class BiomeAnnouncements {
    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static final ChatFormatting[] COLORS = { ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_RED };
    public record Region(Identifier dimension, Identifier biome, int danger) {}
    /** Require a stable crossing for two checks; suppress boundary jitter without suppressing a return visit. */
    public static final class Visit {
        private Region announced, candidate;
        private int stable;
        public boolean update(Region next) {
            if (next.equals(announced)) { candidate = null; stable = 0; return false; }
            if (!next.equals(candidate)) { candidate = next; stable = 1; }
            else stable++;
            if (announced == null || stable >= 2) { announced = next; candidate = null; stable = 0; return true; }
            return false;
        }
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!Config.BIOME_MESSAGES.get() || !player.isAlive()) { VISITS.remove(player.getUUID()); return; }
        var biome = player.level().getBiome(player.blockPosition());
        var key = biome.unwrapKey();
        if (key.isEmpty()) return;
        var id = key.get().identifier();
        int danger = player.level().dimension() == Level.OVERWORLD ? DangerTier.at(player.level(), player.blockPosition()).dangerLevel() : 0;
        var region = new Region(player.level().dimension().identifier(), id, danger);
        if (!VISITS.computeIfAbsent(player.getUUID(), unused -> new Visit()).update(region)) return;
        String fallback = java.util.Arrays.stream(id.getPath().split("_"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
        var name = Component.translatableWithFallback("biome." + id.getNamespace() + "." + id.getPath(), fallback);
        if (danger == 0) {
            player.sendSystemMessage(Component.translatable("chat.arksurvivalreturns.biome_unrated", name).withStyle(ChatFormatting.GRAY));
        } else {
            var tier = DangerTier.values()[danger - 1];
            int a = Config.MIN_LEVEL.get(tier).get(), b = Config.MAX_LEVEL.get(tier).get();
            player.sendSystemMessage(Component.translatable("chat.arksurvivalreturns.biome", name, danger,
                    Math.min(a, b), Math.max(a, b)).withStyle(COLORS[danger - 1]));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { VISITS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { VISITS.clear(); }
    private BiomeAnnouncements() {}
}
