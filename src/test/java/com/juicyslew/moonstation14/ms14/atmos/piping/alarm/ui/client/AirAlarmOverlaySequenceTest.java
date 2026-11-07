package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import net.minecraft.client.gui.Font;

class AirAlarmOverlaySequenceTest {
    @Test void filterTabNeverExposesEnabledHitEvenWhenOptimisticStateExists(){
        var snap=new AirAlarmControlSnapshot(0,SESSION,0,true,true,List.of(new AirAlarmControlSnapshot.Device(DEVICE,VentilationDeviceConfig.Kind.SCRUBBER,new net.minecraft.core.BlockPos(0,0,0),true,true,false,0,0,1)));
        var l=AirAlarmPaintLayout.forViewport(304,224);
        var view=new AirAlarmPaint.View(snap,0,0,304,224,0,0,0,0,2,"",null,"",false,false);
        assertTrue(AirAlarmPaint.hitRegions(l,view).stream().noneMatch(h->h.id().equals("enabled")));
        assertTrue(AirAlarmPaint.hitRegions(l,view).stream().anyMatch(h->h.id().startsWith("gas:")));
    }
    private static final UUID SESSION=UUID.randomUUID(), DEVICE=UUID.randomUUID();
    private static AirAlarmControlSnapshot snapshot(UUID session,long request,boolean accepted,long revision,boolean enabled){
        return new AirAlarmControlSnapshot(0,session,request,accepted,true,List.of(new AirAlarmControlSnapshot.Device(DEVICE,
                VentilationDeviceConfig.Kind.VENT,new net.minecraft.core.BlockPos(0,0,0),true,true,enabled,0,0,revision)));
    }
    private static double x(int logical){return 80+logical;}
    private static double y(int logical){return 75+logical;}
    private static AirAlarmOverlay create(List<AirAlarmControlRequest> sent,AtomicLong clock){return new AirAlarmOverlay(null,SESSION,400,300,()->true,sent::add,id->{},clock::get,mock(Font.class));}
    private static AirAlarmOverlay enabledOverlay(AtomicLong clock,List<AirAlarmControlRequest> sent){var overlay=create(sent,clock);overlay.snapshot(snapshot(SESSION,0,true,1,false));var tab=AirAlarmPaintLayout.forViewport(304,224).tab(1);overlay.mouseClicked(x(tab.x()+1),y(tab.y()+1),0);var b=AirAlarmPaintLayout.forViewport(304,224).enabled();overlay.mouseClicked(x(b.x()+1),y(b.y()+1),0);return overlay;}

    @Test void enabledPresentationIsImmediateAndSettlesOnlyOnMatchingAuthority(){
        AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();var overlay=enabledOverlay(clock,sent);
        assertEquals(Boolean.TRUE,overlay.testIntendedEnabled());assertFalse(overlay.testView().snapshot().devices().get(0).enabled());
        var command=sent.get(0);assertEquals(1,command.expectedRevision());assertEquals(DEVICE,command.deviceId());
        overlay.snapshot(snapshot(SESSION,command.requestId(),true,1,false));assertEquals(Boolean.TRUE,overlay.testIntendedEnabled());
        overlay.snapshot(snapshot(SESSION,command.requestId()+1,true,2,true));assertNull(overlay.testIntendedEnabled());assertTrue(overlay.testView().snapshot().devices().get(0).enabled());
    }

    @Test void enabledDenialTimeoutAndStaleSnapshotsRollbackToLatestAuthority(){
        AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();var denied=enabledOverlay(clock,sent);var request=sent.get(0);
        denied.snapshot(snapshot(SESSION,request.requestId(),false,1,false));assertNull(denied.testIntendedEnabled());assertFalse(denied.testView().snapshot().devices().get(0).enabled());
         sent.clear();var timed=enabledOverlay(clock,sent);timed.snapshot(snapshot(SESSION,1,true,2,false));assertEquals(Boolean.TRUE,timed.testIntendedEnabled());
         timed.snapshot(snapshot(SESSION,1,true,1,true));assertEquals(Boolean.TRUE,timed.testIntendedEnabled()); // older authority cannot replace newer device revision
         clock.set(101);timed.tick();assertNull(timed.testIntendedEnabled());assertFalse(timed.testView().snapshot().devices().get(0).enabled());
    }
    private static AirAlarmControlSnapshot many(UUID second,long request,boolean accepted,long firstRevision,double firstValue,long secondRevision,double secondValue){return new AirAlarmControlSnapshot(0,SESSION,request,accepted,true,List.of(
            new AirAlarmControlSnapshot.Device(DEVICE,VentilationDeviceConfig.Kind.VENT,new net.minecraft.core.BlockPos(0,0,0),true,true,false,0,0,firstRevision,firstValue,0),
            new AirAlarmControlSnapshot.Device(second,VentilationDeviceConfig.Kind.VENT,new net.minecraft.core.BlockPos(20,0,0),true,true,false,0,0,secondRevision,secondValue,0)));}

    @Test void intendedSettingSurvivesStaleAckAndQueriesUntilAuthoritativeRevision(){
        AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();
        var overlay=create(sent,clock);
        var layout=AirAlarmPaintLayout.forContent(304,224,0,0);var refresh=layout.refresh();
        assertTrue(overlay.contains(x(refresh.x()+1),y(refresh.y()+1)));
        assertTrue(refresh.contains((int)(x(refresh.x()+1)-80),(int)(y(refresh.y()+1)-75)));
        assertTrue(overlay.mouseClicked(x(refresh.x()+1),y(refresh.y()+1),0));
        assertEquals(AirAlarmControlRequest.Operation.QUERY,sent.get(0).operation());
        overlay.snapshot(snapshot(SESSION,sent.get(0).requestId(),true,1,false)); // matching QUERY clears pending
        assertTrue(overlay.mouseClicked(x(AirAlarmPaintLayout.forContent(304,224,0,0).tab(1).x()+1),y(29),0));
        var enabled=AirAlarmPaint.hitRegions(layout,new AirAlarmPaint.View(snapshot(SESSION,0,true,1,false),0,0,152,112,0,0,0,0,1,"",null,"",false,false)).stream().filter(h->h.id().equals("enabled")).findFirst().orElseThrow();
        assertTrue(overlay.mouseClicked(x(enabled.bounds().x()+1),y(enabled.bounds().y()+1),0));
        assertEquals(2,sent.size());assertEquals(AirAlarmControlRequest.Operation.SET_ENABLED,sent.get(1).operation());
        long commandId=sent.get(1).requestId();
        overlay.snapshot(snapshot(SESSION,commandId,true,1,false)); // ack accepted, state is still stale
        assertTrue(overlay.mouseClicked(x(enabled.bounds().x()+1),y(enabled.bounds().y()+1),0));assertEquals(2,sent.size());
        overlay.snapshot(snapshot(SESSION,0,true,1,false)); // delayed opening-query response must not clear intent
        assertEquals(2,sent.size());
        for(int tick=2;tick<=100;tick++){
            clock.set(tick);overlay.tick();int count=sent.size();overlay.tick();assertEquals(count,sent.size());
            if(tick<100&&tick%20==0){assertEquals(AirAlarmControlRequest.Operation.QUERY,sent.get(sent.size()-1).operation());overlay.snapshot(snapshot(SESSION,sent.get(sent.size()-1).requestId(),true,1,false));}
        }
        assertEquals(6,sent.size()); // initial query, setting and polls at 20, 40, 60, 80
        overlay.snapshot(snapshot(SESSION,5,true,2,true));
        assertTrue(overlay.mouseClicked(x(enabled.bounds().x()+1),y(enabled.bounds().y()+1),0));
        assertEquals(7,sent.size());assertEquals(AirAlarmControlRequest.Operation.SET_ENABLED,sent.get(6).operation());
    }

    @Test void matchingAdvancedConflictingAckClearsOptimisticEnabledState() {
        AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();
        var overlay=enabledOverlay(clock,sent);var command=sent.get(0);
        overlay.snapshot(snapshot(SESSION,command.requestId()+1,true,2,false));
        assertEquals(Boolean.TRUE,overlay.testIntendedEnabled());
        overlay.snapshot(snapshot(SESSION,command.requestId(),true,3,false));
        assertNull(overlay.testIntendedEnabled());
        assertFalse(overlay.testView().snapshot().devices().get(0).enabled());
    }

    @Test void olderCommandAckWithNewerDeviceRevisionReconcilesAfterNewerQuery() {
        AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();var overlay=enabledOverlay(clock,sent);var command=sent.get(0);
        overlay.snapshot(snapshot(SESSION,command.requestId()+1,true,2,false));assertEquals(Boolean.TRUE,overlay.testIntendedEnabled());
        overlay.snapshot(snapshot(SESSION,command.requestId(),true,3,false));
        assertNull(overlay.testIntendedEnabled());assertFalse(overlay.testView().snapshot().devices().get(0).enabled());
    }

    @Test void rejectsOtherSessionAndOlderSnapshotWhileRetainingSelectionByUuid(){
        AtomicLong clock=new AtomicLong(10);List<AirAlarmControlRequest> sent=new ArrayList<>();UUID second=UUID.randomUUID();
        var overlay=create(sent,clock);
        var first=snapshot(SESSION,3,true,2,false);
        overlay.snapshot(new AirAlarmControlSnapshot(0,SESSION,3,true,true,List.of(first.devices().get(0),new AirAlarmControlSnapshot.Device(second,VentilationDeviceConfig.Kind.VENT,new net.minecraft.core.BlockPos(1,0,0),true,true,false,0,0,1))));
        var layout=AirAlarmPaintLayout.forViewport(304,224);
        var device2=layout.device(1);overlay.mouseClicked(x(device2.x()+1),y(device2.y()+1),0);
        overlay.snapshot(snapshot(UUID.randomUUID(),99,true,9,true));overlay.snapshot(snapshot(SESSION,2,true,8,true));
        // Retained selection is the second UUID; its visible enabled hit remains usable.
        var controls=layout.tab(1);overlay.mouseClicked(x(controls.x()+1),y(controls.y()+1),0);
        var enabled=layout.enabled();overlay.mouseClicked(x(enabled.x()+1),y(enabled.y()+1),0);
        assertEquals(1,sent.size());assertEquals(second,sent.get(0).deviceId());assertEquals(1,sent.get(0).expectedRevision());
    }

    @Test void searchPaintAndHitRegionsUseSameFilteredUuidRowsAndNeverRetargetSelection(){
        UUID second=UUID.randomUUID();var overlay=create(new ArrayList<>(),new AtomicLong(1));
        overlay.snapshot(many(second,1,true,1,100,1,200));var layout=AirAlarmPaintLayout.forViewport(304,224);
        var row=layout.device(1);overlay.mouseClicked(x(row.x()+1),y(row.y()+1),0);
        overlay.editSearch("20,");var view=overlay.testView();var hits=AirAlarmPaint.hitRegions(layout,view).stream().filter(h->h.id().startsWith("device:")).toList();
        assertEquals(List.of("device:"+second),hits.stream().map(AirAlarmPaint.Hit::id).toList());
        assertEquals("device:"+second,hits.get(0).id());assertTrue(hits.get(0).bounds().contains(layout.device(0).x()+1,layout.device(0).y()+1));
        overlay.editSearch("no match");assertTrue(AirAlarmPaint.hitRegions(layout,overlay.testView()).stream().noneMatch(h->h.id().startsWith("device:")));
        overlay.editSearch("");var again=AirAlarmPaint.hitRegions(layout,overlay.testView()).stream().filter(h->h.id().startsWith("device:")).toList();
        assertEquals("device:"+DEVICE,again.get(0).id());assertEquals("device:"+second,again.get(1).id());
    }

    @Test void numericDraftWaitsForAppliedTargetRevisionAndSurvivesStaleAck(){
        UUID second=UUID.randomUUID();AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();
        var overlay=create(sent,clock);overlay.snapshot(many(second,1,true,1,100,1,200));
        overlay.testBeginNumeric(DEVICE);overlay.testEditNumeric("150.000");assertTrue(overlay.testSubmitNumeric());assertEquals(DEVICE,sent.get(0).deviceId());
        overlay.snapshot(many(second,sent.get(0).requestId(),true,1,100,1,200));assertEquals("PENDING",overlay.testNumericState());
        // Select another device while the request is outstanding: its UUID remains the submitted target.
        var row=AirAlarmPaintLayout.forViewport(304,224).device(1);overlay.mouseClicked(x(row.x()+1),y(row.y()+1),0);
        assertEquals(DEVICE,sent.get(0).deviceId());overlay.snapshot(many(second,2,true,2,150,1,200));
        assertEquals("CLEAN",overlay.testNumericState());assertEquals(150.0,sent.get(0).numericValue());
    }

    @Test void explicitRejectionConflictAndTimeoutReleaseNumericModelAndKeepTargetStable(){
        UUID second=UUID.randomUUID();AtomicLong clock=new AtomicLong(1);List<AirAlarmControlRequest> sent=new ArrayList<>();
        var overlay=create(sent,clock);overlay.snapshot(many(second,1,true,1,100,1,200));
        overlay.testBeginNumeric(DEVICE);overlay.testEditNumeric("125.000");assertTrue(overlay.testSubmitNumeric());
        overlay.snapshot(many(second,sent.get(0).requestId(),false,1,100,1,200));assertNotEquals("PENDING",overlay.testNumericState());
        assertEquals(DEVICE,sent.get(0).deviceId());
        // Retry demonstrates a revision-advanced conflicting applied value also resolves the draft.
        overlay.testBeginNumeric(DEVICE);overlay.testEditNumeric("130.000");assertTrue(overlay.testSubmitNumeric());
        overlay.snapshot(many(second,2,true,3,140,1,200));assertNotEquals("PENDING",overlay.testNumericState());
        overlay.testBeginNumeric(DEVICE);overlay.testEditNumeric("145.000");assertTrue(overlay.testSubmitNumeric());clock.set(102);overlay.tick();
        assertNotEquals("PENDING",overlay.testNumericState());assertTrue(overlay.testMessage().contains("timed out"));assertEquals(DEVICE,sent.get(2).deviceId());
    }
}

