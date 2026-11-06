package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.UUID;

/** Narrow diagnostic read of Sable's ordinary held frame payload; never restores state. */
public final class OfflineFlightPayload {
    private OfflineFlightPayload() { }

    public static Sample read(ServerLevel level, UUID expected) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap map = container == null ? null : container.getHoldingChunkMap();
        HoldingSubLevel held = map == null ? null : map.getHoldingSubLevel(expected);
        SubLevelData data = held == null ? null : held.data();
        CompoundTag saved = data == null ? null : data.fullTag();
        return validate(expected, saved);
    }

    static Sample validate(UUID expected, CompoundTag saved) {
        if (expected == null) return Sample.invalid("expected uuid missing", null, null, null, null);
        if (saved == null) return Sample.invalid("ordinary holding payload missing", null, null, null, null);
        if (!saved.hasUUID("uuid") || !expected.equals(saved.getUUID("uuid")))
            return Sample.invalid("saved uuid missing/mismatch", null, null, null, null);
        if (!saved.contains("pose", Tag.TAG_COMPOUND)) return Sample.invalid("saved pose missing", null, null, null, null);
        CompoundTag poseTag = saved.getCompound("pose");
        if (!poseParts(poseTag)) return Sample.invalid("saved pose fields/types missing", saved.getUUID("uuid"), null, null, null);
        var decodedPose = SableNBTUtils.readPose3d(poseTag);
        CompoundTag position = poseTag.getCompound("position");
        CompoundTag orientation = poseTag.getCompound("orientation");
        CompoundTag rotationPoint = poseTag.getCompound("rotation_point");
        if (decodedPose == null || !finite(position, "x", "y", "z")
                || !finite(rotationPoint, "x", "y", "z") || !finite(orientation, "x", "y", "z", "w"))
            return Sample.invalid("saved pose nonfinite", saved.getUUID("uuid"), poseTag, null, null);
        double qx=orientation.getDouble("x"), qy=orientation.getDouble("y"), qz=orientation.getDouble("z"), qw=orientation.getDouble("w");
        double qnorm=qx*qx+qy*qy+qz*qz+qw*qw;
        if (!Double.isFinite(qnorm) || qnorm < 1.0e-12)
            return Sample.invalid("saved pose quaternion degenerate", saved.getUUID("uuid"), poseTag, null, null);
        if (!saved.contains("linear_velocity", Tag.TAG_COMPOUND))
            return Sample.invalid("saved linear velocity missing", saved.getUUID("uuid"), poseTag, null, null);
        CompoundTag linearTag=saved.getCompound("linear_velocity");
        if (!vectorFields(linearTag)) return Sample.invalid("saved linear velocity coordinates missing", saved.getUUID("uuid"), poseTag, null, null);
        Vector3d linear=SableNBTUtils.readVector3d(linearTag);
        if (linear == null || !finite(linear)) return Sample.invalid("saved linear velocity nonfinite", saved.getUUID("uuid"), poseTag, linear, null);
        Vector3d angular=null;
        if (saved.contains("angular_velocity")) {
            if (!saved.contains("angular_velocity", Tag.TAG_COMPOUND))
                return Sample.invalid("saved angular velocity has wrong type", saved.getUUID("uuid"), poseTag, linear, null);
            CompoundTag angularTag=saved.getCompound("angular_velocity");
            if (!vectorFields(angularTag)) return Sample.invalid("saved angular velocity coordinates missing", saved.getUUID("uuid"), poseTag, linear, null);
            angular=SableNBTUtils.readVector3d(angularTag);
            if (angular == null || !finite(angular)) return Sample.invalid("saved angular velocity nonfinite", saved.getUUID("uuid"), poseTag, linear, angular);
        }
        CompoundTag poseCopy=poseTag.copy();
        if (linear.y <= 1) return Sample.invalid("saved upward velocity insufficient", saved.getUUID("uuid"), poseCopy, linear, angular);
        return new Sample(true, "valid", saved.getUUID("uuid"), poseCopy, linear, angular);
    }

    private static boolean poseParts(CompoundTag pose) {
        return pose.contains("position",Tag.TAG_COMPOUND) && pose.contains("orientation",Tag.TAG_COMPOUND)
                && pose.contains("rotation_point",Tag.TAG_COMPOUND)
                && finite(pose.getCompound("position"),"x","y","z")
                && finite(pose.getCompound("orientation"),"x","y","z","w")
                && finite(pose.getCompound("rotation_point"),"x","y","z");
    }
    private static boolean vectorFields(CompoundTag tag) { return finite(tag,"x","y","z"); }
    private static boolean finite(CompoundTag tag,String... keys) {
        for (String key:keys) if (!tag.contains(key,Tag.TAG_DOUBLE) || !Double.isFinite(tag.getDouble(key))) return false;
        return true;
    }
    private static boolean finite(Vector3d v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }

    public record Sample(boolean valid, String reason, UUID uuid, CompoundTag pose, Vector3d linear, Vector3d angular) {
        public Sample { pose=pose==null?null:pose.copy(); linear=linear==null?null:new Vector3d(linear); angular=angular==null?null:new Vector3d(angular); }
        @Override public CompoundTag pose() { return pose==null?null:pose.copy(); }
        @Override public Vector3d linear() { return linear==null?null:new Vector3d(linear); }
        @Override public Vector3d angular() { return angular==null?null:new Vector3d(angular); }
        static Sample invalid(String reason,UUID uuid,CompoundTag pose,Vector3d linear,Vector3d angular) {
            return new Sample(false,reason,uuid,pose,linear,angular);
        }
    }
}
