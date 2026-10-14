package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.hands.client.HandBarLayout;
import java.util.ArrayList;
import java.util.List;

/** SS14-style split HUD: clothing at bottom left, other equipment on the hotbar. */
public final class EquipmentPanelLayout {
    public static final int GRID_GAP = 1;
    private EquipmentPanelLayout() { }

    public record SlotRect(String slotId, HandBarLayout.Rect rect) { }
    /** Shared wearable tile size used by the HUD and compact strip panel. */
    public static int cellSizeForViewport(int screenWidth, int screenHeight) {
        int width = Math.max(1, screenWidth), height = Math.max(1, screenHeight);
        var bar = HandBarLayout.calculate(width, height);
        return bar != null ? bar.back().width() : Math.max(1, Math.min(15, (width - 17) / 6));
    }

    /** Template coordinates are bottom-up from the shoes/toggle baseline. */
    public static HandBarLayout.Rect clothingRect(int originX, int baselineY, int col, int row, int cell) {
        return new HandBarLayout.Rect(originX + col * (cell + GRID_GAP), baselineY - row * (cell + GRID_GAP), cell, cell);
    }
    public record Layout(HandBarLayout.Rect toggleRect, HandBarLayout.Rect expandedBounds,
                         List<SlotRect> slots, int cellSize, boolean expanded) {
        public Layout { slots = List.copyOf(slots); }
    }
    /** Front-layer window hit always wins over this HUD surface. */
    public static boolean receivesInput(boolean panelHit, boolean managedWindowHit) {
        return panelHit && !managedWindowHit;
    }

    public static Layout calculate(int screenWidth, int screenHeight, List<InventoryTemplateData.Slot> template,
                                   boolean expanded) {
        int width = Math.max(1, screenWidth), height = Math.max(1, screenHeight);
        var bar = HandBarLayout.calculate(width, height);
        int cell = cellSizeForViewport(width, height);
        int x0 = Math.max(0, Math.min(4, width - cell));
        int toggleY = Math.max(0, height - cell - 2);
        HandBarLayout.Rect toggle = new HandBarLayout.Rect(x0, toggleY, cell, cell);
        List<SlotRect> slots = new ArrayList<>();
        for (InventoryTemplateData.Slot slot : template) {
            HandBarLayout.Rect target = null;
            if (slot.group().equals("Default")) {
                int col = slot.x(), row = slot.y();
                // Shoes share the toggle baseline; the remaining clothing follows the
                // resolved template's bottom-up coordinates, without a spacer row.
                var rect = clothingRect(x0, toggleY, col, row, cell);
                int sx = Math.max(0, Math.min(width-cell, rect.x()));
                int sy = Math.max(0, Math.min(height-cell, rect.y()));
                target = new HandBarLayout.Rect(sx, sy, cell, cell);
            } else if (bar != null) {
                target = switch (slot.id()) {
                    case "back" -> bar.back(); case "belt" -> bar.belt();
                    default -> bar.equipmentSlot(slot.id());
                };
            }
            if (target == null) continue;
            int sx=target.x(), sy=target.y();
            slots.add(new SlotRect(slot.id(), target));
        }
        int minX = toggle.x(), minY = toggle.y(), maxX = toggle.x()+toggle.width(), maxY = toggle.y()+toggle.height();
        if (expanded) for (SlotRect s : slots) {
            var r=s.rect(); minX=Math.min(minX,r.x()); minY=Math.min(minY,r.y());
            maxX=Math.max(maxX,r.x()+r.width()); maxY=Math.max(maxY,r.y()+r.height());
        }
        return new Layout(toggle, new HandBarLayout.Rect(minX,minY,maxX-minX,maxY-minY), slots, cell, expanded);
    }
}

