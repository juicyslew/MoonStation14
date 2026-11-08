package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Attachment contact is stored as a native-root model point; direction is normalized model space. */
public record ItemAttachment(UUID targetUUID, int targetEntityId, Vec3 targetLocalContact,
                             Vec3 targetLocalDirection, AttachmentKind kind, long attachedAtTick) {
    public enum AttachmentKind { EMBEDDED, ENSNARED }
    private static final String PREFIX = "LooseAttachment";

    public void writeTo(CompoundTag tag) {
        tag.putUUID(PREFIX + "Target", targetUUID);
        tag.putInt(PREFIX + "TargetId", targetEntityId);
        tag.putInt(PREFIX + "Kind", kind.ordinal());
        tag.putLong(PREFIX + "AttachedAtTick", attachedAtTick);
        putVector(tag, PREFIX + "Contact", targetLocalContact);
        putVector(tag, PREFIX + "Direction", targetLocalDirection);
    }

    public static java.util.Optional<ItemAttachment> readFrom(CompoundTag tag) {
        String targetKey = PREFIX + "Target";
        String idKey = PREFIX + "TargetId";
        String kindKey = PREFIX + "Kind";
        if (!tag.hasUUID(targetKey) || !tag.contains(idKey, Tag.TAG_INT) || !tag.contains(kindKey, Tag.TAG_INT))
            return java.util.Optional.empty();
        Vec3 contact = readVector(tag, PREFIX + "Contact");
        Vec3 direction = readVector(tag, PREFIX + "Direction");
        int ordinal = tag.getInt(kindKey);
        AttachmentKind[] kinds = AttachmentKind.values();
        if (contact == null || direction == null || ordinal < 0 || ordinal >= kinds.length) return java.util.Optional.empty();
        long tick = tag.contains(PREFIX + "AttachedAtTick", Tag.TAG_LONG) ? tag.getLong(PREFIX + "AttachedAtTick") : 0;
        if (tick < 0) return java.util.Optional.empty();
        return java.util.Optional.of(new ItemAttachment(tag.getUUID(targetKey), tag.getInt(idKey), contact, direction, kinds[ordinal], tick));
    }

    private static void putVector(CompoundTag tag, String key, Vec3 vector) {
        tag.putFloat(key + "X", (float) vector.x);
        tag.putFloat(key + "Y", (float) vector.y);
        tag.putFloat(key + "Z", (float) vector.z);
    }

    private static Vec3 readVector(CompoundTag tag, String key) {
        String x = key + "X", y = key + "Y", z = key + "Z";
        if (!tag.contains(x, Tag.TAG_FLOAT) || !tag.contains(y, Tag.TAG_FLOAT) || !tag.contains(z, Tag.TAG_FLOAT)) return null;
        float fx = tag.getFloat(x), fy = tag.getFloat(y), fz = tag.getFloat(z);
        if (!Float.isFinite(fx) || !Float.isFinite(fy) || !Float.isFinite(fz)) return null;
        return new Vec3(fx, fy, fz);
    }

    public ItemAttachment {
        java.util.Objects.requireNonNull(targetUUID);
        java.util.Objects.requireNonNull(targetLocalContact);
        java.util.Objects.requireNonNull(targetLocalDirection);
        java.util.Objects.requireNonNull(kind);
        if (attachedAtTick < 0) throw new IllegalArgumentException("attachedAtTick must be nonnegative");
    }

    /** Compatibility constructor for historical callers and persisted pre-timestamp attachments. */
    public ItemAttachment(UUID targetUUID, int targetEntityId, Vec3 targetLocalContact,
                          Vec3 targetLocalDirection, AttachmentKind kind) {
        this(targetUUID, targetEntityId, targetLocalContact, targetLocalDirection, kind, 0);
    }
}
