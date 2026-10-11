package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterPaintPendingTest {
    @Test void pendingRequestDoesNotPaintStatusTextAndStillPaintsControls() {
        var state = new ChemMasterWindowState(1, 1, 0, -1, Optional.empty(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, ResourceLocation.withDefaultNamespace("overworld"), 0, 64, 0, true,
                Optional.empty(), Optional.empty(), 0, 0, List.of(), List.of(),
                ChemMasterControlRequest.Mode.TRANSFER, ChemMasterControlRequest.Sort.OLDEST_FIRST);
        var geometry = ChemMasterLayout.geometry(320, 240);
        var view = new ChemMasterPaint.View(state, "No container", List.of(), List.of(), geometry, 0, 0, 320, 240,
                Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, true, false, Component.literal("Container"), null);
        var paint = new RecordingPaint();

        ChemMasterPaint.paint(paint, view);

        assertFalse(paint.text.stream().anyMatch(s -> s.toLowerCase().contains("pending")), paint.text.toString());
        assertTrue(paint.text.contains("Eject"));
        assertTrue(paint.text.contains("Transfer"));
        assertTrue(paint.text.contains("Discard"));
        assertTrue(paint.fills > 0, "normal frame and controls remain painted");
    }

    private static final class RecordingPaint implements OrderedPaint {
        final List<String> text = new ArrayList<>();
        int fills;
        public void fill(int l,int t,int r,int b,int color) { fills++; }
        public void text(Component c,int x,int y,int color,boolean shadow) { text.add(c.getString()); }
        public int width(Component c) { return width(c.getString()); }
        public int width(String s) { return s.length()*6; }
        public int lineHeight() { return 9; }
        public String plainSubstrByWidth(String s,int width) { return s.substring(0,Math.min(s.length(),Math.max(0,width/6))); }
        public void push() { }
        public void pop() { }
        public void translate(float x,float y,float z) { }
        public void scale(float x,float y,float z) { }
        public void clip(int l,int t,int r,int b) { }
        public void unclip() { }
        public void sprite(ResourceLocation texture,int x,int y,int width,int height,int u,int v,int sw,int sh,int tw,int th,int tint) { }
    }
}
