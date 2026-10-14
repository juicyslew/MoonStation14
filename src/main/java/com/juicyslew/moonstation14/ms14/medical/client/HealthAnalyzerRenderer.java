package com.juicyslew.moonstation14.ms14.medical.client;

import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact readonly analyzer presentation drawn over gameplay and the storage pointer layer. */
public final class HealthAnalyzerRenderer {
    private static final float SCALE = .5f;
    private HealthAnalyzerRenderer() { }

    public static void draw(GuiGraphics g, int width, int height) {
        var ws = HealthAnalyzerWorkspace.INSTANCE;
        ws.resize(width, height);
        for (var w : ws.windows().stream().filter(HealthAnalyzerWorkspace.Window::visible)
                .sorted(java.util.Comparator.comparingLong(HealthAnalyzerWorkspace.Window::z)).toList()) drawWindow(g, w);
    }

    public static void drawWindow(GuiGraphics g, HealthAnalyzerWorkspace.Window w) {
        var mc = Minecraft.getInstance();
        var reading = w.state().readings();
        if (reading.isPresent()) {
            var r = reading.get();
            String[] labels = {label("state"),label("temperature"),label("blood"),label("total_damage"),label("species")};
            String[] values = {r.state(),r.temperatureKelvin().map(k -> String.format(java.util.Locale.ROOT,"%.1f°C",k-273.15)).orElse("Unknown"),r.usableBloodFraction().map(v -> String.format(java.util.Locale.ROOT,"%.1f%%",v*100)).orElse("Unknown"),String.format(java.util.Locale.ROOT,"%.1f",r.totalDamage()),r.species()};
            int lw=0,vw=0; for(int i=0;i<labels.length;i++){lw=Math.max(lw,mc.font.width(labels[i]));vw=Math.max(vw,mc.font.width(values[i]));}
            int dl=0,dv=mc.font.width("999.9"); for(var group:r.groups()){dl=Math.max(dl,mc.font.width(label("group."+group.name().toLowerCase(java.util.Locale.ROOT))));for(var e:group.damage().entrySet())dl=Math.max(dl,mc.font.width(damageLabel(e.getKey())));}
            int title=mc.font.width(Component.translatable("gui.moonstation14.medical.analyzer").getString());
            int logical=HealthAnalyzerLayout.measuredWidth(lw,vw,dl,dv,title);
            HealthAnalyzerWorkspace.INSTANCE.contentWidth(w,(logical+1)/2,mc.getWindow().getGuiScaledWidth());
        }
        int footer = HealthAnalyzerLayout.footerHeight(reading.orElse(null));
        int x = w.x(), y = w.y(), width = w.width(), height = w.height();
        int logicalWidth = Math.round(width / SCALE), logicalHeight = Math.round(height / SCALE);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SCALE, SCALE, 1);
        MachineWindowRenderer.window(g, mc.font, 0, 0, logicalWidth, logicalHeight,
                Component.translatable("gui.moonstation14.medical.analyzer"));
        // Reuse the toolkit Button's native title/hover styling and its canonical geometry.
        var close = WindowChromeButtons.close(0, 0, logicalWidth, () -> { });
        close.setX(logicalWidth - WindowChromeButtons.EDGE - WindowChromeButtons.SIZE);
        close.setY(WindowChromeButtons.EDGE);
        int mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        close.render(g, Math.round((mouseX - x) / SCALE), Math.round((mouseY - y) / SCALE), 0);
        var closeBounds = WindowChromeButtons.closeBounds(0, 0, logicalWidth, SCALE);
        var led = HealthAnalyzerLayout.ledBounds(width, height, closeBounds.left());
        int titleBar = HealthAnalyzerLayout.HEADER * 2;
        int bodyTop = titleBar;
        int max = HealthAnalyzerLayout.maxScroll(reading.orElse(null), height);
        HealthAnalyzerWorkspace.INSTANCE.maxScroll(w, max);
        int scroll = w.scroll();
        // GuiGraphics.enableScissor consumes screen-space GUI pixels; its scissor stack ignores the pose matrix.
        var clip = HealthAnalyzerLayout.bodyClipBounds(x, y, width, height, footer);
        g.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        int row = bodyTop + HealthAnalyzerLayout.CONTENT_TOP_PAD * 2;
        if (reading.isEmpty()) {
            texture(g, "moonstation14:textures/gui/medical/unknown.png", 8, row + 4, 28, 28);
            text(g, mc, "gui.moonstation14.medical.inactive", 42, row + 14, 0xffe07870);
            row += HealthAnalyzerLayout.SUMMARY_HEIGHT * 2;
        } else {
            var r = reading.get();
            int labelColumn=8; for(String key:new String[]{"state","temperature","blood","total_damage","species"}) labelColumn=Math.max(labelColumn,8+mc.font.width(label(key)));
            int valueX=labelColumn+6;
            int valueWidth=0; for(String value:new String[]{r.state(),r.temperatureKelvin().map(k -> String.format(java.util.Locale.ROOT,"%.1f°C",k-273.15)).orElse("Unknown"),r.usableBloodFraction().map(v -> String.format(java.util.Locale.ROOT,"%.1f%%",v*100)).orElse("Unknown"),String.format(java.util.Locale.ROOT,"%.1f",r.totalDamage()),r.species()}) valueWidth=Math.max(valueWidth,mc.font.width(value));
            int portraitX = valueX+valueWidth+6;
            int portraitY = bodyTop + 2;
            if (!r.inRange()) texture(g, "moonstation14:textures/gui/medical/out_of_range.png", portraitX, portraitY, HealthAnalyzerLayout.PORTRAIT_WIDTH, HealthAnalyzerLayout.PORTRAIT_WIDTH);
            else if (r.active() && !portrait(g, mc, r.targetEntityId(), r.targetId(), portraitX, portraitY, HealthAnalyzerLayout.PORTRAIT_WIDTH))
                 texture(g, "moonstation14:textures/gui/medical/unknown.png", portraitX, portraitY, HealthAnalyzerLayout.PORTRAIT_WIDTH, HealthAnalyzerLayout.PORTRAIT_WIDTH);
            row = stat(g, mc, 8, valueX, row, "gui.moonstation14.medical.state", r.state());
            row = stat(g, mc, 8, valueX, row, "gui.moonstation14.medical.temperature",
                    r.temperatureKelvin().map(k -> String.format(java.util.Locale.ROOT, "%.1f°C", k - 273.15)).orElse("Unknown"));
            row = stat(g, mc, 8, valueX, row, "gui.moonstation14.medical.blood",
                    r.usableBloodFraction().map(v -> String.format(java.util.Locale.ROOT, "%.1f%%", v * 100)).orElse("Unknown"));
            row = stat(g, mc, 8, valueX, row, "gui.moonstation14.medical.total_damage",
                    String.format(java.util.Locale.ROOT, "%.1f", r.totalDamage()));
            row = stat(g, mc, 8, valueX, row, "gui.moonstation14.medical.species", r.species());
            row += 6;
            g.disableScissor();
            var damageClip=HealthAnalyzerLayout.bodyClipBounds(x,y,width,height,footer);
            g.enableScissor(damageClip.left(),Math.min(damageClip.bottom(),y+HealthAnalyzerLayout.summaryBottom()),damageClip.right(),damageClip.bottom());
            row=HealthAnalyzerLayout.summaryBottom()*2-scroll*2;
            var visibleGroups = r.groups().stream().filter(group -> group.total() > 0 && !group.damage().isEmpty()).toList();
            for (int groupIndex = 0; groupIndex < visibleGroups.size(); groupIndex++) {
                var group = visibleGroups.get(groupIndex);
                String key = group.name().toLowerCase(java.util.Locale.ROOT);
                String icon=switch(key){case "brute"->"brute";case "burn"->"burn";case "airloss"->"airloss";case "toxin"->"toxin";case "genetic"->"genetic";case "metaphysical"->"metaphysical";default->null;};
                if(icon!=null) texture(g,"moonstation14:textures/gui/medical/"+icon+".png",8,row,16,16);
                String label = Component.translatable("gui.moonstation14.medical.group." + key).getString();
                drawScaled(g, mc, label, 30, row, 0xffdce5ea);
                rightText(g, mc, String.format(java.util.Locale.ROOT, "%.1f", group.total()), logicalWidth - 8, row, 0xffdce5ea);
                row += HealthAnalyzerLayout.GROUP_ROW * 2;
                var entries = group.damage().entrySet().stream().sorted(java.util.Comparator.comparingInt(e ->
                        com.juicyslew.moonstation14.ms14.damage.DamageKeys.ORDER.indexOf(e.getKey()))).toList();
                for (var entry : entries) {
                    drawScaled(g, mc, damageLabel(entry.getKey()), 42, row, 0xffaebec8);
                    rightText(g, mc, String.format(java.util.Locale.ROOT, "%.1f", entry.getValue()), logicalWidth - 8, row, 0xffaebec8);
                    row += HealthAnalyzerLayout.DAMAGE_ROW * 2;
                }
                if (groupIndex + 1 < visibleGroups.size()) row += HealthAnalyzerLayout.GROUP_SEPARATOR * 2;
            }
        }
        g.disableScissor();
        int viewport = HealthAnalyzerLayout.damageViewportHeight(height, footer);
        if (max > 0) {
            int track=viewport*2, content=HealthAnalyzerLayout.contentExtent(reading.orElse(null));
            int thumb=HealthAnalyzerLayout.scrollbarThumbHeight(track,viewport,content);
            int thumbY=HealthAnalyzerLayout.scrollbarThumbY(HealthAnalyzerLayout.summaryBottom()*2,track,thumb,w.scroll(),max);
            g.fill(logicalWidth-6,thumbY,logicalWidth-4,thumbY+thumb,MachineWindowRenderer.HIGHLIGHT);
        }
        int alertY = logicalHeight - footer * 2;
        if (reading.isPresent()) {
            var r = reading.get();
            if (r.bleeding()) { text(g, mc, "gui.moonstation14.medical.bleeding", 8, alertY, 0xffff7770); alertY += HealthAnalyzerLayout.ALERT_ROW * 2; }
            if (r.unrevivable()) text(g, mc, "gui.moonstation14.medical.unrevivable", 8, alertY, 0xffff7770);
        }
        boolean active = reading.map(r -> r.active() && r.inRange()).orElse(false);
        g.fill(led.x() * 2, led.y() * 2, (led.x() + led.size()) * 2, (led.y() + led.size()) * 2,
                active ? 0xff52d273 : 0xffd95151);
        boolean showLedTooltip = mouseX >= x + led.x() && mouseX < x + led.x() + led.size()
                && mouseY >= y + led.y() && mouseY < y + led.y() + led.size()
                && HealthAnalyzerWorkspace.INSTANCE.topmost(mouseX, mouseY) == w;
        g.pose().popPose();
        if (showLedTooltip) g.renderTooltip(mc.font, Component.translatable("gui.moonstation14.medical." +
                (active ? "active" : "inactive")), mouseX, mouseY);
    }

    private static int stat(GuiGraphics g, Minecraft mc, int x, int valueX, int y, String key, String value) {
        String label = Component.translatable(key).getString();
        int clippedWidth=Math.max(1,valueX-x-3);
        String clippedLabel=mc.font.plainSubstrByWidth(label,clippedWidth);
        String clippedValue=mc.font.plainSubstrByWidth(value,Math.max(1,mc.font.width(value)));
        drawScaled(g, mc, clippedLabel, x, y, MachineWindowRenderer.MUTED_TEXT);
        drawScaled(g, mc, clippedValue, valueX, y, MachineWindowRenderer.TEXT);
        return y + HealthAnalyzerLayout.FIELD_ROW * 2;
    }

    private static String label(String suffix){return Component.translatable("gui.moonstation14.medical."+suffix).getString();}

    private static void rightText(GuiGraphics g, Minecraft mc, String value, int right, int y, int color) {
        drawScaled(g, mc, value, right - mc.font.width(value), y, color);
    }
    private static void drawScaled(GuiGraphics g, Minecraft mc, String value, int x, int y, int color) {
        g.drawString(mc.font, value, x, y, color, false);
    }
    private static void text(GuiGraphics g, Minecraft mc, String key, int x, int y, int color) {
        drawScaled(g, mc, Component.translatable(key).getString(), x, y, color);
    }
    private static String damageLabel(String key) {
        return Component.translatable("gui.moonstation14.medical.damage." + key).getString();
    }
    private static void texture(GuiGraphics g, String id, int x, int y, int width, int height) {
        g.blit(net.minecraft.resources.ResourceLocation.parse(id), x, y, 0, 0, width, height, width, height);
    }
    private static boolean portrait(GuiGraphics g, Minecraft mc, int id, java.util.UUID target, int x, int y, int size) {
        var entity = mc.level == null ? null : mc.level.getEntity(id);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !entity.getUUID().equals(target)) return false;
        try { return NeutralPortrait.render(g, living, x, y, size); }
        catch (RuntimeException ignored) { return false; }
    }
}

