package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import java.util.ArrayList;
import java.util.List;

/** Output-pane geometry in the caller's current GUI-pixel coordinate space. */
public final class ChemMasterOutputLayout {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Geometry(Rect label, Rect sourceCaption, Rect eject, Rect sourceSelection,
                           Rect outputCaption, Rect pillPalette, List<Rect> pills,
                           Rect pillCountLabel, Rect pillCount, Rect pillCountMinus, Rect pillCountPlus,
                           Rect pillDoseLabel, Rect pillDose, Rect pillDoseMinus, Rect pillDosePlus, Rect createPills,
                           Rect bottleDoseLabel, Rect bottleDose, Rect bottleDoseMinus, Rect bottleDosePlus,
                           Rect fillBottle, Rect outputInfo) { }
    private ChemMasterOutputLayout() { }

    public static Geometry geometry(Rect pane, int fontHeight) {
        int x = pane.x() + 8, w = Math.max(0, pane.width() - 16), y = pane.y() + 6;
        int row = Math.max(10, fontHeight + 3), gap = 3;
        Rect label = new Rect(x, y, w, row + 2); y += row + 5;
        int half = Math.max(0, (w - gap) / 2);
        Rect caption = new Rect(x, y, half, row), eject = new Rect(x + half + gap, y, w - half - gap, row); y += row + 2;
        int sourceButtonHeight=Math.max(row,14);
        int selectionWidth=Math.min(w,88);
        Rect source = new Rect(x+w-selectionWidth, y, selectionWidth, sourceButtonHeight); y += sourceButtonHeight + 2;
        Rect output = new Rect(x, y, w, row); y += row + 2;
        int paletteRowHeight=Math.max(1,fontHeight+7);
        int paletteHeight = Math.min(paletteRowHeight * 2, Math.max(0, pane.y() + pane.height() - y - (row * 4 + 8)));
        Rect palette = new Rect(x, y, w, paletteHeight);
        List<Rect> pills = new ArrayList<>(20);
        int cellW=w/10,cellH=paletteHeight/2;
        int gridX=x+(w-cellW*10)/2;
        for(int i=0;i<20;i++)pills.add(new Rect(gridX+(i%10)*cellW,y+(i/10)*cellH,
                cellW,i/10==1?paletteHeight-cellH:cellH));
        y += paletteHeight + 2;
        Rect countLabel=new Rect(x,y,42,row),count = new Rect(x+42, y, Math.max(0,w / 2-42), row), countMinus = new Rect(x + w / 2, y, row, row),
                countPlus = new Rect(x + w / 2 + row, y, row, row);
        y += row + 2;
        Rect doseLabel=new Rect(x,y,42,row),dose = new Rect(x+42, y, Math.max(0,w / 2-42), row), doseMinus = new Rect(x + w / 2, y, row, row),
                dosePlus = new Rect(x + w / 2 + row, y, row, row);
        Rect create = new Rect(x + w / 2 + row * 2 + gap, y, Math.max(0, w / 2 - row * 2 - gap), row); y += row + 2;
        Rect bottleLabel=new Rect(x,y,42,row),bottle = new Rect(x+42, y, Math.max(0,w / 2-42), row), bottleMinus = new Rect(x + w / 2, y, row, row),
                bottlePlus = new Rect(x + w / 2 + row, y, row, row);
        Rect fill = new Rect(x + w / 2 + row * 2 + gap, y, Math.max(0, w / 2 - row * 2 - gap), row); y += row + 2;
        Rect info = new Rect(x, y, w, Math.max(0, pane.y() + pane.height() - y));
        return new Geometry(label, caption, eject, source, output, palette, List.copyOf(pills),countLabel,count,
                countMinus,countPlus,doseLabel,dose,doseMinus,dosePlus,create,bottleLabel,bottle,bottleMinus,bottlePlus,fill,info);
    }
}
