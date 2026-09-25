package com.juicyslew.moonstation14.ms14.movement.protocol;

/** Pure server-side ownership handoff model; packet ordering and runtime ownership remain external. */
public final class MovementSession {
    public enum Phase { VANILLA, PENDING_BEGIN, CUSTOM, PENDING_END }
    public enum Result { ACCEPTED, REJECTED }

    private Phase phase = Phase.VANILLA;
    private long epoch;
    private long lastSequence;
    private boolean acknowledged;
    private boolean disableAcknowledged;
    private boolean teleportAcknowledged;
    private boolean resumeAuthorized;

    public Phase phase() { return phase; }
    public long epoch() { return epoch; }
    public long lastSequence() { return lastSequence; }
    /** CUSTOM and PENDING_END retain custom ownership until the final resume acknowledgement. */
    public boolean customOwnsMovement() { return phase == Phase.CUSTOM || phase == Phase.PENDING_END; }
    public boolean vanillaOwnsMovement() { return phase == Phase.VANILLA; }

    /** Explicit server eligibility gate: connected server-side, bound human, grounded, and default vanilla. */
    public Result begin(long newEpoch, boolean serverSide, boolean boundHuman, boolean grounded) {
        if (!serverSide || !boundHuman || !grounded || phase != Phase.VANILLA || newEpoch <= 0
                || (epoch > 0 && newEpoch <= epoch)) return Result.REJECTED;
        epoch = newEpoch;
        lastSequence = 0;
        acknowledged = false;
        disableAcknowledged = false;
        teleportAcknowledged = false;
        resumeAuthorized = false;
        phase = Phase.PENDING_BEGIN;
        return Result.ACCEPTED;
    }

    /** Server receives the peer's acknowledgement; it is not proof of connection identity or packet ordering. */
    public Result acknowledge(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_BEGIN || receivedEpoch != epoch || acknowledged)
            return Result.REJECTED;
        acknowledged = true;
        return Result.ACCEPTED;
    }

    /** Called only when the server explicitly commits the handoff after its own safe-boundary checks. */
    public Result commit(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_BEGIN || !acknowledged || receivedEpoch != epoch)
            return Result.REJECTED;
        phase = Phase.CUSTOM;
        return Result.ACCEPTED;
    }

    public Result acceptIntent(long receivedEpoch, long sequence, boolean serverSide) {
        if (!serverSide || phase != Phase.CUSTOM || receivedEpoch != epoch || sequence <= 0
                || lastSequence == Long.MAX_VALUE || sequence != lastSequence + 1) return Result.REJECTED;
        lastSequence = sequence;
        return Result.ACCEPTED;
    }

    /** Begin disable while custom remains sole owner until the resume handshake completes. */
    public Result beginDisable(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.CUSTOM || receivedEpoch != epoch) return Result.REJECTED;
        disableAcknowledged = false;
        teleportAcknowledged = false;
        resumeAuthorized = false;
        phase = Phase.PENDING_END;
        return Result.ACCEPTED;
    }

    /** Records the peer's acknowledgement of Disable; it does not itself release custom ownership. */
    public Result acknowledgeDisable(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_END || receivedEpoch != epoch || disableAcknowledged)
            return Result.REJECTED;
        disableAcknowledged = true;
        return Result.ACCEPTED;
    }

    /** Call only after the server matched its captured vanilla teleport ID to a vanilla accept. */
    public Result acknowledgeTeleport(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_END || receivedEpoch != epoch || !disableAcknowledged
                || teleportAcknowledged) return Result.REJECTED;
        teleportAcknowledged = true;
        return Result.ACCEPTED;
    }

    /** Authorizes the client to resume vanilla movement after the teleport boundary is verified. */
    public Result authorizeResume(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_END || receivedEpoch != epoch || !teleportAcknowledged
                || resumeAuthorized) return Result.REJECTED;
        resumeAuthorized = true;
        return Result.ACCEPTED;
    }

    /** Called only for the final ResumeAcknowledge; ownership changes only at this point. */
    public Result finishDisable(long receivedEpoch, boolean serverSide) {
        if (!serverSide || phase != Phase.PENDING_END || receivedEpoch != epoch || !resumeAuthorized)
            return Result.REJECTED;
        phase = Phase.VANILLA;
        acknowledged = false;
        disableAcknowledged = false;
        teleportAcknowledged = false;
        resumeAuthorized = false;
        return Result.ACCEPTED;
    }

    /** Disconnect, teleport, clone, or other lifecycle discontinuity invalidates the whole session. */
    public void reset() {
        phase = Phase.VANILLA;
        lastSequence = 0;
        acknowledged = false;
        disableAcknowledged = false;
        teleportAcknowledged = false;
        resumeAuthorized = false;
    }
}
