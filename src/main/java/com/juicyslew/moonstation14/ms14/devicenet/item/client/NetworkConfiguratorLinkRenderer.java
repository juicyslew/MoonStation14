package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Snapshot-only visual pane, drawn inside the retained storage workspace. */
public final class NetworkConfiguratorLinkRenderer {
    private NetworkConfiguratorLinkRenderer() { }
    public static void draw(GuiGraphics g,NetworkConfiguratorLinkWindow w,int sw,int sh,int mouseX,int mouseY){
        if(!w.visible()||w.snapshot()==null)return;w.resize(sw,sh);
        var mc=Minecraft.getInstance();var s=w.snapshot();int x=0,y=0;
        g.pose().pushPose();g.pose().translate(w.x(),w.y(),0);g.pose().scale((float)w.displayScale(),(float)w.displayScale(),1);
        MachineWindowRenderer.window(g,mc.font,x,y,NetworkConfiguratorLinkWindow.WIDTH,NetworkConfiguratorLinkWindow.HEIGHT,Component.empty());
        title(g,mc,w,x+10,y+10,Component.translatable("gui.moonstation14.devicenet.configurator.link"));
        MachineWindowRenderer.insetPanel(g,x+8,y+34,134,168);MachineWindowRenderer.insetPanel(g,x+158,y+34,134,168);
        text(g,mc,w,x+14,y+40,Component.translatable("gui.moonstation14.devicenet.configurator.sources"),MachineWindowRenderer.TEXT);
        text(g,mc,w,x+164,y+40,Component.translatable("gui.moonstation14.devicenet.configurator.sinks"),MachineWindowRenderer.TEXT);
        int logicalMouseX=(int)((mouseX-w.x())/w.displayScale()),logicalMouseY=(int)((mouseY-w.y())/w.displayScale());
        Component hover=null;
        for(int i=0;i<8&&i+w.portScroll()<s.sources().size();i++){int row=NetworkConfiguratorLinkWindow.portRowTop(i);var p=s.sources().get(i+w.portScroll());port(g,mc,w,p,x+14,y+row,w.selectedSource());if(hit(logicalMouseX,logicalMouseY,x+12,y+row-2,130,17))hover=description(p);}
        for(int i=0;i<8&&i+w.portScroll()<s.sinks().size();i++){int row=NetworkConfiguratorLinkWindow.portRowTop(i);var p=s.sinks().get(i+w.portScroll());port(g,mc,w,p,x+164,y+row,w.selectedSink());if(hit(logicalMouseX,logicalMouseY,x+162,y+row-2,130,17))hover=description(p);}
        for(var link:s.links()){
            int a=index(s.sources(),link.sourcePort()),b=index(s.sinks(),link.sinkPort());
            if(a<0||b<0)continue;var segment=connectionSegment(a,b,w.portScroll());if(segment==null)continue;
            line(g,x+segment.x1(),y+segment.y1(),x+segment.x2(),y+segment.y2(),0xff68c989);
        }
        String src=address(s.sourceAddress()),sink=address(s.sinkAddress());
        var from=Component.translatable("gui.moonstation14.devicenet.configurator.from",src);
        var to=Component.translatable("gui.moonstation14.devicenet.configurator.to",sink);
        float bodyScale=textScale(w);text(g,mc,w,x+9,y+207,fit(mc,from,137,bodyScale),MachineWindowRenderer.MUTED_TEXT);
        text(g,mc,w,x+155,y+207,fit(mc,to,137,bodyScale),MachineWindowRenderer.MUTED_TEXT);
        if(logicalMouseY>=204&&logicalMouseY<220){if(logicalMouseX>=8&&logicalMouseX<149)hover=from;else if(logicalMouseX>=154&&logicalMouseX<292)hover=to;}
        button(g,mc,w,ConfiguratorPaneLayout.LINK_CLEAR,"gui.moonstation14.devicenet.configurator.clear",0xffe36b6b);
        button(g,mc,w,ConfiguratorPaneLayout.LINK_DEFAULTS,"gui.moonstation14.devicenet.configurator.defaults",0xff78a9ed);
        button(g,mc,w,ConfiguratorPaneLayout.LINK_DONE,"gui.moonstation14.devicenet.configurator.done",0xff68c989);
        g.pose().popPose();
        if(ConfiguratorPaneLayout.LINK_DEFAULTS.contains(logicalMouseX,logicalMouseY))hover=Component.translatable("gui.moonstation14.devicenet.configurator.link_defaults.tooltip");
        if(hover!=null)g.renderTooltip(mc.font,hover,mouseX,mouseY);
    }
    record Segment(int x1,int y1,int x2,int y2) { }
    /** Direct visible portion of the endpoint-to-endpoint line, clipped to the port-row viewport. */
    static Segment connectionSegment(int sourceIndex,int sinkIndex,int scroll){
        int sourceRow=sourceIndex-scroll,sinkRow=sinkIndex-scroll;
        boolean sourceVisible=sourceRow>=0&&sourceRow<8,sinkVisible=sinkRow>=0&&sinkRow<8;
        if(!sourceVisible&&!sinkVisible)return null;
        double y1=65+sourceRow*19.0,y2=65+sinkRow*19.0,dy=y2-y1,t0=0,t1=1;
        if(dy==0){if(y1<56||y1>209)return null;}
        else {double a=(56-y1)/dy,b=(209-y1)/dy;t0=Math.max(0,Math.min(a,b));t1=Math.min(1,Math.max(a,b));if(t0>t1)return null;}
        return new Segment((int)Math.round(142+20*t0),(int)Math.round(y1+dy*t0),
                (int)Math.round(142+20*t1),(int)Math.round(y1+dy*t1));
    }
    private static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){int dx=x2-x1,dy=y2-y1,steps=Math.max(Math.abs(dx),Math.abs(dy));for(int i=0;i<=steps;i++){int x=x1+dx*i/steps,y=y1+dy*i/steps;g.fill(x,y,x+1,y+1,color);}}
    private static void port(GuiGraphics g,Minecraft mc,NetworkConfiguratorLinkWindow w,NetworkConfiguratorSnapshot.Port p,int x,int y,String selected){
        boolean active=p.id().equals(selected);g.fill(x-2,y-2,x+128,y+15,active?0xff526d73:0xff293940);
        Component name=Component.translatable(p.name());
        text(g,mc,w,x,y,fit(mc,Component.literal(name.getString()),116,textScale(w)),active?0xffffff88:MachineWindowRenderer.TEXT);
    }
    private static boolean hit(int px,int py,int x,int y,int width,int height){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    private static Component description(NetworkConfiguratorSnapshot.Port p){return p.description().isBlank()?null:Component.translatable(p.description());}
    private static int index(java.util.List<NetworkConfiguratorSnapshot.Port> ports,String id){for(int i=0;i<ports.size();i++)if(ports.get(i).id().equals(id))return i;return -1;}
    private static String address(String value){return value==null||value.isBlank()?"":value;}
    private static void button(GuiGraphics g,Minecraft mc,NetworkConfiguratorLinkWindow w,ConfiguratorPaneLayout.ButtonRect rect,String key,int color){MachineWindowRenderer.insetPanel(g,rect.x(),rect.y(),rect.width(),rect.height());text(g,mc,w,rect.x()+4,rect.y()+5,Component.translatable(key),color);}
    private static void text(GuiGraphics g,Minecraft mc,NetworkConfiguratorLinkWindow w,int x,int y,Component value,int color){drawText(g,mc,x,y,value,color,textScale(w));}
    private static void title(GuiGraphics g,Minecraft mc,NetworkConfiguratorLinkWindow w,int x,int y,Component value){drawText(g,mc,x,y,value,MachineWindowRenderer.TEXT,ConfiguratorPaneLayout.textGlyphScale(w.displayScale(),.65f,2f));}
    private static float textScale(NetworkConfiguratorLinkWindow w){return ConfiguratorPaneLayout.textGlyphScale(w.displayScale(),.5f,1.5f);}
    private static void drawText(GuiGraphics g,Minecraft mc,int x,int y,Component value,int color,float glyphScale){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(glyphScale,glyphScale,1);g.drawString(mc.font,value,0,0,color,false);g.pose().popPose();}
    private static Component fit(Minecraft mc,Component value,int logicalWidth,float glyphScale){return Component.literal(mc.font.plainSubstrByWidth(value.getString(),ConfiguratorPaneLayout.textWidthBudget(logicalWidth,glyphScale)));}
}
