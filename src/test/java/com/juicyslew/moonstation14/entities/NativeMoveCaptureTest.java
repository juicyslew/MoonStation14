package com.juicyslew.moonstation14.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeMoveCaptureTest {
    @Test
    void selectedContactsAreDefensivelyCopiedAndImmutable() {
        Object collisionInfo = new Object();
        var contact = new LooseItemEntity.NativeFrameContact(collisionInfo, UUID.randomUUID(), BlockPos.ZERO,
                new Vec3(0, 1, 0), true, true, Vec3.ZERO, Vec3.ZERO, 17);
        var mutable = new ArrayList<>(List.of(contact));
        var capture = capture(collisionInfo, mutable, false);
        mutable.clear();

        assertEquals(List.of(contact), capture.selectedContacts());
        assertThrows(UnsupportedOperationException.class, () -> capture.selectedContacts().clear());
    }

    @Test
    void preservesCollisionInfoIdentityAndOverflowFlag() {
        Object current = new Object();
        var capture = capture(current, List.of(), true);

        assertSame(PREVIOUS, capture.previousCollisionInfo());
        assertSame(current, capture.currentCollisionInfo());
        assertTrue(capture.selectedContactsOverflow());
    }

    private static LooseItemEntity.NativeMoveCapture capture(Object current,
            List<LooseItemEntity.NativeFrameContact> contacts, boolean overflow) {
        Object previous = PREVIOUS;
        return new LooseItemEntity.NativeMoveCapture(1, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                previous, current, true, true, null, null, null, null, contacts, overflow);
    }

    private static final Object PREVIOUS = new Object();
}
