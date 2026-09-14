package dev.nez.arksurvivalreturns.client;

import java.util.*;
import dev.nez.arksurvivalreturns.feature.land.LandHabitatPayload;
import dev.nez.arksurvivalreturns.feature.map.DangerMapView;
import io.github.billstark001.xaerobridge.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** One stable marker per discovered land home, clustered when their screen footprints overlap. */
public final class XaeroLandHabitatOverlay {
    private record Visible(int x,int y,List<LandHabitatPayload.Marker> markers) {}
    private static final List<Visible> visible=new ArrayList<>();
    private static boolean enabled=true;
    private static long refreshedAt;
    private static String dimension="";
    private static DangerMapView.Exploration exploration=(a,b,c,d)->false;
    public static void register() {
        XaeroWorldMapBridge.registerMapOverlay("arksurvivalreturns:land_habitats",111,XaeroLandHabitatOverlay::render);
        NeoForge.EVENT_BUS.addListener(XaeroLandHabitatOverlay::init);NeoForge.EVENT_BUS.addListener(XaeroLandHabitatOverlay::labels);
    }
    private static Component text(){return Component.translatable("map.arksurvivalreturns.land_"+(enabled?"on":"off"));}
    public static void reset(){visible.clear();refreshedAt=0;dimension="";exploration=(a,b,c,d)->false;}
    public static void init(ScreenEvent.Init.Post event){
        if(!DangerMapClient.isMap(event.getScreen()))return;reset();
        event.addListener(Button.builder(text(),b->{enabled=!enabled;b.setMessage(text());visible.clear();}).bounds(160,48,114,20).build());
    }
    private static void render(MapOverlayContext context){
        visible.clear();var packet=HabitatMapClient.landSnapshot();
        if(!enabled||!DangerMapClient.unlocked()||packet==null||!context.dimension().equals(packet.dimension()))return;
        if(!dimension.equals(context.dimension())||System.nanoTime()-refreshedAt>500_000_000L){dimension=context.dimension();exploration=XaeroExploration.current(dimension);refreshedAt=System.nanoTime();}
        for(var marker:packet.markers()){
            var p=marker.center();int x=context.worldToScreenX(p.getX()+0.5),y=context.worldToScreenY(p.getZ()+0.5);
            if(x<6||y<6||x>context.width()-6||y>context.height()-6||!exploration.contains(p.getX(),p.getZ(),p.getX(),p.getZ()))continue;
            var overlap=visible.stream().filter(v->Math.abs(x-v.x)<12&&Math.abs(y-v.y)<12).findFirst().orElse(null);
            if(overlap!=null)overlap.markers.add(marker);else visible.add(new Visible(x,y,new ArrayList<>(List.of(marker))));
        }
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
        for(var v:visible){
            var marker=v.markers.getFirst();boolean predator=marker.species().predator;
            var glyph=predator?LandHabitatSymbols.CARNIVORE:LandHabitatSymbols.HERBIVORE;
            context.canvas().fill(v.x-6,v.y-6,v.x+6,v.y+6,0xDD182028);
            int body=predator?0xFFE48E72:0xFF93BE8B,detail=predator?0xFFFFF4D8:0xFFE2F2CC;
            for(int row=0;row<9;row++)for(int col=0;col<9;col++){
                char pixel=glyph.get(row).charAt(col);if(pixel!='0')context.canvas().fill(v.x-4+col,v.y-4+row,v.x-3+col,v.y-3+row,pixel=='1'?body:detail);
            }
        }
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
    }
    public static void labels(ScreenEvent.Render.Post event){
        if(!enabled||!DangerMapClient.unlocked()||!DangerMapClient.isMap(event.getScreen()))return;
        var font=Minecraft.getInstance().font;
        for(var v:visible){
            if(v.markers.size()>1)event.getGuiGraphics().text(font,Component.literal(Integer.toString(v.markers.size())),v.x+4,v.y-10,0xFFFFFFFF);
            if(Math.abs(event.getMouseX()-v.x)>7||Math.abs(event.getMouseY()-v.y)>7)continue;
            var lines=new ArrayList<Component>();
            for(var marker:v.markers.stream().limit(4).toList()){
                var p=marker.center();
                var status=Component.translatable("map.arksurvivalreturns.land_"+(!marker.valid()?"invalid":marker.occupied()?"occupied":"vacant"));
                lines.add(Component.translatable("map.arksurvivalreturns.land_label",marker.species().displayName,p.getX(),p.getY(),p.getZ(),status));
            }
            if(v.markers.size()>4)lines.add(Component.translatable("map.arksurvivalreturns.land_more",v.markers.size()-4));
            int width=lines.stream().mapToInt(font::width).max().orElse(0),x=Math.clamp(v.x+9,4,Math.max(4,event.getScreen().width-width-4));
            int y=Math.clamp(v.y-16,4,Math.max(4,event.getScreen().height-lines.size()*12-4));
            event.getGuiGraphics().fill(x-3,y-3,x+width+3,y+lines.size()*12,0xED182028);
            for(int i=0;i<lines.size();i++)event.getGuiGraphics().text(font,lines.get(i),x,y+i*12,0xFFFFFFFF);
            break;
        }
    }
    private XaeroLandHabitatOverlay() {}
}
