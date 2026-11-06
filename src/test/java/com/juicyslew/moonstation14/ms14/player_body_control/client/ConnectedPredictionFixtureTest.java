package com.juicyslew.moonstation14.ms14.player_body_control.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ConnectedPredictionFixtureTest {
    private static final UUID BODY=UUID.randomUUID();
    private static final UUID FRAME=UUID.randomUUID();
    @Test void reportIdentityIsBoundToBodyRatherThanControllingPlayer() {
        UUID player=UUID.randomUUID();
        assertNotEquals(player,BODY);
        var fixture=new ConnectedPredictionFixture();
        assertTrue(fixture.start(9,BODY,"overworld",0,1));
        assertEquals(BODY,fixture.bodyId());
        assertNotEquals(player,fixture.bodyId());
    }
    @Test void serializedReportBodyComesFromFixtureBodyBinding() throws Exception {
        Path directory=Path.of("").toAbsolutePath().normalize();
        while(directory!=null&&!Files.isRegularFile(directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"))) directory=directory.getParent();
        assertNotNull(directory,"project root");
        String source=Files.readString(directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(source.contains("report.putAll(connectedPredictionFixture.diagnosticReport())"));
        assertTrue(Files.readString(directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ConnectedPredictionFixture.java"))
                .contains("report.put(\"epoch\",epoch);report.put(\"body\",body)"));
        assertFalse(source.contains("report.put(\"body\",connectedPredictionFixture.sent().isEmpty()?\"unknown\""));
    }
    @Test void scriptedTwoFlightsRequireSecondFlightAndPresentation(){
        var f=new ConnectedPredictionFixture(); assertTrue(f.start(1,BODY,"overworld",0,1));long seq=0;
        for(int i=1;i<=3;i++){f.input(i,i,1,BODY,"overworld",true,FRAME);f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,false),i);}
        f.snapshot(snapshot(1,0,3,true,List.of()));
        assertTrue(f.input(4,4,1,BODY,"overworld",true,FRAME).jump());f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,true),4);
        f.complete();assertEquals(ConnectedPredictionFixture.State.RUNNING,f.state());
        f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,false),5);
        f.input(6,6,1,BODY,"overworld",false,null);for(int i=0;i<3;i++)f.input(7+i,7+i,1,BODY,"overworld",true,FRAME);
        f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,false),10);
        f.snapshot(snapshot(1,1,seq-1,false,List.of()));f.snapshot(snapshot(1,2,seq-1,true,List.of(new ConnectedPredictionFixture.Replay(seq,true))));
        f.snapshot(snapshot(1,3,seq-1,true,List.of()));f.snapshot(snapshot(1,4,seq-1,true,List.of()));
        var second=f.input(11,11,1,BODY,"overworld",true,FRAME);assertTrue(second.jump(),"blocked second pulse; failure="+f.failure()+" replay="+f.actualReplayCount());f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,true),11);
        f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,false),12);
        f.input(13,13,1,BODY,"overworld",false,null);for(int i=0;i<3;i++)f.input(14+i,14+i,1,BODY,"overworld",true,FRAME);
        f.sent(new ConnectedPredictionFixture.Sent(1,++seq,(short)0,(short)0,false),17);
        f.snapshot(snapshot(1,5,seq-1,false,List.of()));f.snapshot(snapshot(1,6,seq-1,true,List.of(new ConnectedPredictionFixture.Replay(seq,true))));
        f.snapshot(snapshot(1,7,seq-1,true,List.of()));f.snapshot(snapshot(1,8,seq-1,true,List.of()));
        f.step(new ConnectedPredictionFixture.Step(10,1,true));f.step(new ConnectedPredictionFixture.Step(11,1,false));
        var identity=new ArrayList<>(Collections.nCopies(16,0d));identity.set(0,1d);identity.set(5,1d);identity.set(10,1d);identity.set(15,1d);
        var zero=new ConnectedPredictionFixture.Vector(0,0,0);
        f.interval(new ConnectedPredictionFixture.Interval(10,11,seq,1,false,FRAME,"EXACT","10,11",new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(1,2,3),zero,zero,null,identity,false,FRAME,identity,true,true));
        f.render(new ConnectedPredictionFixture.Render(17,.5,17,new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(1,2,3),FRAME,Collections.nCopies(16,0d),11,List.of(),null,null,true,FRAME,10L,11L,-1,-1,-1,"COMMITTED_PRESENTATION",null,null,null,"UNAVAILABLE"));
        f.complete();assertEquals(ConnectedPredictionFixture.State.COMPLETE,f.state());
    }
    private static ConnectedPredictionFixture.Snapshot snapshot(long e,long t,long a,boolean g,List<ConnectedPredictionFixture.Replay> r){long[] p=r.stream().mapToLong(ConnectedPredictionFixture.Replay::sequence).toArray();var world=new ConnectedPredictionFixture.Vector(1,2,78.44);return new ConnectedPredictionFixture.Snapshot(e,t,a,-1,0,p,true,true,g,g?FRAME:null,world,"WORLD",null,0,"test",world,new ConnectedPredictionFixture.Vector(1,2,78.3),new ConnectedPredictionFixture.Vector(1,2,78.5),r,List.of(),List.of());}
    @Test void authorityProjectionIsRetainedButNotUsedAsCircularPassOracle(){
        var mismatch=new ConnectedPredictionFixture(); mismatch.start(20,BODY,"overworld",0,1); mismatch.input(1,1,20,BODY,"overworld",true,FRAME);
        var server=new ConnectedPredictionFixture.Vector(0,0,78.44001328730697);
        var projected=new ConnectedPredictionFixture.Vector(0,0,78.28029194497682);
        mismatch.snapshot(authoritySnapshot(20,1,0,FRAME,server,projected,projected));
        assertNull(mismatch.failure());

        var common=new ConnectedPredictionFixture(); common.start(21,BODY,"overworld",0,1); common.input(1,1,21,BODY,"overworld",true,FRAME);
        var replayFinal=new ConnectedPredictionFixture.Vector(0,0,78.5);
        common.snapshot(authoritySnapshot(21,1,0,FRAME,server,server,replayFinal));
        assertNull(common.failure());
        assertEquals(0,common.snapshots().get(0).world().distanceTo(common.snapshots().get(0).authorityWorld()),0);
        assertTrue(common.snapshots().get(0).beforeWorld().distanceTo(common.snapshots().get(0).finalWorld())>0);
    }
    @Test void temporalCoverageRejectsMissingDuplicateAndOutOfOrderObservations(){
        var f=new ConnectedPredictionFixture();f.start(31,BODY,"overworld",0,1);
        assertFalse(f.coverage().complete());
        f.step(new ConnectedPredictionFixture.Step(1,1,true));f.step(new ConnectedPredictionFixture.Step(2,1,false));
        var identity=new ArrayList<>(Collections.nCopies(16,0d));identity.set(0,1d);identity.set(5,1d);identity.set(10,1d);identity.set(15,1d);
        var interval=metric(1,2,0,0,0,true,identity,1,1,"EXACT");
        f.step(new ConnectedPredictionFixture.Step(2,1,false));f.interval(interval);f.interval(interval);
        assertFalse(f.coverage().complete());assertTrue(f.coverage().reason().contains("duplicate"));
        f.reset();f.start(33,BODY,"overworld",0,1);f.step(new ConnectedPredictionFixture.Step(1,1,true));f.step(new ConnectedPredictionFixture.Step(2,1,false));
        f.interval(new ConnectedPredictionFixture.Interval(8,9,1,1,false,FRAME,"EXACT","8,9",null,null,null,null,null,Collections.nCopies(16,0d),false));
        f.interval(new ConnectedPredictionFixture.Interval(7,8,2,1,false,FRAME,"EXACT","7,8",null,null,null,null,null,Collections.nCopies(16,0d),false));
        assertTrue(f.coverage().reason().contains("non-consecutive"));
    }
    @Test void coveragePairsReplayAndLiveIntervalsBijectivelyAndCountsRendersOnEarlyFailure(){
        var f=new ConnectedPredictionFixture();f.start(45,BODY,"overworld",0,1);f.input(1,1,45,BODY,"overworld",true,FRAME);
        var pose=new ArrayList<>(Collections.nCopies(16,0d));pose.set(0,1d);pose.set(5,1d);pose.set(10,1d);pose.set(15,1d);
        var render=new ConnectedPredictionFixture.Render(1,.5,1,new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(1,2,3),FRAME,pose,2,List.of(),null,null,true,FRAME,1L,2L,-1,-1,-1,"COMMITTED_PRESENTATION",null,null,null,"UNAVAILABLE");
        var zero=new ConnectedPredictionFixture.Vector(0,0,0);
        f.render(render);f.step(new ConnectedPredictionFixture.Step(0,1,true));f.step(new ConnectedPredictionFixture.Step(1,1,false));
        f.step(new ConnectedPredictionFixture.Step(1,2,true));f.step(new ConnectedPredictionFixture.Step(2,2,false));
        f.interval(metric(0,1,0,0,0,true,pose,1,1,"EXACT"));
        f.interval(new ConnectedPredictionFixture.Interval(1,2,2,2,true,FRAME,"EXACT","replay",zero,zero,zero,zero,null,pose,false,FRAME,pose,true,true));
        assertTrue(f.coverage().complete(),f.coverage().reason());assertEquals(1,f.coverage().renderPairs());
        var missing=new ConnectedPredictionFixture();missing.start(46,BODY,"overworld",0,1);missing.input(1,1,46,BODY,"overworld",true,FRAME);
        missing.render(render);missing.step(new ConnectedPredictionFixture.Step(0,1,true));missing.step(new ConnectedPredictionFixture.Step(1,1,false));
        missing.step(new ConnectedPredictionFixture.Step(1,2,true));
        missing.interval(metric(0,1,0,0,0,true,pose,1,1,"EXACT"));
        missing.interval(new ConnectedPredictionFixture.Interval(1,2,2,2,true,FRAME,"EXACT","replay",zero,zero,zero,zero,null,pose,false,FRAME,pose,true,true));
        assertFalse(missing.coverage().complete());
        var extra=new ConnectedPredictionFixture();extra.start(47,BODY,"overworld",0,1);extra.input(1,1,47,BODY,"overworld",true,FRAME);extra.render(render);
        extra.step(new ConnectedPredictionFixture.Step(0,1,true));extra.step(new ConnectedPredictionFixture.Step(1,1,false));extra.step(new ConnectedPredictionFixture.Step(2,1,false));extra.interval(metric(0,1,0,0,0,true,pose,1,1,"EXACT"));
        assertFalse(extra.coverage().complete());assertEquals(1,extra.coverage().renderPairs());
    }
    @Test void trajectoryUsesActualWorldPointsInLaunchSupportPoseAndExposesDrift(){
        var f=new ConnectedPredictionFixture();f.start(32,BODY,"overworld",0,1);f.input(1,1,32,BODY,"overworld",true,FRAME);
        f.step(new ConnectedPredictionFixture.Step(0,1,true));
        var pose=new ArrayList<>(Collections.nCopies(16,0d));pose.set(0,1d);pose.set(5,1d);pose.set(10,1d);pose.set(15,1d);pose.set(12,10d);
        f.interval(metric(0,1,10.00,0,0,true,pose));f.interval(metric(1,2,10.15,0,0,true,pose));
        f.interval(metric(2,3,10.30,1,0,false,pose));f.interval(metric(3,4,10.01,0,0,true,pose));
        var result=f.trajectoryEvidence();assertTrue(result.available());
        assertEquals(.15,result.baselineDrift().x(),1e-9);
        assertEquals(.15,result.airHorizontalDrift().x(),1e-9);
        assertEquals(.01,result.landingOffset().x(),1e-9);
        assertTrue(result.maxDrift()>=.15);
        assertTrue(result.classification().contains("threshold unverified"));
    }
    @Test void frozenAirReferenceIsComparedWithinEachFlightOnly(){
        var f=new ConnectedPredictionFixture();f.start(48,BODY,"overworld",0,1);f.input(1,1,48,BODY,"overworld",true,FRAME);
        var pose=new ArrayList<>(Collections.nCopies(16,0d));pose.set(0,1d);pose.set(5,1d);pose.set(10,1d);pose.set(15,1d);
        f.interval(referenceMetric(0,1,true,0,pose));f.interval(referenceMetric(1,2,false,0,pose));f.interval(referenceMetric(2,3,true,0,pose));
        f.interval(referenceMetric(3,4,false,100,pose));f.interval(referenceMetric(4,5,false,100,pose));f.interval(referenceMetric(5,6,true,0,pose));
        assertEquals(0,f.trajectoryEvidence().airReferenceDrift().x(),0);
        var varying=new ConnectedPredictionFixture();varying.start(49,BODY,"overworld",0,1);varying.input(1,1,49,BODY,"overworld",true,FRAME);
        varying.interval(referenceMetric(0,1,true,0,pose));varying.interval(referenceMetric(1,2,false,0,pose));varying.interval(referenceMetric(2,3,true,0,pose));
        varying.interval(referenceMetric(3,4,false,100,pose));varying.interval(referenceMetric(4,5,false,102,pose));
        assertEquals(2,varying.trajectoryEvidence().airReferenceDrift().x(),0);
    }
    private static ConnectedPredictionFixture.Interval referenceMetric(long from,long to,boolean grounded,double reference,List<Double> pose){var zero=new ConnectedPredictionFixture.Vector(0,0,0);return new ConnectedPredictionFixture.Interval(from,to,to,1,false,FRAME,"EXACT","ref",zero,zero,zero,zero,grounded?null:new ConnectedPredictionFixture.Vector(reference,0,0),pose,false,FRAME,pose,grounded,true);}
    @Test void liveTrajectorySpansFrequentRestoreGenerationsWithoutMixingReplay(){
        var f=new ConnectedPredictionFixture();f.start(36,BODY,"overworld",0,1);f.input(1,1,36,BODY,"overworld",true,FRAME);
        double[] localX={0,.05,.15,.30,.50,.15,.01};double[] localY={0,0,0,1,2,1,0};boolean[] ground={true,true,true,false,false,false,true};
        int sequence=0;long[] anchors={0,2,4,6};
        for(int generation=1;generation<=4;generation++){
            int start=(generation-1)*2,end=Math.min(start+2,7);f.step(new ConnectedPredictionFixture.Step(anchors[generation-1],generation,true));
            for(int n=start;n<end;n++){
                int seq=++sequence;f.step(new ConnectedPredictionFixture.Step(n+1,generation,false));
                var pose=new ArrayList<>(Collections.nCopies(16,0d));pose.set(0,1d);pose.set(5,1d);pose.set(10,1d);pose.set(15,1d);pose.set(12,10d+n);
                var before=new ConnectedPredictionFixture.Vector(10+n+localX[n],localY[n],0);
                var after=new ConnectedPredictionFixture.Vector(10+n+1+localX[n],localY[n],0);
                var zero=new ConnectedPredictionFixture.Vector(0,0,0);
                f.interval(new ConnectedPredictionFixture.Interval(n,n+1,seq,generation,false,FRAME,"EXACT","timeline:"+n,before,after,zero,zero,ground[n]?null:zero,pose,false,FRAME,pose,ground[n],true,"EXACT","launch:"+n));
            }
        }
        var pose=Collections.nCopies(16,0d);var renderPose=new ArrayList<>(pose);renderPose.set(0,1d);renderPose.set(5,1d);renderPose.set(10,1d);renderPose.set(15,1d);
        f.render(new ConnectedPredictionFixture.Render(8,.5,8,new ConnectedPredictionFixture.Vector(0,0,0),new ConnectedPredictionFixture.Vector(0,0,0),new ConnectedPredictionFixture.Vector(0,0,0),FRAME,renderPose,7,List.of(),null,null,true,FRAME,7L,7L,-1,-1,-1,"COMMITTED_PRESENTATION",null,null,null,"UNAVAILABLE"));
        var result=f.trajectoryEvidence();assertTrue(result.available(),result.classification());
        assertEquals(.15,result.baselineDrift().x(),1e-9);assertEquals(.35,result.airHorizontalDrift().x(),1e-9);
        assertEquals(.01,result.landingOffset().x(),1e-9);assertEquals(7,result.baselineSamples()+result.airSamples()+result.landingSamples());
        assertTrue(f.coverage().complete(),f.coverage().reason());
        var duplicate=new ConnectedPredictionFixture();duplicate.start(37,BODY,"overworld",0,1);duplicate.input(1,1,37,BODY,"overworld",true,FRAME);
        duplicate.interval(metric(0,1,0,0,0,true,pose,1,1,"EXACT"));duplicate.interval(metric(1,2,0,0,0,false,pose,2,1,"EXACT"));
        assertEquals("DUPLICATE_LIVE_INPUT_SEQUENCE",duplicate.trajectoryEvidence().classification());
    }
    @Test void coverageAllowsSameSdkIntervalAfterNewRestoreGenerationButRejectsMissingHistoryKind(){
        var f=new ConnectedPredictionFixture();f.start(34,BODY,"overworld",0,1);f.input(1,1,34,BODY,"overworld",true,FRAME);
        var pose=new ArrayList<>(Collections.nCopies(16,0d));pose.set(0,1d);pose.set(5,1d);pose.set(10,1d);pose.set(15,1d);
        f.step(new ConnectedPredictionFixture.Step(10,1,true));f.step(new ConnectedPredictionFixture.Step(11,1,false));
        f.interval(metric(10,11,0,0,0,true,pose,1,2,"EXACT"));
        f.step(new ConnectedPredictionFixture.Step(10,2,true));f.step(new ConnectedPredictionFixture.Step(11,2,false));
        f.interval(metric(10,11,0,0,0,true,pose,2,2,"INTERPOLATED"));
        f.render(new ConnectedPredictionFixture.Render(1,.5,1,new ConnectedPredictionFixture.Vector(0,0,0),new ConnectedPredictionFixture.Vector(0,0,0),new ConnectedPredictionFixture.Vector(0,0,0),FRAME,pose,11,List.of(),null,null,true,FRAME,10L,11L,-1,-1,-1,"COMMITTED_PRESENTATION",null,null,null,"UNAVAILABLE"));
        assertTrue(f.coverage().complete(),f.coverage().reason());
        var bad=new ConnectedPredictionFixture();bad.start(35,BODY,"overworld",0,1);bad.input(1,1,35,BODY,"overworld",true,FRAME);
        bad.step(new ConnectedPredictionFixture.Step(10,1,true));bad.step(new ConnectedPredictionFixture.Step(11,1,false));
        bad.interval(metric(10,11,0,0,0,true,pose,1,1,"MISSING"));
        assertFalse(bad.coverage().complete());assertTrue(bad.coverage().reason().contains("invalid temporal geometry"));
    }
    private static ConnectedPredictionFixture.Interval metric(long from,long to,double x,double y,double z,boolean grounded,List<Double> pose){
        return metric(from,to,x,y,z,grounded,pose,1,100+from,"EXACT");
    }
    private static ConnectedPredictionFixture.Interval metric(long from,long to,double x,double y,double z,boolean grounded,List<Double> pose,long generation,long sequence,String kind){
        var point=new ConnectedPredictionFixture.Vector(x+10,y,z);
        var zero=new ConnectedPredictionFixture.Vector(0,0,0);
        return new ConnectedPredictionFixture.Interval(from,to,sequence,generation,false,FRAME,kind,from+":"+to,point,point,zero,zero,new ConnectedPredictionFixture.Vector(0,0,0),pose,false,FRAME,pose,grounded,true);
    }
    private static ConnectedPredictionFixture.Snapshot authoritySnapshot(long e,long t,long ack,UUID frame,ConnectedPredictionFixture.Vector world,ConnectedPredictionFixture.Vector authority,ConnectedPredictionFixture.Vector finalPos){return new ConnectedPredictionFixture.Snapshot(e,t,ack,-1,0,new long[0],true,true,true,frame,world,"FRAME",new ConnectedPredictionFixture.Vector(4,5,6),0,"accepted",authority,world,finalPos,List.of(),List.of(),List.of());}
    @Test void supportedFrameChangeFailsAndWorldAirborneFrameIsAllowed(){
        var f=new ConnectedPredictionFixture();f.start(8,BODY,"overworld",0,1);
        for(int i=1;i<=2;i++)f.input(i,i,8,BODY,"overworld",true,FRAME);
        f.input(3,3,8,BODY,"overworld",true,UUID.randomUUID());
        assertEquals(ConnectedPredictionFixture.Failure.CONTEXT_CHANGED,f.failure());
        f.reset();f.start(8,BODY,"overworld",0,1);
        f.input(1,1,8,BODY,"overworld",true,FRAME);f.input(2,2,8,BODY,"overworld",false,null);
        assertNull(f.failure());
    }
    @Test void delayedBaselineAckDoesNotChaseLaterIntents(){
        var f=new ConnectedPredictionFixture();f.start(9,BODY,"overworld",0,1);long seq=0;
        for(int i=1;i<=3;i++){f.input(i,i,9,BODY,"overworld",true,FRAME);f.sent(new ConnectedPredictionFixture.Sent(9,++seq,(short)0,(short)0,false),i);}
        for(int i=4;i<=16;i++)f.sent(new ConnectedPredictionFixture.Sent(9,++seq,(short)0,(short)0,false),i);
        f.snapshot(snapshot(9,1,3,true,List.of()));
        assertTrue(f.input(17,17,9,BODY,"overworld",true,FRAME).jump());
    }
    @Test void tickAndMonotonicDeadlinesFailClosed(){
        var ticks=new ConnectedPredictionFixture();ticks.start(10,BODY,"overworld",0,100);
        ticks.input(ConnectedPredictionFixture.MAX_TICKS,101,10,BODY,"overworld",true,FRAME);
        assertEquals(ConnectedPredictionFixture.Failure.TIMEOUT,ticks.failure());
        var time=new ConnectedPredictionFixture();time.start(11,BODY,"overworld",0,100);
        time.input(1,30_000_000_100L,11,BODY,"overworld",true,FRAME);
        assertEquals(ConnectedPredictionFixture.Failure.TIMEOUT,time.failure());
    }
    @Test void rejectsSessionLossAndBoundsSnapshotAndRenderRecords(){
        var f=new ConnectedPredictionFixture(); f.start(2,BODY,"overworld",0,1);
        f.input(1,2,2,BODY,"nether",true,FRAME);
        assertEquals(ConnectedPredictionFixture.Failure.CONTEXT_CHANGED,f.failure());
        f.reset(); f.start(2,BODY,"overworld",0,1);
        for(int i=0;i<ConnectedPredictionFixture.MAX_RECORDS;i++)f.render(new ConnectedPredictionFixture.Render(i,0,new ConnectedPredictionFixture.Vector(0,0,0)));
        f.render(new ConnectedPredictionFixture.Render(1,0,new ConnectedPredictionFixture.Vector(0,0,0)));
        assertEquals(ConnectedPredictionFixture.Failure.OVERFLOW,f.failure());
    }
    @Test void snapshotsCopyReplayArraysAndRequireActualReplayEvidence(){
        var f=new ConnectedPredictionFixture(); f.start(3,BODY,"overworld",0,1);
        f.sent(new ConnectedPredictionFixture.Sent(3,9,(short)0,(short)0,false),1);
        long[] plans={10}; var s=new ConnectedPredictionFixture.Snapshot(3,1,1,plans,true,true,true,null,
                new ConnectedPredictionFixture.Vector(0,0,0),null,null,null,List.of()); plans[0]=99;
        assertEquals(10,s.replaySequences()[0]);
        f.snapshot(s); assertEquals(ConnectedPredictionFixture.Failure.ACK_RETIRED_REPLAY,f.failure());
    }
    @Test void abortIsTerminalWithoutClaimingProtocolPass(){
        var f=new ConnectedPredictionFixture();f.start(4,BODY,"overworld",0,1);
        f.abort(ConnectedPredictionFixture.Failure.AUTHORITY_BARRIER);
        assertEquals(ConnectedPredictionFixture.State.FAILED,f.state());
        assertEquals(ConnectedPredictionFixture.Failure.AUTHORITY_BARRIER,f.failure());
        f.complete();assertEquals(ConnectedPredictionFixture.State.FAILED,f.state());
    }
    @Test void heartbeatTimesOutEvenWithoutFurtherSamples(){
        var f=new ConnectedPredictionFixture();f.start(5,BODY,"overworld",0,100);
        f.heartbeat(30_000_000_099L);assertEquals(ConnectedPredictionFixture.State.RUNNING,f.state());
        f.heartbeat(30_000_000_100L);assertEquals(ConnectedPredictionFixture.State.FAILED,f.state());
        assertEquals(ConnectedPredictionFixture.Failure.TIMEOUT,f.failure());
    }
    @Test void secondStartCannotResetActiveCapture(){
        var f=new ConnectedPredictionFixture(); assertTrue(f.start(12,BODY,"overworld",0,1));
        f.render(new ConnectedPredictionFixture.Render(1,0,1,new ConnectedPredictionFixture.Vector(1,2,3)));
        f.sent(new ConnectedPredictionFixture.Sent(12,1,(short)0,(short)0,false,1,1),1);
        assertFalse(f.start(13,BODY,"overworld",1,2));
        assertEquals(1,f.renders().size()); assertEquals(1,f.sent().size()); assertEquals(12,f.epoch());
    }
    @Test void replayOfPreArmPendingIntentIsAllowedAndBaselineAckIsFixed(){
        var f=new ConnectedPredictionFixture();f.start(6,BODY,"overworld",0,1);
        for(long s=1;s<=13;s++)f.sent(new ConnectedPredictionFixture.Sent(6,s,(short)0,(short)0,false),s);
        f.input(1,1,6,BODY,"overworld",true,FRAME);f.input(2,2,6,BODY,"overworld",true,FRAME);f.input(3,3,6,BODY,"overworld",true,FRAME);
        f.snapshot(new ConnectedPredictionFixture.Snapshot(6,1,0,new long[]{1},true,true,true,FRAME,null,null,null,null,List.of(new ConnectedPredictionFixture.Replay(1,true))));
        f.sent(new ConnectedPredictionFixture.Sent(6,14,(short)0,(short)0,false),4);
        f.snapshot(new ConnectedPredictionFixture.Snapshot(6,2,14,new long[0],true,true,true,FRAME,null,null,null,null,List.of()));
        assertTrue(f.input(4,4,6,BODY,"overworld",true,FRAME).jump());
        assertFalse(f.input(5,5,6,BODY,"overworld",true,FRAME).jump());
    }
    @Test void historyLossAbortCannotBeOverwrittenByLaterCleanSnapshot(){
        var f=new ConnectedPredictionFixture();f.start(7,BODY,"overworld",0,1);
        f.abort(ConnectedPredictionFixture.Failure.REPLAY_FAILURE);
        f.snapshot(new ConnectedPredictionFixture.Snapshot(7,1,0,new long[0],true,true,true,FRAME,null,null,null,null,List.of()));
        assertEquals(ConnectedPredictionFixture.State.FAILED,f.state());
        assertEquals(ConnectedPredictionFixture.Failure.REPLAY_FAILURE,f.failure());
    }
    @Test void renderObservationSerializesHeadingPoseAndUnavailablePairAndWiresAfterParticlesOwner() throws Exception {
        var f=new ConnectedPredictionFixture();f.start(50,BODY,"overworld",0,1);
        var r=new ConnectedPredictionFixture.Render(1,.5,3,new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(4,5,6),new ConnectedPredictionFixture.Vector(7,8,9),null,List.of(),-1,List.of(.1,.2,.3,.9),90d,12d,false,null,null,null,7,8,0,"UNAVAILABLE",null,null,null,"UNAVAILABLE_NO_SAFE_PRESENTATION_HEADING_ACCESSOR");
        f.render(r);var json=com.google.gson.JsonParser.parseString(ConnectedPredictionFixture.reportGson().toJson(Map.of("renders",f.renders()))).getAsJsonObject().getAsJsonArray("renders").get(0).getAsJsonObject();
        assertEquals(4,json.getAsJsonObject("bodyWorld").get("x").getAsDouble());assertEquals(90,json.get("logicalYaw").getAsDouble());assertEquals("UNAVAILABLE",json.get("poseSource").getAsString());
        assertTrue(json.get("trackingFrame").isJsonNull());assertTrue(json.get("historyNewestMinusPublishedToSteps").isJsonNull());assertFalse(json.get("grounded").getAsBoolean());assertEquals(9,json.getAsJsonObject("logicalBodyPosition").get("z").getAsDouble());assertEquals(4,json.getAsJsonArray("cameraQuaternion").size());
        var supported=new ConnectedPredictionFixture.Render(2,.25,4,new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(4,5,6),new ConnectedPredictionFixture.Vector(7,8,9),FRAME,Collections.nCopies(16,1d),11,List.of(0d,0d,0d,1d),0d,0d,true,FRAME,10L,11L,9,8,1,"COMMITTED_PRESENTATION",8L,0L,3L,"UNAVAILABLE_NO_SAFE_PRESENTATION_HEADING_ACCESSOR");
        var supportedJson=com.google.gson.JsonParser.parseString(ConnectedPredictionFixture.reportGson().toJson(List.of(supported))).getAsJsonArray().get(0).getAsJsonObject();assertTrue(supportedJson.get("grounded").getAsBoolean());assertEquals("COMMITTED_PRESENTATION",supportedJson.get("poseSource").getAsString());assertEquals(3,supportedJson.get("rebaseRevision").getAsLong());
        Path root=Path.of("").toAbsolutePath().normalize();while(root!=null&&!Files.isRegularFile(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java")))root=root.getParent();
        String source=Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(source.contains("connectedPredictionFixture.render(observeConnectedRender"));assertTrue(source.contains("observeConnectedRender(mc,body,camera,partialTick,wallCapture.observationReferenceFrame())"));assertTrue(source.contains("newest-published.to()"));assertTrue(source.contains("characterMovementRebaseRevision,"));
        var negativeGap=new ConnectedPredictionFixture.Render(2,.25,4,new ConnectedPredictionFixture.Vector(1,2,3),new ConnectedPredictionFixture.Vector(4,5,6),new ConnectedPredictionFixture.Vector(7,8,9),FRAME,Collections.nCopies(16,1d),500,List.of(0d,0d,0d,1d),0d,0d,true,FRAME,400L,500L,9,8,1,"COMMITTED_PRESENTATION",400L,-100L,3L,"UNAVAILABLE_NO_SAFE_PRESENTATION_HEADING_ACCESSOR");
        var gapJson=com.google.gson.JsonParser.parseString(ConnectedPredictionFixture.reportGson().toJson(negativeGap)).getAsJsonObject();assertEquals(-100,gapJson.get("historyNewestMinusPublishedToSteps").getAsLong());
    }
    @Test void partialAuthorityFailureSerializesValidJsonWithRealIdentityAndExplicitUnknownMetrics(){
        var f=new ConnectedPredictionFixture();assertTrue(f.start(43,BODY,"overworld",0,1));
        f.authorityFailure("SNAPSHOT_FRAME_ACTOR_BASIS",FRAME,12L,12L,"POSE_PAIR_SELECTED","frame-resident");
        String json=ConnectedPredictionFixture.reportGson().toJson(f.diagnosticReport());
        var parsed=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals(43,parsed.get("epoch").getAsLong());assertEquals(BODY.toString(),parsed.get("body").getAsString());
        assertEquals("UNAVAILABLE_AUTHORITY",parsed.get("failure").getAsString());
        var missing=parsed.getAsJsonObject("authorityDependency");
        assertEquals("SNAPSHOT_FRAME_ACTOR_BASIS",missing.get("dependency").getAsString());
        assertEquals(FRAME.toString(),missing.get("frameId").getAsString());
        assertEquals(12,missing.get("sdkFromStep").getAsLong());
        assertTrue(parsed.getAsJsonObject("temporalCoverage").get("maxRelativeDrift").isJsonNull());
        assertTrue(parsed.getAsJsonObject("trajectoryEvidence").get("maxDrift").isJsonNull());
    }
    @Test void finiteSuccessfulMetricReportKeepsNumbersUnchanged(){
        var coverage=new ConnectedPredictionFixture.Coverage(true,"measured",2,1,2,3,null,null,null,1.25,4,
                "MEASURED",2,1,1,4,0,null,0,-.5,.75);
        var trajectory=new ConnectedPredictionFixture.TrajectoryEvidence(true,"measured",null,null,null,2,1,1,4,
                1.25,0,null,0,-.5,.75);
        var report=new LinkedHashMap<String,Object>();report.put("temporalCoverage",coverage);report.put("trajectoryEvidence",trajectory);
        var parsed=com.google.gson.JsonParser.parseString(ConnectedPredictionFixture.reportGson().toJson(report)).getAsJsonObject();
        assertEquals(1.25,parsed.getAsJsonObject("temporalCoverage").get("maxRelativeDrift").getAsDouble(),0);
        assertEquals(1.25,parsed.getAsJsonObject("trajectoryEvidence").get("maxDrift").getAsDouble(),0);
        assertEquals(-.5,parsed.getAsJsonObject("trajectoryEvidence").get("airMinRelativeY").getAsDouble(),0);
    }
}
