package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.title.MenuColumn;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The new-world tabs and their contents go to the menu column; {@link MenuColumnMixin} moves the buttons under them. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldTabsMixin {
    @Shadow @Final private HeaderAndFooterLayout layout;
    @Shadow @Final private TabManager tabManager;
    @Shadow private @Nullable TabNavigationBar tabNavigationBar;

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void ark$tabsInColumn(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        int column = MenuColumn.width(screen);
        if (column == 0 || tabNavigationBar == null) return;
        tabNavigationBar.updateWidth(column);
        int top = tabNavigationBar.getRectangle().bottom();
        // The tab manager keeps this area: a tab selected later is laid out in the column too.
        tabManager.setTabArea(new ScreenRectangle(0, top, column, screen.height - layout.getFooterHeight() - top));
    }
}
