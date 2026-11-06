package com.juicyslew.moonstation14.ms14.doors;

/** Short-lived, client-only bump opening composed from the same state transitions as authority. */
final class AirlockOpeningPrediction {
    private AirlockBlockEntity.ClientView authoritative;
    private java.util.Set<java.util.UUID> authoritativeCrushVictims=new java.util.HashSet<>();
    private AirlockState opening;
    private long startedAt;
    private long phaseStartedAt;
    private long expiresAt;

    AirlockBlockEntity.ClientView authoritative() { return authoritative; }
    java.util.Set<java.util.UUID> authoritativeCrushVictims() { return authoritativeCrushVictims; }
    boolean active() { return opening != null; }

    boolean start(long now,
                  com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData data) {
        if (active()) return false;
        AirlockBlockEntity.ClientView view=authoritative;
        if (view == null || data == null || !data.door().bumpOpen() || !view.powered() || view.boltsDown()
                || view.phase() != AirlockState.DoorPhase.CLOSED) return false;
        authoritative = view;
        AirlockState base = AirlockState.initial(view.identity(), true, false)
                .withPower(true).withBolts(view.boltsDown());
        opening = base.open(now, false, data.door().openTimeOneSeconds());
        if (opening.phase() != AirlockState.DoorPhase.OPENING) { opening = null; return false; }
        startedAt = now;
        phaseStartedAt = now;
        expiresAt = opening.transitionAt() + AirlockState.ticks(data.door().openTimeTwoSeconds()) + 20;
        return true;
    }

    void tick(long now, com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData data) {
        if (opening == null || data == null) return;
        if (now >= expiresAt) { clear(); return; }
        if (now <= startedAt) return;
        AirlockState advanced = opening.tick(now, data.door().openTimeTwoSeconds(),
                data.door().closeTimeTwoSeconds(), data.airlock().autoCloseSeconds());
        if (advanced.phase() != opening.phase()) phaseStartedAt = now;
        if (advanced != opening) opening = advanced;
    }

    boolean accept(AirlockBlockEntity.ClientView next) { return accept(next,java.util.Set.of()); }

    /** Atomically admits all revision-coupled client fields from one authoritative packet. */
    boolean accept(AirlockBlockEntity.ClientView next,java.util.Set<java.util.UUID> crushVictims) {
        if (next == null) return false;
        java.util.Set<java.util.UUID> incomingVictims=new java.util.HashSet<>(crushVictims);
        if (authoritative == null || !authoritative.identity().equals(next.identity())) {
            clear(); authoritative = next; authoritativeCrushVictims=incomingVictims; return true;
        }
        if (next.revision() <= authoritative.revision()) return false;
        authoritative = next;
        authoritativeCrushVictims=incomingVictims;
        if (!next.powered() || next.boltsDown() || next.phase() == AirlockState.DoorPhase.CLOSED
                || next.phase() == AirlockState.DoorPhase.DENYING || next.phase() == AirlockState.DoorPhase.CLOSING
                || next.phase() == AirlockState.DoorPhase.CLOSING_PASSABLE) { clear(); return true; }
        if (next.passable() || next.phase() == AirlockState.DoorPhase.OPEN) { clear(); return true; }
        return true;
    }

    AirlockBlockEntity.ClientView effective() {
        if (opening == null || authoritative == null) return authoritative;
        return new AirlockBlockEntity.ClientView(authoritative.identity(), authoritative.revision(), opening.phase(),
                phaseStartedAt, opening.transitionAt(), opening.autoCloseAt(), opening.doorPassable(), authoritative.powered(),
                authoritative.boltsDown(), authoritative.boltLightsVisible(), authoritative.panelOpen(), authoritative.panelVisible());
    }

    void clear() { opening = null; }
    void reset() { clear(); authoritative = null; authoritativeCrushVictims.clear(); }
}
