package dev.nez.arksurvivalreturns;

import dev.nez.arksurvivalreturns.registry.ModContent;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArkSurvivalReturns.MOD_ID)
public final class ArkSurvivalReturns {
    public static final String MOD_ID = "arksurvivalreturns";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ark Survival Returns");

    public ArkSurvivalReturns(IEventBus bus, ModContainer container) {
        ModContent.ENTITIES.register(bus);
        ModContent.ITEMS.register(bus);
        ModContent.BLOCKS.register(bus);
        ModContent.TABS.register(bus);
        ModContent.MENUS.register(bus);
        ModContent.BLOCK_ENTITIES.register(bus);
        dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.register(bus);
        dev.nez.arksurvivalreturns.feature.taming.TamingAttachments.register(bus);
        dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments.register(bus);
        dev.nez.arksurvivalreturns.gametest.ArkGameTests.FUNCTIONS.register(bus);
        bus.addListener(ModContent::attributes);
        bus.addListener(SpawnRules::placements);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, NighttimeClientConfig.SPEC);
    }

    /** True when the taming feature is enabled by the per-world server configuration. */
    public static boolean tamingEnabled() {
        return Config.TAMING_ENABLED.get();
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
