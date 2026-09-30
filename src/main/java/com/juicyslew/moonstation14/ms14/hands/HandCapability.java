package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        return resolveHostCharacter(body).map(CharacterData::hands)
                .filter(ids -> !ids.isEmpty()).map(List::copyOf);
    }

    /** Bound, current host prototype; only the dedicated lifecycle harness may host human exceptionally. */
    public static Optional<CharacterData> resolveHostCharacter(Entity body) {
        if (!(body instanceof LivingEntity living) || !(body.level() instanceof ServerLevel level))
            return Optional.empty();
        if (body.getClass() == PlayerCharacterHarnessEntity.class) {
            var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
            if (identity == null || !identity.isBound()
                    || !ModCharacters.HUMAN_ID.equals(identity.characterId())) return Optional.empty();
            return CharacterIdentitySystem.resolveHost(body, level, ModCharacters.HUMAN_ID);
        }
        return CharacterIdentitySystem.resolveForHost(living);
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
