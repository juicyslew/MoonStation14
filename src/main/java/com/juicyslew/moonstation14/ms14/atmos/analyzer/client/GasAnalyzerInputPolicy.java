package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

/** Narrow wheel admission so gameplay input never leaks into hotbar/menu owners. */
public final class GasAnalyzerInputPolicy {
    public enum Route { NONE, WINDOW_GAS_SCROLL, RANGE_DISTANCE }
    public record Cursor(double x,double y) { }
    public record DistanceRequest(int distance,boolean shouldSend) { }
    private GasAnalyzerInputPolicy() { }
    public static DistanceRequest distanceRequest(int current,int maximum,int delta,boolean pinnedSource) {
        if(delta==0)return new DistanceRequest(Math.max(0,Math.min(maximum,current)),false);
        int next=Math.max(0,Math.min(maximum,current+Integer.signum(delta)));
        return new DistanceRequest(next,next!=current||(pinnedSource&&next==current));
    }
    public static Cursor guiCursor(double windowX,double windowY,int guiWidth,int guiHeight,int windowWidth,int windowHeight) {
        return new Cursor(windowX*guiWidth/windowWidth,windowY*guiHeight/windowHeight);
    }
    public static Route wheel(boolean shift,boolean control,boolean alt,boolean screenOwnsInput,
                              boolean pointerLocked,boolean overWindow,boolean overGasList,boolean analyzerMode,
                              boolean exactSourceHeld,boolean overOtherInteractiveWindow) {
        if(shift||control||alt||screenOwnsInput)return Route.NONE;
        if(pointerLocked)return analyzerMode&&exactSourceHeld?Route.RANGE_DISTANCE:Route.NONE;
        if(overGasList&&!overOtherInteractiveWindow&&analyzerMode&&exactSourceHeld)return Route.WINDOW_GAS_SCROLL;
        return !overWindow&&analyzerMode&&exactSourceHeld&&!overOtherInteractiveWindow?Route.RANGE_DISTANCE:Route.NONE;
    }
}
