package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared compact construction HUD; all coordinates are GUI-scaled pixels. */
public final class ConstructionHudRenderer {
    public static final int TOP_OFFSET = 18;
    private static final float TEXT_SCALE=.5f;
    private static final int MAX_LINES = 2;
    public static final int GUIDANCE_MAX_LINES = 4;
    public record Guidance(String goal,String requirement) { }
    public static final float guidanceTextScale() { return TEXT_SCALE; }
    public static int guidanceContentWidth(int titleWidth,int requirementWidth) { return (int)Math.ceil(Math.max(titleWidth,requirementWidth)*TEXT_SCALE)+8; }
    public static int guidanceHeaderGap() { return 1; }
    public static int guidanceTopPadding() { return 2; }
    /** Physical GUI-pixel metrics; drawing converts these coordinates to logical font units once. */
    public record GuidanceMetrics(int titleBaseline,int dividerY,int bodyStart,int rowStride,int panelHeight) { }
    record GuidanceContentLayout(java.util.List<net.minecraft.util.FormattedCharSequence> lines, GuidanceMetrics metrics) { }
    static GuidanceContentLayout guidanceContentLayout(Component text,int width,int lineHeight,int maximum,
            java.util.function.BiFunction<net.minecraft.network.chat.FormattedText,Integer,java.util.List<net.minecraft.util.FormattedCharSequence>> splitter) {
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(var line:splitter.apply(text,Math.max(1,width))) { if(lines.size()>=Math.max(1,maximum))break; lines.add(line); }
        return new GuidanceContentLayout(java.util.List.copyOf(lines),guidanceMetrics(lineHeight,lines.size()));
    }
    public static int guidancePhysicalLineHeight(int unscaledFontHeight) {
        return Math.max(1,(int)Math.ceil(Math.max(1,unscaledFontHeight)*TEXT_SCALE));
    }
    public static GuidanceMetrics guidanceMetrics(int physicalLineHeight,int rows) {
        int glyph=Math.max(1,physicalLineHeight);
        int title=guidanceTopPadding();
        int divider=title+glyph;
        int body=divider+guidanceHeaderGap()+1;
        int stride=glyph+1;
        return new GuidanceMetrics(title,divider,body,stride,body+Math.max(0,rows)*stride+3);
    }
    public static int guidanceBodyTop(int lineHeight) { return guidanceMetrics(lineHeight,0).bodyStart(); }
    public static int guidancePanelHeight(int lineHeight,int rows) { return guidanceMetrics(lineHeight,rows).panelHeight(); }
    public static int paletteHeight(int visibleRows) { return 28+Math.min(5,Math.max(0,visibleRows))*9; }
    public static java.util.List<Component> guidanceRows(Guidance guidance) { return java.util.List.of(Component.literal(guidance.requirement())); }
    private static int lastPlanBottom;
    private ConstructionHudRenderer() { }

    public record Plan(String recipeName, String materialName, int held, int required,
                       String blockedReason, String controls) { }

    public static Plan plan(String recipeName, String materialName, int held, int required,
                            String blockedReason, String controls) {
        return new Plan(recipeName == null ? "" : recipeName, materialName == null ? "" : materialName,
                Math.max(0, held), Math.max(0, required), blockedReason == null ? "" : blockedReason,
                controls == null ? "" : controls);
    }

    public static void drawPlan(GuiGraphics graphics, Minecraft mc, Plan plan) {
        var rows=planRows(plan);
        int sw=mc.getWindow().getGuiScaledWidth();
        int available=Math.max(0,sw-8);
        int width=Math.min(available,Math.max(90,(int)Math.ceil(Math.max(mc.font.width(plan.recipeName()),Math.max(mc.font.width(rows.get(0)),mc.font.width(rows.get(1))))*TEXT_SCALE)+16));
        int textWidth=Math.max(1,(int)((width-12)/TEXT_SCALE));
        Component first=Component.literal(mc.font.plainSubstrByWidth(rows.get(0).getString(),textWidth));
        Component controls=Component.literal(mc.font.plainSubstrByWidth(rows.get(1).getString(),textWidth));
        int[] bounds=drawAt(graphics,mc,Component.literal(mc.font.plainSubstrByWidth(plan.recipeName(),textWidth)),TOP_OFFSET,MAX_LINES,
                new int[]{materialColor(plan.held(),plan.required())},first,controls);
        lastPlanBottom=bounds[3]<=0?0:bounds[1]+bounds[3];
    }
    public static int lastPlanBottom() { return lastPlanBottom; }

    public static int materialColor(int held,int required) {
        if(held<=0) return 0xffff5555;
        if(held<required) return 0xffffcc44;
        return 0xff55dd66;
    }
    public static int panelTextWidth(int panelWidth) { return Math.max(1,(int)((panelWidth-12)/TEXT_SCALE)); }
    public static int centeredTitleX(int panelWidth,int measuredFontWidth) {
        return Math.max(0,(panelTextWidth(panelWidth)-Math.max(0,measuredFontWidth))/2);
    }

    public static java.util.List<Component> planRows(Plan plan) {
        Component material = Component.literal(plan.materialName().isEmpty() || plan.required()<=0 ? "" : plan.materialName()+" "+plan.held()+"/"+plan.required());
        Component status=material.getString().isEmpty()?(plan.blockedReason().isEmpty()?Component.literal(""):Component.translatable(plan.blockedReason())):material;
        if(!plan.blockedReason().isEmpty()&&!material.getString().isEmpty()) status=material.copy().append(" · ").append(Component.translatable(plan.blockedReason()));
        return java.util.List.of(status,Component.literal(plan.controls()));
    }

    public static void drawPalette(GuiGraphics graphics, Minecraft mc, Component title,
                                   java.util.List<Component> labels, int selected, Component controls) {
        int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();
        int width=Math.min(300,Math.max(0,sw-8)),rows=Math.min(5,labels.size());
        int height=paletteHeight(rows); int[] b=bounds(sw,sh,width,height); int x=b[0],y=b[1],w=b[2];
        MachineWindowRenderer.window(graphics,mc.font,x,y,w,height,title);
        graphics.pose().pushPose();graphics.pose().translate(x,y+28,0);graphics.pose().scale(.75f,.75f,1f);
        int first=Math.max(0,Math.min(labels.size()-rows,selected-rows/2));
        for(int i=0;i<rows;i++){int index=first+i;String label=mc.font.plainSubstrByWidth(labels.get(index).getString(),Math.max(1,(int)((w-18)/.75f)));graphics.drawString(mc.font,Component.literal(label),10,i*12,index==selected?0xffffff55:MachineWindowRenderer.TEXT,true);}
        graphics.pose().popPose();
    }

    public static int[] bounds(int screenWidth, int screenHeight, int width, int height) {
        int w=Math.max(0,Math.min(width,screenWidth-8));
        int h=Math.max(0,Math.min(height,screenHeight));
        return new int[]{(screenWidth-w)/2,Math.min(TOP_OFFSET,Math.max(0,screenHeight-h)),w,h};
    }

    /** Resolves a compact banner around the project's published chat rectangle; {x,y,w,h}, or zero-size when impossible. */
    public static int[] layoutBounds(int sw,int sh,int desiredWidth,int height,int topOffset,
            com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.Rect chat) {
        int h=Math.min(Math.max(0,height),Math.max(0,sh));
        if(sw<=8||h<=0)return new int[]{0,0,0,0};
        int width=Math.min(Math.min(220,sw-8),Math.max(1,desiredWidth));
        int y=Math.min(Math.max(0,topOffset),Math.max(0,sh-h));
        int x=(sw-width)/2;
        if(!overlaps(x,y,width,h,chat))return new int[]{x,y,width,h};
        int minimumWidth=Math.min(width,76);
        int leftWidth=Math.min(width,chat.left()-8);
        if(leftWidth>=minimumWidth)return new int[]{4,y,leftWidth,h};
        int rightX=chat.right()+4,rightWidth=Math.min(width,sw-4-rightX);
        if(rightWidth>=minimumWidth)return new int[]{rightX,y,rightWidth,h};
        int below=chat.bottom()+4;
        if(below+h<=sh)return new int[]{(sw-width)/2,below,width,h};
        return new int[]{0,0,0,0};
    }
    private static boolean overlaps(int x,int y,int w,int h,com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.Rect r) {
        return x<r.right()&&x+w>r.left()&&y<r.bottom()&&y+h>r.top();
    }

    public static void draw(GuiGraphics graphics, Minecraft mc, Component title, Component... rows) {
        drawAt(graphics,mc,title,TOP_OFFSET,rows);
    }

    public static void drawAt(GuiGraphics graphics, Minecraft mc, Component title, int topOffset, Component... rows) {
        drawAt(graphics,mc,title,topOffset,MAX_LINES,new int[0],rows);
    }
    /** Guidance alone can use extra compact rows so the immediate material/tool instruction survives wrapping. */
    public static void drawGuidanceAt(GuiGraphics graphics,Minecraft mc,Component title,int topOffset,Component text) {
        int lineHeight=guidancePhysicalLineHeight(mc.font.lineHeight);
        int desired=guidanceContentWidth(mc.font.width(title),mc.font.width(text));
        int maxWidth=Math.min(mc.getWindow().getGuiScaledWidth()-8,Math.min(220,desired));
        int budget=guidanceLineBudget(mc.getWindow().getGuiScaledHeight(),topOffset,lineHeight);
        drawCompactGuidance(graphics,mc,title,topOffset,maxWidth,lineHeight,budget,text);
    }
    private static void drawCompactGuidance(GuiGraphics graphics,Minecraft mc,Component title,int topOffset,int maxWidth,int lineHeight,int budget,Component text) {
        int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();var font=mc.font;
        var chat=com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.chat(sw,sh);
        int lines=Math.max(1,Math.min(budget,Math.max(1,(sh-topOffset-5)/Math.max(1,lineHeight))));
        var b=layoutBounds(sw,sh,maxWidth,guidancePanelHeight(lineHeight,lines),topOffset,chat);
        if(b[2]<=0||b[3]<=0)return;
        var content=guidanceContentLayout(text,Math.max(1,(int)((b[2]-8)/TEXT_SCALE)),lineHeight,lines,font::split);
        var wrapped=content.lines();
        int height=content.metrics().panelHeight();
        b=layoutBounds(sw,sh,b[2],height,topOffset,chat);if(b[2]<=0||b[3]<=0)return;
        int x=b[0],y=b[1],w=b[2];
        MachineWindowRenderer.insetPanel(graphics,x,y,w,height);
        var metrics=content.metrics();
        MachineWindowRenderer.divider(graphics,x+3,y+metrics.dividerY(),w-6);
        graphics.enableScissor(x+2,y+1,x+w-2,y+height-1);
        graphics.pose().pushPose();graphics.pose().translate(x+4,y,0);graphics.pose().scale(TEXT_SCALE,TEXT_SCALE,1f);
        int titleWidth=Math.max(1,(int)((w-8)/TEXT_SCALE));
        var fitted=Component.literal(font.plainSubstrByWidth(title.getString(),titleWidth));
        graphics.drawString(font,fitted,Math.max(0,(titleWidth-font.width(fitted))/2),(int)(metrics.titleBaseline()/TEXT_SCALE),MachineWindowRenderer.TEXT,true);
        for(int i=0;i<wrapped.size();i++)graphics.drawString(font,wrapped.get(i),0,(int)((metrics.bodyStart()+i*metrics.rowStride())/TEXT_SCALE),MachineWindowRenderer.TEXT,true);
        graphics.pose().popPose();graphics.disableScissor();
    }
    public static int guidanceLineBudget(int screenHeight,int topOffset,int lineHeight) {
        int available=Math.max(1,(screenHeight-Math.max(0,topOffset)-14)/(Math.max(1,lineHeight)+1));
        return Math.max(1,Math.min(GUIDANCE_MAX_LINES,available));
    }
    private static int[] drawAt(GuiGraphics graphics, Minecraft mc, Component title, int topOffset,int maxLines,int[] rowColors, Component... rows) {
        int sw=mc.getWindow().getGuiScaledWidth(), sh=mc.getWindow().getGuiScaledHeight();
        var font=mc.font;
        int desired=0;
        for(Component row:rows) if(row!=null) desired=Math.max(desired,font.width(row));
        desired=Math.max(desired,font.width(title));
        int maxWidth=Math.max(0,Math.min(sw-8,Math.min(220,Math.max(90,desired*3/4+16))));
        int lineHeight=Math.max(1,(int)Math.ceil(font.lineHeight*TEXT_SCALE));
        var chat=com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.chat(sw,sh);
        int budget=Math.max(1,Math.min(maxLines,Math.max(1,(sh-Math.max(0,topOffset)-14)/(lineHeight+1))));
        int[] b={0,0,0,0};
        for(;budget>=1;budget--) {
            int candidateHeight=Math.min(sh,8+lineHeight+2+budget*(lineHeight+1)+4);
            b=layoutBounds(sw,sh,maxWidth,candidateHeight,topOffset,chat);
            if(b[2]>0&&b[3]>0)break;
        }
        if(b[2]<=0||b[3]<=0)return b;
        int x=b[0],y=b[1],w=b[2];
        int finalLineWidth=Math.max(1,(int)((w-12)/TEXT_SCALE));
        var wrapped=wrap(font,rows,finalLineWidth,budget);
        int height=Math.min(sh,8+lineHeight+2+wrapped.size()*(lineHeight+1)+4);
        b=layoutBounds(sw,sh,w,height,topOffset,chat);
        if(b[2]<=0||b[3]<=0)return b;
        x=b[0];y=b[1];w=b[2];
        // Reflow once at the final placement width (which may differ when a shorter panel fits below chat).
        wrapped=wrap(font,rows,Math.max(1,(int)((w-12)/TEXT_SCALE)),budget);
        height=Math.min(sh,8+lineHeight+2+wrapped.size()*(lineHeight+1)+4);
        b=layoutBounds(sw,sh,w,height,topOffset,chat);
        if(b[2]<=0||b[3]<=0)return b;
        x=b[0];y=b[1];w=b[2];
        MachineWindowRenderer.insetPanel(graphics,x,y,w,height);
        MachineWindowRenderer.divider(graphics,x+3,y+8+lineHeight,w-6);
        graphics.enableScissor(x+4,y+2,x+w-4,y+height-2);
        graphics.pose().pushPose(); graphics.pose().translate(x+6,y+3,0); graphics.pose().scale(TEXT_SCALE,TEXT_SCALE,1f);
        int finalTextWidth=panelTextWidth(w);
        Component fittedTitle=Component.literal(font.plainSubstrByWidth(title.getString(),finalTextWidth));
        graphics.drawString(font,fittedTitle,centeredTitleX(w,font.width(fittedTitle)),0,MachineWindowRenderer.TEXT,true);
        for(int i=0;i<wrapped.size();i++) graphics.drawString(font,wrapped.get(i),0,(int)((lineHeight+8+i*(lineHeight+1))/TEXT_SCALE),i<rowColors.length?rowColors[i]:MachineWindowRenderer.TEXT,true);
        graphics.pose().popPose();
        graphics.disableScissor();
        return b;
    }

    static java.util.ArrayList<net.minecraft.util.FormattedCharSequence> wrap(net.minecraft.client.gui.Font font,
            Component[] rows,int width,int maximum) {
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(Component row:rows) {
            if(row==null)continue;
            for(var line:font.split(row,width)) { if(lines.size()>=maximum)return lines; lines.add(line); }
        }
        return lines;
    }

    public static int planHeight(net.minecraft.client.gui.Font font) {
        return 8+(int)Math.ceil(font.lineHeight*TEXT_SCALE)+2+2*((int)Math.ceil(font.lineHeight*TEXT_SCALE)+1)+4;
    }
}
