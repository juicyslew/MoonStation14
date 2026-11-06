package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.UUID;

/** Exact-actor temporal frame selection; does not alter Sable's logical pose. */
public final class OwnedFrameSimulationScope implements AutoCloseable {
    public enum Failure { NONE, INVALID_ACTOR, NOT_TRACKED, FRAME_UNAVAILABLE, HISTORY_UNAVAILABLE, MOTION_UNAVAILABLE }
    public static final class TemporalUnavailableException extends RuntimeException {
        private final Failure reason;
        public TemporalUnavailableException(Failure reason){super("Owned frame temporal query unavailable: "+reason);this.reason=reason;}
        public Failure reason(){return reason;}
    }
    public static final int MAX_FRAMES_PER_SCOPE = 256;
    private static final ThreadLocal<ArrayDeque<OwnedFrameSimulationScope>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private final Entity actor;
    private final SubLevel frame;
    private final Pose3dc from, to;
    private final AuthoritativeFrameTimeline.Result motionFrom, motionTo;
    private Failure failure;
    private final java.util.Map<UUID, Interval> intervals = new java.util.HashMap<>();
    private java.util.Map<UUID,net.minecraft.world.phys.AABB> temporalBounds;
    public record Interval(Pose3dc from, Pose3dc to, AuthoritativeFrameTimeline.Result motionFrom,
                           AuthoritativeFrameTimeline.Result motionTo) { }
    private boolean closed;

    private OwnedFrameSimulationScope(Entity actor, SubLevel frame, Pose3dc from, Pose3dc to,
            AuthoritativeFrameTimeline.Result a, AuthoritativeFrameTimeline.Result b, Failure failure,
            long fromTick,long toTick) {
        this.actor=actor; this.frame=frame; this.from=copy(from); this.to=copy(to);
        motionFrom=a; motionTo=b; this.failure=failure;
        motionTickFrom=fromTick; motionTickTo=toTick;
        if(frame!=null&&failure==Failure.NONE) intervals.put(frame.getUniqueId(),new Interval(copy(from),copy(to),a,b));
    }
    public static OwnedFrameSimulationScope open(Level level, Entity actor, long fromTick, long toTick) {
        Failure f=Failure.NONE; SubLevel frame=null; Pose3dc from=null,to=null;
        AuthoritativeFrameTimeline.Result a=null,b=null;
        if(level==null||actor==null||actor.level()!=level||actor.isRemoved()||fromTick<0||toTick<fromTick) f=Failure.INVALID_ACTOR;
        else if(actor instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension ext
                && (frame=ext.sable$getTrackingSubLevel())!=null) {
            if(frame.isRemoved()||frame.getLevel()!=level||frame.getPlot()==null
                    ||SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId())!=frame) f=Failure.FRAME_UNAVAILABLE;
        }
        if(f==Failure.NONE && frame!=null) {
            AuthoritativeFrameTimeline timeline=level.isClientSide
                    ? ClientFrameTimelineStore.timeline(level,frame.getUniqueId()):null;
            if(timeline==null) f=Failure.HISTORY_UNAVAILABLE;
            else { a=timeline.sample(frame.getUniqueId(),fromTick); b=timeline.sample(frame.getUniqueId(),toTick);
                if(a.kind()==AuthoritativeFrameTimeline.Kind.MISSING||b.kind()==AuthoritativeFrameTimeline.Kind.MISSING) f=Failure.HISTORY_UNAVAILABLE;
                else { from=a.pose(); to=b.pose(); }
            }
        }
        var scope=new OwnedFrameSimulationScope(actor,frame,from,to,a,b,f,fromTick,toTick); STACK.get().push(scope); return scope;
    }
    private static Pose3dc copy(Pose3dc p) { return p==null?null:new Pose3d(p); }
    public Failure failure(){return failure;} public boolean ready(){return failure==Failure.NONE;}
    public Entity actor(){return actor;} public SubLevel frame(){return frame;}
    public UUID frameId(){return frame==null?null:frame.getUniqueId();}
    public Pose3dc from(){return copy(from);} public Pose3dc to(){return copy(to);}
    public Pose3dc from(SubLevel sub){return copy(requireInterval(actor.level(),sub).from());}
    public Pose3dc to(SubLevel sub){requireInterval(actor.level(),sub);return cachedTo(sub.getUniqueId());}
    Pose3dc cachedTo(UUID frameId){
        if(!ready())throw new TemporalUnavailableException(failure);
        Interval interval=intervals.get(frameId);
        if(interval==null){fail(Failure.HISTORY_UNAVAILABLE);throw new TemporalUnavailableException(failure);}
        return copy(interval.to());
    }
    public Vec3 linearVelocity(){return motionTo==null?null:motionTo.linearVelocity();}
    public Vec3 angularVelocity(){return motionTo==null?null:motionTo.angularVelocity();}
    /** Cost: one loaded-frame/timeline preparation per scope (max 256), then at most 256 cached AABB checks per query.
     * This deliberately bounded union never scans blocks or SAT rows; it does not claim a spatial index. */
    public synchronized java.util.List<SubLevel> candidates(Level level, dev.ryanhcode.sable.companion.math.BoundingBox3dc query,
            Iterable<SubLevel> sdkCandidates) {
        if(!ready()||level==null||level.isClientSide==false)return java.util.List.of();
        var sdkList=new java.util.ArrayList<SubLevel>(); if(sdkCandidates!=null)for(SubLevel candidate:sdkCandidates)if(candidate!=null)sdkList.add(candidate);
        if(temporalBounds==null) {
            var loaded=SubLevelContainer.getContainer(level).getAllSubLevels();
            if(loaded.size()>MAX_FRAMES_PER_SCOPE){failure=Failure.FRAME_UNAVAILABLE;return java.util.List.of();}
            var sdkSet=new java.util.HashSet<UUID>(); for(SubLevel candidate:sdkList)sdkSet.add(candidate.getUniqueId());
            var prepared=new java.util.HashMap<UUID,net.minecraft.world.phys.AABB>();
            for(SubLevel sub:loaded) {
                if(!valid(level,sub))continue;
                if(ClientFrameTimelineStore.timeline(level,sub.getUniqueId())==null) {
                    if(missingHistoryIsFatal(sdkSet.contains(sub.getUniqueId()))){failure=Failure.HISTORY_UNAVAILABLE;return java.util.List.of();}
                    continue;
                }
                Interval i=interval(level,sub);
                if(i==null){if(sdkSet.contains(sub.getUniqueId())){fail(Failure.HISTORY_UNAVAILABLE);return java.util.List.of();}continue;}
                var bounds=unionBounds(sub.getPlot().getBoundingBox().toAABB(),i.from(),i.to());
                prepared.put(sub.getUniqueId(),bounds);
            }
            temporalBounds=prepared;
        }
        var result=new java.util.ArrayList<SubLevel>(); var queryBox=query.toMojang();
        for(SubLevel candidate:sdkList)if(valid(level,candidate)
                &&!temporalBounds.containsKey(candidate.getUniqueId())) {
            Interval i=interval(level,candidate);
            if(i==null){fail(Failure.HISTORY_UNAVAILABLE);return java.util.List.of();}
            temporalBounds.put(candidate.getUniqueId(),unionBounds(candidate.getPlot().getBoundingBox().toAABB(),i.from(),i.to()));
        }
        for(var entry:temporalBounds.entrySet()) if(entry.getValue().intersects(queryBox)) {
            SubLevel sub=SubLevelContainer.getContainer(level).getSubLevel(entry.getKey());
            if(valid(level,sub))result.add(sub);
        }
        return java.util.List.copyOf(result);
    }
    public synchronized Interval interval(Level level, SubLevel sub) {
        if(!ready()||!valid(level,sub))return null;
        var cached=intervals.get(sub.getUniqueId()); if(cached!=null)return cached;
        var timeline=ClientFrameTimelineStore.timeline(level,sub.getUniqueId());
        if(timeline==null)return null;
        var a=timeline.sample(sub.getUniqueId(),motionTickFrom); var b=timeline.sample(sub.getUniqueId(),motionTickTo);
        if(a.kind()==AuthoritativeFrameTimeline.Kind.MISSING||b.kind()==AuthoritativeFrameTimeline.Kind.MISSING)return null;
        cached=new Interval(a.pose(),b.pose(),a,b); intervals.put(sub.getUniqueId(),cached); return cached;
    }
    /** Immutable small-pose snapshot of intervals already selected in this scope, optionally including AIR launch frame. */
    public record PresentationPair(Pose3d from, Pose3d to) {
        public PresentationPair {
            from = new Pose3d(java.util.Objects.requireNonNull(from));
            to = new Pose3d(java.util.Objects.requireNonNull(to));
        }
        public PresentationPair(Pose3dc from, Pose3dc to) {
            this(from instanceof Pose3d poseFrom?poseFrom:new Pose3d(from),to instanceof Pose3d poseTo?poseTo:new Pose3d(to));
        }
        @Override public Pose3d from() { return new Pose3d(from); }
        @Override public Pose3d to() { return new Pose3d(to); }
    }
    public synchronized java.util.Map<UUID, PresentationPair> presentationSnapshot(UUID... additionalFrames) {
        if (!ready()) throw new TemporalUnavailableException(failure);
        if (additionalFrames != null) for (UUID additionalFrame : additionalFrames) {
            if (additionalFrame != null && !intervals.containsKey(additionalFrame)) {
                var sub = SubLevelContainer.getContainer(actor.level()).getSubLevel(additionalFrame);
                if (sub == null || interval(actor.level(), sub) == null) {
                    fail(Failure.HISTORY_UNAVAILABLE);
                    throw new TemporalUnavailableException(failure);
                }
            }
        }
        if (intervals.size() > MAX_FRAMES_PER_SCOPE) {
            fail(Failure.FRAME_UNAVAILABLE);
            throw new TemporalUnavailableException(failure);
        }
        var result = new java.util.HashMap<UUID, PresentationPair>();
        intervals.forEach((id, value) -> result.put(id, new PresentationPair(value.from(), value.to())));
        return java.util.Map.copyOf(result);
    }
    public synchronized Interval requireInterval(Level level,SubLevel sub) {
        if(!ready())throw new TemporalUnavailableException(failure);
        var value=interval(level,sub);
        if(value==null){fail(Failure.HISTORY_UNAVAILABLE);throw new TemporalUnavailableException(failure);}
        return value;
    }
    public AuthoritativeFrameTimeline.Result requireMotionTo(Level level,SubLevel sub) {
        var interval=requireInterval(level,sub);
        try{return requireKnownMotion(interval.motionTo());}
        catch(TemporalUnavailableException missing){fail(Failure.MOTION_UNAVAILABLE);throw missing;}
    }
    static AuthoritativeFrameTimeline.Result requireKnownMotion(AuthoritativeFrameTimeline.Result sample) {
        if(sample==null||sample.kind()==AuthoritativeFrameTimeline.Kind.MISSING||!sample.motionKnown())
            throw new TemporalUnavailableException(Failure.MOTION_UNAVAILABLE);
        return sample;
    }
    private void fail(Failure reason){failure=reason;}
    public Pose3dc poseAt(SubLevel sub,float alpha){var i=requireInterval(actor.level(),sub);return new Pose3d(i.from()).lerp(i.to(),Math.max(0d,Math.min(1d,alpha)));}
    static net.minecraft.world.phys.AABB unionBounds(net.minecraft.world.phys.AABB plot,
            Pose3dc from,Pose3dc to) {
        var fromWorld=new dev.ryanhcode.sable.companion.math.BoundingBox3d(plot).transform(from).toMojang();
        var toWorld=new dev.ryanhcode.sable.companion.math.BoundingBox3d(plot).transform(to).toMojang();
        var endpoints=fromWorld.minmax(toWorld);
        if(translationOnly(from,to))return endpoints;
        // Pose3dc transforms p as position + R*((p - rotationPoint) * scale). For any
        // interpolated orientation, bound the rotated (corner - pivot) vector by its maximum
        // scaled radius. Relative corner/pivot deltas avoid dependence on far-away plot origins;
        // position itself is linearly interpolated. This is candidate inclusion only.
        var fp=from.rotationPoint();var tp=to.rotationPoint();
        var fs=from.scale();var ts=to.scale();
        double dx=maxCornerPivotDelta(plot.minX,plot.maxX,fp.x(),tp.x())*Math.max(Math.abs(fs.x()),Math.abs(ts.x()));
        double dy=maxCornerPivotDelta(plot.minY,plot.maxY,fp.y(),tp.y())*Math.max(Math.abs(fs.y()),Math.abs(ts.y()));
        double dz=maxCornerPivotDelta(plot.minZ,plot.maxZ,fp.z(),tp.z())*Math.max(Math.abs(fs.z()),Math.abs(ts.z()));
        double radius=Math.sqrt(square(dx)+square(dy)+square(dz));
        var p=from.position(); var q=to.position();
        return new net.minecraft.world.phys.AABB(Math.min(p.x(),q.x())-radius,Math.min(p.y(),q.y())-radius,
                Math.min(p.z(),q.z())-radius,Math.max(p.x(),q.x())+radius,Math.max(p.y(),q.y())+radius,
                Math.max(p.z(),q.z())+radius);
    }
    private static boolean translationOnly(Pose3dc a,Pose3dc b) {
        var ap=a.rotationPoint();var bp=b.rotationPoint();var as=a.scale();var bs=b.scale();
        var aq=a.orientation();var bq=b.orientation();
        double dot=Math.abs(aq.x()*bq.x()+aq.y()*bq.y()+aq.z()*bq.z()+aq.w()*bq.w());
        return dot>=1d-1e-12&&near(ap.x(),bp.x())&&near(ap.y(),bp.y())&&near(ap.z(),bp.z())
                &&near(as.x(),bs.x())&&near(as.y(),bs.y())&&near(as.z(),bs.z());
    }
    private static boolean near(double a,double b){return Math.abs(a-b)<=1e-12+Math.ulp(a)+Math.ulp(b);}
    private static double maxCornerPivotDelta(double min,double max,double from,double to){
        return Math.max(Math.max(Math.abs(min-from),Math.abs(max-from)),Math.max(Math.abs(min-to),Math.abs(max-to)));
    }
    private static double square(double v){return v*v;}
    static boolean missingHistoryIsFatal(boolean currentSdkCandidate){return currentSdkCandidate;}
    private long motionTickFrom, motionTickTo;
    private boolean valid(Level level,SubLevel sub){return sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel&&!sub.isRemoved()&&sub.getLevel()==level&&sub.getPlot()!=null&&SubLevelContainer.getContainer(level).getSubLevel(sub.getUniqueId())==sub;}
    public static OwnedFrameSimulationScope active(Level level, SubLevel frame) {
        return activeExact(level,frame);
    }
    public static OwnedFrameSimulationScope activeExact(Level level, SubLevel frame) {
        var stack=STACK.get(); if(stack.isEmpty())return null;
        var s=stack.peek(); return s.actor!=null&&s.actor.level()==level&&s.valid(level,frame)?s:null;
    }
    public static OwnedFrameSimulationScope activeFor(Level level) {
        var stack=STACK.get(); if(stack.isEmpty())return null; var s=stack.peek();
        return s.actor!=null&&s.actor.level()==level?s:null;
    }
    @Override public void close(){ if(closed)return; closed=true; var stack=STACK.get(); if(!stack.isEmpty()&&stack.peek()==this)stack.pop(); else stack.remove(this); if(stack.isEmpty())STACK.remove(); }
}
