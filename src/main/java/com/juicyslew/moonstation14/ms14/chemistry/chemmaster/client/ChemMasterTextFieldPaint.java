package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;

/** Shared production/offline painter for native EditBox display state. */
public final class ChemMasterTextFieldPaint {
    public record Viewport(int x,int y,int width,int height) { }
    public record Snapshot(String value, int cursor, int anchor, int displayStart, boolean focused,
                           boolean editable, boolean visible, int caretTicks) {
        public Snapshot { value = value == null ? "" : value; }
    }
    private ChemMasterTextFieldPaint() { }

    public static Viewport viewport(ChemMasterOutputLayout.Rect r,int lineHeight) {
        var v=MachineTextFieldPaint.viewport(new MachineTextFieldPaint.Rect(r.x(),r.y(),r.width(),r.height()),lineHeight);
        return new Viewport(v.x(),v.y(),v.width(),v.height());
    }

    public static void paint(OrderedPaint p, ChemMasterOutputLayout.Rect r, Snapshot s) {
        paint(p,r,s,0,0);
    }
    public static void paint(OrderedPaint p, ChemMasterOutputLayout.Rect r, Snapshot s,int originX,int originY) {
        MachineTextFieldPaint.paint(p,new MachineTextFieldPaint.Rect(r.x(),r.y(),r.width(),r.height()),
                new MachineTextFieldPaint.Snapshot(s.value(),s.cursor(),s.anchor(),s.displayStart(),s.focused(),s.editable(),s.visible(),s.caretTicks()),originX,originY);
    }
}
