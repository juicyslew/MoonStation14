package com.juicyslew.moonstation14.ms14.player_body_control.client;

import java.util.*;

/** Bounded, opt-in observations for a connected prediction flight; supplies only its scripted test input. */
final class ConnectedPredictionFixture {
    private static final com.google.gson.Gson REPORT_GSON = createReportGson();
    static com.google.gson.Gson reportGson(){return REPORT_GSON;}
    private static com.google.gson.Gson createReportGson(){
        var finiteDouble=new com.google.gson.TypeAdapter<Double>() {
            @Override public void write(com.google.gson.stream.JsonWriter out,Double value)throws java.io.IOException {
                if(value==null||!Double.isFinite(value))out.nullValue();else out.value(value);
            }
            @Override public Double read(com.google.gson.stream.JsonReader in)throws java.io.IOException {
                if(in.peek()==com.google.gson.stream.JsonToken.NULL){in.nextNull();return null;}return in.nextDouble();
            }
        };
        return new com.google.gson.GsonBuilder().serializeNulls().registerTypeAdapter(Double.class,finiteDouble)
                .registerTypeAdapter(double.class,finiteDouble).setPrettyPrinting().create();
    }
    static final int MAX_TICKS = 400;
    static final int MAX_RECORDS = 2048;
    enum State { OFF, RUNNING, COMPLETE, FAILED }
    enum Failure { TIMEOUT, OVERFLOW, REPLAY_FAILURE, ACK_RETIRED_REPLAY, SESSION_CHANGED, CONTEXT_CHANGED, AUTHORITY_BARRIER, UNAVAILABLE_AUTHORITY, AUTHORITY_POSITION_MISMATCH, TEMPORAL_COVERAGE_INCOMPLETE, STOPPED }
    record Sample(short wishX, short wishZ, boolean jump, boolean sprint) { }
    record Sent(long epoch, long sequence, short wishX, short wishZ, boolean jump, long clientTick, long monotonicNanos) {
        Sent(long epoch,long sequence,short wishX,short wishZ,boolean jump){this(epoch,sequence,wishX,wishZ,jump,-1,0);}
    }
    record Vector(double x,double y,double z) { double distanceTo(Vector other){return Math.sqrt(Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2));} }
    record Replay(long sequence, boolean executed) { }
    record Snapshot(long epoch, long tick, long ack, long arrivalClientTick, long monotonicNanos, long[] replaySequences, boolean accepted,
                    boolean replaySucceeded, boolean onGround, UUID frameId, Vector world,
                    String authorityBasis, Vector frameStorage, long rebaseRevision, String reconciliationStatus,
                    Vector authorityWorld, Vector beforeWorld, Vector finalWorld, List<Replay> actualReplay,
                    List<Double> lastPoseMatrix, List<Double> logicalPoseMatrix) {
        Snapshot { replaySequences=replaySequences.clone(); actualReplay=List.copyOf(actualReplay); lastPoseMatrix=lastPoseMatrix==null?List.of():List.copyOf(lastPoseMatrix); logicalPoseMatrix=logicalPoseMatrix==null?List.of():List.copyOf(logicalPoseMatrix); }
        Snapshot(long epoch,long tick,long ack,long[] sequences,boolean accepted,boolean succeeded,boolean onGround,UUID frameId,Vector world,Vector authorityWorld,Vector beforeWorld,Vector finalWorld,List<Replay> replay){this(epoch,tick,ack,-1,0,sequences,accepted,succeeded,onGround,frameId,world==null?new Vector(0,0,0):world,"UNKNOWN",null,0,"unknown",authorityWorld==null?(world==null?new Vector(0,0,0):world):authorityWorld,beforeWorld,finalWorld,replay,List.of(),List.of());}
        @Override public long[] replaySequences() { return replaySequences.clone(); }
    }
    record Step(long sdkStep, long reconciliationGeneration, boolean restored) { }
    record Interval(long fromStep, long toStep, long sequence, long reconciliationGeneration, boolean replay,
                    UUID selectedFrame, String historyKind, String sourceKeys, Vector beforeWorld,
                    Vector afterWorld, Vector beforeVelocity, Vector afterVelocity, Vector frozenAirReference,
                    List<Double> framePoseMatrix, boolean collisionDiagnosticsAvailable,
                    UUID launchSupportFrame, List<Double> launchSupportPoseMatrix, boolean grounded,
                    boolean uniformTranslationNonRotating, String launchSupportHistoryKind,
                    String launchSupportSourceKeys) {
        Interval { framePoseMatrix=framePoseMatrix==null?List.of():List.copyOf(framePoseMatrix);launchSupportPoseMatrix=launchSupportPoseMatrix==null?List.of():List.copyOf(launchSupportPoseMatrix); }
        Interval(long fromStep,long toStep,long sequence,long generation,boolean replay,UUID frame,String history,String keys,Vector before,Vector after,Vector beforeVelocity,Vector afterVelocity,Vector air,List<Double> pose,boolean collisions,UUID support,List<Double> supportPose,boolean grounded,boolean uniform){this(fromStep,toStep,sequence,generation,replay,frame,history,keys,before,after,beforeVelocity,afterVelocity,air,pose,collisions,support,supportPose,grounded,uniform,history,keys);}
        Interval(long fromStep,long toStep,long sequence,long generation,boolean replay,UUID frame,String history,String keys,Vector before,Vector after,Vector beforeVelocity,Vector afterVelocity,Vector air,List<Double> pose,boolean collisions){this(fromStep,toStep,sequence,generation,replay,frame,history,keys,before,after,beforeVelocity,afterVelocity,air,pose,collisions,null,List.of(),false,false,null,null);}
    }
    record Render(long tick, double fraction, long monotonicNanos, Vector position, Vector bodyWorld, Vector logicalBodyPosition,
                  UUID selectedFrame, UUID observationReferenceFrame, List<Double> framePoseMatrix, long sdkStep, List<Double> cameraQuaternion,
                  Double logicalYaw, Double logicalPitch, boolean grounded, UUID trackingFrame,
                  Long publishedFrom, Long publishedTo, Long snapshotTick, Long snapshotAck, Integer replayCount,
                  String poseSource, Long historyNewestTick, Long historyNewestMinusPublishedToSteps, Long rebaseRevision,
                  String modelHeadingSource) {
        static UUID selectObservationFrame(UUID trackingFrame,UUID observationReferenceFrame){return trackingFrame!=null?trackingFrame:observationReferenceFrame;}
        Render { framePoseMatrix=framePoseMatrix==null?List.of():List.copyOf(framePoseMatrix);cameraQuaternion=cameraQuaternion==null?List.of():List.copyOf(cameraQuaternion); }
        Render(long tick,double fraction,Vector position){this(tick,fraction,0,position,null,null,null,null,List.of(),-1,List.of(),null,null,false,null,null,null,null,null,null,"UNAVAILABLE",null,null,null,"UNAVAILABLE");}
        Render(long tick,double fraction,long nanos,Vector position){this(tick,fraction,nanos,position,null,null,null,null,List.of(),-1,List.of(),null,null,false,null,null,null,null,null,null,"UNAVAILABLE",null,null,null,"UNAVAILABLE");}
        Render(long tick,double fraction,long nanos,Vector position,Vector bodyWorld,Vector logicalBodyPosition,UUID frame,List<Double> matrix,long step,List<Double> cameraQuaternion,Double logicalYaw,Double logicalPitch,boolean grounded,UUID trackingFrame,Long publishedFrom,Long publishedTo,long snapshotTick,long snapshotAck,int replayCount,String poseSource,Long historyNewestTick,Long historyNewestMinusPublishedToSteps,Long rebaseRevision,String modelHeadingSource){this(tick,fraction,nanos,position,bodyWorld,logicalBodyPosition,frame,frame,matrix,step,cameraQuaternion,logicalYaw,logicalPitch,grounded,trackingFrame,publishedFrom,publishedTo,snapshotTick,snapshotAck,replayCount,poseSource,historyNewestTick,historyNewestMinusPublishedToSteps,rebaseRevision,modelHeadingSource);}
        String observationText(){return "cameraQuaternionXYZW="+cameraQuaternion+";logicalBodyYawPitch="+logicalYaw+"/"+logicalPitch+";ownedBodyRenderPosition="+bodyWorld+";logicalBodyPosition="+logicalBodyPosition+";grounded="+grounded+";trackingFrame="+trackingFrame+";observationReferenceFrame="+observationReferenceFrame+";selectedFrame="+selectedFrame+";publishedInterval="+publishedFrom+"/"+publishedTo+";poseSource="+poseSource+";framePoseMatrix="+framePoseMatrix+";historyNewestTick="+historyNewestTick+";historyNewestMinusPublishedToSteps="+historyNewestMinusPublishedToSteps+";snapshotTick="+snapshotTick+";snapshotAck="+snapshotAck+";replayCount="+replayCount+";rebaseRevision="+rebaseRevision+";modelHeadingSource="+modelHeadingSource;}
    }
    record Coverage(boolean complete, String reason, int completedSteps, int restoredSteps, int intervals,
                    int renderPairs, Vector standingDrift, Vector airDrift, Vector landingDisplacement,
                    double maxRelativeDrift, int measuredSamples, String movementClassification,
                    int standingSamples,int airSamples,int landingSamples,long measuredDurationSteps,int excludedReplaySamples,
                    Vector airReferenceDrift,int airReferenceSamples,double airMinRelativeY,double airMaxRelativeY) { }
    record TrajectoryEvidence(boolean available,String classification,Vector baselineDrift,Vector airHorizontalDrift,
                              Vector landingOffset,int baselineSamples,int airSamples,int landingSamples,
                              long durationSteps,double maxDrift,int excludedReplaySamples,
                               Vector airReferenceDrift,int airReferenceSamples,double airMinRelativeY,double airMaxRelativeY) { }
    record AuthorityDependency(String dependency,UUID frameId,Long sdkFromStep,Long sdkToStep,
                               String historyClassification,String residency) { }
    private final ArrayList<Sent> sent = new ArrayList<>();
    private final ArrayList<Snapshot> snapshots = new ArrayList<>();
    private final ArrayList<Render> renders = new ArrayList<>();
    private final ArrayList<Step> steps = new ArrayList<>();
    private final ArrayList<Interval> intervals = new ArrayList<>();
    private final Set<Long> actualReplays = new HashSet<>();
    private State state = State.OFF;
    private Failure failure;
    private long epoch, startTick, deadlineNanos, ack = -1, fixtureSequenceFloor = Long.MAX_VALUE;
    private UUID body;
    private String dimension;
    private int jumpEdges, releaseEdges, replayed, baselineSamples;
    private boolean previousJump;
    private enum Phase { BASELINE, FIRST_FLIGHT, SECOND_FLIGHT, DONE }
    private Phase phase=Phase.BASELINE;
    private boolean localAir,localLanded,serverAir,serverLanded, pulseInFlight;
    private int localLandTicks,serverLandTicks;
    private long jumpSequence=-1, releaseSequence=-1, lastInputTick=Long.MIN_VALUE, baselineSequence=-1;
    private UUID startFrame;
    private AuthorityDependency authorityDependency;

    boolean start(long epoch, UUID body, String dimension, long tick, long nowNanos) {
        if(state==State.RUNNING)return false;
        reset();
        if (epoch <= 0 || body==null || dimension==null || dimension.isBlank()) return false;
        this.epoch=epoch; this.body=body; this.dimension=dimension;
        startTick=tick; deadlineNanos=nowNanos+30_000_000_000L; state=State.RUNNING; return true;
    }
    Sample input(long tick,long now,long epoch,UUID body,String dimension,boolean onGround,UUID frameId) {
        if(state!=State.RUNNING)return new Sample((short)0,(short)0,false,true);
        if(epoch!=this.epoch || !Objects.equals(body,this.body) || !Objects.equals(dimension,this.dimension)){fail(Failure.CONTEXT_CHANGED);return neutral();}
        if(tick-startTick>=MAX_TICKS || now-deadlineNanos>=0){fail(Failure.TIMEOUT);return neutral();}
        if(tick==lastInputTick)return neutral(); lastInputTick=tick;
        if(phase==Phase.BASELINE && onGround) {
            if(frameId==null)return neutral();
            if(startFrame==null)startFrame=frameId;
            else if(!startFrame.equals(frameId)){fail(Failure.CONTEXT_CHANGED);return neutral();}
        }
        if(phase!=Phase.BASELINE && onGround && frameId!=null && startFrame!=null && !startFrame.equals(frameId)){fail(Failure.CONTEXT_CHANGED);return neutral();}
        boolean supportedOnStartingFrame=frameId!=null && frameId.equals(startFrame);
        if(onGround && supportedOnStartingFrame) { if(localAir)localLandTicks++; }
        else if(!onGround) {localAir=true;localLandTicks=0;}
        localLanded=localLandTicks>=3;
        if(phase==Phase.FIRST_FLIGHT && localLanded && serverLanded && releaseSequence>=0 && ack>=releaseSequence)phase=Phase.SECOND_FLIGHT;
        if(phase==Phase.BASELINE){if(onGround && supportedOnStartingFrame && baselineSamples<3)baselineSamples++;if(baselineSamples<3||baselineSequence<0||ack<baselineSequence)return neutral();phase=Phase.FIRST_FLIGHT;}
        if(pulseInFlight)return neutral();
        if((phase==Phase.FIRST_FLIGHT && jumpEdges==0) || (phase==Phase.SECOND_FLIGHT && jumpEdges==1 && localLanded&&serverLanded&&ack>=releaseSequence)){pulseInFlight=true;return new Sample((short)0,(short)0,true,true);}
        return neutral();
    }
    private Sample neutral(){return new Sample((short)0,(short)0,false,true);}
    void sent(Sent sample, long tick) {
        if (state!=State.RUNNING) return;
        if (sample.epoch()!=epoch) { fail(Failure.SESSION_CHANGED); return; }
        if (tick-startTick>=MAX_TICKS) { fail(Failure.TIMEOUT); return; }
        if (recordCount()>=MAX_RECORDS) { fail(Failure.OVERFLOW); return; }
        if (sample.jump()&&!previousJump) jumpEdges++;
        if (!sample.jump()&&previousJump) releaseEdges++;
        previousJump=sample.jump(); sent.add(sample);
        if(phase==Phase.BASELINE && baselineSamples>=3 && baselineSequence<0 && !sample.jump())baselineSequence=sample.sequence();
        if(fixtureSequenceFloor==Long.MAX_VALUE && !sample.jump()) fixtureSequenceFloor=sample.sequence();
        if(sample.jump()) { if(jumpEdges==2 && phase==Phase.SECOND_FLIGHT)phase=Phase.DONE; jumpSequence=sample.sequence();releaseSequence=-1;localAir=localLanded=serverAir=serverLanded=false;localLandTicks=serverLandTicks=0;pulseInFlight=false; }
        else if(jumpSequence>=0 && releaseSequence<0) releaseSequence=sample.sequence();
    }
    void snapshot(Snapshot sample) {
        if (state!=State.RUNNING || sample.epoch()!=epoch || !sample.accepted()) return;
        if(sample.onGround() && sample.frameId()!=null && startFrame!=null && !startFrame.equals(sample.frameId())){fail(Failure.CONTEXT_CHANGED);return;}
        // Keep world and projected authority coordinates as raw evidence. Comparing
        // them to one another is circular and is not a correctness oracle.
        if (!sample.replaySucceeded()) { fail(Failure.REPLAY_FAILURE); return; }
        if (sample.ack()<ack || (!snapshots.isEmpty() && sample.tick()<=snapshots.get(snapshots.size()-1).tick())) return;
        ack=sample.ack();
        for (long seq:sample.replaySequences()) {
            if (seq<=ack || (seq>=fixtureSequenceFloor && !known(seq))) {
                fail(Failure.ACK_RETIRED_REPLAY); return;
            }
            replayed++;
        }
        boolean supportedSnapshot=sample.frameId()!=null && sample.frameId().equals(startFrame);
        if(sample.onGround() && supportedSnapshot && jumpSequence>=0 && sample.ack()>=jumpSequence) {if(serverAir)serverLandTicks++;} else if(!sample.onGround() && jumpSequence>=0 && sample.ack()>=jumpSequence){serverAir=true;serverLandTicks=0;} serverLanded=serverLandTicks>=3;
        Set<Long> planned=new HashSet<>();for(long seq:sample.replaySequences())planned.add(seq);
        for(Replay r:sample.actualReplay()) if(r.executed()) {if(r.sequence()<=ack){fail(Failure.ACK_RETIRED_REPLAY);return;}if(r.sequence()>=fixtureSequenceFloor&&!known(r.sequence())){fail(Failure.ACK_RETIRED_REPLAY);return;}if(planned.contains(r.sequence()))actualReplays.add(r.sequence());}
        if(sample.replaySequences().length>0 && sample.actualReplay().isEmpty()){fail(Failure.REPLAY_FAILURE);return;}
        if(recordCount()>=MAX_RECORDS){fail(Failure.OVERFLOW);return;}
        snapshots.add(sample);
    }
    void render(Render render){if(state!=State.RUNNING)return;if(recordCount()>=MAX_RECORDS){fail(Failure.OVERFLOW);return;}renders.add(render);}
    void step(Step step){if(state!=State.RUNNING)return;if(recordCount()>=MAX_RECORDS){fail(Failure.OVERFLOW);return;}steps.add(step);}
    void interval(Interval interval){if(state!=State.RUNNING)return;if(recordCount()>=MAX_RECORDS){fail(Failure.OVERFLOW);return;}intervals.add(interval);}
    private boolean known(long sequence){for(Sent s:sent)if(s.epoch()==epoch&&s.sequence()==sequence)return true;return false;}
    private int recordCount(){return sent.size()+snapshots.size()+renders.size()+steps.size()+intervals.size()+actualReplays.size();}
    Coverage coverage(){
        int pairs=0;for(Render r:renders)if(finite(r.bodyWorld())&&finite(r.position())&&r.selectedFrame()!=null&&validMatrix(r.framePoseMatrix())&&r.sdkStep()>=0)pairs++;
        int completed=0,restored=0;Map<Long,Long> anchors=new HashMap<>(),lastStep=new HashMap<>();Map<Long,Set<Long>> committedSteps=new HashMap<>();Set<String> uniqueIntervals=new HashSet<>();Map<Long,Long> lastSequence=new HashMap<>();
        for(Step s:steps){if(s.restored()){restored++;if(anchors.putIfAbsent(s.reconciliationGeneration(),s.sdkStep())!=null)return incomplete("duplicate restore anchor",completed,restored);}else{completed++;if(!committedSteps.computeIfAbsent(s.reconciliationGeneration(),ignored->new HashSet<>()).add(s.sdkStep()))return incomplete("duplicate completed SDK step",completed,restored);}}
        for(Interval i:intervals){long g=i.reconciliationGeneration();Long anchor=anchors.get(g);if(anchor==null)return incomplete("missing restore anchor",completed,restored);String key=g+":"+i.fromStep()+":"+i.toStep();if(!uniqueIntervals.add(key))return incomplete("duplicate interval",completed,restored);long prev=lastStep.getOrDefault(g,anchor);if(i.fromStep()!=prev)return incomplete("non-consecutive intervals from restored anchor",completed,restored);if(i.toStep()!=i.fromStep()+1)return incomplete("invalid interval",completed,restored);if(i.sequence()<lastSequence.getOrDefault(g,Long.MIN_VALUE))return incomplete("unordered interval input sequence",completed,restored);lastStep.put(g,i.toStep());lastSequence.put(g,i.sequence());if(!committedSteps.getOrDefault(g,Set.of()).contains(i.toStep()))return incomplete("interval lacks matching completed SDK step",completed,restored);if((i.selectedFrame()!=null&&(!validHistoryKind(i.historyKind())||i.sourceKeys()==null||i.sourceKeys().isBlank()||!validMatrix(i.framePoseMatrix())))||!validHistoryKind(i.launchSupportHistoryKind())||i.launchSupportSourceKeys()==null||i.launchSupportSourceKeys().isBlank()||i.launchSupportFrame()==null||!i.launchSupportFrame().equals(startFrame)||!validMatrix(i.launchSupportPoseMatrix()))return incomplete("missing or invalid temporal geometry",completed,restored);if(!finite(i.beforeWorld())||!finite(i.afterWorld())||!finite(i.beforeVelocity())||!finite(i.afterVelocity()))return incomplete("missing/invalid body coordinate or velocity",completed,restored);if(i.frozenAirReference()!=null&&!finite(i.frozenAirReference()))return incomplete("invalid air reference",completed,restored);}
        Set<String> intervalEnds=new HashSet<>();
        for(Interval i:intervals)intervalEnds.add(i.reconciliationGeneration()+":"+i.toStep());
        Set<String> completedKeys=new HashSet<>();for(var entry:committedSteps.entrySet())for(long step:entry.getValue())completedKeys.add(entry.getKey()+":"+step);
        if(completed==0||restored==0||intervals.isEmpty()||!intervalEnds.equals(completedKeys))return incomplete("missing/mismatched SDK step intervals",completed,restored,pairs);
        if(pairs==0)return incomplete("missing paired body/render pose",completed,restored);
        TrajectoryEvidence t=trajectoryEvidence();
        return new Coverage(true,"temporal coverage only; motion acceptance threshold unverified",completed,restored,intervals.size(),pairs,t.baselineDrift(),t.airHorizontalDrift(),t.landingOffset(),t.maxDrift(),t.baselineSamples()+t.airSamples()+t.landingSamples(),t.classification(),t.baselineSamples(),t.airSamples(),t.landingSamples(),t.durationSteps(),t.excludedReplaySamples(),t.airReferenceDrift(),t.airReferenceSamples(),t.airMinRelativeY(),t.airMaxRelativeY());
    }
    private Coverage incomplete(String reason,int completed,int restored){int pairs=0;for(Render r:renders)if(finite(r.bodyWorld())&&finite(r.position())&&r.selectedFrame()!=null&&validMatrix(r.framePoseMatrix())&&r.sdkStep()>=0)pairs++;return incomplete(reason,completed,restored,pairs);}
    private Coverage incomplete(String reason,int completed,int restored,int pairs){return new Coverage(false,reason,completed,restored,intervals.size(),pairs,null,null,null,Double.NaN,0,"UNAVAILABLE",0,0,0,0,0,null,0,Double.NaN,Double.NaN);}
    TrajectoryEvidence trajectoryEvidence(){
        ArrayList<Interval> samples=new ArrayList<>();boolean unsupported=false;int excludedReplay=0;long first=Long.MAX_VALUE,last=Long.MIN_VALUE,previousSequence=Long.MIN_VALUE;Set<Long> liveSequences=new HashSet<>();
        // Keep append/observation order across generations. Reconciliation may rewind
        // SDK steps; those transitions are measured, not erased by selecting one gen.
        for(Interval i:intervals){if(i.replay()){excludedReplay++;continue;}if(!liveSequences.add(i.sequence()))return unavailable("DUPLICATE_LIVE_INPUT_SEQUENCE",excludedReplay);if(i.sequence()<previousSequence)return unavailable("OUT_OF_ORDER_LIVE_OBSERVATIONS",excludedReplay);previousSequence=i.sequence();samples.add(i);first=Math.min(first,i.toStep());last=Math.max(last,i.toStep());if(!i.uniformTranslationNonRotating())unsupported=true;for(Sent s:sent)if(s.sequence()==i.sequence()&&(s.wishX()!=0||s.wishZ()!=0))unsupported=true;}
        ArrayList<Vector> local=new ArrayList<>();for(Interval i:samples){if(!finite(i.afterWorld())||!validMatrix(i.launchSupportPoseMatrix()))return unavailable("INVALID_SUPPORT_OBSERVATION",excludedReplay);Vector p=transformPoint(i.afterWorld(),i.launchSupportPoseMatrix());if(p==null||!finite(p))return unavailable("INVALID_SUPPORT_POSE",excludedReplay);local.add(p);}
        int firstAir=-1;for(int n=0;n<samples.size();n++)if(!samples.get(n).grounded()){firstAir=n;break;}
        if(firstAir<1)return unavailable("MISSING_BASELINE_OR_FLIGHT",excludedReplay);
        Vector base=local.get(0),baseline=new Vector(0,0,0);double baselineMax=0;for(int n=1;n<firstAir;n++){Vector d=minus(local.get(n),base);if(horizontalLength(d)>baselineMax){baselineMax=horizontalLength(d);baseline=new Vector(d.x(),0,d.z());}}
        double airMax=0,landingMax=0,airMinY=0,airMaxY=0;Vector airPeak=new Vector(0,0,0),landing=null,airReference=null;int airCount=0,landingCount=0,airReferenceSamples=0;boolean airborne=false,missingAirReference=false;Vector takeoff=null;double referenceMax=0;
        for(int n=firstAir;n<samples.size();n++){Interval interval=samples.get(n);if(!interval.grounded()){if(!airborne){airborne=true;takeoff=local.get(n-1);airReference=null;}Vector d=minus(local.get(n),takeoff);double h=horizontalLength(d);if(h>airMax){airMax=h;airPeak=new Vector(d.x(),0,d.z());}airMinY=Math.min(airMinY,d.y());airMaxY=Math.max(airMaxY,d.y());airCount++;if(interval.frozenAirReference()==null)missingAirReference=true;else{if(!finite(interval.frozenAirReference()))return unavailable("INVALID_AIR_REFERENCE",excludedReplay);if(airReference==null)airReference=interval.frozenAirReference();double delta=interval.frozenAirReference().distanceTo(airReference);referenceMax=Math.max(referenceMax,delta);airReferenceSamples++;}}else if(airborne){airborne=false;Vector d=minus(local.get(n),base);double h=horizontalLength(d);if(h>landingMax){landingMax=h;landing=d;}landingCount++;}}
        if(airCount==0)return unavailable("MISSING_FLIGHT_SAMPLES",excludedReplay);
        if(missingAirReference)return unavailable("MISSING_AIR_REFERENCE",excludedReplay);
        String classification=(unsupported?"UNSUPPORTED_NONZERO_INPUT_OR_NONUNIFORM_FRAME":"MEASURED_LIVE_SUPPORT_RELATIVE; threshold unverified")+(referenceMax==0?"; frozen air reference unchanged in samples":"; frozen air reference variation observed");
        return new TrajectoryEvidence(true,classification,baseline,airPeak,landing,firstAir,airCount,landingCount,last-first,Math.max(baselineMax,Math.max(airMax,landingMax)),excludedReplay,new Vector(referenceMax,0,0),airReferenceSamples,airMinY,airMaxY);
    }
    private static TrajectoryEvidence unavailable(String reason,int excluded){return new TrajectoryEvidence(false,reason,null,null,null,0,0,0,0,Double.NaN,excluded,null,0,Double.NaN,Double.NaN);}
    private static boolean validHistoryKind(String kind){return "EXACT".equals(kind)||"INTERPOLATED".equals(kind)||"PREDICTED".equals(kind);}
    private static boolean validMatrix(List<Double> values){if(values==null||values.size()!=16)return false;for(double v:values)if(!Double.isFinite(v))return false;return true;}
    private static boolean finite(Vector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static double horizontalLength(Vector v){return Math.hypot(v.x(),v.z());}
    private static Vector transformPoint(Vector world,List<Double> values){double[] matrix=new double[16];for(int n=0;n<16;n++){matrix[n]=values.get(n);if(!Double.isFinite(matrix[n]))return null;}org.joml.Matrix4d pose=new org.joml.Matrix4d().set(matrix);if(Math.abs(pose.determinant())<1e-12)return null;org.joml.Vector3d local=pose.invert().transformPosition(world.x(),world.y(),world.z(),new org.joml.Vector3d());return new Vector(local.x,local.y,local.z);}
    private static Vector minus(Vector a,Vector b){return new Vector(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    void complete() { if(state==State.RUNNING && phase==Phase.DONE && jumpEdges>=2 && releaseEdges>=2 && localAir&&localLanded&&serverAir&&serverLanded && releaseSequence>=0 && ack>=releaseSequence && !actualReplays.isEmpty()&&!renders.isEmpty()) {if(coverage().complete())state=State.COMPLETE;else fail(Failure.TEMPORAL_COVERAGE_INCOMPLETE);} }
    private void fail(Failure value) { state=State.FAILED; failure=value; }
    void abort(Failure value) { if(state==State.RUNNING) fail(Objects.requireNonNull(value)); }
    void authorityFailure(String dependency,UUID frameId,Long fromStep,Long toStep,String history,String residency) {
        if(state!=State.RUNNING)return;
        authorityDependency=new AuthorityDependency(dependency,frameId,fromStep,toStep,history,residency);
        fail(Failure.UNAVAILABLE_AUTHORITY);
    }
    void heartbeat(long nowNanos) { if(state==State.RUNNING && nowNanos-deadlineNanos>=0) fail(Failure.TIMEOUT); }
    void reset() { sent.clear(); snapshots.clear(); renders.clear();steps.clear();intervals.clear();actualReplays.clear(); state=State.OFF; failure=null; authorityDependency=null;epoch=0; startTick=deadlineNanos=0; ack=-1;body=null;dimension=null;startFrame=null;phase=Phase.BASELINE;fixtureSequenceFloor=Long.MAX_VALUE;jumpSequence=releaseSequence=-1;lastInputTick=Long.MIN_VALUE;baselineSequence=-1;localLandTicks=serverLandTicks=0; jumpEdges=releaseEdges=replayed=baselineSamples=0; previousJump=false;localAir=localLanded=serverAir=serverLanded=pulseInFlight=false; }
    long epoch(){return epoch;}
    UUID startFrame(){return startFrame;}
    UUID bodyId(){return body;}
    State state() { return state; }
    Failure failure() { return failure; }
    AuthorityDependency authorityDependency(){return authorityDependency;}
    int jumpEdges() { return jumpEdges; }
    int releaseEdges() { return releaseEdges; }
    int replayed() { return replayed; }
    List<Sent> sent() { return List.copyOf(sent); }
    List<Snapshot> snapshots() { return List.copyOf(snapshots); }
    List<Render> renders(){return List.copyOf(renders);}
    List<Step> steps(){return List.copyOf(steps);}
    List<Interval> intervals(){return List.copyOf(intervals);}
    Coverage temporalCoverage(){return coverage();}
    int actualReplayCount(){return actualReplays.size();}
    Map<String,Object> diagnosticReport(){
        var report=new LinkedHashMap<String,Object>();
        report.put("epoch",epoch);report.put("body",body);report.put("failure",failure);
        report.put("authorityDependency",authorityDependency);report.put("temporalCoverage",coverage());
        report.put("trajectoryEvidence",trajectoryEvidence());return java.util.Collections.unmodifiableMap(report);
    }
}
