package dev.nez.arksurvivalreturns.feature.recovery;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player attachments for the downed state (the rescue window before death). */
public final class RecoveryAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArkSurvivalReturns.MOD_ID);
    /** Serializable but not auto-synced: the HUD uses the dedicated downed payload. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DownedState>> DOWNED =
            ATTACHMENTS.register("downed", () -> AttachmentType.serializable(DownedState::new).build());

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private RecoveryAttachments() {}
}
