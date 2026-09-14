package dev.nez.arksurvivalreturns;

import dev.nez.arksurvivalreturns.registry.ModContent;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ArkSurvivalReturns.MOD_ID)
public final class ArkSurvivalReturns {
    public static final String MOD_ID = "arksurvivalreturns";

    public ArkSurvivalReturns(IEventBus bus, ModContainer container) {
        ModContent.ENTITIES.register(bus);
        ModContent.ITEMS.register(bus);
        ModContent.BLOCKS.register(bus);
        ModContent.TABS.register(bus);
        dev.nez.arksurvivalreturns.gametest.ArkGameTests.FUNCTIONS.register(bus);
        bus.addListener(ModContent::attributes);
        bus.addListener(SpawnRules::placements);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, NighttimeClientConfig.SPEC);
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
