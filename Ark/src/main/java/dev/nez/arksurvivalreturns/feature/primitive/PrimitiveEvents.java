package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechEventKind;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The Prehistoric gates: logs need an axe, leaves give the first sticks, wooden tools and furnaces
 * are replaced by the rock route, the stone fire and the primitive forge.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class PrimitiveEvents {
    public static final Set<String> WOODEN_TOOLS = Set.of("minecraft:wooden_pickaxe", "minecraft:wooden_axe",
            "minecraft:wooden_shovel", "minecraft:wooden_hoe", "minecraft:wooden_sword");
    public static final Set<String> FURNACES = Set.of("minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace");

    /** Server config loads after the first data pack load, so recipe filtering falls back to defaults. */
    static boolean enabled(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    public static boolean needsAxe(BlockState state, ItemStack tool) {
        return enabled(Config.PRIMITIVE_LOGS_NEED_AXE) && state.is(BlockTags.LOGS) && !tool.is(ItemTags.AXES);
    }

    @SubscribeEvent public static void harvest(PlayerEvent.HarvestCheck event) {
        if (needsAxe(event.getTargetBlock(), event.getEntity().getMainHandItem())) event.setCanHarvest(false);
    }

    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (needsAxe(event.getState(), event.getEntity().getMainHandItem())) event.setNewSpeed(event.getNewSpeed() * 0.25f);
    }

    /** Leaves broken by hand or tool (never shears) sometimes give a stick: the first handle. */
    @SubscribeEvent public static void leafSticks(BlockDropsEvent event) {
        if (!event.getState().is(BlockTags.LEAVES) || !(event.getBreaker() instanceof Player)) return;
        if (event.getTool().is(Items.SHEARS)) return;
        if (event.getLevel().getRandom().nextDouble() >= Config.PRIMITIVE_LEAF_STICKS.get()) return;
        var pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(Items.STICK)));
    }

    /** Furnaces generated in villages stay as decoration; the fire and the forge do their work. */
    @SubscribeEvent public static void furnaceUse(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled(Config.PRIMITIVE_NO_FURNACES)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(Blocks.FURNACE) && !state.is(Blocks.SMOKER) && !state.is(Blocks.BLAST_FURNACE)) return;
        if (event.getEntity().isSecondaryUseActive() && !event.getItemStack().isEmpty()) return; // Placing against it.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("primitive.arksurvivalreturns.furnace_replaced"), true);
        }
    }

    @SubscribeEvent public static void recipes(ModifyRecipeJsonsEvent event) {
        boolean noWood = enabled(Config.PRIMITIVE_NO_WOODEN_TOOLS);
        boolean noFurnace = enabled(Config.PRIMITIVE_NO_FURNACES);
        if (!noWood && !noFurnace) return;
        int before = event.getRecipeJsons().size();
        event.getRecipeJsons().entrySet().removeIf(entry -> {
            if (entry.getKey().getNamespace().equals(ArkSurvivalReturns.MOD_ID)) return false;
            String result = result(entry.getValue());
            return result != null && (noWood && WOODEN_TOOLS.contains(result) || noFurnace && FURNACES.contains(result));
        });
        if (before != event.getRecipeJsons().size()) {
            ArkSurvivalReturns.LOGGER.info("Prehistoric progression removed {} recipes", before - event.getRecipeJsons().size());
        }
    }

    private static String result(JsonElement recipe) {
        if (!(recipe instanceof JsonObject object) || !(object.get("result") instanceof JsonObject result)) return null;
        return result.has("id") ? result.get("id").getAsString() : null;
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_FIGHT = new java.util.concurrent.ConcurrentHashMap<>();

    /** "We should fight them": player damage on an Ark creature, reported at most every five seconds per player. */
    @SubscribeEvent public static void fight(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof CreatureEntity) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getInflictedDamage() <= 0) return;
        long now = player.level().getGameTime();
        Long last = LAST_FIGHT.get(player.getUUID());
        if (last != null && now - last < 100 && now >= last) return;
        LAST_FIGHT.put(player.getUUID(), now);
        TechService.notify(player, TechEvent.simple(TechEventKind.DAMAGE_CREATURE, player));
    }

    private PrimitiveEvents() {}
}
