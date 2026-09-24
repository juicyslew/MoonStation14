package com.juicyslew.moonstation14.ms14.activity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Runtime-only scheduling flags derived from authoritative entity attachments. */
public final class EntityActivityAttachment {
    private final EnumSet<EntityActivity> active = EnumSet.noneOf(EntityActivity.class);

    public EntityActivityAttachment() {
    }

    public EntityActivityAttachment(Collection<EntityActivity> active) {
        Objects.requireNonNull(active, "active").forEach(this::enable);
    }

    public boolean isActive(EntityActivity activity) {
        return active.contains(Objects.requireNonNull(activity, "activity"));
    }

    /** Returns an immutable snapshot which cannot mutate the attachment. */
    public Set<EntityActivity> snapshot() {
        return Set.copyOf(active);
    }

    public boolean enable(EntityActivity activity) {
        return active.add(Objects.requireNonNull(activity, "activity"));
    }

    public boolean disable(EntityActivity activity) {
        return active.remove(Objects.requireNonNull(activity, "activity"));
    }

    public boolean setActive(EntityActivity activity, boolean enabled) {
        return enabled ? enable(activity) : disable(activity);
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }
}
