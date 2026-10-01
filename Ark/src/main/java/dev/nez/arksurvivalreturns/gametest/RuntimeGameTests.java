package dev.nez.arksurvivalreturns.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/** Required runtime contracts: a missing integration never counts as exercised coverage. */
final class RuntimeGameTests {
    static void pack(GameTestHelper h) {
        boolean shipped = Boolean.getBoolean("arksurvivalreturns.testPack");
        for (String id : new String[]{"ftbquests", "ftbteams", "ftblibrary", "curios", "toms_storage", "terralith", "bettercombat"}) {
            var mod = ModList.get().getModContainerById(id);
            h.assertTrue(mod.isPresent(), "Required gameplay integration missing: " + id);
            if (shipped) h.assertTrue(mod.orElseThrow().getModInfo().getDisplayName().startsWith("Ark - "),
                    "Shipped-pack run loaded an upstream jar: " + id);
        }
        if (shipped) {
            try { Class.forName("top.theillusivec4.curios.common.inventory.container.ArkLayout"); }
            catch (ClassNotFoundException e) { throw h.assertionException("Shipped-pack run is missing the Ark Curios fork"); }
        }
        org.slf4j.LoggerFactory.getLogger(RuntimeGameTests.class).info("Required gameplay integrations present; runtime={}", shipped ? "shipped Ark pack" : "upstream development jars");
        h.succeed();
    }
    private RuntimeGameTests() {}
}
