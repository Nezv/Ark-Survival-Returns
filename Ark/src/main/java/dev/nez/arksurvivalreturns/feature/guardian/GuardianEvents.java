package dev.nez.arksurvivalreturns.feature.guardian;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-side wiring for the First Guardian: the heart drop, the ritual, damage rules and the lifecycle. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class GuardianEvents {

    /** One guaranteed Allosaur Heart from a natural wild Allosaurus killed by a player or their tame. */
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof CreatureEntity creature)) return;
        if (creature.species() != Species.ALLOSAURUS || !creature.isNaturalWildlife()) return;
        if (!(creature.level() instanceof ServerLevel level)) return;
        if (!creditToPlayerOrTame(event.getSource())) return;
        // Exactly one heart; the event never consults Looting, so the drop cannot multiply.
        ItemEntity drop = new ItemEntity(level, creature.getX(), creature.getY(0.5), creature.getZ(),
                new ItemStack(ModContent.ALLOSAUR_HEART.get()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static boolean creditToPlayerOrTame(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) return true;
        if (attacker instanceof CreatureEntity tame) {
            var state = TamingService.of(tame);
            return state.tamed() && state.owner() != null;
        }
        return false;
    }

    /** The heart offering itself: only a verified structure block with a monolith nearby reacts. */
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        InteractionResult result = GuardianService.activate(player, event.getHand(), event.getPos());
        if (result.consumesAction()) event.setCanceled(true);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof GuardianGiganotosaurusEntity guardian) GuardianService.onBossDeath(guardian);
    }

    /** A tame that was not registered for the encounter deals a reduced, configurable share. */
    @SubscribeEvent public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof GuardianGiganotosaurusEntity guardian)) return;
        GuardianService.encounterFor(guardian).ifPresent(encounter -> GuardianBar.update(encounter.key(), guardian));
        if (!(event.getSource().getEntity() instanceof CreatureEntity tame)) return;
        boolean registered = GuardianService.encounterFor(guardian)
                .map(encounter -> encounter.tames().contains(tame.getUUID())).orElse(true);
        if (registered) return;
        float adjusted = (float) GuardianPolicy.tameDamage(false, event.getAmount(),
                Config.GUARDIAN_TAME_DAMAGE_FACTOR.get());
        if (adjusted <= 0f) event.setCanceled(true);
        else event.setAmount(adjusted);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        GuardianService.tick(server);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GuardianBar.removePlayer(player);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        GuardianBar.hideAll();
    }

    private GuardianEvents() {}
}
