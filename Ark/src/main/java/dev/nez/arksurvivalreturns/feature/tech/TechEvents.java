package dev.nez.arksurvivalreturns.feature.tech;

import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires gameplay into the technology tree: the reload listener, the event subscriptions and the
 * small entry points that existing systems call when they perform a task the tree tracks.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TechEvents {
    @SubscribeEvent public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(ArkSurvivalReturns.id("tech_tree"), new TechTreeLoader());
    }

    @SubscribeEvent public static void obtain(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            TechService.notify(player, TechEvent.obtain(player, event.getCurrentStack()));
        }
    }

    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TechService.notify(player, TechEvent.obtain(player, event.getCrafting()));
            TechService.notify(player, TechEvent.craft(player, event.getCrafting()));
        }
    }

    @SubscribeEvent public static void consume(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TechService.notify(player, TechEvent.consume(player, event.getItem()));
        }
    }

    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TechService.notify(player, TechEvent.place(player, event.getPlacedBlock()));
        }
    }

    /** Lighting an unlit campfire with flint and steel counts the same as placing a lit one. */
    @SubscribeEvent public static void lightCampfire(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.hasProperty(CampfireBlock.LIT) || state.getValue(CampfireBlock.LIT)) return;
        if (!event.getItemStack().is(Items.FLINT_AND_STEEL)) return;
        TechService.notify(player, TechEvent.place(player, state.setValue(CampfireBlock.LIT, true)));
    }

    @SubscribeEvent public static void wake(PlayerWakeUpEvent event) {
        if (!event.wakeImmediately() && event.getEntity() instanceof ServerPlayer player) {
            TechService.notify(player, TechEvent.sleep(player));
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        int interval = Config.TECH_SCAN_TICKS.get();
        if (interval <= 0 || event.getServer().getTickCount() % interval != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TechService.scan(player);
        }
    }

    // ------------------------------------------------------------------------- system entry points

    /** Called when a creature is tamed; the owner's tribe owns the progress. */
    public static void onTamed(CreatureEntity creature, UUID owner) {
        if (!(creature.level() instanceof ServerLevel level)) return;
        TechService.notify(level, owner, new TechEvent(TechEventKind.TAME, online(level, owner), null, null,
                EntityType.getKey(creature.getType()), level.getGameTime() / 24000L));
    }

    /** Called by the feeding trough when a tame actually eats. */
    public static void onTroughFed(ServerLevel level, CreatureEntity creature) {
        UUID owner = TamingService.of(creature).owner();
        if (owner == null) return;
        TechService.notify(level, owner, new TechEvent(TechEventKind.TROUGH_FEED, online(level, owner), null, null,
                null, level.getGameTime() / 24000L));
    }

    /** Called by a harvest job after one completed work action. */
    public static void onTameWork(CreatureEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        UUID owner = TamingService.of(worker).owner();
        if (owner == null) return;
        TechService.notify(level, owner, new TechEvent(TechEventKind.TAME_WORK, online(level, owner), null, null,
                null, level.getGameTime() / 24000L));
    }

    /** Called when a tamed creature kills a living target. */
    public static void onTameKill(CreatureEntity killer) {
        if (!(killer.level() instanceof ServerLevel level)) return;
        UUID owner = TamingService.of(killer).owner();
        if (owner == null) return;
        TechService.notify(level, owner, new TechEvent(TechEventKind.TAME_KILL, online(level, owner), null, null,
                null, level.getGameTime() / 24000L));
    }

    private static Player online(ServerLevel level, UUID owner) {
        return level.getServer().getPlayerList().getPlayer(owner);
    }

    private TechEvents() {}
}
