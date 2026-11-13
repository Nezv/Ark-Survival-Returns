package dev.nez.arksurvivalreturns.feature.testground;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Ark Testing Ground: a world type (Create World, World Type) that is flat grass in a biome of its own, with no
 * structures, no features and no spawns of any kind. The first time a player joins such a world it turns off every
 * spawning rule, the day cycle and the weather, sets noon, and lays the ground out ({@link TestGroundBuilder}). The same
 * layout can be built anywhere with {@code /arktest build}.
 *
 * <p>The workshop's stations are topped up every five seconds while their land is loaded, so their processes keep
 * running: the forge smelts, the fires burn and roast, the crusher grinds, the pot cooks, the rack dries.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TestGround {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, ArkSurvivalReturns.id("testing_ground"));
    private static final int KEEP_TICKS = 100;

    /** Every Ark workstation, in the workshop's order. */
    static final Supplier<List<Block>> WORKSHOP = () -> List.of(
            StationContent.WORKING_STATION.get(), StationContent.ARMOURY.get(), StationContent.SADDLERY.get(),
            StationContent.MEDICINE_BENCH.get(), StationContent.MORTAR_AND_PESTLE.get(), StationContent.SMITHING_TABLE.get(),
            StationContent.STORAGE_CRATE.get(), StationContent.CRUSHER.get(), PrimitiveContent.STONE_FIRE.get(),
            PrimitiveContent.PRIMITIVE_FORGE.get(), ModContent.COOKING_POT.get(), ModContent.DRYING_RACK.get(), ModContent.TROUGH.get());

    /** Creative players and operators may use the posts; everything else here is an operator command. */
    public static boolean mayUse(ServerPlayer player) {
        return player.isCreative() || Commands.LEVEL_GAMEMASTERS.check(player.createCommandSourceStack().permissions());
    }

    /** True in a world made with the Testing Ground world type. */
    public static boolean isTestingWorld(ServerLevel overworld) {
        return overworld.getBiome(overworld.getRespawnData().pos()).is(BIOME);
    }

    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var overworld = player.level().getServer().overworld();
        if (!isTestingWorld(overworld)) return;
        var data = Data.get(overworld);
        if (data.built) return;
        quiet(overworld);
        var builder = new TestGroundBuilder(overworld, overworld.getRespawnData().pos());
        var line = builder.buildAll();
        data.built = true;
        data.setDirty();
        player.sendSystemMessage(line);
    }

    /** Nothing spawns, the clock and the weather stand still at noon, and nothing is griefed. */
    public static void quiet(ServerLevel level) {
        var rules = level.getGameRules();
        var server = level.getServer();
        for (var rule : List.of(GameRules.SPAWN_MOBS, GameRules.SPAWN_MONSTERS, GameRules.SPAWN_PATROLS, GameRules.SPAWN_PHANTOMS,
                GameRules.SPAWN_WANDERING_TRADERS, GameRules.SPAWN_WARDENS, GameRules.ADVANCE_TIME, GameRules.ADVANCE_WEATHER, GameRules.MOB_GRIEFING))
            rules.set(rule, false, server);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 6000");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
    }

    /** Adds workshop stations to the ones kept running. */
    static void keep(ServerLevel level, List<BlockPos> stations) {
        var data = Data.get(level);
        for (var pos : stations) data.stations.add(pos.asLong());
        data.setDirty();
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % KEEP_TICKS != 0) return;
        var level = server.overworld();
        var data = level.getDataStorage().get(Data.TYPE);
        if (data == null || data.stations.isEmpty()) return;
        for (long packed : data.stations) {
            var pos = BlockPos.of(packed);
            if (level.isLoaded(pos)) refill(level, pos);
        }
    }

    /** Puts fuel and work into a station and takes what it made, so it never runs dry or fills up. */
    private static void refill(ServerLevel level, BlockPos pos) {
        switch (level.getBlockEntity(pos)) {
            case PrimitiveForgeBlockEntity forge -> {
                if (!forge.output().isEmpty()) forge.extract();
                if (forge.input().getCount() < 16) forge.insert(new ItemStack(Items.RAW_IRON, 64), PrimitiveForgeBlockEntity.INPUT);
                if (forge.fuel().getCount() < 16) forge.insert(new ItemStack(Items.CHARCOAL, 64), PrimitiveForgeBlockEntity.FUEL);
            }
            case StoneFireBlockEntity fire -> {
                if (fire.cooked().getCount() >= 8) fire.extract();
                for (int i = 0; i < 4 && fire.burnTicks() < 2400; i++) if (!fire.addFuel(new ItemStack(Items.OAK_LOG))) break;
                fire.light();
                if (!fire.getBlockState().getValue(StoneFireBlock.POT) && fire.spit().isEmpty()) fire.insertFood(new ItemStack(Items.BEEF, 4));
            }
            case CrusherBlockEntity crusher -> {
                crusher.takeOutput();
                if (crusher.getItem(CrusherBlockEntity.INPUT).getCount() < 16) crusher.insert(new ItemStack(Items.COBBLESTONE, 64));
            }
            case CookingPotBlockEntity pot -> {
                pot.removeItem(CookingPotBlockEntity.OUTPUT_SLOT, 64);
                ItemStack[] meal = {new ItemStack(PrimitiveContent.DRIED_MEAT.get(), 16), new ItemStack(PrimitiveContent.DRIED_MEAT.get(), 16),
                        new ItemStack(Items.BEEF, 16), new ItemStack(Items.CARROT, 16)};
                for (int slot = 0; slot < meal.length; slot++) if (pot.getItem(slot).isEmpty()) pot.setItem(slot, meal[slot]);
            }
            case DryingRackBlockEntity rack -> {
                if (!rack.output().isEmpty()) rack.extract();
                if (rack.input().isEmpty()) rack.insert(new ItemStack(Items.BEEF, 16));
            }
            case TroughBlockEntity trough -> trough.insert(new ItemStack(ModContent.BERRIES.values().iterator().next().get(), 64));
            case null, default -> {}
        }
    }

    /** Whether this world's ground was built, and the workshop stations kept running. */
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("built", false).forGetter(data -> data.built),
                Codec.LONG.listOf().optionalFieldOf("stations", List.of()).forGetter(data -> new ArrayList<>(data.stations))
        ).apply(instance, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(ArkSurvivalReturns.id("testing_ground"), Data::new, CODEC);

        boolean built;
        final Set<Long> stations;

        Data() { this(false, List.of()); }
        Data(boolean built, List<Long> stations) { this.built = built; this.stations = new LinkedHashSet<>(stations); }

        static Data get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE); }
    }

    private TestGround() {}
}
