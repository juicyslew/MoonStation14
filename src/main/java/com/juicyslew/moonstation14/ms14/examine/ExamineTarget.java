package com.juicyslew.moonstation14.ms14.examine;

import java.util.Objects;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;

/** Stable network identity for a selectable world root. */
public final class ExamineTarget {
    public enum Kind {
        BLOCK, ENTITY, SCENE, ITEM, STORED_ITEM;

        /** Whether this target kind owns live world-space outline geometry. */
        public boolean hasWorldGeometry() {
            return switch (this) {
                case BLOCK, ENTITY -> true;
                case SCENE, ITEM, STORED_ITEM -> false;
            };
        }
    }
    /** Exact source identity for an item inside the controlled body's LiveHands owner. */
    public record OwnedItem(LiveOwnedItemAccess.Location location, String slotId, long revision, String token) {
        public OwnedItem {
            Objects.requireNonNull(location); Objects.requireNonNull(slotId); Objects.requireNonNull(token);
            if (slotId.isBlank() || token.isBlank() || revision < 0) throw new IllegalArgumentException("invalid owned item binding");
        }
        LiveOwnedItemAccess.Binding binding() { return new LiveOwnedItemAccess.Binding(location, slotId, revision, token); }
    }
    /** A child of a body-owned grid. worldSession is reserved for the existing live world-grid lease. */
    public record StoredItem(boolean worldSession, LiveOwnedItemAccess.Location location, String hostId,
                             long revision, String hostToken, String childToken, UUID hostUuid,
                             UUID sessionNonce, long hostRevision) {
        public StoredItem {
            Objects.requireNonNull(location); Objects.requireNonNull(hostId); Objects.requireNonNull(hostToken);
            Objects.requireNonNull(childToken);
            if (hostId.isBlank() || hostId.length() > 64 || hostToken.isBlank() || hostToken.length() > 128
                    || childToken.isBlank() || childToken.length() > 128 || revision < 0 || hostRevision < 0
                    || worldSession != (hostUuid != null && sessionNonce != null)
                    || worldSession && !hostToken.equals(sessionNonce.toString()))
                throw new IllegalArgumentException("invalid stored item binding");
        }
    }
    private final Kind kind;
    private final BlockPos blockPos;
    private final int entityId;
    private final UUID entityUuid;
    private final ResourceLocation sceneId;
    private final UUID frameId;
    private final OwnedItem ownedItem;
    private final StoredItem storedItem;

    private ExamineTarget(Kind kind, BlockPos blockPos, int entityId, UUID entityUuid, ResourceLocation sceneId) {
        this(kind, blockPos, entityId, entityUuid, sceneId, null, null, null);
    }
    private ExamineTarget(Kind kind, BlockPos blockPos, int entityId, UUID entityUuid, ResourceLocation sceneId,
                          OwnedItem ownedItem, StoredItem storedItem) {
        this(kind, blockPos, entityId, entityUuid, sceneId, null, ownedItem, storedItem);
    }
    private ExamineTarget(Kind kind, BlockPos blockPos, int entityId, UUID entityUuid, ResourceLocation sceneId,
                          UUID frameId, OwnedItem ownedItem, StoredItem storedItem) {
        this.kind = kind;
        this.blockPos = blockPos;
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.sceneId = sceneId;
        this.frameId = frameId;
        this.ownedItem = ownedItem;
        this.storedItem = storedItem;
    }
    public static ExamineTarget block(BlockPos pos) {
        return new ExamineTarget(Kind.BLOCK, Objects.requireNonNull(pos).immutable(), 0, null, null, null, null, null);
    }
    /** Exact native Sable frame plus global storage block identity. */
    public static ExamineTarget frameBlock(LoadedFrameRayQuery.Hit hit) {
        Objects.requireNonNull(hit);
        return frameBlock(hit.frameId(), hit.storagePos());
    }
    public static ExamineTarget frameBlock(UUID frameId, BlockPos storagePos) {
        return new ExamineTarget(Kind.BLOCK, Objects.requireNonNull(storagePos).immutable(), 0, null, null,
                Objects.requireNonNull(frameId), null, null);
    }
    public static ExamineTarget entity(Entity entity) {
        Objects.requireNonNull(entity);
        return entity(entity.getId(), entity.getUUID());
    }
    public static ExamineTarget entity(int entityId, UUID uuid) {
        return new ExamineTarget(Kind.ENTITY, null, entityId, Objects.requireNonNull(uuid), null, null, null, null);
    }
    public static ExamineTarget scene(ResourceLocation id) {
        Objects.requireNonNull(id);
        if (!id.equals(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN)
                && !id.equals(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.EARTH))
            throw new IllegalArgumentException("unknown celestial scene target: " + id);
        return new ExamineTarget(Kind.SCENE, null, 0, null, id, null, null, null);
    }
    public static ExamineTarget ownedItem(LiveOwnedItemAccess.Location location, String slotId, long revision, String token) {
        return new ExamineTarget(Kind.ITEM, null, 0, null, null, new OwnedItem(location, slotId, revision, token), null);
    }
    public static ExamineTarget storedItem(LiveOwnedItemAccess.Location location, String hostId, long revision,
                                           String hostToken, String childToken) {
        return new ExamineTarget(Kind.STORED_ITEM, null, 0, null, null, null,
                new StoredItem(false, location, hostId, revision, hostToken, childToken, null, null, 0));
    }
    public static ExamineTarget worldStoredItem(LiveOwnedItemAccess.Location location, String hostId, long revision,
            String hostToken, String childToken, UUID hostUuid, UUID sessionNonce, long hostRevision) {
        return new ExamineTarget(Kind.STORED_ITEM, null, 0, null, null, null,
                new StoredItem(true, location, hostId, revision, hostToken, childToken, hostUuid, sessionNonce, hostRevision));
    }
    /** World-session convenience API; location is a protocol discriminator placeholder and is not an authority claim. */
    public static ExamineTarget worldStoredItem(UUID hostUuid, UUID sessionNonce, long bodyRevision,
                                                long hostRevision, String childToken) {
        Objects.requireNonNull(sessionNonce);
        return worldStoredItem(LiveOwnedItemAccess.Location.HELD, "world", bodyRevision,
                sessionNonce.toString(), childToken, hostUuid, sessionNonce, hostRevision);
    }
    public Kind kind() { return kind; }
    public BlockPos blockPos() { return blockPos; }
    public int entityId() { return entityId; }
    public UUID entityUuid() { return entityUuid; }
    public ResourceLocation sceneId() { return sceneId; }
    public UUID frameId() { return frameId; }
    public OwnedItem ownedItem() { return ownedItem; }
    public StoredItem storedItem() { return storedItem; }
    void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(kind);
        if (kind == Kind.BLOCK) { buf.writeBlockPos(blockPos); buf.writeBoolean(frameId != null); if (frameId != null) buf.writeUUID(frameId); }
        else if (kind == Kind.ENTITY) { buf.writeVarInt(entityId); buf.writeUUID(entityUuid); }
        else if (kind == Kind.SCENE) buf.writeResourceLocation(sceneId);
        else if (kind == Kind.ITEM) { buf.writeEnum(ownedItem.location()); buf.writeUtf(ownedItem.slotId(), 128); buf.writeLong(ownedItem.revision()); buf.writeUtf(ownedItem.token(), 128); }
        else {
            var v = storedItem; buf.writeBoolean(v.worldSession()); buf.writeEnum(v.location()); buf.writeUtf(v.hostId(), 64);
            buf.writeLong(v.revision()); buf.writeUtf(v.hostToken(), 128); buf.writeUtf(v.childToken(), 128);
            buf.writeBoolean(v.worldSession()); if (v.worldSession()) { buf.writeUUID(v.hostUuid()); buf.writeUUID(v.sessionNonce()); buf.writeLong(v.hostRevision()); }
        }
    }
    static ExamineTarget read(RegistryFriendlyByteBuf buf) {
        return switch (buf.readEnum(Kind.class)) {
            case BLOCK -> { BlockPos pos=buf.readBlockPos(); yield buf.readBoolean() ? frameBlock(buf.readUUID(), pos) : block(pos); }
            case ENTITY -> entity(buf.readVarInt(), buf.readUUID());
            case SCENE -> scene(buf.readResourceLocation());
            case ITEM -> ownedItem(buf.readEnum(LiveOwnedItemAccess.Location.class), buf.readUtf(128), buf.readLong(), buf.readUtf(128));
            case STORED_ITEM -> {
                boolean world = buf.readBoolean(); var location = buf.readEnum(LiveOwnedItemAccess.Location.class);
                String host = buf.readUtf(64); long revision = buf.readLong(); String hostToken = buf.readUtf(128), child = buf.readUtf(128);
                boolean hasLease = buf.readBoolean();
                if (world != hasLease) throw new IllegalArgumentException("invalid stored item lease");
                yield world ? worldStoredItem(location, host, revision, hostToken, child, buf.readUUID(), buf.readUUID(), buf.readLong())
                        : storedItem(location, host, revision, hostToken, child);
            }
        };
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ExamineTarget target) || kind != target.kind) return false;
        return switch (kind) {
            case BLOCK -> blockPos.equals(target.blockPos) && Objects.equals(frameId, target.frameId);
            case ENTITY -> entityId == target.entityId && entityUuid.equals(target.entityUuid);
            case SCENE -> sceneId.equals(target.sceneId);
            case ITEM -> ownedItem.equals(target.ownedItem);
            case STORED_ITEM -> storedItem.equals(target.storedItem);
        };
    }
    @Override public int hashCode() { return switch (kind) {
        case BLOCK -> Objects.hash(kind, blockPos, frameId);
        case ENTITY -> Objects.hash(kind, entityId, entityUuid);
        case SCENE -> Objects.hash(kind, sceneId);
        case ITEM -> Objects.hash(kind, ownedItem);
        case STORED_ITEM -> Objects.hash(kind, storedItem);
    }; }
    @Override public String toString() {
        return switch (kind) {
            case BLOCK -> "BLOCK[" + (frameId == null ? "world:" : frameId + ":") + blockPos + "]";
            case ENTITY -> "ENTITY[id=" + entityId + ", uuid=" + entityUuid + "]";
            case SCENE -> "SCENE[" + sceneId + "]";
            case ITEM -> "ITEM[" + ownedItem + "]";
            case STORED_ITEM -> "STORED_ITEM[" + storedItem + "]";
        };
    }
}
