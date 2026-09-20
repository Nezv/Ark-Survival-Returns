package dev.nez.arksurvivalreturns.feature.farm;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Planting: use an Ark berry on suitable ground to plant its bush. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class FarmEvents {
    @SubscribeEvent public static void plant(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.FARM_ENABLED.get() || event.getLevel().isClientSide() || event.getFace() != Direction.UP) return;
        Block bush = FarmCrops.bushFor(event.getItemStack().getItem());
        if (bush == null) return;
        Level level = event.getLevel();
        BlockPos ground = event.getPos();
        BlockPos target = ground.above();
        if (!level.getBlockState(ground).is(FarmTags.PLANTABLE_ON)) return;
        if (!level.getBlockState(target).isAir()) return;
        if (!level.setBlock(target, bush.defaultBlockState(), Block.UPDATE_ALL)) return;
        level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (event.getEntity() instanceof Player player && !player.getAbilities().instabuild) event.getItemStack().shrink(1);
        // The planting is done; vanilla block and item use must not run again on top of it.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
    }

    private FarmEvents() {}
}
