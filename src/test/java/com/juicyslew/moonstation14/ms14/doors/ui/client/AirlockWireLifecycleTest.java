package com.juicyslew.moonstation14.ms14.doors.ui.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class AirlockWireLifecycleTest {
    @Test void realWireScreenQueryTimeoutReissuesAndLateOldSessionReplyCannotDriveActions(){
        var sent=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest>();
        var session=java.util.UUID.randomUUID();var closed=new java.util.concurrent.atomic.AtomicInteger();
        var screen=new AirlockWireScreen(5,session,sent::add,closed::incrementAndGet,()->{},
                ()->new AirlockWireScreen.MovementKeys(false,false,false,false,false,false,false,false,false),(w,h)->{},()->true);
        screen.startView();screen.workspaceSize(640,360);var first=sent.getFirst();
        for(int i=0;i<51;i++)screen.tick();
        for(int i=51;i<60;i++)screen.tick();
        assertEquals(2,sent.size());assertEquals(1,sent.getLast().requestId());
        screen.handleResponse(snapshot(5,session,first.requestId(),java.util.List.of(new com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot.Row(11,0xffffffff,"A",false))));
        var wire=com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireScreenLayout.wire(215,100,0);
        screen.workspaceMouseClicked(wire.x()+1,wire.y()+1,0);
        assertEquals(2,sent.size()); // timed-out response remains stale while retry is pending
        screen.onClose();assertEquals(1,closed.get());
    }

    @Test void actualWireScreenWorkspaceRouteRefreshDragReleaseMovementAndCloseUseOwnedSessionAndRequestSender(){
        var sent=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest>();
        var closed=new java.util.concurrent.atomic.AtomicInteger();var serverClose=new java.util.concurrent.atomic.AtomicInteger();
        var keys=new AirlockWireScreen.MovementKeys(true,true,true,false,false,true,true,false,true);
        var session=java.util.UUID.randomUUID();
        var lifecycle=new AirlockWireLifecycle();var owner=new java.util.concurrent.atomic.AtomicReference<AirlockWireScreen>();
        var embeddedInit=new java.util.concurrent.atomic.AtomicInteger();
        var screen=new AirlockWireScreen(12,session,sent::add,()->{closed.incrementAndGet();lifecycle.unregister(owner.get());},serverClose::incrementAndGet,()->keys,
                (w,h)->embeddedInit.incrementAndGet(),()->true);owner.set(screen);
        lifecycle.register(screen);
        screen.startView();assertEquals(1,sent.size());assertEquals(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest.Operation.QUERY,sent.getFirst().operation());
        screen.workspaceSize(640,360);assertEquals(1,embeddedInit.get());
        screen.workspaceSize(640,360);assertEquals(1,embeddedInit.get());
        var query=sent.getFirst();
        screen.handleResponse(snapshot(99,session,query.requestId(),java.util.List.of()));
        screen.handleResponse(snapshot(12,java.util.UUID.randomUUID(),query.requestId(),java.util.List.of()));
        assertEquals(1,sent.size()); // wrong container/session cannot settle the live query
        int wireId=com.juicyslew.moonstation14.ms14.doors.AirlockState.Wire.POWER_ONE.ordinal();
        screen.handleResponse(snapshot(12,session,query.requestId(),java.util.List.of(new com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot.Row(wireId,0xff00ff00,"A",false))));
        for(int i=0;i<20;i++)screen.tick();
        assertEquals(2,sent.size());assertEquals(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest.Operation.QUERY,sent.getLast().operation());
        var refresh=sent.getLast();var scheduler=new com.juicyslew.moonstation14.ms14.testsupport.DeterministicPacketScheduler<com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot>();
        scheduler.send(3,snapshot(12,session,refresh.requestId(),java.util.List.of(new com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot.Row(wireId,0xff00ff00,"A",false))));
        scheduler.send(1,snapshot(12,session,query.requestId(),java.util.List.of()));
        scheduler.advance(1,screen::handleResponse);
        var wire=com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireScreenLayout.wire(215,100,0);
        screen.workspaceMouseClicked(wire.x()+2,wire.y()+2,0);assertEquals(2,sent.size()); // an unacknowledged refresh suppresses actions
        scheduler.advance(3,screen::handleResponse);
        assertTrue(screen.workspaceMouseClicked(wire.x()+2,wire.y()+2,0));
        assertEquals(3,sent.size());assertEquals(wireId,sent.getLast().rowId());
        assertEquals(session,sent.getLast().session());assertEquals(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest.Operation.CLICK,sent.getLast().operation());
        var move=new com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State(-1,1,true,false,true);
        var host=new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen(
                new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupWorkspace(320,240),()->move);
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(host,false));
        var captured=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent>();
        var actual=com.juicyslew.moonstation14.ms14.player_body_control.client.GameplayWindowMovementDispatch.dispatch(4,9,
                com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER,host,
                com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State.IDLE,0,0,ignored->true,captured::add);
        assertEquals(java.util.List.of(actual),captured);assertEquals(-707,actual.wishX());assertEquals(707,actual.wishZ());
        var textFocus=new TextFocusWindow();com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.register(textFocus);
        try {
            var blocked=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent>();
            var intent=com.juicyslew.moonstation14.ms14.player_body_control.client.GameplayWindowMovementDispatch.dispatch(4,10,
                    com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER,host,move,0,0,ignored->true,blocked::add);
            assertEquals(0,intent.wishX());assertEquals(0,intent.wishZ());assertEquals(java.util.List.of(intent),blocked);
        } finally {com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.unregister(textFocus);}
        screen.workspaceMouseClicked(220,105,0); // title press
        assertTrue(screen.workspaceMouseDragged(-400,-400,0,-620,-505));
        assertEquals(0,screen.windowX());assertEquals(0,screen.windowY());
        assertTrue(screen.workspaceMouseReleased(-400,-400,0));
        screen.workspaceSize(220,170);assertEquals(2,embeddedInit.get());assertTrue(screen.windowX()+AirlockWireScreenLayout.WIDTH<=220);
        screen.onClose();assertFalse(lifecycle.isActive(screen));
        assertEquals(1,closed.get());assertEquals(1,serverClose.get());
        screen.onClose();assertEquals(1,closed.get());assertEquals(1,serverClose.get());
    }

    private static com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot snapshot(int id,java.util.UUID session,long request,
            java.util.List<com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot.Row> rows){
        var off=com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.OFF;
        return new com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot(id,session,request,true,1,true,off,off,off,off,off,off,off,false,false,rows);
    }
    private static final class TextFocusWindow implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
        public boolean visible(){return true;}public boolean contains(double x,double y){return false;}
        public void renderIntoGui(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p,int w,int h){}
        public boolean mouseClicked(double x,double y,int b){return false;}public boolean mouseDragged(double x,double y){return false;}
        public boolean mouseReleased(int b){return false;}public boolean mouseScrolled(double x,double y,double d){return false;}
        public boolean keyPressed(int k,int s,int m){return false;}public boolean charTyped(char c,int m){return false;}
        public boolean isSearchFocused(){return true;}public void clearTextFocus(){}public void resetOwnedContext(){}
    }
    @Test void explicitRemovalClearsOnceAndOldRemovalCannotClearReplacement() {
        var lifecycle = new AirlockWireLifecycle();
        Object first = new Object(), second = new Object();
        lifecycle.register(first);
        assertTrue(lifecycle.unregister(first)); // explicit X/ESC or foreign screen replacement
        assertFalse(lifecycle.unregister(first)); // removed after onClose is idempotent
        lifecycle.register(second);
        assertFalse(lifecycle.unregister(first)); // stale callback is identity guarded
        assertSame(second, lifecycle.active());
    }

    @Test void serverCloseAndAuthorityLossClearCurrentWindowOnly() {
        var lifecycle = new AirlockWireLifecycle();
        Object first = new Object(), second = new Object();
        lifecycle.register(first);
        assertTrue(lifecycle.unregister(first)); // server-close callback
        assertFalse(lifecycle.unregister(first));
        lifecycle.register(second);
        assertFalse(lifecycle.unregister(first)); // stale server-close for old session
        assertTrue(lifecycle.unregister(second)); // authority loss
        assertNull(lifecycle.active());
    }

    @Test void captureTransitionDoesNotUnregisterRetainedScreen() {
        var lifecycle = new AirlockWireLifecycle();
        Object screen = new Object();
        lifecycle.register(screen);
        // Screen removal during the try/finally capture transition deliberately does not
        // call unregister; restoration uses the same retained active instance.
        assertSame(screen, lifecycle.active());
        assertTrue(lifecycle.isActive(screen));
        assertFalse(lifecycle.unregister(new Object()));
        assertSame(screen, lifecycle.active());
    }

}
