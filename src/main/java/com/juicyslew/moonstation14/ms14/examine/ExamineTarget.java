package com.juicyslew.moonstation14.ms14.examine;

import java.util.Objects;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Stable network identity for a selectable world root. */
public final class ExamineTarget {
    public enum Kind { BLOCK, ENTITY, SCENE }
    private final Kind kind;
    private final BlockPos blockPos;
    private final int entityId;
    private final UUID entityUuid;
    private final ResourceLocation sceneId;
    private final UUID frameId;

    private ExamineTarget(Kind kind, BlockPos blockPos, int entityId, UUID entityUuid, ResourceLocation sceneId, UUID frameId) {
        this.kind = kind;
        this.blockPos = blockPos;
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.sceneId = sceneId;
        this.frameId = frameId;
    }
    public static ExamineTarget block(BlockPos pos) {
        return new ExamineTarget(Kind.BLOCK, Objects.requireNonNull(pos).immutable(), 0, null, null, null);
    }
    /** Exact native Sable frame plus global storage block identity. */
    public static ExamineTarget frameBlock(LoadedFrameRayQuery.Hit hit) {
        Objects.requireNonNull(hit);
        return frameBlock(hit.frameId(), hit.storagePos());
    }
    public static ExamineTarget frameBlock(UUID frameId, BlockPos storagePos) {
        return new ExamineTarget(Kind.BLOCK, Objects.requireNonNull(storagePos).immutable(), 0, null, null,
                Objects.requireNonNull(frameId));
    }
    public static ExamineTarget entity(Entity entity) {
        Objects.requireNonNull(entity);
        return entity(entity.getId(), entity.getUUID());
    }
    public static ExamineTarget entity(int entityId, UUID uuid) {
        return new ExamineTarget(Kind.ENTITY, null, entityId, Objects.requireNonNull(uuid), null, null);
    }
    public static ExamineTarget scene(ResourceLocation id) {
        Objects.requireNonNull(id);
        if (!id.equals(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN)
                && !id.equals(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.EARTH))
            throw new IllegalArgumentException("unknown celestial scene target: " + id);
        return new ExamineTarget(Kind.SCENE, null, 0, null, id, null);
    }
    public Kind kind() { return kind; }
    public BlockPos blockPos() { return blockPos; }
    public int entityId() { return entityId; }
    public UUID entityUuid() { return entityUuid; }
    public ResourceLocation sceneId() { return sceneId; }
    public UUID frameId() { return frameId; }
    void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(kind);
        if (kind == Kind.BLOCK) { buf.writeBlockPos(blockPos); buf.writeBoolean(frameId != null); if (frameId != null) buf.writeUUID(frameId); }
        else if (kind == Kind.ENTITY) { buf.writeVarInt(entityId); buf.writeUUID(entityUuid); }
        else buf.writeResourceLocation(sceneId);
    }
    static ExamineTarget read(RegistryFriendlyByteBuf buf) {
        return switch (buf.readEnum(Kind.class)) {
            case BLOCK -> { BlockPos pos=buf.readBlockPos(); yield buf.readBoolean() ? frameBlock(buf.readUUID(), pos) : block(pos); }
            case ENTITY -> entity(buf.readVarInt(), buf.readUUID());
            case SCENE -> scene(buf.readResourceLocation());
        };
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ExamineTarget target) || kind != target.kind) return false;
        return switch (kind) {
            case BLOCK -> blockPos.equals(target.blockPos) && Objects.equals(frameId, target.frameId);
            case ENTITY -> entityId == target.entityId && entityUuid.equals(target.entityUuid);
            case SCENE -> sceneId.equals(target.sceneId);
        };
    }
    @Override public int hashCode() { return switch (kind) {
        case BLOCK -> Objects.hash(kind, blockPos, frameId);
        case ENTITY -> Objects.hash(kind, entityId, entityUuid);
        case SCENE -> Objects.hash(kind, sceneId);
    }; }
    @Override public String toString() {
        return switch (kind) {
            case BLOCK -> "BLOCK[" + (frameId == null ? "world:" : frameId + ":") + blockPos + "]";
            case ENTITY -> "ENTITY[id=" + entityId + ", uuid=" + entityUuid + "]";
            case SCENE -> "SCENE[" + sceneId + "]";
        };
    }
}
