package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/** Loaded-only, present-pose occupants of an airlock's physical closed volume. */
final class AirlockDoorwayActors {
    record Result(boolean known, List<LivingEntity> actors) { }
    private AirlockDoorwayActors() { }

    static Result query(ServerLevel level, BlockPos root, AABB doorway, boolean touching) {
        var plot = SubLevelContainer.getContainer(level).getPlot(new net.minecraft.world.level.ChunkPos(root));
        if (plot == null || !plot.contains(new net.minecraft.world.level.ChunkPos(root)))
            return world(level, doorway, touching);
        var frame = plot.getSubLevel();
        if (!(frame instanceof ServerSubLevel) || frame.isRemoved() || frame.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                || !frame.getPlot().contains(new net.minecraft.world.level.ChunkPos(root))) return new Result(false, List.of());
        var lower = LoadedFrameRayQuery.knownServerBlock(level, frame, root);
        var upper = LoadedFrameRayQuery.knownServerBlock(level, frame, root.above());
        if (lower == null || upper == null || !(lower.getBlock() instanceof AirlockBlock)
                || !(upper.getBlock() instanceof AirlockBlock)) return new Result(false, List.of());
        var poseSource = level instanceof LevelPoseProviderExtension provider
                ? provider.sable$getPose(frame) : frame.logicalPose();
        if (poseSource == null) return new Result(false, List.of());
        var pose = new dev.ryanhcode.sable.companion.math.Pose3d().set(poseSource);
        AABB broad = com.juicyslew.moonstation14.ms14.examine.FrameBoundsTransform.transformedBounds(doorway,
                pose.bakeIntoMatrix(new org.joml.Matrix4d()));
        if (broad == null || !finite(broad)) return new Result(false, List.of());
        var actors = level.getEntitiesOfClass(LivingEntity.class, broad.inflate(touching ? 1.0E-5 : 0), e -> eligible(e, level));
        var result = actors.stream().filter(actor -> {
            return intersects(doorway, pose, actor.getBoundingBox(), touching ? 1.0E-5 : 0);
        }).toList();
        return new Result(true, result);
    }

    private static Result world(ServerLevel level, AABB box, boolean touching) {
        List<LivingEntity> actors = level.getEntitiesOfClass(LivingEntity.class, touching ? box.inflate(1.0E-5) : box,
                e -> eligible(e, level) && (touching ? AirlockGeometry.touchesDoorway(e.getBoundingBox(), box) : e.getBoundingBox().intersects(box)));
        return new Result(true, actors);
    }
    private static boolean eligible(LivingEntity e, ServerLevel level) {
        return !e.isSpectator() && !e.noPhysics && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(e, level);
    }

    /** SDK-backed OBB narrowphase; all borrowed SDK scratch is private to this call. */
    static boolean intersects(AABB storageDoorway, dev.ryanhcode.sable.companion.math.Pose3dc pose,
                              AABB actorWorldBounds, double touchMargin) {
        if (storageDoorway == null || pose == null || actorWorldBounds == null || !finite(storageDoorway)
                || !finite(actorWorldBounds) || !Double.isFinite(touchMargin) || touchMargin < 0) return false;
        var sink = new LevelReusedVectors();
        Vector3d center = new Vector3d((storageDoorway.minX + storageDoorway.maxX) * .5,
                (storageDoorway.minY + storageDoorway.maxY) * .5, (storageDoorway.minZ + storageDoorway.maxZ) * .5);
        pose.transformPosition(center);
        var shape = new OrientedBoundingBox3d(center,
                new Vector3d(Math.abs(pose.scale().x()) * storageDoorway.getXsize(),
                        Math.abs(pose.scale().y()) * storageDoorway.getYsize(),
                        Math.abs(pose.scale().z()) * storageDoorway.getZsize()), pose.orientation(), sink);
        // This SDK's satToleranced argument is not a spatial skin; native contact
        // code supplies face tolerance by inflating the actor box before SAT.
        AABB b = touchMargin == 0 ? actorWorldBounds : actorWorldBounds.inflate(touchMargin);
        var body = new OrientedBoundingBox3d(new Vector3d((b.minX+b.maxX)*.5,(b.minY+b.maxY)*.5,(b.minZ+b.maxZ)*.5),
                new Vector3d(b.getXsize(),b.getYsize(),b.getZsize()), new Quaterniond(), sink);
        Vector3d mtv = new Vector3d(OrientedBoundingBox3d.satToleranced(body, shape, touchMargin));
        double length = mtv.lengthSquared();
        return length > 0 && Double.isFinite(length);
    }
    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);
    }
}
