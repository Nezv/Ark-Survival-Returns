package dev.nez.arksurvivalreturns.feature.inventory;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The Ark inventory: its menu and the side pockets it adds to the player. */
public final class InventoryContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ArkSurvivalReturns.MOD_ID);
    // Kept through death only when the inventory is: otherwise the pockets were dropped before the copy (dropAll).
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SidePockets>> POCKETS =
            ATTACHMENTS.register("side_pockets", () -> AttachmentType.serializable(SidePockets::new).copyOnDeath().build());
    public static final DeferredHolder<MenuType<?>, MenuType<ArkInventoryMenu>> MENU =
            MENUS.register("inventory", () -> new MenuType<>(ArkInventoryMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); MENUS.register(bus); }
    private InventoryContent() {}
}
