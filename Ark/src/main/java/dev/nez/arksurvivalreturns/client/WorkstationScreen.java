package dev.nez.arksurvivalreturns.client;

import com.google.gson.Gson;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.inventory.InventoryPayload;
import dev.nez.arksurvivalreturns.feature.station.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A workstation's graph screen: the shared {@link WorkstationView} in its original 320 x 200 design units, in the
 * bench's baroque frame, scaled to half the window. */
public final class WorkstationScreen extends AbstractContainerScreen<WorkstationMenu> implements WorkstationView.Host {
    private record Frame(int margin, int top, int bottom, int width, int height) {}
    private Frame frame;
    private Identifier frameTexture;
    private WorkstationView view;
    private double scale, panelX, panelY;

    public WorkstationScreen(WorkstationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override public String station() { return menu.station; }
    @Override public int containerId() { return menu.containerId; }
    @Override public int craftingLevel() { return menu.craftingLevel(); }
    @Override public boolean levelGate() { return menu.levelGate(); }
    @Override public int displayedLevel() { return menu.displayedLevel(); }

    @Override protected void init() {
        super.init();
        try (var reader = minecraft.getResourceManager().getResourceOrThrow(ArkSurvivalReturns.id("workstation/frame.json")).openAsReader()) {
            frame = new Gson().fromJson(reader, Frame.class);
        } catch (java.io.IOException e) { throw new IllegalStateException("Missing workstation frame", e); }
        var assets = WorkstationView.Assets.load(minecraft.getResourceManager());
        frameTexture = ArkSurvivalReturns.id("textures/gui/workstation/" + Identifier.parse(menu.station).getPath() + ".png");
        view = new WorkstationView(assets, assets.style(), this, null);
        view.overlay = g -> g.blit(RenderPipelines.GUI_TEXTURED, frameTexture, -frame.margin, -frame.top, 0f, 0f, frame.width, frame.height, frame.width, frame.height);
    }
    public void refresh() { if (view != null) view.refresh(); }
    public void crafted(WorkstationPayload.Result packet) { if (view != null) view.crafted(packet); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
    }
    private void layout() {
        var p = view.style.panel(); double tall = p.height() + frame.top + frame.bottom;
        scale = Math.min(width * p.widthFraction(), height * p.maxHeightFraction() * p.width() / tall) / p.width();
        double uw = Math.ceil(width / scale), uh = Math.ceil(height / scale);
        panelX = Math.floor((uw - p.width()) / 2); panelY = Math.floor((uh - tall) / 2) + frame.top;
    }
    private double localX(double x) { return x / scale - panelX; }
    private double localY(double y) { return y / scale - panelY; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (view == null) return;
        layout();
        g.fill(0, 0, width, height, WorkstationView.hex(view.style.palette().get("backdrop")));
        view.tick();
        g.pose().pushMatrix(); g.pose().scale((float) scale, (float) scale); g.pose().translate((float) panelX, (float) panelY);
        view.render(g, localX(mouseX), localY(mouseY));
        g.pose().popMatrix();
        g.pose().pushMatrix(); g.pose().scale((float) scale, (float) scale);
        view.tooltip(g, (int) (mouseX / scale), (int) (mouseY / scale), (int) (width / scale), (int) (height / scale));
        g.pose().popMatrix();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return view != null && view.mouseClicked(localX(event.x()), localY(event.y()), event.button());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return view != null && view.mouseDragged(localX(event.x()), localY(event.y()), 3 / scale);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (view == null || event.button() != 0) return false;
        return view.mouseReleased(localX(event.x()), localY(event.y()), (event.modifiers() & 1) != 0);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return view != null && view.mouseScrolled(localX(x), localY(y), scrollY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && view != null && view.back()) return true;
        return super.keyPressed(event);
    }
    /** The inventory's own graph, opened full size from the inventory, closes back into it. */
    @Override public void onClose() {
        super.onClose();
        if (InventoryCrafting.STATION.equals(menu.station)) ClientPacketDistributor.sendToServer(new InventoryPayload.Open());
    }
}
