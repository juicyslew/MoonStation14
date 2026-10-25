package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Combat-mode capability and authoritative per-body state API. */
public final class CombatModeSystem {
    private CombatModeSystem() { }

    public record ToggleResult(boolean success, boolean enabled) { }

    public static Optional<CombatModeComponent> capability(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) return Optional.empty();
        var resolved = entity.level().isClientSide()
                ? CharacterIdentitySystem.projectForActor(entity)
                : CharacterIdentitySystem.resolveForActor(entity);
        return resolved.flatMap(data -> data.component(CombatModeComponent.class));
    }

    public static boolean isInCombatMode(LivingEntity entity) {
        if (capability(entity).isEmpty()) return false;
        var state = entity.getExistingDataOrNull(ModDataAttachments.COMBAT_MODE.get());
        return state != null && state.enabled();
    }

    public static ToggleResult toggle(LivingEntity entity) {
        if (!hasServerAuthority(entity) || capability(entity).isEmpty())
            return new ToggleResult(false, isInCombatMode(entity));
        var state = entity.getData(ModDataAttachments.COMBAT_MODE.get());
        boolean enabled = state.toggle();
        entity.setData(ModDataAttachments.COMBAT_MODE.get(), state);
        return new ToggleResult(true, enabled);
    }

    private static boolean hasServerAuthority(LivingEntity entity) {
        return entity != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getServer() != null && level.getServer().isSameThread();
    }
}
