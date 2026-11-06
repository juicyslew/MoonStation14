package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Validated point-velocity changes between exact native tracking frames, in m/s. */
public final class SableFrameVelocity {
    private SableFrameVelocity() { }

    /**
     * Returns old-point minus new-point SDK velocity. Null means either endpoint was
     * unknown/unloaded; a valid map/world endpoint contributes zero.
     */
    public static Vec3 pointDeltaPerSecond(Level level,
            LoadedFrameRayQuery.ActorPoint before, LoadedFrameRayQuery.ActorPoint after) {
        if (level == null || !validPoint(before) || !validPoint(after)) return null;
        SubLevel oldFrame = liveFrame(level, before);
        SubLevel newFrame = liveFrame(level, after);
        if (before.frameId() != null && oldFrame == null || after.frameId() != null && newFrame == null)
            return null;
        if (java.util.Objects.equals(before.frameId(), after.frameId())) return Vec3.ZERO;

        Vector3d oldVelocity = pointVelocity(level, oldFrame, before);
        Vector3d newVelocity = pointVelocity(level, newFrame, after);
        if (oldVelocity == null || newVelocity == null) return null;
        Vec3 delta = new Vec3(oldVelocity.x - newVelocity.x,
                oldVelocity.y - newVelocity.y, oldVelocity.z - newVelocity.z);
        return finite(delta) ? delta : null;
    }

    private static SubLevel liveFrame(Level level, LoadedFrameRayQuery.ActorPoint point) {
        if (point.frameId() == null) return null;
        SubLevel current = SubLevelContainer.getContainer(level).getSubLevel(point.frameId());
        if (current == null || current.isRemoved() || current.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(point.frameId()) != current
                || current.getPlot() == null
                || LoadedFrameRayQuery.projectActorPoint(level, point.frameId(), point.storagePoint()) == null) return null;
        return current;
    }

    /** Validated carrier velocity at the exact captured world/storage point, in SDK m/s. */
    public static Vec3 pointVelocityAt(Level level, LoadedFrameRayQuery.ActorPoint point) {
        if (level == null || !validPoint(point)) return null;
        SubLevel frame = liveFrame(level, point);
        if (point.frameId() != null && frame == null) return null;
        Vector3d velocity = pointVelocity(level, frame, point);
        if (velocity == null) return null;
        Vec3 result = new Vec3(velocity.x, velocity.y, velocity.z);
        return finite(result) ? result : null;
    }

    private static Vector3d pointVelocity(Level level, SubLevel frame,
                                          LoadedFrameRayQuery.ActorPoint point) {
        if (point.frameId() == null) return new Vector3d();
        if (frame == null) return null;
        var temporal = OwnedFrameSimulationScope.activeFor(level);
        if (temporal != null) {
            var motion=temporal.requireMotionTo(level,frame);
            Vec3 linear=motion.linearVelocity(); Vec3 angular=motion.angularVelocity();
            if(linear==null||angular==null) return null;
            var pose=temporal.to(frame); Vec3 center=pose.transformPosition(new Vec3(pose.rotationPoint().x(),pose.rotationPoint().y(),pose.rotationPoint().z()));
            Vec3 r=point.worldPoint().subtract(center);
            Vec3 result=linear.add(angular.cross(r));
            return finite(result)?new Vector3d(result.x,result.y,result.z):null;
        }
        Vector3d velocity = new Vector3d();
        Sable.HELPER.getVelocity(level, frame,
                new Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z), velocity);
        return Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                ? velocity : null;
    }

    private static boolean validPoint(LoadedFrameRayQuery.ActorPoint point) {
        return point != null && finite(point.worldPoint()) && finite(point.storagePoint());
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
