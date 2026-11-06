package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

/** Captured coordinate: world-space when unframed, absolute storage-space when framed. */
public record CapturedHandTarget(Vec3 point, UUID frameId, BlockPos frameCell) {
    /** Wire order: xyz doubles, frame-present boolean, then UUID and cell position when framed. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedHandTarget> STREAM_CODEC = StreamCodec.of(
            (buf, target) -> {
                buf.writeDouble(target.point.x); buf.writeDouble(target.point.y); buf.writeDouble(target.point.z);
                buf.writeBoolean(target.frameId != null);
                if (target.frameId != null) { buf.writeUUID(target.frameId); buf.writeBlockPos(target.frameCell); }
            }, buf -> {
                Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                UUID frameId = buf.readBoolean() ? buf.readUUID() : null;
                BlockPos cell = frameId == null ? null : buf.readBlockPos();
                return new CapturedHandTarget(point, frameId, cell);
            });
    public CapturedHandTarget {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
            throw new IllegalArgumentException("point must be finite");
        point = new Vec3(point.x, point.y, point.z);
        if ((frameId == null) != (frameCell == null)) throw new IllegalArgumentException("frame identity and cell must pair");
        if (frameCell != null) frameCell = frameCell.immutable();
    }

    public static CapturedHandTarget world(Vec3 worldPoint) { return new CapturedHandTarget(worldPoint, null, null); }
    public static CapturedHandTarget frame(Vec3 storagePoint, UUID frameId, BlockPos hitCell) {
        return new CapturedHandTarget(storagePoint, Objects.requireNonNull(frameId), Objects.requireNonNull(hitCell));
    }
}
