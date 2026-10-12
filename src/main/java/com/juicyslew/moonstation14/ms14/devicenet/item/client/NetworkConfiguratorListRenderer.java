package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draws authoritative staged-device and target-membership snapshots in retained workspace. */
public final class NetworkConfiguratorListRenderer {
    private NetworkConfiguratorListRenderer() { }
    public static void draw(GuiGraphics g,NetworkConfiguratorListWindow w,int sw,int sh){
        if(!w.visible()||w.snapshot()==null)return;w.resize(sw,sh);var mc=Minecraft.getInstance();var s=w.snapshot();int x=0,y=0;
        boolean configuring=!s.targetAddress().isEmpty();
        g.pose().pushPose();g.pose().translate(w.x(),w.y(),0);g.pose().scale((float)w.displayScale(),(float)w.displayScale(),1);
        MachineWindowRenderer.window(g,mc.font,x,y,NetworkConfiguratorListWindow.WIDTH,NetworkConfiguratorListWindow.HEIGHT,Component.empty());
        title(g,mc,w,x+10,y+10,Component.translatable(configuring?"gui.moonstation14.devicenet.configurator.configure":"gui.moonstation14.devicenet.configurator.list"));
        if(w.undersized()){g.drawCenteredString(mc.font,"Enlarge viewport to use this window",sw/2,y+NetworkConfiguratorListWindow.HEIGHT/2,MachineWindowRenderer.HIGHLIGHT);g.pose().popPose();return;}
        MachineWindowRenderer.insetPanel(g,x+8,y+34,324,configuring?190:218);
        text(g,mc,w,x+14,y+39,fit(mc,configuring?Component.literal("Target: "+s.targetAddress()):Component.translatable("gui.moonstation14.devicenet.configurator.staged"),306,textScale(w)),MachineWindowRenderer.TEXT);
        var rows=configuring?s.memberDisplayRows():s.staged();
        int max=ConfiguratorListLayout.MAX_ROWS,offset=w.scroll();
        for(int i=0;i<max&&i+offset<rows.size();i++){
            int ry=y+ConfiguratorListLayout.ROW_TOP+i*ConfiguratorListLayout.ROW_HEIGHT;g.fill(x+12,ry-2,x+328,ry+15,i+offset==w.selected()?0xff526d73:0xff293940);
            var row=rows.get(i+offset);text(g,mc,w,x+17,ry,fit(mc,Component.literal(row.address()),148,textScale(w)),MachineWindowRenderer.TEXT);
            text(g,mc,w,x+170,ry,fit(mc,Component.translatable(row.name()),132,textScale(w)),MachineWindowRenderer.TEXT);
            text(g,mc,w,x+310,ry,Component.literal("×"),MachineWindowRenderer.HIGHLIGHT);
        }
        if(configuring){button(g,mc,w,x+ConfiguratorListLayout.SET.x(),y+ConfiguratorListLayout.SET.y(),ConfiguratorListLayout.SET.width(),"Set");button(g,mc,w,x+ConfiguratorListLayout.ADD.x(),y+ConfiguratorListLayout.ADD.y(),ConfiguratorListLayout.ADD.width(),"Add");button(g,mc,w,x+ConfiguratorListLayout.CONFIG_CLEAR.x(),y+ConfiguratorListLayout.CONFIG_CLEAR.y(),ConfiguratorListLayout.CONFIG_CLEAR.width(),"Clear");}
        else {button(g,mc,w,x+ConfiguratorListLayout.REMOVE.x(),y+ConfiguratorListLayout.REMOVE.y(),ConfiguratorListLayout.REMOVE.width(),"Remove");button(g,mc,w,x+ConfiguratorListLayout.CLEAR.x(),y+ConfiguratorListLayout.CLEAR.y(),ConfiguratorListLayout.CLEAR.width(),"Clear");}
        button(g,mc,w,x+ConfiguratorListLayout.COPY.x(),y+ConfiguratorListLayout.COPY.y(),ConfiguratorListLayout.COPY.width(),"Copy");button(g,mc,w,x+ConfiguratorListLayout.SHOW.x(),y+ConfiguratorListLayout.SHOW.y(),ConfiguratorListLayout.SHOW.width(),"Show");button(g,mc,w,x+ConfiguratorListLayout.DONE.x(),y+ConfiguratorListLayout.DONE.y(),ConfiguratorListLayout.DONE.width(),"Done");
        g.pose().popPose();
    }
    private static void button(GuiGraphics g,Minecraft mc,NetworkConfiguratorListWindow w,int x,int y,int width,String label){MachineWindowRenderer.insetPanel(g,x,y,width,18);text(g,mc,w,x+4,y+5,fit(mc,Component.literal(label),width-8,textScale(w)),MachineWindowRenderer.TEXT);}
    private static void text(GuiGraphics g,Minecraft mc,NetworkConfiguratorListWindow w,int x,int y,Component value,int color){drawText(g,mc,x,y,value,color,textScale(w));}
    private static void title(GuiGraphics g,Minecraft mc,NetworkConfiguratorListWindow w,int x,int y,Component value){drawText(g,mc,x,y,value,MachineWindowRenderer.TEXT,ConfiguratorPaneLayout.textGlyphScale(w.displayScale(),.65f,2f));}
    private static float textScale(NetworkConfiguratorListWindow w){return ConfiguratorPaneLayout.textGlyphScale(w.displayScale(),.5f,1.5f);}
    private static void drawText(GuiGraphics g,Minecraft mc,int x,int y,Component value,int color,float glyphScale){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(glyphScale,glyphScale,1);g.drawString(mc.font,value,0,0,color,false);g.pose().popPose();}
    private static Component fit(Minecraft mc,Component value,int logicalWidth,float glyphScale){return Component.literal(mc.font.plainSubstrByWidth(value.getString(),ConfiguratorPaneLayout.textWidthBudget(logicalWidth,glyphScale)));}
}
