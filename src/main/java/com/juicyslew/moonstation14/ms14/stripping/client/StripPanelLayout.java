package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout;
import com.juicyslew.moonstation14.ms14.hands.client.HandBarLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry for the compact strip panel; origin is the clothing baseline. */
public final class StripPanelLayout {
    public enum Kind { EQUIPMENT, HAND }
    public record SlotRect(Kind kind, String id, HandBarLayout.Rect rect) { }
    public record Layout(List<SlotRect> slots, int cellSize, HandBarLayout.Rect bounds) {
        public Layout { slots = List.copyOf(slots); }
    }
    public record WindowSize(int width,int height,boolean gridFits) { }
    private StripPanelLayout() { }

    /** Compact chrome sizing derived from this exact painted/hit-tested content plan. */
    public static WindowSize windowSize(Layout content,int viewportWidth,int viewportHeight) {
        int vw=Math.max(1,viewportWidth),vh=Math.max(1,viewportHeight);
        int wantedWidth=Math.max(40,content.bounds().width()+8);
        int wantedHeight=Math.max(48,content.bounds().height()+28);
        return new WindowSize(Math.min(vw,wantedWidth),Math.min(vh,wantedHeight),wantedWidth<=vw&&wantedHeight<=vh);
    }

    public static Layout calculate(int originX, int clothingBaselineY, int viewportWidth, int viewportHeight,
                                   List<InventoryTemplateData.Slot> template, List<String> actualTargetHandIds) {
        int cell = EquipmentPanelLayout.cellSizeForViewport(viewportWidth, viewportHeight);
        var result = new ArrayList<SlotRect>();
        int maxClothingRow = 0;
        for (var slot : template) if (slot.group().equals("Default")) {
            maxClothingRow = Math.max(maxClothingRow, slot.y());
            result.add(new SlotRect(Kind.EQUIPMENT, slot.id(), EquipmentPanelLayout.clothingRect(originX, clothingBaselineY, slot.x(), slot.y(), cell)));
        }

        // Derive declared equipment order within each source group from the existing HUD.
        var canonical = HandBarLayout.calculate(Math.max(90, viewportWidth), Math.max(120, viewportHeight));
        Comparator<InventoryTemplateData.Slot> equipmentOrder = Comparator.comparingInt(s -> {
            var r = canonical == null ? null : switch (s.id()) {
                case "back" -> canonical.back(); case "belt" -> canonical.belt(); default -> canonical.equipmentSlot(s.id());
            };
            return r == null ? Integer.MAX_VALUE : r.x();
        });
        var topEquipment = template.stream().filter(s -> s.group().equals("SecondHotbar")).sorted(equipmentOrder).toList();
        var lowerEquipment = template.stream().filter(s -> s.group().equals("MainHotbar")).sorted(equipmentOrder).toList();
        var handIds = List.copyOf(actualTargetHandIds);
        int topRows = (topEquipment.size() + 2) / 3;
        int handRows = (handIds.size() + 2) / 3;
        int lowerRows = (lowerEquipment.size() + 2) / 3;
        int aboveRows = topRows + handRows + lowerRows;
        int clothingTopY = clothingBaselineY - maxClothingRow * (cell + EquipmentPanelLayout.GRID_GAP);
        int rowY = clothingTopY - aboveRows * (cell + EquipmentPanelLayout.GRID_GAP);

        int row = 0;
        row = appendEquipmentRow(result, topEquipment, originX, rowY, row, cell);
        rowY += topRows * (cell + EquipmentPanelLayout.GRID_GAP);
        row = appendHandRows(result, handIds, originX, rowY, row, cell);
        rowY += handRows * (cell + EquipmentPanelLayout.GRID_GAP);
        appendEquipmentRow(result, lowerEquipment, originX, rowY, row, cell);

        int minX = originX, minY = Integer.MAX_VALUE, maxX = originX, maxY = Integer.MIN_VALUE;
        for (var item : result) { var r = item.rect(); minX = Math.min(minX,r.x()); minY = Math.min(minY,r.y()); maxX=Math.max(maxX,r.x()+r.width()); maxY=Math.max(maxY,r.y()+r.height()); }
        if (result.isEmpty()) minY = maxY = clothingBaselineY;
        return new Layout(result, cell, new HandBarLayout.Rect(minX,minY,maxX-minX,maxY-minY));
    }

    private static int appendEquipmentRow(List<SlotRect> result, List<InventoryTemplateData.Slot> slots,
                                         int originX, int rowY, int row, int cell) {
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            int r = row + i / 3, col = i % 3;
            result.add(new SlotRect(Kind.EQUIPMENT, slot.id(), new HandBarLayout.Rect(
                    originX + col * (cell + EquipmentPanelLayout.GRID_GAP), rowY + (i / 3) * (cell + EquipmentPanelLayout.GRID_GAP), cell, cell)));
        }
        return row + (slots.size() + 2) / 3;
    }

    private static int appendHandRows(List<SlotRect> result, List<String> handIds, int originX, int rowY, int row, int cell) {
        for (int i = 0; i < handIds.size(); i++) {
            result.add(new SlotRect(Kind.HAND, handIds.get(i), new HandBarLayout.Rect(
                    originX + (i % 3) * (cell + EquipmentPanelLayout.GRID_GAP),
                    rowY + (i / 3) * (cell + EquipmentPanelLayout.GRID_GAP), cell, cell)));
        }
        return row + (handIds.size() + 2) / 3;
    }
}

