package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

/** Compact, scale-independent gameplay window layout in GUI pixels. */
public final class GasAnalyzerLayout {
    public static final int HEADER = 14, ROW = 10;
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public record Layout(int width,int height,Rect close,Rect status,Rect metrics,Rect gasList) { }
    private GasAnalyzerLayout() { }
    public static Layout calculate(int viewportWidth,int viewportHeight) {
        return calculate(viewportWidth,viewportHeight,false);
    }
    public static Layout calculate(int viewportWidth,int viewportHeight,boolean namedNodes) {
        int w=Math.max(1,Math.min(152,viewportWidth-8));
        int h=Math.max(1,Math.min(108,viewportHeight-8));
        int listTop=Math.min(namedNodes?20:40,Math.max(0,h- GasAnalyzerLayout.ROW-2)), bottom=h-4;
        return new Layout(w,h,new Rect(Math.max(0,w-14),1,Math.min(12,w),Math.max(0,Math.min(11,h-1))),
                new Rect(6,20,Math.max(0,w-12),20),new Rect(6,20,Math.max(0,w-12),20),
                new Rect(Math.min(6,w),listTop,Math.max(0,w-12),Math.max(0,bottom-listTop)));
    }
    public static int visibleGasRows(Layout layout) { return Math.max(layout.gasList().height()>=ROW?1:0,layout.gasList().height()/ROW); }
    public static int maxScroll(Layout layout,int gasCount) { return Math.max(0,gasCount-visibleGasRows(layout)); }
    public static int clampScroll(Layout layout,int gasCount,int scroll) { return Math.max(0,Math.min(scroll,maxScroll(layout,gasCount))); }
}
