package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HealthAnalyzerWorkspaceTest {
    private final UUID scanner=UUID.randomUUID(), actor=UUID.randomUUID();
    private HealthAnalyzerWindowState state(long frame, boolean open, Optional<HealthAnalyzerSnapshot> readings) {
        return state(1,frame,open,readings);
    }
    private HealthAnalyzerWindowState state(long epoch,long frame,boolean open,Optional<HealthAnalyzerSnapshot> readings) {
        return new HealthAnalyzerWindowState(scanner,epoch,actor,frame,open,readings);
    }
    @Test void updatesRetainWindowAndRejectOldFramesAndServerCloseRemovesIt() {
        var ws=new HealthAnalyzerWorkspace(); ws.accept(state(2,true,Optional.empty()),800,600);
        var w=ws.get(scanner); assertNotNull(w); ws.click(w.x()+40,w.y()+10,0,800,600); ws.drag(w.x()+90,w.y()+60,800,600);
        int movedX=w.x(); ws.accept(state(1,true,Optional.empty()),800,600); assertEquals(movedX,w.x());
        ws.accept(state(3,true,Optional.empty()),800,600); assertEquals(movedX,w.x());
        ws.accept(state(4,false,Optional.empty()),800,600); assertNull(ws.get(scanner));
    }
    @Test void captureStateDoesNotControlWindowVisibilityAndWindowsClamp() {
        var ws=new HealthAnalyzerWorkspace(); ws.accept(state(1,true,Optional.empty()),100,100);
        var w=ws.get(scanner); assertNotNull(w); assertTrue(w.visible());
        ws.click(w.x()+10,w.y()+10,0,100,100); ws.drag(999,999,100,100);
        assertEquals(0,w.x()); assertEquals(48,w.y()); assertTrue(w.visible());
    }
    @Test void explicitCloseAndCloseAcknowledgementDismissEveryLaterOpenInThatEpoch() {
        var ws=new HealthAnalyzerWorkspace(); var sent=new java.util.ArrayList<HealthAnalyzerCloseRequest>(); ws.closeSender(sent::add);
        ws.accept(state(7,true,Optional.empty()),800,600); var w=ws.get(scanner);
        clickClose(ws,w,800,600);
        assertEquals(List.of(new HealthAnalyzerCloseRequest(scanner,1,actor)),sent);
        ws.accept(state(8,false,Optional.empty()),800,600); // close acknowledgement
        ws.accept(state(9,true,Optional.empty()),800,600);
        assertNull(ws.get(scanner));
        ws.accept(state(2,1,true,Optional.empty()),800,600); // scanner reopened in a new epoch
        assertNotNull(ws.get(scanner));
    }
    @Test void explicitCloseWinsAgainstLateStreamOpenAndCloseAckUntilFreshEpochIntent() {
        var ws=new HealthAnalyzerWorkspace();
        ws.accept(state(20,true,Optional.empty()),800,600);
        var window=ws.get(scanner);
        clickClose(ws,window,800,600);
        assertNull(ws.get(scanner));
        // In-flight open/readings and the close acknowledgement share the dismissed epoch.
        ws.accept(state(21,true,Optional.empty()),800,600);
        ws.accept(state(22,false,Optional.empty()),800,600);
        ws.accept(state(23,true,Optional.empty()),800,600);
        assertNull(ws.get(scanner));
        // A later explicit server activation has a fresh epoch and may reopen normally.
        ws.accept(state(2,1,true,Optional.empty()),800,600);
        assertNotNull(ws.get(scanner));
    }
    @Test void serverCloseDismissesLaterOpenFramesInSameEpoch() {
        var ws=new HealthAnalyzerWorkspace();
        ws.accept(state(1,true,Optional.empty()),800,600);
        ws.accept(state(2,false,Optional.empty()),800,600);
        ws.accept(state(3,true,Optional.empty()),800,600);
        assertNull(ws.get(scanner));
        ws.accept(state(2,1,true,Optional.empty()),800,600);
        assertNotNull(ws.get(scanner));
    }
    @Test void scrollBelongsToWindowAndIsClampedToContentBounds() {
        var ws=new HealthAnalyzerWorkspace(); ws.accept(state(1,true,Optional.empty()),800,600);
        var w=ws.get(scanner); ws.scroll(w.x()+20,w.y()+35,-1); assertEquals(24,w.scroll());
        ws.accept(state(2,true,Optional.empty()),800,600); assertEquals(24,w.scroll());
        ws.maxScroll(w,12); assertEquals(12,w.scroll()); ws.maxScroll(w,0); assertEquals(0,w.scroll());
    }
    @Test void measuredWidthsSurviveClampResizeAndStateUpdates() {
        var ws=new HealthAnalyzerWorkspace(); ws.accept(state(1,true,Optional.empty()),480,360);
        var w=ws.get(scanner);
        ws.contentWidth(w,102,480); assertEquals(102,w.width());
        ws.resize(480,360); assertEquals(102,w.width());
        ws.accept(state(2,true,Optional.empty()),480,360); assertEquals(102,w.width());
        ws.contentWidth(w,180,480); assertEquals(180,w.width());
        ws.resize(480,360); assertEquals(180,w.width());
    }
    @Test void differentActorAndOlderFramesCannotReplaceCurrentWindow() {
        var ws=new HealthAnalyzerWorkspace();ws.accept(state(10,true,Optional.empty()),800,600);
        var original=ws.get(scanner);
        ws.accept(new HealthAnalyzerWindowState(scanner,1,UUID.randomUUID(),11,true,Optional.empty()),800,600);
        ws.accept(state(9,true,Optional.empty()),800,600);
        assertSame(original,ws.get(scanner)); assertEquals(10,ws.get(scanner).state().frame());
    }
    private static void clickClose(HealthAnalyzerWorkspace ws, HealthAnalyzerWorkspace.Window window, int width, int height) {
        var b=com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(
                window.x(),window.y(),window.width()*2,.5f);
        ws.click(b.left()+1,b.top()+1,0,width,height);
    }
}
