package dev.nez.arksurvivalreturns.client;

import java.util.*;
import dev.nez.arksurvivalreturns.feature.flying.HabitatPayload;
import dev.nez.arksurvivalreturns.feature.map.DangerMapView;
import io.github.billstark001.xaerobridge.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Optional fullscreen map integration. A tiny pixel nest is one glyph per colony. */
public final class XaeroHabitatOverlay {
    // 0 transparent, 1 nest rim, 2 egg; mirrored by docs/assets/habitat-symbol.svg.
    private static final String[] GLYPH = {"000220000","002222000","002222000","100220001","110000011","011111110","001111100"};
    private record Visible(HabitatPayload.Marker marker,int x,int y) {}
    private static final List<Visible> visible = new ArrayList<>();
    private static boolean enabled = true;
    private static String dimension = "";
    private static long refreshedAt;
    private static DangerMapView.Exploration exploration = (a,b,c,d) -> false;
    public static void register() {
        XaeroWorldMapBridge.registerMapOverlay("arksurvivalreturns:nests",110,XaeroHabitatOverlay::render);
        NeoForge.EVENT_BUS.addListener(XaeroHabitatOverlay::init);
        NeoForge.EVENT_BUS.addListener(XaeroHabitatOverlay::labels);
    }
    public static void init(ScreenEvent.Init.Post event) {
        if (!DangerMapClient.isMap(event.getScreen())) return;
        refreshedAt=0;visible.clear();
        event.addListener(Button.builder(text(),button -> {enabled=!enabled;button.setMessage(text());}).bounds(160,25,114,20).build());
    }
    private static Component text() { return Component.translatable("map.arksurvivalreturns.nests_"+(enabled?"on":"off")); }
    private static void render(MapOverlayContext context) {
        visible.clear();var packet=HabitatMapClient.snapshot();
        if (!enabled || !DangerMapClient.unlocked() || packet==null || !context.dimension().equals(packet.dimension())) return;
        if (!dimension.equals(context.dimension()) || System.nanoTime()-refreshedAt>500_000_000L) {
            dimension=context.dimension();exploration=XaeroExploration.current(dimension);refreshedAt=System.nanoTime();
        }
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
        for (var marker:packet.markers()) {
            var p=marker.center();int x=context.worldToScreenX(p.getX()+0.5),y=context.worldToScreenY(p.getZ()+0.5);
            if (x<5 || y<5 || x>context.width()-5 || y>context.height()-5 || !exploration.contains(p.getX(),p.getZ(),p.getX(),p.getZ())) continue;
            visible.add(new Visible(marker,x,y));context.canvas().fill(x-5,y-4,x+5,y+4,0xCC182028);
            int color=switch(marker.species()) {
                case ARGENTAVIS -> 0xFF93BE8B;
                case ARCHAEOPTERYX -> 0xFF7FA05A;
                case QUETZAL -> 0xFFC8A165;
                case DRAGON -> 0xFF8A8A93;
                default -> 0xFFE7C583;
            };
            for (int row=0;row<GLYPH.length;row++) for (int col=0;col<GLYPH[row].length();col++) {
                char pixel=GLYPH[row].charAt(col);if(pixel!='0')context.canvas().fill(x-4+col,y-3+row,x-3+col,y-2+row,pixel=='2'?0xFFFFF4D8:color);
            }
        }
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
    }
    public static void labels(ScreenEvent.Render.Post event) {
        if (!enabled || !DangerMapClient.unlocked() || !DangerMapClient.isMap(event.getScreen())) return;
        for(var v:visible) if(Math.abs(event.getMouseX()-v.x)<=6 && Math.abs(event.getMouseY()-v.y)<=6) {
            var p=v.marker.center();var label=Component.translatable("map.arksurvivalreturns.nest_label",v.marker.species().displayName,p.getX(),p.getY(),p.getZ());
            var font=Minecraft.getInstance().font;int width=font.width(label),x=Math.clamp(v.x+9,4,Math.max(4,event.getScreen().width-width-4)),y=Math.max(4,v.y-16);
            event.getGuiGraphics().fill(x-3,y-3,x+width+3,y+12,0xED182028);event.getGuiGraphics().text(font,label,x,y,0xFFFFFFFF);break;
        }
    }
    private XaeroHabitatOverlay() {}
}
