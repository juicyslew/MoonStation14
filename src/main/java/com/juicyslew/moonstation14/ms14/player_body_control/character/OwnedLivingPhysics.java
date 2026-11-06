package com.juicyslew.moonstation14.ms14.player_body_control.character;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Dispatches one fallback physical step for eligible living harness bodies not stepped by player input. */
public final class OwnedLivingPhysics {
    private OwnedLivingPhysics() { }

    public static void tick(ServerLevel level) {
        var stepper = new GroundedHarnessWorldStep();
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof Mob body) || !(body instanceof MindControlledMob owner)) continue;
            if (owner.moonstation14$physicalStepConsumed(level, level.getServer().getTickCount())
                    || com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.alreadyAdvancedThisTick(body)) continue;
            if (!level.isPositionEntityTicking(body.blockPosition())) continue;
            boolean pendingAnchor = body instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                    && pending.sable$frameAnchorInitializationPending();
            if (OwnedCorpsePhysics.eligible(body)) {
                if (pendingAnchor) OwnedCorpsePhysics.step(body);
                continue;
            }
            if (body instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity character) {
                if (GroundedHarnessWorldStep.isPassivePlayerCharacterEligible(body)) stepper.passiveStep(character);
            }
        }
    }
}
