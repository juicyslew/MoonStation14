package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.neoforged.bus.api.Event;

/** Server domain notification, emitted once for a real medical-state transition. */
public final class MobStateChangedEvent extends Event {
    private final LivingEntity body;
    private final MobStateComponent.State previous;
    private final MobStateComponent.State current;

    public MobStateChangedEvent(LivingEntity body, MobStateComponent.State previous,
                                MobStateComponent.State current) {
        this.body = java.util.Objects.requireNonNull(body, "body");
        this.previous = java.util.Objects.requireNonNull(previous, "previous");
        this.current = java.util.Objects.requireNonNull(current, "current");
    }

    public LivingEntity body() {
        return body;
    }

    public MobStateComponent.State previous() {
        return previous;
    }

    public MobStateComponent.State current() {
        return current;
    }
}
