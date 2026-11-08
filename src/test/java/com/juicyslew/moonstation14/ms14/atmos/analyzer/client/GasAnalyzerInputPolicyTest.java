package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasAnalyzerInputPolicyTest {
    @Test void wheelRoutingPreservesExamineScreenAndOtherWindowPriorities() {
        assertEquals(GasAnalyzerInputPolicy.Route.RANGE_DISTANCE,route(false,false,false,false,false,false,false,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.WINDOW_GAS_SCROLL,route(false,false,false,false,false,true,true,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(true,false,false,false,false,false,false,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,true,false,false,false,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,false,false,true,false,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,false,false,true,true,true));
    }
    @Test void lockedPointerUsesRangeRatherThanStaleWindowCoordinates() {
        for(boolean overWindow:new boolean[]{true,false})
            assertEquals(GasAnalyzerInputPolicy.Route.RANGE_DISTANCE,
                    route(false,false,false,false,true,overWindow,overWindow,true,true,true));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,true,true,true,true,false,false));
    }
    @Test void unlockedListConsumesOnlyForActiveExactAnalyzerSource() {
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,true,false,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,true,false,true,true,true));
        assertEquals(GasAnalyzerInputPolicy.Route.WINDOW_GAS_SCROLL,route(false,false,false,false,false,true,true,true,true,false));
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,true,true,true,false,false),"offhand/inactive token cannot scroll analyzer list");
        assertEquals(GasAnalyzerInputPolicy.Route.NONE,route(false,false,false,false,false,true,true,false,true,false),"inactive analyzer mode cannot scroll list");
        assertEquals(GasAnalyzerInputPolicy.Route.RANGE_DISTANCE,route(false,false,false,false,true,true,true,true,true,false),"active exact source still controls locked range");
    }
    @Test void physicalCursorAdapterProducesGuiScaledCoordinates() {
        assertEquals(new GasAnalyzerInputPolicy.Cursor(160,120),
                GasAnalyzerInputPolicy.guiCursor(640,480,320,240,1280,960));
    }
    @Test void distanceRequestDecisionClampsAndReissuesOnlyPinnedBoundaryRequests() {
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(0,true),GasAnalyzerInputPolicy.distanceRequest(0,6,-1,true));
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(0,false),GasAnalyzerInputPolicy.distanceRequest(0,6,-1,false));
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(3,false),GasAnalyzerInputPolicy.distanceRequest(3,6,0,true));
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(6,true),GasAnalyzerInputPolicy.distanceRequest(6,6,1,true));
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(4,true),GasAnalyzerInputPolicy.distanceRequest(3,6,1,false));
        assertEquals(new GasAnalyzerInputPolicy.DistanceRequest(2,true),GasAnalyzerInputPolicy.distanceRequest(3,6,-1,false));
    }
    private static GasAnalyzerInputPolicy.Route route(boolean shift,boolean control,boolean alt,boolean screen,
            boolean locked,boolean window,boolean list,boolean active,boolean held,boolean other) {
        return GasAnalyzerInputPolicy.wheel(shift,control,alt,screen,locked,window,list,active,held,other);
    }
}
