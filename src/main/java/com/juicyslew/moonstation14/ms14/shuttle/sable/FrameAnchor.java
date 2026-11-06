package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Authoritative storage-space transform retained while a reconstructed actor awaits its exact frame. */
public record FrameAnchor(UUID frameId, Vec3 storagePoint) {
    public static final String KEY = "SableFrameAnchor";
    public FrameAnchor {
        if (frameId == null || storagePoint == null || !finite(storagePoint))
            throw new IllegalArgumentException("invalid frame anchor");
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Frame", frameId);
        tag.putDouble("X", storagePoint.x); tag.putDouble("Y", storagePoint.y); tag.putDouble("Z", storagePoint.z);
        return tag;
    }
    /** Empty means no authoritative anchor; malformed-present data remains distinguishable as invalid. */
    public static Decode load(CompoundTag entity) {
        if (entity == null || !entity.contains(KEY)) return new Decode(false, null, false, "");
        if (!(entity.get(KEY) instanceof CompoundTag tag)) return new Decode(true, null, true, "invalid-anchor-compound");
        if ("UNRESOLVED".equals(tag.getString("Status"))) return new Decode(true, null, true,
                tag.getString("Reason").isBlank() ? "saved-unresolved" : tag.getString("Reason").substring(0, Math.min(64, tag.getString("Reason").length())));
        if (!tag.hasUUID("Frame")
                || !tag.contains("X", CompoundTag.TAG_DOUBLE) || !tag.contains("Y", CompoundTag.TAG_DOUBLE)
                || !tag.contains("Z", CompoundTag.TAG_DOUBLE)) return new Decode(true, null, true, "invalid-anchor-fields");
        Vec3 point = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        if (!finite(point)) return new Decode(true, null, true, "nonfinite-anchor-point");
        return new Decode(true, new FrameAnchor(tag.getUUID("Frame"), point), false, "");
    }
    public record Decode(boolean present, FrameAnchor anchor, boolean invalid, String reason) { }
    /** Performs the one deserialization placement; false leaves the dependency pending without motion. */
    public static boolean hydrate(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity actor, FrameAnchor anchor) {
        return hydrate(level, actor, anchor, false);
    }

    /** Living movement entry reconstructs at the SDK's previous interval pose; cargo retains current-pose semantics. */
    public static boolean hydrateForOwnedMovement(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.Entity actor, FrameAnchor anchor) {
        return hydrate(level, actor, anchor, true);
    }

    private static boolean hydrate(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity actor,
                                   FrameAnchor anchor, boolean movementEntry) {
        if (level == null || actor == null || actor.level() != level || actor.isRemoved()
                || !(actor instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement)) return false;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return false;
        var sub = container.getSubLevel(anchor.frameId());
        if (sub == null || sub.isRemoved() || sub.getLevel() != level || sub.getPlot() == null
                || container.getSubLevel(anchor.frameId()) != sub || sub.getUniqueId() == null) return false;
        var pose = movementEntry ? sub.lastPose() : null;
        Vec3 world = movementEntry
                ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .projectActorPointForOwnedMovement(level, anchor.frameId(), anchor.storagePoint(), pose)
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .projectActorPoint(level, anchor.frameId(), anchor.storagePoint());
        if (world == null || !level.getWorldBorder().isWithinBounds(world)) return false;
        var worldBlock = net.minecraft.core.BlockPos.containing(world);
        var worldChunk = level.getChunkSource().getChunkNow(worldBlock.getX() >> 4, worldBlock.getZ() >> 4);
        if (worldChunk == null || !worldChunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) return false;
        actor.setPos(world);
        movement.sable$setTrackingSubLevel(sub);
        return true;
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
