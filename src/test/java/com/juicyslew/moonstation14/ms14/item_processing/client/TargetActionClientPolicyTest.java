package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TargetActionClientPolicyTest {
    @Test void replyMustMatchLiveRequestAndContextAndRemainUnexpired(){
        var target=UUID.randomUUID();var dim=ResourceLocation.withDefaultNamespace("overworld");
        var request=new TargetActionClientPolicy.Request(4,9,12,"right","token",target,dim,100);
        var good=new BodyTargetActionSnapshot(4,9,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason"));
        assertTrue(TargetActionClientPolicy.accepts(request,good,100,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,101,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,false,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,true,ResourceLocation.withDefaultNamespace("nether"),target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,true,dim,UUID.randomUUID()));
        assertFalse(TargetActionClientPolicy.accepts(null,good,10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,8,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(3,9,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,11,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"left","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"right","different",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"right","token",UUID.randomUUID(),Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
    }

    @Test void sharedLayoutUsesHalfOpenBoundsForWindowAndAction(){
        var layout=new TargetActionClientPolicy.Layout(10,20);
        assertTrue(layout.window().contains(10,20));assertFalse(layout.window().contains(158,20));
        assertTrue(layout.action().contains(layout.action().x(),layout.action().y()));assertFalse(layout.action().contains(layout.x()+layout.width(),layout.action().y()));
        assertTrue(layout.window().contains(layout.action().x(),layout.action().y()));
        assertTrue(layout.window().contains(layout.close().x(),layout.close().y()));
        assertTrue(TargetActionClientPolicy.dispatchesButcher(true,true,layout.action().contains(layout.action().x(),layout.action().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(true,false,layout.action().contains(layout.action().x(),layout.action().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(true,true,layout.action().contains(layout.close().x(),layout.close().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(false,true,true));
    }
    @Test void selectedEnabledGunRowsDispatchIndependentOfLegacyButcherView(){
        var target=UUID.randomUUID();
        var spin=new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER,Component.literal("spin"),true,null);
        var disabled=new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER,Component.literal("empty"),false,Component.literal("no"));
        var gunOnly=new BodyTargetActionSnapshot(1,2,3,"right",null,target,Component.literal("gun"),java.util.List.of(spin,disabled));
        assertFalse(gunOnly.enabled());
        var layout=new TargetActionClientPolicy.Layout(20,20,400,300,gunOnly.actions().size());
        boolean hitSpin=layout.action(0).contains(layout.action(0).x(),layout.action(0).y());
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,
                TargetActionClientPolicy.dispatchAction(gunOnly,spin.id(),hitSpin).orElseThrow());
        assertTrue(TargetActionClientPolicy.dispatchAction(gunOnly,disabled.id(),true).isEmpty());
        assertTrue(TargetActionClientPolicy.dispatchAction(gunOnly,spin.id(),false).isEmpty());
        var mixed=new BodyTargetActionSnapshot(1,2,3,"right",null,target,Component.literal("mixed"),java.util.List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.BUTCHER,Component.literal("butcher"),false,Component.literal("no")),spin));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,
                TargetActionClientPolicy.dispatchAction(mixed,spin.id(),true).orElseThrow());
    }
    @Test void acceptedWorldMutationCanRefreshSameUuidWithNewHandProofAndRejectsStaleReply(){
        var target=UUID.randomUUID();var dim=ResourceLocation.withDefaultNamespace("overworld");
        var refreshed=new TargetActionClientPolicy.Request(7,12,19,"left","new-hand-token",target,dim,200);
        var rows=java.util.List.of(new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.GUN_EJECT_CELL,
                Component.literal("eject cell"),true,null));
        var response=new BodyTargetActionSnapshot(7,12,19,"left","new-hand-token",target,Component.literal("ground gun"),rows);
        assertTrue(TargetActionClientPolicy.accepts(refreshed,response,150,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(refreshed,response,150,false,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(refreshed,response,150,true,dim,UUID.randomUUID()));
        var oldQuery=new TargetActionClientPolicy.Request(7,11,18,"right","old-token",target,dim,200);
        assertFalse(TargetActionClientPolicy.accepts(oldQuery,response,150,true,dim,target));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EJECT_CELL,
                TargetActionClientPolicy.dispatchAction(response,BodyTargetActionSnapshot.ActionId.GUN_EJECT_CELL,true).orElseThrow());
        var status=new BodyTargetActionSnapshot(7,12,19,"left","new-hand-token",target,Component.literal("ground gun"),
                java.util.List.of(new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.GUN_STATUS,Component.literal("read only"),false,Component.literal("read only"))));
        assertTrue(TargetActionClientPolicy.dispatchAction(status,BodyTargetActionSnapshot.ActionId.GUN_STATUS,true).isEmpty());
    }
    @Test void worldRefreshBarrierAcceptsUnchangedProofButWaitsForAuthoritativeRevision(){
        assertTrue(TargetActionClientPolicy.revisionReady(9,9)); // successful world-only action
        assertTrue(TargetActionClientPolicy.revisionReady(9,10));
        assertFalse(TargetActionClientPolicy.revisionReady(10,9)); // reordered/old hand snapshot
        assertFalse(TargetActionClientPolicy.revisionReady(-1,Long.MAX_VALUE));
    }
    @Test void retainedWorldSourceContextRequiresAdmissionPresenceAndSharedPhysicalReachNotAim(){
        var eye=new net.minecraft.world.phys.Vec3(0,0,0);
        var near=new net.minecraft.world.phys.AABB(1,0,0,1.25,0.25,0.25);
        var far=new net.minecraft.world.phys.AABB(100,0,0,100.25,0.25,0.25);
        boolean nearReach=com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinPhysicalReach(
                eye,near,com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
        boolean farReach=com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinPhysicalReach(
                eye,far,com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
        assertTrue(nearReach);assertFalse(farReach);
        // Look direction is intentionally not part of retained-source admission after the window opens.
        assertTrue(TargetActionClientPolicy.retainedSourceContextMatches(true,true,nearReach));
        assertFalse(TargetActionClientPolicy.retainedSourceContextMatches(true,true,farReach));
        assertFalse(TargetActionClientPolicy.retainedSourceContextMatches(false,true,nearReach));
        assertFalse(TargetActionClientPolicy.retainedSourceContextMatches(true,false,nearReach));
    }
    @Test void ordinaryGunActionRowsResolveToDistinctRequestKinds(){
        var target=UUID.randomUUID();
        var ballistic=new BodyTargetActionSnapshot(1,1,1,"right",null,target,Component.literal("Mosin"),java.util.List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.GUN_CYCLE_ACTION,Component.literal("Cycle action"),true,null)));
        var battery=new BodyTargetActionSnapshot(1,2,1,"right",null,target,Component.literal("Energy Shotgun"),java.util.List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.GUN_CYCLE_BATTERY_MODE,Component.literal("Energy mode"),true,null)));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_ACTION,
                TargetActionClientPolicy.dispatchAction(ballistic,BodyTargetActionSnapshot.ActionId.GUN_CYCLE_ACTION,true).orElseThrow());
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE,
                TargetActionClientPolicy.dispatchAction(battery,BodyTargetActionSnapshot.ActionId.GUN_CYCLE_BATTERY_MODE,true).orElseThrow());
    }
    @Test void viewportFittingClampsAnchorsAndResizesEverySemanticRectangle(){
        var standard=new TargetActionClientPolicy.Layout(310,170,320,180);
        assertTrue(standard.usable());assertEquals(TargetActionClientPolicy.Layout.WIDTH,standard.width());assertEquals(47,standard.height());
        var narrow=new TargetActionClientPolicy.Layout(310,170,160,90);
        assertTrue(narrow.usable());assertEquals(148,narrow.width());assertEquals(47,narrow.height());assertEquals(12,narrow.x());assertEquals(43,narrow.y());
        var rects=java.util.List.of(narrow.window(),narrow.action(),narrow.reason(),narrow.close());
        for(var rect:rects)assertTrue(narrow.rectWithinViewport(rect),"semantic rectangle must fit viewport: "+rect);
        assertTrue(narrow.action().within(narrow.window()));assertTrue(narrow.reason().within(narrow.window()));assertTrue(narrow.close().within(narrow.window()));
        assertEquals(142,narrow.action().width());assertEquals(0,narrow.reason().height());
        var tiny=new TargetActionClientPolicy.Layout(400,-3,100,50);assertFalse(tiny.usable());
        for(var rect:java.util.List.of(tiny.window(),tiny.action(),tiny.reason(),tiny.close()))assertTrue(tiny.rectWithinViewport(rect));
        var resized=new TargetActionClientPolicy.Layout(310,170,640,360);
        assertEquals(TargetActionClientPolicy.Layout.WIDTH,resized.width());assertEquals(47,resized.height());assertEquals(310,resized.x());assertEquals(170,resized.y());
    }
    @Test void compactLayoutsForOneTwoAndFourRowsFitAndSeparateAtStandardViewports(){
        for(int[] viewport:new int[][]{{320,240},{640,360}})for(int rows:new int[]{1,2,4}){
            var l=new TargetActionClientPolicy.Layout(160,120,viewport[0],viewport[1],rows,106,false);
            assertTrue(l.usable());assertTrue(l.width()<=148);assertTrue(l.height()<=127);
            assertTrue(l.window().within(new TargetActionClientPolicy.Rect(0,0,viewport[0],viewport[1])));
            assertTrue(l.close().within(l.window()));
            for(int i=0;i<rows;i++){var r=l.action(i);assertTrue(r.within(l.window()));assertTrue(r.y()>=l.y()+27);if(i>0)assertTrue(r.y()>=l.action(i-1).y()+l.action(i-1).height());}
            assertTrue(l.reason().within(l.window()));
        }
    }
    @Test void productionPressDecisionRoutesCloseTitleActionsAndConsumesInsideBody(){
        for(int rows:new int[]{1,2,4}){
            var l=new TargetActionClientPolicy.Layout(40,30,320,240,rows,128,false);
            assertEquals(TargetActionClientPolicy.PressKind.CLOSE,TargetActionClientPolicy.press(l,l.close().x(),l.close().y()).kind());
            assertEquals(TargetActionClientPolicy.PressKind.TITLE,TargetActionClientPolicy.press(l,l.x()+4,l.y()+4).kind());
            for(int i=0;i<rows;i++)assertEquals(new TargetActionClientPolicy.Press(TargetActionClientPolicy.PressKind.ACTION,i),TargetActionClientPolicy.press(l,l.action(i).x()+1,l.action(i).y()+1));
            assertEquals(TargetActionClientPolicy.PressKind.OUTSIDE,TargetActionClientPolicy.press(l,l.x()-1,l.y()).kind());
            var geometry=new com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController(320,240,l.x(),l.y(),l.width(),l.height(),1,1,false);
            var title=TargetActionClientPolicy.press(l,l.x()+4,l.y()+4);
            assertEquals(TargetActionClientPolicy.PressKind.TITLE,title.kind());
            assertTrue(geometry.beginPointer(l.x()+4,l.y()+4,0,TargetActionClientPolicy.Layout.TITLE_HEIGHT,18));
            assertTrue(geometry.dragPointer(l.x()+34,l.y()+24));assertTrue(geometry.releasePointer(0));
            assertEquals(l.x()+30,geometry.x());assertEquals(l.y()+20,geometry.y());
        }
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hostPointerAvailable(false));
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hostPointerAvailable(true));
    }
}
