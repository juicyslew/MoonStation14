package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Combat-mode capability and authoritative per-body state API. */
public final class CombatModeSystem {
    private CombatModeSystem() { }

    public record ToggleResult(boolean success, boolean enabled) { }

    public static Optional<CombatModeComponent> capability(LivingEntity entity) {
        return com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.capability(entity);
    }

    public static boolean isInCombatMode(LivingEntity entity) {
        return com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.isInCombatMode(entity);
    }

    public static ToggleResult toggle(LivingEntity entity) {
        boolean enabled = isInCombatMode(entity);
        if (!hasServerAuthority(entity) || capability(entity).isEmpty())
            return new ToggleResult(false, enabled);
        boolean next = !enabled;
        boolean success = com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.setMode(entity, next);
        return new ToggleResult(success, success ? next : enabled);
    }

    private static boolean hasServerAuthority(LivingEntity entity) {
        return entity != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getServer() != null && level.getServer().isSameThread();
    }
}
