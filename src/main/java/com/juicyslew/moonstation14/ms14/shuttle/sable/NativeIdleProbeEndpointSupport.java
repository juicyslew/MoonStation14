package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedBlockGetter;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/** Conservative same-frame floor support check for the artificial idle probe. */
public final class NativeIdleProbeEndpointSupport {
    private NativeIdleProbeEndpointSupport() { }

    public static boolean supports(Level level, Entity entity, AABB candidate, NativeActorYaw yaw,
            SubLevel frame, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace) {
        if (level == null || entity == null || entity.level() != level || entity.isRemoved() || candidate == null
                || yaw == null || frame == null || trace == null || trace.isEmpty() || frame.isRemoved()
                || frame.getLevel() != level || frame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame) return false;
        var pose = frame.logicalPose();
        if (!FrameBoxIntersection.supportsPose(pose)) return false;
        try {
            java.util.ArrayList<AABB> selected = new java.util.ArrayList<>();
            var plot = frame.getPlot();
            var getter = level instanceof ServerLevel server
                    ? LoadedBlockGetter.forFullFrame(server, plot, new BoundingBox3d(plot.getBoundingBox()).toMojang(), 16)
                    : LoadedBlockGetter.clientForFrame(level, plot, new BoundingBox3d(plot.getBoundingBox()).toMojang(), 16);
            var query = new NativeFrameShapeQuery(level, entity, pose, getter, candidate);
            Quaterniond orientation = new Quaterniond(yaw.orientation());
            boolean touched = false;
            for (var row : trace) {
                if (row == null || !frame.getUniqueId().equals(row.frameId()) || row.selectedPos() == null
                        || row.selectedState() == null || row.geometry() == null) return false;
                BlockPos pos = row.selectedPos();
                var state = getter.getBlockState(pos);
                if (!state.equals(row.selectedState())) return false;
                var shapes = query.shape(pos, state).toAabbs();
                AABB captured = row.geometry().selectedStorageBounds();
                if (!valid(captured) || captured.minX < pos.getX()-NativeCollisionSlop.MAX_RESIDUAL
                        || captured.maxX > pos.getX()+1+NativeCollisionSlop.MAX_RESIDUAL
                        || captured.minY < pos.getY()-NativeCollisionSlop.MAX_RESIDUAL
                        || captured.maxY > pos.getY()+1+NativeCollisionSlop.MAX_RESIDUAL
                        || captured.minZ < pos.getZ()-NativeCollisionSlop.MAX_RESIDUAL
                        || captured.maxZ > pos.getZ()+1+NativeCollisionSlop.MAX_RESIDUAL) return false;
                boolean matched = false;
                for (AABB shape : shapes) {
                    AABB bounds = shape.move(pos);
                    if (!valid(bounds)) return false;
                    if (sameBounds(bounds, captured)) { selected.add(bounds); matched = true; }
                }
                if (!matched) return false;
            }
            return geometricSupport(candidate, yaw, pose, selected);
        } catch (RuntimeException unknown) { return false; }
    }

    static boolean geometricSupport(AABB candidate, NativeActorYaw yaw,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, List<AABB> floors) {
        if (!valid(candidate) || yaw == null || !FrameBoxIntersection.supportsPose(pose) || floors == null || floors.isEmpty()) return false;
        double top = Double.NaN;
        for (AABB floor : floors) {
            if (!valid(floor)) return false;
            if (Double.isNaN(top)) top = floor.maxY;
            else if (Math.abs(top-floor.maxY)>NativeCollisionSlop.MAX_RESIDUAL) return false;
        }
        var local = inverseBounds(candidate, yaw.orientation(), pose);
        if (local == null) return false;
        Vector3d normal = new Vector3d(0,1,0);
        pose.orientation().transform(normal);
        double eps = NativeCollisionSlop.MAX_RESIDUAL;
        if (!(normal.y > 0) || local.minY < top-eps
                || local.minY-top > CharacterMovementMotor.GROUNDED_SUPPORT_PROBE_BLOCKS*normal.y+eps) return false;
        Quaterniond orientation = new Quaterniond(yaw.orientation());
        boolean touch=false;
        for (AABB floor:floors) {
            if (FrameBoxIntersection.intersects(candidate,orientation,floor,pose)) return false;
            touch |= FrameBoxIntersection.intersects(candidate.move(0,-CharacterMovementMotor.GROUNDED_SUPPORT_PROBE_BLOCKS,0),orientation,floor,pose);
        }
        return touch;
    }

    private static boolean sameBounds(AABB a,AABB b) {
        double e=NativeCollisionSlop.MAX_RESIDUAL;
        return Math.abs(a.minX-b.minX)<=e&&Math.abs(a.minY-b.minY)<=e&&Math.abs(a.minZ-b.minZ)<=e
                &&Math.abs(a.maxX-b.maxX)<=e&&Math.abs(a.maxY-b.maxY)<=e&&Math.abs(a.maxZ-b.maxZ)<=e;
    }

    private static AABB inverseBounds(AABB box, org.joml.Quaterniondc yaw, dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        if (!valid(box)) return null;
        AABB result = null;
        var center = box.getCenter();
        for (int i=0;i<8;i++) {
            Vector3d v = new Vector3d((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ);
            v.sub(center.x,center.y,center.z); new Quaterniond(yaw).transform(v); v.add(center.x,center.y,center.z);
            var p = pose.transformPositionInverse(new net.minecraft.world.phys.Vec3(v.x,v.y,v.z));
            AABB point = new AABB(p.x,p.y,p.z,p.x,p.y,p.z);
            result = result == null ? point : result.minmax(point);
        }
        return result;
    }
    private static boolean valid(AABB b) { return b != null && Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ)&&b.getXsize()>0&&b.getYsize()>0&&b.getZsize()>0; }
}
