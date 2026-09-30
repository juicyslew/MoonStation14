package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only runtime view of the hand capability declared by a body's bound character prototype. */
public final class HandCapability {
    private HandCapability() { }

    /**
     * Resolves hand IDs from the body's own bound character identity. This never creates identity or hand state.
     * Empty, unbound, dangling, client-side, and non-server bodies have no runtime hand capability.
     */
    public static Optional<List<String>> resolve(Entity body) {
        Objects.requireNonNull(body, "body");
        Optional<CharacterData> character = body instanceof net.minecraft.world.entity.LivingEntity living
                ? CharacterIdentitySystem.resolveForActor(living) : Optional.empty();
        return character.flatMap(policy -> policy.component(HandsPrototypeComponent.class))
                .map(HandsPrototypeComponent::hands).filter(ids -> !ids.isEmpty());
    }

    /**
     * Returns whether persisted hand IDs exactly match the prototype's IDs and order. A mismatch is rejected
     * rather than rewriting slots and potentially discarding held tokens; this method never mutates or saves state.
     */
    public static boolean isCompatible(HandComponent existing, List<String> prototypeHandIds) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(prototypeHandIds, "prototypeHandIds");
        return existing.hands().stream().map(HandComponent.Slot::id).toList().equals(prototypeHandIds);
    }
}
