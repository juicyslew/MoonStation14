package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.ElectrifiedData;
import com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-side power-wire electrification mechanics; root ownership is supplied by the caller. */
public final class AirlockElectrificationSystem {
    private AirlockElectrificationSystem() {}

    public enum WireAction { CUT, MEND, PULSE }
    public enum Contact { TOUCH, BUMP, USING, ATTACKED }

    public record State(boolean enabled, boolean wireCut, boolean pulsed, long pulseCancelAt,
                        long activatedUntil) {
        public static State initial(ElectrifiedData data) {
            return new State(data.enabled(), false, false, 0, 0);
        }
    }
    public record WireResult(State state, EffectResult attemptedShock) {}

    public static boolean freshFullyPowered(boolean meterKnown, boolean receiverEnabled,
                                            double deliveredWatts, double requestedWatts) {
        return meterKnown && receiverEnabled && Double.isFinite(deliveredWatts)
                && Double.isFinite(requestedWatts) && requestedWatts > 0
                && Double.compare(deliveredWatts, requestedWatts) == 0;
    }

    /** Shock attempt runs before the power-wire mutation, matching TrySetElectrocution ordering. */
    public static WireResult beforePowerWireAction(State state, WireAction action, long now,
                                                    ElectrifiedData data, LivingEntity actor,
                                                    boolean freshFullyPowered) {
        Objects.requireNonNull(state); Objects.requireNonNull(action); Objects.requireNonNull(data);
        // PowerWireAction sets Enabled before calling TryDoElectrifiedAct, even
        // when this action begins from a disabled component.
        boolean shock = actor != null && freshFullyPowered;
        EffectResult result = shock ? ElectrocutionSystem.applySourceShock(actor, data.shockDamage(),
                data.siemensCoefficient(), data.shockSeconds()) : EffectResult.SKIPPED_UNSUPPORTED;
        State next = state;
        if (action == WireAction.CUT) next = new State(true, true, state.pulsed(), state.pulseCancelAt(), state.activatedUntil());
        else if (action == WireAction.MEND) next = new State(true, true, state.pulsed(), 0, state.activatedUntil());
        else if (action == WireAction.PULSE && !state.pulsed())
            next = new State(true, true, true, 0, state.activatedUntil());
        return new WireResult(next, result);
    }

    /** Applies WiresSystem.UpdateElectrocution decisions after cut-count mutation. */
    public static State updateAfterWireMutation(State state, boolean allPowerWiresCut,
                                                boolean allPowerWiresMended, boolean powered, long now,
                                                ElectrifiedData data) {
        boolean activePulse = state.pulsed();
        if (activePulse && powered && !allPowerWiresCut && state.pulseCancelAt() == 0)
            return new State(state.enabled(), state.wireCut(), true, now + ticks(data.electrificationCancelSeconds()), state.activatedUntil());
        if ((!activePulse && allPowerWiresCut) || allPowerWiresMended)
            return new State(false, false, state.pulsed(), state.pulseCancelAt(), state.activatedUntil());
        return state;
    }

    /** Timer callback mirrors AwaitElectrifiedCancel: only all-mended clears the latch. */
    public static State onPulseCancel(State state, boolean allPowerWiresMended) {
        return allPowerWiresMended
                ? new State(false, false, state.pulsed(), 0, state.activatedUntil())
                : new State(state.enabled(), state.wireCut(), state.pulsed(), 0, state.activatedUntil());
    }

    public static State tick(State state, long now, boolean freshFullyPowered) {
        // Power loss suppresses powered contact, not the component's enabled latch.
        boolean enabled = state.enabled();
        long activation = state.activatedUntil() <= now ? 0 : state.activatedUntil();
        return new State(enabled, state.wireCut(), state.pulsed(), state.pulseCancelAt(), activation);
    }

    /** Contact is edge-triggered; both doorway halves should pass the same root and contact UUID. */
    public static final class ContactEdges {
        private final Set<UUID> active = new HashSet<>();
        public boolean begin(UUID actor) { return active.add(Objects.requireNonNull(actor)); }
        public void end(UUID actor) { active.remove(actor); }
    }

    public static EffectResult contact(State state, ElectrifiedData data, Contact contact,
                                       LivingEntity actor, boolean freshFullyPowered) {
        if (state == null || !state.enabled() || !freshFullyPowered || actor == null
                || !(actor.level() instanceof ServerLevel) || !eligible(data, contact))
            return EffectResult.SKIPPED_UNSUPPORTED;
        return ElectrocutionSystem.applySourceShock(actor, data.shockDamage(), data.siemensCoefficient(), data.shockSeconds());
    }

    private static boolean eligible(ElectrifiedData data, Contact contact) {
        return switch (contact) {
            case TOUCH -> data.onTouch(); case BUMP -> data.onBump();
            case USING -> data.onUsing(); case ATTACKED -> data.onAttacked();
        };
    }
    private static long ticks(float seconds) { return Math.max(1, (long) Math.ceil(seconds * 20)); }
}

