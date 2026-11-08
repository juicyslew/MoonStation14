package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Server authority for the body-local combat mode toggle. */
public final class CombatModeSystem {
    private CombatModeSystem() { }

    public static boolean isInCombatMode(LivingEntity body) {
        return body != null && capability(body).isPresent()
                && Optional.ofNullable(body.getExistingDataOrNull(ModDataAttachments.COMBAT_MODE.get()))
                .map(CombatModeAttachment::inCombatMode).orElse(false);
    }

    /** Returns whether this body resolves the capability, independently of its current mode. */
    public static Optional<CombatModeComponent> capability(LivingEntity body) {
        if (body == null) return Optional.empty();
        CharacterData actor = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        return actor == null ? Optional.empty() : actor.component(CombatModeComponent.class);
    }

    public static boolean setMode(LivingEntity body, boolean enabled) {
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || capability(body).isEmpty()) return false;
        CombatModeAttachment current = body.getExistingDataOrNull(ModDataAttachments.COMBAT_MODE.get());
        if (current != null && current.inCombatMode() == enabled) return true;
        body.setData(ModDataAttachments.COMBAT_MODE.get(), new CombatModeAttachment(enabled));
        return true;
    }
}
