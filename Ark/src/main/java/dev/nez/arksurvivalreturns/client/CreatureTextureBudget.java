package dev.nez.arksurvivalreturns.client;

import java.util.HashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Creature textures leave video memory when no creature has been drawn with them for five minutes (A01 OPT-10), so a
 * session that met forty species in four colours does not keep all of them; one comes back from the pack when needed.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class CreatureTextureBudget {
    private static final long IDLE_TICKS = 20L * 60 * 5;
    private static final Map<Identifier, Long> LAST_USE = new HashMap<>();
    private static long now;

    /** Called whenever a creature asks for its texture. */
    static Identifier used(Identifier texture) {
        LAST_USE.put(texture, now);
        return texture;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (++now % 600 != 0) return;
        var textures = Minecraft.getInstance().getTextureManager();
        var iterator = LAST_USE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() < IDLE_TICKS) continue;
            textures.release(entry.getKey());
            iterator.remove();
        }
    }

    private CreatureTextureBudget() {}
}
