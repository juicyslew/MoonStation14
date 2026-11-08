package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import java.util.List;

/** Immutable geometry-to-entry projection pinned to a painted HUD frame. */
final class BodyActionHudProjection {
    record Cell(BodyActionLayout.BarRect rect, BodyActionSnapshot.Entry entry) { }
    private final List<Cell> cells;
    private BodyActionHudProjection(List<Cell> cells) { this.cells = List.copyOf(cells); }
    static BodyActionHudProjection build(List<BodyActionSlots.Slot> slots, int width, int height) {
        var cells = new java.util.ArrayList<Cell>();
        for (var rect : BodyActionLayout.barRects(slots, width, height, 0, 0)) {
            for (var slot : slots) if (slot.number() == rect.slotNumber()) {
                cells.add(new Cell(rect, slot.entry())); break;
            }
        }
        return new BodyActionHudProjection(cells);
    }
    List<Cell> cells() { return cells; }
    Cell hit(double x, double y) { return cells.stream().filter(c -> c.rect().contains(x, y)).findFirst().orElse(null); }
}
