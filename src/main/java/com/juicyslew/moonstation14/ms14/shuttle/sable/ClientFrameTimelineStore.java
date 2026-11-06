package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Client-only packet capture store. Timelines are shared per frame, never per actor. */
public final class ClientFrameTimelineStore {
    private static final Map<Level, LevelHistory> LEVELS = new WeakHashMap<>();
    private ClientFrameTimelineStore() { }
    private static final Map<Level, Map<UUID, Presentation>> PRESENTATION = new WeakHashMap<>();
    public record AngularOrientation(float x,float y,float z,float w) {
        public AngularOrientation {
            double length=Math.sqrt((double)x*x+(double)y*y+(double)z*z+(double)w*w);
            if(!Double.isFinite(length)||length<1e-12){x=y=z=0f;w=1f;}
            else {x=(float)(x/length);y=(float)(y/length);z=(float)(z/length);w=(float)(w/length);}
        }
        public static AngularOrientation capture(Quaternionfc value){return value==null?identity():new AngularOrientation(value.x(),value.y(),value.z(),value.w());}
        public static AngularOrientation identity(){return new AngularOrientation(0,0,0,1);}
        public Quaternionf copy(){return new Quaternionf(x,y,z,w);}
    }
    public record AngularPair(AngularOrientation from,AngularOrientation to) {
        public AngularPair { from=from==null?AngularOrientation.identity():new AngularOrientation(from.x(),from.y(),from.z(),from.w());
            to=to==null?AngularOrientation.identity():new AngularOrientation(to.x(),to.y(),to.z(),to.w()); }
        public static AngularPair capture(Quaternionfc from,Quaternionfc to){return new AngularPair(
                AngularOrientation.capture(from),AngularOrientation.capture(to));}
        public static AngularPair identity(){return new AngularPair(AngularOrientation.identity(),AngularOrientation.identity());}
    }
    public static final class Presentation {
        final long epoch; final UUID body; final long from,to;
        final Map<UUID, OwnedFrameSimulationScope.PresentationPair> pairs;
        final AngularPair angular;
        final PresentationPoseCache poses=new PresentationPoseCache();
        public Presentation(long epoch,UUID body,long from,long to,Map<UUID,OwnedFrameSimulationScope.PresentationPair> pairs){this(epoch,body,from,to,pairs,AngularPair.identity());}
        public Presentation(long epoch,UUID body,long from,long to,Map<UUID,OwnedFrameSimulationScope.PresentationPair> pairs,AngularPair angular){this.epoch=epoch;this.body=body;this.from=from;this.to=to;this.pairs=copyPairs(pairs);this.angular=angular==null?AngularPair.identity():new AngularPair(angular.from(),angular.to());}
        UUID body(){return body;} long from(){return from;} long to(){return to;}
        public AngularPair angular(){return new AngularPair(angular.from(),angular.to());}
        public AngularPair angularFor(long expectedEpoch,UUID expectedBody){
            return epoch!=expectedEpoch||body==null||!body.equals(expectedBody)?null:angular();
        }
        public Pose3dc pose(UUID frame,float partialTick) {
            var pair=pairs.get(frame); if(pair==null||!Float.isFinite(partialTick))return null;
            double fraction=Math.max(0,Math.min(1,partialTick));
            Pose3dc cached=poses.get(frame,(float)fraction,0);
            if(cached!=null)return cached;
            Pose3d selected=from!=to?new Pose3d(pair.from()).lerp(pair.to(),fraction):new Pose3d(pair.to());
            poses.put(frame,(float)fraction,0,selected);
            return new Pose3d(selected);
        }
        private static Map<UUID,OwnedFrameSimulationScope.PresentationPair> copyPairs(Map<UUID,OwnedFrameSimulationScope.PresentationPair> source){
            var copy=new HashMap<UUID,OwnedFrameSimulationScope.PresentationPair>();
            // PosePair values are deeply immutable; copy only the publication map, not every endpoint again.
            if(source!=null)copy.putAll(source);
            return Map.copyOf(copy);
        }
    }
    public record PublishedInterval(long from, long to) { }
    public static synchronized void publish(Level level,long epoch,UUID body,long from,long to,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> pairs) {
        publish(level,epoch,body,from,to,pairs,AngularPair.identity());
    }
    public static synchronized void publish(Level level,long epoch,UUID body,long from,long to,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> pairs,AngularPair angular) {
        if(level==null||body==null||from<0||to<from)return;
        LevelHistory history=LEVELS.get(level);
        publishViews(level(level),PRESENTATION.computeIfAbsent(level,k->new HashMap<>()),epoch,body,from,to,
                history==null?Map.of():history.frames,pairs,angular);
    }
    public static synchronized void reset(Level level, UUID body) {
        var map=PRESENTATION.get(level); if(map!=null){ if(body==null)map.clear(); else map.remove(body); }
        LevelHistory history=LEVELS.get(level);
        if(history!=null)history.reset(body);
    }
    /** Exact SDK ticks currently published for this body's presentation; absent when stale/unpublished. */
    public static synchronized PublishedInterval publishedInterval(Level level, UUID body) {
        var map=PRESENTATION.get(level); var p=map==null?null:map.get(body);
        return p==null?null:new PublishedInterval(p.from(),p.to());
    }
    static synchronized boolean hasPresentation(Level level,UUID body){
        var map=PRESENTATION.get(level);return map!=null&&map.containsKey(body);
    }
    public static synchronized AngularPair publishedAngular(Level level,long epoch,UUID body){
        var map=PRESENTATION.get(level);var p=map==null?null:map.get(body);
        return p==null?null:p.angularFor(epoch,body);
    }
    public static synchronized Pose3dc presentationEndpoint(Level level,UUID body,UUID frame,boolean last){
        return actorEndpoint(PRESENTATION.get(level),body,frame,last);
    }
    static Pose3dc actorEndpoint(Map<UUID,Presentation> actors,UUID body,UUID frame,boolean last){
        var p=actors==null?null:actors.get(body);
        if(p==null)return null;
        var pair=p.pairs.get(frame); if(pair==null)return null;
        Pose3dc endpoint=last?pair.from():pair.to();
        return endpoint==null?null:new Pose3d(endpoint);
    }
    public static synchronized Pose3dc presentation(Level level, UUID body, UUID frame, float partialTick) {
        var map=PRESENTATION.get(level); var p=map==null?null:map.get(body);
        if(p==null)return null;
        // Render interpolation uses only this immutable publication, never mutable packet history.
        return p.pose(frame,partialTick);
    }
    public static synchronized Pose3dc presentationForFrame(Level level, UUID frame, float partialTick) {
        LevelHistory history=LEVELS.get(level);
        return history==null||history.renderPublication==null?null:history.renderPublication.pose(frame,partialTick);
    }
    static final class LevelHistory {
        final UUID identity = UUID.randomUUID();
        final Map<UUID, AuthoritativeFrameTimeline> frames = new HashMap<>();
        Presentation renderPublication;
        void publish(Presentation publication){renderPublication=publication;}
        void reset(UUID body){if(body==null||renderPublication!=null&&body.equals(renderPublication.body()))renderPublication=null;}
        void remove(UUID frame){
            AuthoritativeFrameTimeline removed=frames.remove(frame);if(removed!=null)removed.clear();
            if(renderPublication!=null&&renderPublication.pairs.containsKey(frame)){
                Presentation p=renderPublication;var retained=new HashMap<>(p.pairs);retained.remove(frame);
                renderPublication=new Presentation(p.epoch,p.body,p.from,p.to,retained,p.angular);
            }
        }
    }
    private static LevelHistory level(Level level) { return LEVELS.computeIfAbsent(level, ignored -> new LevelHistory()); }
    public static synchronized void capture(Level level, ClientSubLevel frame, long tick, Pose3dc pose,
                                            Vector3fc linear, Vector3fc angular) {
        if (level == null || frame == null || frame.isRemoved() || frame.getLevel() != level) return;
        LevelHistory history = level(level);
        history.frames.computeIfAbsent(frame.getUniqueId(), ignored -> new AuthoritativeFrameTimeline())
                .receive(history.identity, frame.getUniqueId(), tick, pose,
                        new Vec3(linear.x(), linear.y(), linear.z()), new Vec3(angular.x(), angular.y(), angular.z()));
    }
    public static synchronized void seed(Level level, ClientSubLevel frame, long tick,
                                         Pose3dc lastPose, Pose3dc pose) {
        if (level == null || frame == null || frame.isRemoved() || frame.getLevel() != level) return;
        LevelHistory history = level(level);
        history.frames.computeIfAbsent(frame.getUniqueId(), ignored -> new AuthoritativeFrameTimeline())
                .seed(history.identity, frame.getUniqueId(), tick, lastPose, pose);
    }
    public static synchronized void stop(Level level, ClientSubLevel frame) {
        if (level == null || frame == null || frame.isRemoved() || frame.getLevel() != level) return;
        LevelHistory history = level(level);
        AuthoritativeFrameTimeline timeline = history.frames.get(frame.getUniqueId());
        if (timeline != null) timeline.stop(history.identity, frame.getUniqueId(), timeline.newestTick());
    }
    public static synchronized AuthoritativeFrameTimeline timeline(Level level, UUID frame) {
        LevelHistory history = LEVELS.get(level);
        return history == null ? null : history.frames.get(frame);
    }
    public static synchronized void remove(Level level, UUID frame) {
        LevelHistory history = LEVELS.get(level);
        if (history != null) history.remove(frame);
        var presentations=PRESENTATION.get(level);
        if(presentations!=null) for (var entry : new java.util.ArrayList<>(presentations.entrySet())) {
            var presentation=entry.getValue();
            if (presentation.pairs.containsKey(frame)) {
                var retained=new HashMap<>(presentation.pairs); retained.remove(frame);
                presentations.put(entry.getKey(),new Presentation(presentation.epoch,presentation.body,
                        presentation.from,presentation.to,retained,presentation.angular));
            } else presentation.poses.remove(frame);
        }
    }
    private static Map<UUID,OwnedFrameSimulationScope.PresentationPair> copyPairs(
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> source) {
        return source==null?Map.of():Map.copyOf(source);
    }
    static Map<UUID,OwnedFrameSimulationScope.PresentationPair> freezePairs(
            Map<UUID,AuthoritativeFrameTimeline> timelines,long from,long to,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> primary) {
        var frozen=new HashMap<>(copyPairs(primary));
        for(var entry:timelines.entrySet()) {
            if(frozen.containsKey(entry.getKey()))continue;
            AuthoritativeFrameTimeline timeline=entry.getValue();
            synchronized(timeline) {
                var a=timeline.sample(entry.getKey(),from);var b=timeline.sample(entry.getKey(),to);
                if(a.kind()!=AuthoritativeFrameTimeline.Kind.MISSING&&b.kind()!=AuthoritativeFrameTimeline.Kind.MISSING)
                    frozen.put(entry.getKey(),new OwnedFrameSimulationScope.PresentationPair(a.pose(),b.pose()));
            }
        }
        return Map.copyOf(frozen);
    }
    static Presentation actorPublication(long epoch,UUID body,long from,long to,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> primary,AngularPair angular) {
        return new Presentation(epoch,body,from,to,primary,angular);
    }
    static Presentation scenePublication(long epoch,UUID body,long from,long to,
            Map<UUID,AuthoritativeFrameTimeline> timelines,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> primary,AngularPair angular) {
        return new Presentation(epoch,body,from,to,freezePairs(timelines,from,to,primary),angular);
    }
    static void publishViews(LevelHistory history,Map<UUID,Presentation> actors,long epoch,UUID body,long from,long to,
            Map<UUID,AuthoritativeFrameTimeline> timelines,
            Map<UUID,OwnedFrameSimulationScope.PresentationPair> primary,AngularPair angular) {
        actors.put(body,actorPublication(epoch,body,from,to,primary,angular));
        history.publish(scenePublication(epoch,body,from,to,timelines,primary,angular));
    }
}
