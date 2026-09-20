package dev.nez.arksurvivalreturns.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ftb.mods.ftbquests.client.FTBQuestsClient;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.journal.JournalOpener;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client wiring for the Field Journal: the J key and the item's screen hook. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class JournalClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ArkSurvivalReturns.id("keys"));
    private static final KeyMapping OPEN_JOURNAL = new KeyMapping("key.arksurvivalreturns.journal",
            InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY);

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_JOURNAL);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> JournalOpener.set(FTBQuestsClient::openGui));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        while (OPEN_JOURNAL.consumeClick()) FTBQuestsClient.openGui();
    }

    private JournalClient() {}
}
