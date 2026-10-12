package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;

import java.util.List;

/** One authenticated authority decision shared by native chunk tracking and observer tickets. */
final class ControlledViewTarget {
    enum Family { LIFECYCLE_GHOST, MEDICAL_OBSERVER, LIFECYCLE_CHARACTER, HARNESS }
    record Target(Entity entity, Family family, long epoch) { }

    private ControlledViewTarget() { }

    static Target resolve(ServerPlayer player) {
        if (player == null) return null;
        var ghost = LifecycleGhostSessionControl.canonicalGhost(player).orElse(null);
        Target lifecycleGhost = ghost == null ? null : new Target(ghost.ghost(), Family.LIFECYCLE_GHOST, ghost.epoch());
        ObserverSession medical = ObserverSystem.query(player);
        // A bound lifecycle session may be in staging/commit. Its medical session is not
        // independent authority until the canonical lifecycle control is committed.
        Target medicalTarget = null;
        if (medical != null && medical.boundAuthority() == null
                && medical.mode() == ObserverSession.Mode.GHOST
                && medical.ownerId().equals(player.getUUID()) && medical.epoch() > 0
                && medical.ghostId() != null) {
            Entity entity = player.serverLevel().getEntity(medical.ghostId());
            if (entity != null && entity.getUUID().equals(medical.ghostId()) && valid(player, entity))
                medicalTarget = new Target(entity, Family.MEDICAL_OBSERVER, medical.epoch());
        }
        var character = LifecycleCharacterSessionControl.committedActorSnapshot(player).orElse(null);
        Target lifecycleCharacter = character == null ? null
                : new Target(character.body(), Family.LIFECYCLE_CHARACTER, character.epoch());
        var harness = GhostMobHarnessControl.committedControlledEntity(player).orElse(null);
        return select(valid(player, lifecycleGhost == null ? null : lifecycleGhost.entity()) ? lifecycleGhost : null,
                valid(player, medicalTarget == null ? null : medicalTarget.entity()) ? medicalTarget : null,
                valid(player, lifecycleCharacter == null ? null : lifecycleCharacter.entity()) ? lifecycleCharacter : null,
                valid(player, harness) ? new Target(harness, Family.HARNESS, -1) : null);
    }

    /** Select only among candidates authenticated by their owning authority; never infers authority from the camera. */
    static Target select(Target lifecycleGhost, Target medicalObserver, Target lifecycleCharacter, Target harness) {
        for (Target candidate : new Target[]{lifecycleGhost, medicalObserver, lifecycleCharacter, harness}) {
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static boolean valid(ServerPlayer player, Entity entity) {
        return entity != null && entity != player && !entity.isRemoved() && entity.isAlive()
                && entity.level() == player.level();
    }
}
