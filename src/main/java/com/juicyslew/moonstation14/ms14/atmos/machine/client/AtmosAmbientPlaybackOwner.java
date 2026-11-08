package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import java.util.*;

/** Ephemeral owner of atmos ambience loops; all observations are supplied by the client adapter. */
final class AtmosAmbientPlaybackOwner {
    interface Handle {
        void stopHandle();
        void updateGain(float gain);
        boolean isStoppedHandle();
    }
    interface Playback { Handle start(Snapshot source, float alreadyAttenuatedGain); }
    record Snapshot(Object source, Object level, UUID placement, int x, int y, int z,
                    Object cue, float gain, float range, boolean eligible, boolean exactLoaded,
                    double cameraX, double cameraY, double cameraZ) {
        double distance() { double dx=x+.5-cameraX, dy=y+.5-cameraY, dz=z+.5-cameraZ; return Math.sqrt(dx*dx+dy*dy+dz*dz); }
    }
    private static final class Tracked {
        Snapshot snapshot; Handle handle;
        Tracked(Snapshot s, Handle h) { snapshot=s; handle=h; }
    }
    private final IdentityHashMap<Object, Tracked> playing = new IdentityHashMap<>();
    private final Playback playback;
    AtmosAmbientPlaybackOwner(Playback playback) { this.playback=playback; }

    void observe(Snapshot s) {
        Tracked t=playing.get(s.source());
        if (!s.eligible() || !s.exactLoaded() || s.cue()==null || s.range()<=0 || !Double.isFinite(s.distance()) || s.distance()>s.range()) { remove(s.source()); return; }
        if (t!=null && (!Objects.equals(t.snapshot.level(),s.level()) || !Objects.equals(t.snapshot.placement(),s.placement()) || !Objects.equals(t.snapshot.cue(),s.cue()) || t.snapshot.gain()!=s.gain() || t.snapshot.range()!=s.range() || t.handle.isStoppedHandle())) {
            remove(s.source()); t=null;
        }
        float gain=attenuated(s.gain(),s.range(),s.distance());
        if(t==null) { playing.put(s.source(),new Tracked(s,playback.start(s,gain))); }
        else { t.snapshot=s; t.handle.updateGain(gain); }
    }
    static float attenuated(float gain,float range,double distance) { return range<=0 || distance>=range ? 0 : gain*(float)Math.max(0,1-distance/range); }
    void remove(Object source) { Tracked t=playing.remove(source); if(t!=null) t.handle.stopHandle(); }
    void clear() { for(Tracked t:playing.values()) t.handle.stopHandle(); playing.clear(); }
    void removeChunk(Object level,int x,int z) { for(var it=playing.entrySet().iterator();it.hasNext();) { var e=it.next(); Snapshot s=e.getValue().snapshot; if(s.level()==level && (s.x()>>4)==x && (s.z()>>4)==z) { e.getValue().handle.stopHandle(); it.remove(); } } }
    Set<Object> sources() { Set<Object> out=Collections.newSetFromMap(new IdentityHashMap<>()); out.addAll(playing.keySet()); return Collections.unmodifiableSet(out); }
}
