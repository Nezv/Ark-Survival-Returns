package dev.nez.arksurvivalreturns.feature.levels;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class LevelAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArkSurvivalReturns.MOD_ID);
    // Serializable attachments also copy when returning from the End. Death opts in explicitly.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArkProgress>> PROGRESS =
            ATTACHMENTS.register("progress", () -> AttachmentType.serializable(ArkProgress::new)
                    .copyOnDeath().sync((holder, owner) -> holder == owner, ArkProgress.STREAM_CODEC).build());

    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); }
    private LevelAttachments() {}
}
