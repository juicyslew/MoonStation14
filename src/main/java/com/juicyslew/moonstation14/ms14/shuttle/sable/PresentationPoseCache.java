package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-published-clock, per-frame render cache keyed by interpolation fraction and source revision. */
final class PresentationPoseCache {
    private record Entry(float partial,long revision,Pose3d pose) { }
    private final Map<UUID,Entry> entries=new HashMap<>();
    Pose3dc get(UUID frame,float partial,long revision) {
        var entry=entries.get(frame);
        return entry==null||Float.compare(partial,entry.partial())!=0||revision!=entry.revision()
                ?null:new Pose3d(entry.pose());
    }
    void put(UUID frame,float partial,long revision,Pose3dc pose) {
        entries.put(frame,new Entry(partial,revision,new Pose3d(pose)));
    }
    void remove(UUID frame){entries.remove(frame);}
}
