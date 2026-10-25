package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable authoritative projection of a body's available actions. */
public record BodyActionSnapshot(UUID bodyId, long bodyEpoch, long revision, long serverGameTime, List<Entry> actions) {
    public record Entry(ResourceLocation actionId, String providerId, String displayName, ResourceLocation icon,
                        Optional<ResourceLocation> iconOn, ActionPrototype.Kind kind, boolean enabled,
                        boolean active, long cooldownEndTick, int useDelayTicks, String disabledReason, int priority) {
        public Entry {
            if (actionId == null || providerId == null || providerId.isBlank() || displayName == null || displayName.isBlank()
                    || icon == null || iconOn == null || kind == null || cooldownEndTick < 0 || useDelayTicks < 0
                    || disabledReason == null || kind == ActionPrototype.Kind.INSTANT && active)
                throw new IllegalArgumentException("Invalid body action entry");
        }
    }
    public BodyActionSnapshot {
        if (bodyId == null || bodyEpoch <= 0 || revision < 0 || serverGameTime < 0 || actions == null
                || actions.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Invalid body action snapshot");
        actions = List.copyOf(actions);
    }
}
