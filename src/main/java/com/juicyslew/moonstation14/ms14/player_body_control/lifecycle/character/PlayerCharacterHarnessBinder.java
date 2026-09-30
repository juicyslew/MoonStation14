package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.UUID;

/** Explicit server-only binding API; it never spawns an entity or changes the operator debug route. */
public final class PlayerCharacterHarnessBinder {
    private PlayerCharacterHarnessBinder() { }

    public static boolean bind(Mob body, UUID accountId, String profileKey,
                               UUID mindId, ResourceLocation characterPrototypeKey) {
        // Startup-sampled policy is checked before inspecting the target or its server/lifecycle state.
        if (!gatesPermitBinding()) return false;
        if (body == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        if (!(body instanceof PlayerCharacterHarnessEntity harness)
                || body.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || body.isRemoved() || !body.isAddedToLevel()
                || body.level().isClientSide || level.getEntity(body.getUUID()) != body) return false;
        if (harness.isInvalidUnbindable()) return false;
        if (!isSupportedPrototypeKey(characterPrototypeKey)) return false;

        PlayerCharacterBinding requested;
        try {
            requested = new PlayerCharacterBinding(Objects.requireNonNull(accountId),
                    Objects.requireNonNull(profileKey), Objects.requireNonNull(mindId));
        } catch (RuntimeException invalid) {
            return false;
        }
        if (CharacterIdentitySystem.resolve(level, characterPrototypeKey).isEmpty()
                || ModCharacters.characterForHost(level, PlayerCharacterHarnessRegistration.ID).isPresent()) {
            return false;
        }

        PlayerCharacterBinding current = harness.playerCharacterBinding();
        if (!canReuseBinding(current, requested)) return false;
        CharacterIdentityAttachment identity = body.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity != null && (!identity.isBound() || !identity.characterId().equals(characterPrototypeKey))) {
            return false;
        }
        if (current != null && identity == null) return false;

        if (!CharacterIdentitySystem.enroll(body, level, characterPrototypeKey)) return false;
        if (current == null) harness.setPlayerCharacterBinding(requested);
        return CharacterIdentitySystem.resolveForActor(body).isPresent();
    }

    /** Exact binding inspection; does not resolve or infer ownership from a prototype key. */
    public static PlayerCharacterBinding binding(Entity entity) {
        return entity instanceof PlayerCharacterHarnessEntity harness ? harness.playerCharacterBinding() : null;
    }

    static boolean gatesPermitBinding() {
        return MindGhostStartupGate.enabledForServer() && !MovementStartupGate.enabledForServer();
    }

    static boolean canReuseBinding(PlayerCharacterBinding current, PlayerCharacterBinding requested) {
        return current == null || current.equals(requested);
    }

    static boolean isSupportedPrototypeKey(ResourceLocation prototypeKey) {
        return ModCharacters.HUMAN_ID.equals(prototypeKey);
    }
}
