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
    /**
     * Synced to the player and everyone who can see them (so a reviver's client knows who is downed) when it
     * starts and ends; the owner's HUD countdown still uses the dedicated downed payload.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DownedState>> DOWNED =
            ATTACHMENTS.register("downed", () -> AttachmentType.serializable(DownedState::new)
                    .sync(DownedState.STREAM_CODEC).build());

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private RecoveryAttachments() {}
}
