package com.juicyslew.moonstation14.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemAttachmentPersistenceTest {
    @Test void roundTripsBothKindsAndAsymmetricPose() {
        for (var kind : ItemAttachment.AttachmentKind.values()) {
            var value = new ItemAttachment(UUID.randomUUID(), 817, new Vec3(.125, -2.75, 3.5),
                    new Vec3(-.625, .875, 1.25), kind, 12345);
            var tag = new CompoundTag();
            value.writeTo(tag);
            var actual = ItemAttachment.readFrom(tag).orElseThrow();
            assertEquals(value.targetUUID(), actual.targetUUID());
            assertEquals(value.targetEntityId(), actual.targetEntityId());
            assertEquals(value.kind(), actual.kind());
            assertEquals(value.targetLocalContact(), actual.targetLocalContact());
            assertEquals(value.targetLocalDirection(), actual.targetLocalDirection());
            assertEquals(12345, actual.attachedAtTick());
        }
    }

    @Test void supportsLegacyTickDefaultAndRejectsNegativeTimestamp() {
        var old = new ItemAttachment(UUID.randomUUID(), 3, Vec3.ZERO, Vec3.ZERO, ItemAttachment.AttachmentKind.EMBEDDED);
        assertEquals(0, old.attachedAtTick());
        var tag = new CompoundTag(); old.writeTo(tag);
        assertEquals(0, ItemAttachment.readFrom(tag).orElseThrow().attachedAtTick());
        tag.putLong("LooseAttachmentAttachedAtTick", -1);
        assertTrue(ItemAttachment.readFrom(tag).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ItemAttachment(UUID.randomUUID(), 3, Vec3.ZERO, Vec3.ZERO,
                ItemAttachment.AttachmentKind.EMBEDDED, -1));
    }

    @Test void rejectsIncompleteMalformedAndLegacyCompoundShape() {
        var source = new ItemAttachment(UUID.randomUUID(), 2, new Vec3(1, 2, 3), new Vec3(4, 5, 6),
                ItemAttachment.AttachmentKind.EMBEDDED);
        var tag = new CompoundTag(); source.writeTo(tag);
        tag.remove("LooseAttachmentContactY");
        assertTrue(ItemAttachment.readFrom(tag).isEmpty());
        var legacy = new CompoundTag(); legacy.put("LooseAttachmentContact", new CompoundTag());
        assertTrue(ItemAttachment.readFrom(legacy).isEmpty());
        tag = new CompoundTag(); source.writeTo(tag); tag.putFloat("LooseAttachmentDirectionZ", Float.NaN);
        assertTrue(ItemAttachment.readFrom(tag).isEmpty());
        tag = new CompoundTag(); source.writeTo(tag); tag.putInt("LooseAttachmentKind", 99);
        assertTrue(ItemAttachment.readFrom(tag).isEmpty());
        tag = new CompoundTag(); source.writeTo(tag); tag.remove("LooseAttachmentTarget");
        assertTrue(ItemAttachment.readFrom(tag).isEmpty());
    }
}
