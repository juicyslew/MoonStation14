package com.juicyslew.moonstation14.entities;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;

/** Loaded, synchronized attachment queries shared by client presentation and world-item rendering. */
public final class ItemAttachmentQueries {
    private ItemAttachmentQueries() { }

    /** Returns live loose items attached to this exact body in its current level. */
    public static List<LooseItemEntity> attachedTo(LivingEntity body) {
        Objects.requireNonNull(body, "body");
        var level = body.level();
        var target = body.getUUID();
        var targetId = body.getId();
        return List.copyOf(level.getEntitiesOfClass(LooseItemEntity.class,
                body.getBoundingBox().inflate(2.0), item -> !item.isRemoved()
                        && item.level() == level && !item.getItem().isEmpty()
                        && item.attachment().filter(attachment -> attachment.targetUUID().equals(target)
                        && attachment.targetEntityId() == targetId).isPresent()));
    }

    public static boolean has(LivingEntity body, ItemAttachment.AttachmentKind kind) {
        Objects.requireNonNull(kind, "kind");
        return attachedTo(body).stream().anyMatch(item -> item.attachment()
                .filter(attachment -> attachment.kind() == kind).isPresent());
    }

    /** Ensnaring restraints precede embedded items; within each class newest attach wins. */
    public static java.util.Optional<LooseItemEntity> selfRemovalTarget(LivingEntity body) {
        return attachedTo(body).stream().min(java.util.Comparator
                .comparingInt((LooseItemEntity item) -> item.attachment().orElseThrow().kind() == ItemAttachment.AttachmentKind.ENSNARED ? 0 : 1)
                .thenComparing((a, b) -> Long.compare(b.attachment().orElseThrow().attachedAtTick(), a.attachment().orElseThrow().attachedAtTick()))
                .thenComparing(LooseItemEntity::getUUID));
    }
}
