package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Gameplay renderer delegates exclusively to the same production OrderedPaint painter used by previews. */
public final class GasAnalyzerRenderer {
    private GasAnalyzerRenderer() { }
    public static void draw(GuiGraphics graphics,int width,int height,int mouseX,int mouseY) {
        var workspace=GasAnalyzerWorkspace.INSTANCE;
        if(!workspace.visible())return;
        workspace.resize(width,height);
        drawWindow(graphics,width,height,mouseX,mouseY);
    }
    public static void drawWindow(GuiGraphics graphics,int width,int height,int mouseX,int mouseY) {
        var workspace=GasAnalyzerWorkspace.INSTANCE;
        if(!workspace.visible())return;
        var mc=Minecraft.getInstance();
        var body=com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.currentGasAnalyzerBody();
        var paint=new GuiGraphicsPaint(graphics,mc.font);
        GasAnalyzerPaint.paint(paint,workspace.view(width,height,mouseX,mouseY,body));
    }
}
