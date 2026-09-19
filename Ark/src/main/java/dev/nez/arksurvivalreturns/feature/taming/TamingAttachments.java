package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge data attachments for torpor and taming.
 *
 * <p>Attachments are the loader's own per-entity component system: the state lives on the entity, is
 * serialized with it, survives logout and chunk unload, and is sent to tracking clients. No global
 * {@code UUID -> state} map is used anywhere in this feature.
 *
 * <p>Neither attachment copies on death. A player's sedation is explicitly cleared on death, and an entity
 * cannot carry a tame across a respawn.
 */
public final class TamingAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArkSurvivalReturns.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TorporState>> TORPOR =
            ATTACHMENTS.register("torpor", () -> AttachmentType.serializable(TorporState::new)
                    .sync(TorporState.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TamingState>> TAMING =
            ATTACHMENTS.register("taming", () -> AttachmentType.serializable(TamingState::new)
                    .sync(TamingState.STREAM_CODEC)
                    .build());

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private TamingAttachments() {}
}
