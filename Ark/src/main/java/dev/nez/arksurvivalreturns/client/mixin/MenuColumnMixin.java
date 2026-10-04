package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.title.MenuColumn;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world list, the server list and the buttons under them (and under the new-world tabs) go to the menu column. */
@Mixin({SelectWorldScreen.class, JoinMultiplayerScreen.class, CreateWorldScreen.class})
abstract class MenuColumnMixin {
    @Shadow @Final private HeaderAndFooterLayout layout;

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void ark$menuColumn(CallbackInfo ci) {
        MenuColumn.apply((Screen) (Object) this, layout);
    }
}
