package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.world.entity.LivingEntity;

/** Pure admission policy for resolved body action profiles. */
public final class BodyActionAdmission {
    public enum Reason { ENABLED, UNAVAILABLE, UNCONSCIOUS, CANNOT_INTERACT }

    public record Decision(boolean enabled, Reason reason) { }

    private BodyActionAdmission() { }

    /** Evaluates the profile's independent consciousness and interaction gates. */
    public static Decision evaluate(ActionPrototype profile, boolean conscious, boolean canInteract) {
        if (profile == null) return new Decision(false, Reason.UNAVAILABLE);
        if (profile.checkConsciousness() && !conscious) return new Decision(false, Reason.UNCONSCIOUS);
        if (profile.checkCanInteract() && !canInteract) return new Decision(false, Reason.CANNOT_INTERACT);
        return new Decision(true, Reason.ENABLED);
    }

    /**
     * Maps the local medical state to consciousness. This deliberately uses
     * MobStateSystem's ALIVE state (not stun state), retaining the project's
     * existing medical-state model without introducing statuses.
     */
    public static Decision evaluate(ActionPrototype profile, LivingEntity body) {
        return evaluate(profile, com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body),
                com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body));
    }
}
