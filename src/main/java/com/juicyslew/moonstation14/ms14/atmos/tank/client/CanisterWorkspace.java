package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.ms14.atmos.tank.*;

import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import com.juicyslew.moonstation14.ms14.atmos.tank.client.CanisterOverlay;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;

import java.util.UUID;

/** Client model/request builder for an already-open canister window. Drawing and input belong to its overlay. */
public final class CanisterWorkspace {
    public static final CanisterWorkspace INSTANCE = new CanisterWorkspace();
    private static final long QUERY_INTERVAL = 40;
    private UUID selectedSession;
    private BlockPos selectedPosition;
    private long sequence;
    private long lastQuery = Long.MIN_VALUE;
    private final CanisterOverlay overlay = new CanisterOverlay(this);

    private CanisterWorkspace() { }
    public CanisterOverlay overlay() { return overlay; }

    public synchronized void select(UUID session, BlockPos position) {
        selectedSession = session;
        selectedPosition = position == null ? null : position.immutable();
        lastQuery = Long.MIN_VALUE;
        PointerOverlayRegistry.register(overlay);
        PointerOverlayRegistry.bringToFront(overlay);
    }

    public synchronized UUID selectedSession() { return selectedSession; }
    public synchronized BlockPos selectedPosition() { return selectedPosition; }

    public synchronized CanisterSessionSnapshot session() {
        return selectedSession == null ? null : CanisterClientCache.get(selectedSession).orElse(null);
    }

    public synchronized boolean requestsEnabled(UUID body, long epoch) {
        return CanisterClientCache.matches(session(), body, epoch) && BodyHandClient.currentHandProof() != null;
    }

    /** Builds solely from the latest accepted session and current authoritative hand proof. */
    public synchronized CanisterControlRequest request(UUID body, long epoch,
            CanisterControlRequest.Operation operation, long clientTick, boolean valveClosed, double pressureKpa) {
        var state = session();
        if (!CanisterClientCache.matches(state, body, epoch) || operation == null) return null;
        var proof = BodyHandClient.currentHandProof();
        if (proof == null || !body.equals(proof.snapshot().bodyId()) || epoch != proof.snapshot().epoch()) return null;
        return new CanisterControlRequest(state.sessionId(), state.machineId(), state.bodyId(), state.authorityEpoch(),
                state.dimension(), state.x(), state.y(), state.z(), nextSequence(), state.stateRevision(),
                state.slotRevision(), state.ledgerRevision(), proof.snapshot().revision(), proof.hand().id(),
                proof.hand().token(), operation, valveClosed, pressureKpa);
    }

    public synchronized boolean send(UUID body, long epoch, CanisterControlRequest.Operation operation,
            long clientTick, boolean valveClosed, double pressureKpa) {
        var request = request(body, epoch, operation, clientTick, valveClosed, pressureKpa);
        if (request == null) return false;
        PacketDistributor.sendToServer(request);
        return true;
    }

    /** Refresh is rate bounded. Invalid or authority-mismatched sessions are closed and forgotten. */
    public synchronized void tick(UUID body, long epoch, long clientTick) {
        var state = session();
        if (state == null) { clearSelection(); return; }
        if (!CanisterClientCache.matches(state, body, epoch)) {
            PacketDistributor.sendToServer(new CanisterCloseRequest(state.sessionId()));
            clearSelection();
            return;
        }
        if ((lastQuery == Long.MIN_VALUE || clientTick - lastQuery >= QUERY_INTERVAL) && body != null) {
            var request = request(body, epoch, CanisterControlRequest.Operation.QUERY, clientTick,
                    state.valveClosed(), state.pressureKpa());
            if (request != null) {
                PacketDistributor.sendToServer(request);
                lastQuery = clientTick;
            }
        }
    }

    public synchronized void close() {
        if (selectedSession != null) PacketDistributor.sendToServer(new CanisterCloseRequest(selectedSession));
        clearSelection();
    }

    public synchronized void clearSelection() { selectedSession = null; selectedPosition = null; lastQuery = Long.MIN_VALUE; }
    public synchronized void clear() { clearSelection(); PointerOverlayRegistry.unregister(overlay); }

    private long nextSequence() { sequence = sequence == Long.MAX_VALUE ? 1 : sequence + 1; return sequence; }
}


