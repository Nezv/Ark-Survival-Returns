package dev.nez.arksurvivalreturns.client;

import static dev.nez.arksurvivalreturns.feature.inventory.ArkInventoryLayout.*;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.inventory.ArkInventoryMenu;
import dev.nez.arksurvivalreturns.feature.station.InventoryCrafting;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import dev.nez.arksurvivalreturns.feature.station.WorkstationStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Ark inventory: worn gear around a tall player and the inventory's crafting graph in the top band, the eleven
 * column storage and the hotbar below. The graph is the same {@link WorkstationView} the benches use, at a smaller
 * panel; its corner button opens it full size.
 */
public final class ArkInventoryScreen extends AbstractContainerScreen<ArkInventoryMenu> implements WorkstationView.Host {
    private static final Identifier PANEL = ArkSurvivalReturns.id("textures/gui/ark_inventory.png");
    private final EffectsInInventory effects;
    private WorkstationView view;
    private Button expand;
    private float xMouse, yMouse;
    private boolean viewPressed;

    public ArkInventoryScreen(ArkInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        effects = new EffectsInInventory(this);
    }
    @Override public String station() { return InventoryCrafting.STATION; }
    @Override public int containerId() { return menu.containerId; }
    @Override public int craftingLevel() { return menu.craftingLevel(); }
    @Override public boolean levelGate() { return menu.levelGate(); }
    @Override public int displayedLevel() { return menu.displayedLevel(); }

    @Override protected void init() {
        super.init();
        var assets = WorkstationView.Assets.load(minecraft.getResourceManager());
        var p = assets.style().panel();
        var style = new WorkstationStyle(new WorkstationStyle.Panel(CRAFT_W, CRAFT_H, p.widthFraction(), p.maxHeightFraction(),
                p.title(), p.bar(), p.inset()), assets.style().node(), assets.style().link(), assets.style().force(),
                assets.style().motion(), assets.style().palette());
        view = new WorkstationView(assets, style, this, "Crafting");
        view.titleRightInset = 14;
        expand = addRenderableWidget(Button.builder(Component.literal("+"), b -> ClientPacketDistributor.sendToServer(new WorkstationPayload.OpenInventory()))
                .bounds(leftPos + CRAFT_X + CRAFT_W - 14, topPos + CRAFT_Y + 2, 11, 11)
                .tooltip(Tooltip.create(Component.literal("Open the crafting graph full size"))).build());
    }
    public void refresh() { if (view != null) view.refresh(); }
    public void crafted(WorkstationPayload.Result packet) { if (view != null) view.crafted(packet); }

    private double viewX(double x) { return x - leftPos - CRAFT_X; }
    private double viewY(double y) { return y - topPos - CRAFT_Y; }
    private boolean overView(double x, double y) { return view != null && view.contains(viewX(x), viewY(y)); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        effects.extractRenderState(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
        xMouse = mouseX; yMouse = mouseY;
        if (view != null && overView(mouseX, mouseY)) {
            g.nextStratum();
            view.tooltip(g, mouseX, mouseY, width, height);
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, 0f, 0f, imageWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + PLAYER_X0, y + PLAYER_Y0, x + PLAYER_X1, y + PLAYER_Y1,
                PLAYER_SIZE, 0.0625F, xMouse, yMouse, minecraft.player);
        if (view == null) return;
        view.tick();
        g.pose().pushMatrix(); g.pose().translate(x + CRAFT_X, y + CRAFT_Y);
        view.render(g, viewX(mouseX), viewY(mouseY));
        g.pose().popMatrix();
    }
    /** No titles: the slots speak for themselves and the graph names itself. */
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {}
    @Override public boolean showsActiveEffects() { return effects.canSeeEffects(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overView(event.x(), event.y()) && !expand.isMouseOver(event.x(), event.y())) {
            viewPressed = true;
            view.mouseClicked(viewX(event.x()), viewY(event.y()), event.button());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (viewPressed) return view.mouseDragged(viewX(event.x()), viewY(event.y()), 3);
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (viewPressed) {
            viewPressed = false;
            if (event.button() == 0) view.mouseReleased(viewX(event.x()), viewY(event.y()), (event.modifiers() & 1) != 0);
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (overView(x, y)) return view.mouseScrolled(viewX(x), viewY(y), scrollY) || true;
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
}
