package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for the non-modal ChemMaster Input workspace. All coordinates are GUI pixels. */
public final class ChemMasterLayout {
    public static final int WIDTH = 338, HEIGHT = 272, SCALE = 2, HEADER = 26, MARGIN = 10, ROW_HEIGHT = 13, BUTTON_ROW_HEIGHT = 12;
    public static final int[] AMOUNTS = {1, 5, 10, 15, 20, 25, 30, 50, 100, -1};
    public enum Tab { INPUT, OUTPUT }
    public enum Sort { OLDEST_FIRST, ALPHABETICAL, QUANTITY, RECENT_FIRST;
        public Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px,double py) { return px>=x&&py>=y&&px<x+width&&py<y+height; }
    }
    public record ScreenRect(int left,int top,int right,int bottom) {}
    public static int toLocal(double screenCoordinate,int origin){return (int)Math.floor((screenCoordinate-origin)*SCALE);}
    public static ScreenRect toScreen(Rect local,int originX,int originY){return new ScreenRect(originX+Math.floorDiv(local.x(),SCALE),originY+Math.floorDiv(local.y(),SCALE),
            originX+ceil(local.x()+local.width()),originY+ceil(local.y()+local.height()));}
    /** Conservative pixel clip: never rounds outward across a panel rim. */
    public static ScreenRect toScreenClip(Rect local,int originX,int originY){return new ScreenRect(originX+ceil(local.x()),originY+ceil(local.y()),
            originX+Math.floorDiv(local.x()+local.width(),SCALE),originY+Math.floorDiv(local.y()+local.height(),SCALE));}
    private static int ceil(int value){return Math.floorDiv(value+SCALE-1,SCALE);}
    public record DoseHit(int rowIndex,int amountIndex) { }
    public record Geometry(int width,int height,Rect close,Rect inputTab,Rect outputTab,
                           Rect eject,Rect inputPanel,Rect bufferPanel,Rect inputViewport,Rect bufferViewport,
                           Rect sort,Rect transferMode,Rect discardMode,Rect outputPlaceholder,Rect inputHeading,Rect bufferHeading,
                            Rect inputScrollbar,Rect bufferScrollbar,Rect caption) { }
    private ChemMasterLayout() { }

    public static Geometry geometry(int screenWidth,int screenHeight) {
        return geometry(screenWidth,screenHeight,22,29);
    }
    public static Geometry geometry(int screenWidth,int screenHeight,int inputTextWidth,int outputTextWidth) {
        int w=Math.max(1,Math.min(WIDTH,screenWidth*SCALE));
        int h=Math.max(1,Math.min(HEIGHT,screenHeight*SCALE));
        int header=Math.min(HEADER,h);
        int inputTabWidth=Math.min(w-2*MARGIN,Math.max(1,inputTextWidth+12));
        int y=4;
        int tabHeight=Math.min(22,Math.max(0,h-y));
        int controls=Math.min(12,Math.max(0,h-HEADER));
        int left=HEADER+2;
        int ejectW=Math.min(52,Math.max(0,w-2*MARGIN));
        int inputHeadingY=left+controls;
        int inputTop=inputHeadingY+10;
        int inputViewportH=Math.min(5*ROW_HEIGHT,Math.max(0,h-inputTop-(4+2+14+8*ROW_HEIGHT+4+15)));
        int bufferHeadingY=inputTop+inputViewportH+6;
        int bufferTop=bufferHeadingY+14;
        int bufferViewportH=Math.min(8*ROW_HEIGHT,Math.max(0,h-bufferTop-15));
        int listWidth=Math.max(0,w-2*MARGIN-8);
        int closeWidth=Math.min(12,w);
        boolean actionsFit=w>=2*MARGIN+105+4+108;
        int sortX=MARGIN+38;
        int transferX=w-MARGIN-8-108;
        int sortWidth=actionsFit?Math.min(105,Math.max(0,transferX-4-sortX)):0;
        int outputTabWidth=Math.min(w-2*MARGIN,Math.max(1,outputTextWidth+12));
        int tabsStart=Math.max(MARGIN,w-MARGIN-Math.min(12,w)-inputTabWidth-outputTabWidth);
        Rect inputPanel=rect(w,h,MARGIN,inputTop,listWidth,inputViewportH+4),bufferPanel=rect(w,h,MARGIN,bufferTop,listWidth,bufferViewportH+4);
        Rect inputView=inset(inputPanel,2),bufferView=inset(bufferPanel,2);
        return new Geometry(w,h,rect(w,h,w-closeWidth,0,closeWidth,header),rect(w,h,tabsStart,y,inputTabWidth,tabHeight),
                rect(w,h,tabsStart+inputTabWidth,y,Math.min(w-MARGIN-(tabsStart+inputTabWidth),outputTabWidth),tabHeight),
                rect(w,h,MARGIN,left,ejectW,Math.min(12,controls)),inputPanel,bufferPanel,inputView,bufferView,
                  rect(w,h,sortX,bufferHeadingY,sortWidth,14),
                   rect(w,h,w-MARGIN-8-108,bufferHeadingY,actionsFit?50:0,14),
                   rect(w,h,w-MARGIN-8-54,bufferHeadingY,actionsFit?54:0,14),
                inputView,
                rect(w,h,MARGIN,inputHeadingY,w-2*MARGIN,10),
                rect(w,h,MARGIN,bufferHeadingY,36,10),
                 rect(w,h,MARGIN+listWidth,inputView.y(),8,inputView.height()),
                 rect(w,h,MARGIN+listWidth,bufferView.y(),8,bufferView.height()),
                  rect(w,h,MARGIN+ejectW+8,left,Math.max(0,w-MARGIN-(MARGIN+ejectW+8)),Math.min(12,controls)));
    }

    private static Rect inset(Rect r,int amount){return new Rect(r.x()+Math.min(amount,r.width()),r.y()+Math.min(amount,r.height()),Math.max(0,r.width()-2*amount),Math.max(0,r.height()-2*amount));}

    private static Rect rect(int width,int height,int x,int y,int w,int h) {
        int left=Math.max(0,Math.min(width,x)), top=Math.max(0,Math.min(height,y));
        return new Rect(left,top,Math.max(0,Math.min(w,width-left)),Math.max(0,Math.min(h,height-top)));
    }

    public static List<Rect> amountButtons(Rect row) { return amountButtons(row, 78); }
    public static List<Rect> amountButtons(Rect row,int measuredQuantityWidth) {
        int buttonY=row.y()+Math.max(0,(row.height()-BUTTON_ROW_HEIGHT)/2);
        int buttonLeft=Math.min(row.x()+row.width(),row.x()+96);
        Rect quantity=quantityCell(row,measuredQuantityWidth);
        int buttonRight=Math.max(buttonLeft,quantity.x()-4);
        Rect buttonRow=new Rect(buttonLeft,buttonY,Math.max(0,buttonRight-buttonLeft),Math.min(BUTTON_ROW_HEIGHT,row.height()));
        List<Rect> result=new ArrayList<>(AMOUNTS.length);
        int gap=1, button=Math.max(0,(buttonRow.width()-gap*(AMOUNTS.length-1))/AMOUNTS.length);
        int x=buttonRow.x();
        for(int i=0;i<AMOUNTS.length;i++) {
            x=Math.min(x,buttonRow.x()+buttonRow.width());
            int width=i==AMOUNTS.length-1?Math.max(0,buttonRow.x()+buttonRow.width()-x):Math.min(button,Math.max(0,buttonRow.x()+buttonRow.width()-x));
            result.add(new Rect(x,buttonRow.y(),width,buttonRow.height())); x=Math.min(buttonRow.x()+buttonRow.width(),x+width+gap);
        }
        return List.copyOf(result);
    }

    /** Right-side reagent amount cell, sized for the supported maximum exact quantity. */
    public static Rect quantityCell(Rect row) { return quantityCell(row,78); }
    public static Rect quantityCell(Rect row,int measuredTextWidth) {
        int width=Math.min(Math.max(0,measuredTextWidth),row.width());
        return new Rect(row.x()+row.width()-width-2,row.y(),width,row.height());
    }
    public static int quantityTextX(Rect cell,int measuredTextWidth) {
        return cell.x()+cell.width()-Math.max(0,Math.min(cell.width(),measuredTextWidth));
    }

    public static Rect reagentRow(Rect viewport,int scroll,int index) {
        int y=viewport.y()+index*ROW_HEIGHT-scroll;
        return new Rect(viewport.x(),y,viewport.width(),ROW_HEIGHT);
    }

    public static int maxScroll(int count,int rowHeight,int viewportHeight) {
        if(count<0||rowHeight<=0||viewportHeight<0) throw new IllegalArgumentException("Invalid list dimensions");
        return Math.max(0,Math.max(0,count*rowHeight-viewportHeight));
    }
    public static Rect scrollbarThumb(Rect track,int count,int rowHeight,int scroll) {
        if(count<0||rowHeight<=0)throw new IllegalArgumentException("Invalid scrollbar dimensions");
        int content=count*rowHeight;
        if(track.height()<=0||content<=track.height())return new Rect(track.x(),track.y(),track.width(),0);
        int h=Math.max(2,(int)((long)track.height()*track.height()/content));
        h=Math.min(track.height(),h);
        int max=maxScroll(count,rowHeight,track.height());
        int y=track.y()+(max==0?0:(track.height()-h)*clampScroll(scroll,count,rowHeight,track.height())/max);
        return new Rect(track.x(),y,track.width(),h);
    }
    public static int clampScroll(int scroll,int count,int rowHeight,int viewportHeight) {
        return Math.max(0,Math.min(scroll,maxScroll(count,rowHeight,viewportHeight)));
    }
    public static int visibleRowAt(Rect viewport,int scroll,double x,double y,int count) {
        if(!viewport.contains(x,y)||count<=0) return -1;
        int index=(int)((y-viewport.y()+Math.max(0,scroll))/ROW_HEIGHT);
        return index<count?index:-1;
    }
    public static DoseHit doseHit(Rect viewport,int scroll,int count,double x,double y){
        return doseHit(viewport,scroll,count,x,y,78);
    }
    public static DoseHit doseHit(Rect viewport,int scroll,int count,double x,double y,int measuredQuantityWidth){
        return doseHit(viewport,scroll,count,x,y,index->measuredQuantityWidth);
    }
    public static DoseHit doseHit(Rect viewport,int scroll,int count,double x,double y,java.util.function.IntUnaryOperator measuredQuantityWidth){
        if(!viewport.contains(x,y)||count<=0)return null;
        int first=Math.max(0,(int)Math.floor((y-viewport.y()+Math.max(0,scroll))/ROW_HEIGHT));
        int last=Math.min(count-1,first+1);
        for(int row=first;row<=last;row++) {
            var buttons=amountButtons(reagentRow(viewport,scroll,row),measuredQuantityWidth.applyAsInt(row));
            for(int i=0;i<buttons.size();i++)if(buttons.get(i).contains(x,y))return new DoseHit(row,i);
        }
        return null;
    }
}

