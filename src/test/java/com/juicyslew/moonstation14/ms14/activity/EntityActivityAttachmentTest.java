package com.juicyslew.moonstation14.ms14.activity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityActivityAttachmentTest {
    @Test
    void transitionsAndSnapshotsAreIndependent() {
        EntityActivityAttachment attachment = new EntityActivityAttachment();
        assertTrue(attachment.isEmpty());
        assertTrue(attachment.enable(EntityActivity.STATUS_EFFECT));
        assertFalse(attachment.enable(EntityActivity.STATUS_EFFECT));
        assertTrue(attachment.isActive(EntityActivity.STATUS_EFFECT));

        Set<EntityActivity> snapshot = attachment.snapshot();
        assertEquals(Set.of(EntityActivity.STATUS_EFFECT), snapshot);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.remove(EntityActivity.STATUS_EFFECT));
        assertTrue(attachment.isActive(EntityActivity.STATUS_EFFECT));

        assertTrue(attachment.setActive(EntityActivity.REAGENT_METABOLISM, true));
        assertTrue(attachment.disable(EntityActivity.STATUS_EFFECT));
        assertTrue(attachment.disable(EntityActivity.REAGENT_METABOLISM));
        assertTrue(attachment.isEmpty());
    }
}
