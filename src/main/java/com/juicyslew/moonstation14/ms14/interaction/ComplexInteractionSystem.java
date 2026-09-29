package com.juicyslew.moonstation14.ms14.interaction;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Server-only body capability. No interaction action or item semantics are implemented here. */
public final class ComplexInteractionSystem {
    private static final String INITIALIZED_MARKER = "moonstation14:complex_interaction_initialized";

    private ComplexInteractionSystem() { }

    /** Called after a successful identity enrollment; never reseeds a body with any prior state. */
    public static void bootstrap(Entity body, ServerLevel level, CharacterData prototype) {
        if (body == null || body.level() != level || body.isRemoved() || level.getServer() == null
                || !level.getServer().isSameThread() || prototype == null) return;
        var persisted = body.getPersistentData();
        ComplexInteractionAttachment existing = body.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
        if (persisted.contains(INITIALIZED_MARKER)
                || (existing != null && (existing.initialized() || existing.enabled()))) return;
        // Write the independent marker first: interruption between writes fails closed, not open.
        persisted.putBoolean(INITIALIZED_MARKER, true);
        body.setData(ModDataAttachments.COMPLEX_INTERACTION.get(),
                new ComplexInteractionAttachment(true,
                        prototype.components().contains(CharacterData.Capability.COMPLEX_INTERACTION)));
    }

    /** Read-only: neither a prototype declaration nor an absent attachment grants permission. */
    public static boolean enabled(Entity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || !exactServerBody(body, level)
                || !validMarker(body)) return false;
        CharacterIdentityAttachment identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound() || CharacterIdentitySystem.resolveHost(body, level, identity.characterId()).isEmpty())
            return false;
        ComplexInteractionAttachment state = body.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
        return state != null && state.initialized() && state.enabled();
    }

    /** Explicit runtime grant/revoke; does not consult prototype components after bootstrap. */
    public static boolean setEnabled(Entity body, boolean enabled) {
        if (body == null || !(body.level() instanceof ServerLevel level) || !exactServerBody(body, level)
                || !validMarker(body)) return false;
        CharacterIdentityAttachment identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound() || CharacterIdentitySystem.resolveHost(body, level, identity.characterId()).isEmpty())
            return false;
        ComplexInteractionAttachment existing = body.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
        if (existing != null && !existing.initialized()) return false;
        body.setData(ModDataAttachments.COMPLEX_INTERACTION.get(), new ComplexInteractionAttachment(true, enabled));
        return true;
    }

    private static boolean validMarker(Entity body) {
        return body.getPersistentData().contains(INITIALIZED_MARKER, Tag.TAG_BYTE)
                && body.getPersistentData().getByte(INITIALIZED_MARKER) == 1;
    }

    private static boolean exactServerBody(Entity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && level.getServer() != null
                && level.getServer().isSameThread() && level.getEntity(body.getUUID()) == body;
    }
}
