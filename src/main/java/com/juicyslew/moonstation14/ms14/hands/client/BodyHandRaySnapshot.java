package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A single rendered-camera ray and its captured world/frame target. */
public final class BodyHandRaySnapshot {
    private BodyHandRaySnapshot() { }

    public record Snapshot(Vec3 from, Vec3 direction, Vec3 to, float partialTick, LoadedFrameRayQuery.Result result) { }

    public static Snapshot capture(Level level, Mob body, Camera camera, long epoch,
                                   long committedEpoch, double reach,
                                   double margin, int chunkMargin) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (level == null || body == null || camera == null || !camera.isInitialized()
                || body.level() != level || epoch <= 0 || epoch != committedEpoch
                || mc.getCameraEntity() != body || camera.getEntity() != body
                || !Double.isFinite(reach) || reach <= 0) return null;
        float partialTick = camera.getPartialTickTime();
        if (!Float.isFinite(partialTick) || partialTick < 0 || partialTick > 1) return null;
        Vec3 from = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vec3 direction = new Vec3(look.x, look.y, look.z);
        if (!finite(from) || !finite(direction)) return null;
        double length = direction.length();
        if (!Double.isFinite(length) || length < 1.0e-8) return null;
        direction = direction.scale(1.0 / length);
        Vec3 end = from.add(direction.scale(reach));
        if (!finite(end)) return null;
        var result = LoadedFrameRayQuery.clip(level, from, end,
                com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_CHUNKS,
                margin, chunkMargin, partialTick, (getter, rayFrom, rayTo) -> new ClipContext(
                        rayFrom, rayTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        if (result.state() == LoadedFrameRayQuery.State.UNKNOWN) return null;
        return new Snapshot(from, direction, end, partialTick, result);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
