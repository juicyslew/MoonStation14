package com.juicyslew.moonstation14.entities;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemAttachmentQueriesTest {
    @Test void usesSynchronizedExactBodyAttachmentAndKindForStatus() {
        var level = mock(Level.class);
        var body = mock(LivingEntity.class);
        var bodyId = UUID.randomUUID();
        when(body.level()).thenReturn(level);
        when(body.getUUID()).thenReturn(bodyId);
        when(body.getId()).thenReturn(12);
        when(body.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 2, 1));

        var validEnsnare = item(level, bodyId, 12, ItemAttachment.AttachmentKind.ENSNARED);
        var embedded = item(level, bodyId, 12, ItemAttachment.AttachmentKind.EMBEDDED);
        var otherBody = item(level, UUID.randomUUID(), 12, ItemAttachment.AttachmentKind.ENSNARED);
        var staleEntityId = item(level, bodyId, 13, ItemAttachment.AttachmentKind.ENSNARED);
        var candidates = List.of(validEnsnare, embedded, otherBody, staleEntityId);
        when(level.getEntitiesOfClass(eq(LooseItemEntity.class), any(AABB.class), any()))
                .thenAnswer(invocation -> candidates.stream()
                        .filter(invocation.<java.util.function.Predicate<LooseItemEntity>>getArgument(2))
                        .toList());

        assertEquals(List.of(validEnsnare, embedded), ItemAttachmentQueries.attachedTo(body));
        assertTrue(ItemAttachmentQueries.has(body, ItemAttachment.AttachmentKind.ENSNARED));
        assertTrue(ItemAttachmentQueries.has(body, ItemAttachment.AttachmentKind.EMBEDDED));
        verify(level, times(3)).getEntitiesOfClass(eq(LooseItemEntity.class), eq(new AABB(0, 0, 0, 1, 2, 1).inflate(2)), any());
    }

    @Test void selfRemovalPrioritizesRestraintsThenNewestAttachmentAndStableUuid() {
        var level = mock(Level.class); var body = mock(LivingEntity.class); var bodyId = UUID.randomUUID();
        when(body.level()).thenReturn(level); when(body.getUUID()).thenReturn(bodyId); when(body.getId()).thenReturn(12);
        when(body.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 2, 1));
        var oldRestraint = item(level, bodyId, 12, ItemAttachment.AttachmentKind.ENSNARED, 4);
        var newDart = item(level, bodyId, 12, ItemAttachment.AttachmentKind.EMBEDDED, 900);
        var older = item(level, bodyId, 12, ItemAttachment.AttachmentKind.EMBEDDED, 8);
        var newest = item(level, bodyId, 12, ItemAttachment.AttachmentKind.EMBEDDED, 9);
        var candidates = new java.util.concurrent.atomic.AtomicReference<List<LooseItemEntity>>(List.of(oldRestraint, newDart, older, newest));
        when(level.getEntitiesOfClass(eq(LooseItemEntity.class), any(AABB.class), any()))
                .thenAnswer(i -> candidates.get().stream().filter(i.<java.util.function.Predicate<LooseItemEntity>>getArgument(2)).toList());
        assertSame(oldRestraint, ItemAttachmentQueries.selfRemovalTarget(body).orElseThrow());
        candidates.set(List.of(older, newest));
        assertSame(newest, ItemAttachmentQueries.selfRemovalTarget(body).orElseThrow());
    }

    private static LooseItemEntity item(Level level, UUID target, int id, ItemAttachment.AttachmentKind kind) {
        return item(level, target, id, kind, 0);
    }
    private static LooseItemEntity item(Level level, UUID target, int id, ItemAttachment.AttachmentKind kind, long tick) {
        var item = mock(LooseItemEntity.class);
        when(item.level()).thenReturn(level);
        var stack = mock(net.minecraft.world.item.ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(item.getItem()).thenReturn(stack);
        when(item.isRemoved()).thenReturn(false);
        when(item.attachment()).thenReturn(java.util.Optional.of(new ItemAttachment(target, id, Vec3.ZERO, Vec3.ZERO, kind, tick)));
        return item;
    }
}
