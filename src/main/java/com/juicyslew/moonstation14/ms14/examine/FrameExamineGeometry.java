package com.juicyslew.moonstation14.ms14.examine;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Native-frame outline geometry, kept in stable storage coordinates until render time. */
public final class FrameExamineGeometry {
    private FrameExamineGeometry() { }

    public static AABB localBounds(Level level, ExamineTarget target) {
        if (level == null || target == null || target.frameId() == null) return null;
        var sub = SubLevelContainer.getContainer(level).getSubLevel(target.frameId());
        if (!(sub instanceof ClientSubLevel) || sub.isRemoved() || sub.getLevel() != level) return null;
        BlockPos pos = target.blockPos();
        var state = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.knownClientBlock(level, sub, pos);
        if (state == null || state.isAir()) return null;
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            BlockPos root = ExamineWorldSelector.canonicalRoot(state, pos,
                    candidate -> com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownClientBlock(level, sub, candidate));
            if (state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER && root.equals(pos)) return null;
            var rootState = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.knownClientBlock(level, sub, root);
            if (rootState == null) return null;
            return ExamineWorldSelector.airlockLocalBounds(sub.getLevel(), root, rootState,
                    com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.knownBlockEntity(level, sub, root));
        }
        var shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    public static dev.ryanhcode.sable.companion.math.Pose3dc renderPose(Level level, ExamineTarget target,
                                                                        float partialTick) {
        if (level == null || target == null || target.frameId() == null) return null;
        var sub = SubLevelContainer.getContainer(level).getSubLevel(target.frameId());
        return sub instanceof ClientSubLevel && !sub.isRemoved() && sub.getLevel() == level
                ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.renderPose(sub, partialTick) : null;
    }

    public static AABB worldBounds(Level level, ExamineTarget target, float partialTick) {
        AABB local = localBounds(level, target);
        var pose = renderPose(level, target, partialTick);
        return transformedBounds(local, pose);
    }

    public static AABB transformedBounds(AABB local, dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        return pose == null ? null : FrameBoundsTransform.transformedBounds(local,
                pose.bakeIntoMatrix(new org.joml.Matrix4d()));
    }

    public static AABB transformedBounds(AABB local, org.joml.Matrix4dc matrix) {
        return FrameBoundsTransform.transformedBounds(local, matrix);
    }

    /** Builds a camera-relative float matrix and small local box, avoiding float conversion of global storage coordinates. */
    public static CameraRelativeOutline cameraRelativeOutline(AABB local,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, net.minecraft.world.phys.Vec3 camera) {
        if (local == null || pose == null || camera == null) return null;
        double ax = (local.minX + local.maxX) * 0.5;
        double ay = (local.minY + local.maxY) * 0.5;
        double az = (local.minZ + local.maxZ) * 0.5;
        var nativeMatrix = pose.bakeIntoMatrix(new org.joml.Matrix4d());
        var anchor = nativeMatrix.transformPosition(new org.joml.Vector3d(ax, ay, az));
        nativeMatrix.m30(0).m31(0).m32(0);
        var matrix = new org.joml.Matrix4f().set(nativeMatrix);
        matrix.m30((float)(anchor.x - camera.x));
        matrix.m31((float)(anchor.y - camera.y));
        matrix.m32((float)(anchor.z - camera.z));
        return new CameraRelativeOutline(matrix,
                new AABB(local.minX-ax, local.minY-ay, local.minZ-az,
                        local.maxX-ax, local.maxY-ay, local.maxZ-az));
    }

    public record CameraRelativeOutline(org.joml.Matrix4f matrix, AABB localBounds) { }
}
