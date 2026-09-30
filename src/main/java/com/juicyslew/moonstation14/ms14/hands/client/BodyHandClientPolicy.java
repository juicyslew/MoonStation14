package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Main-thread client correlation gate. Only a matching full query reply can enable an action. */
public final class BodyHandClientPolicy {
    // Packet replies have no connection nonce. Never reuse a correlation ID, even after logout.
    private static final AtomicLong QUERY_IDS = new AtomicLong();
    private static final AtomicLong ACTION_IDS = new AtomicLong();
    private UUID account, body;
    private long epoch, querySequence, actionSequence, pendingQuery, pendingAction, lastQueryTick = Long.MIN_VALUE;
    private long pendingQueryEpoch;
    private long sentActionTick;
    private BodyHandStateSnapshot snapshot;
    private String feedback;
    private long localFeedbackUntil;
    private boolean inputEnabled;

    public void reset() {
        suspend();
        querySequence = actionSequence = 0;
    }

    /** Drop all proof and outstanding correlations without rewinding this connection's sequence history. */
    public void suspend() {
        account = body = null;
        epoch = pendingQuery = pendingAction = pendingQueryEpoch = 0;
        lastQueryTick = Long.MIN_VALUE;
        snapshot = null;
        feedback = null;
        localFeedbackUntil = 0;
        inputEnabled = false;
    }

    public void bind(UUID accountId, UUID bodyId, long committedEpoch) {
        if (accountId == null || bodyId == null || committedEpoch <= 0) { suspend(); return; }
        if (!accountId.equals(account) || !bodyId.equals(body) || committedEpoch != epoch) {
            suspend();
            account = accountId;
            body = bodyId;
            epoch = committedEpoch;
        }
        inputEnabled = true;
    }

    private static long next(AtomicLong ids) {
        return ids.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
    }

    public long query(long tick) {
        if (!inputEnabled || epoch == 0 || pendingAction != 0 || querySequence == Long.MAX_VALUE
                || (lastQueryTick != Long.MIN_VALUE && tick - lastQueryTick < 40)) return 0;
        long sequence = next(QUERY_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        lastQueryTick = tick;
        pendingQueryEpoch = queryEpoch();
        pendingQuery = querySequence = sequence;
        return pendingQuery;
    }

    public long queryEpoch() { return snapshot == null ? 0 : epoch; }

    public boolean accept(BodyHandStateSnapshot incoming) {
        if (!inputEnabled || epoch == 0 || incoming.sequence() != pendingQuery || pendingQuery == 0
                || incoming.requestedEpoch() != pendingQueryEpoch) return false;
        pendingQuery = 0;
        if (incoming.reason() != BodyHandStateSnapshot.Reason.OK
                || !account.equals(incoming.accountId()) || !body.equals(incoming.bodyId())
                || epoch != incoming.epoch()) {
            snapshot = null;
            return false;
        }
        snapshot = incoming;
        pendingAction = 0;
        return true;
    }

    public BodyHandStateSnapshot snapshot() { return snapshot; }
    /** Rendering must not expose the last authoritative state while an action is unresolved. */
    public BodyHandStateSnapshot hudSnapshot() { return pendingAction == 0 ? snapshot : null; }
    public String feedback() { return feedback; }

    /** Presentation only: no candidate was nominated, so no request reached the server. */
    public void noItemUnderCursor(long tick) {
        if (inputEnabled && snapshot != null && pendingAction == 0 && pendingQuery == 0) {
            feedback = "Hands: no item under cursor";
            localFeedbackUntil = tick + 60;
        }
    }

    public record HudPosition(int x, int y) { }

    /** The body vital rows end near y=48. Keep hands above chat and away from the hotbar. */
    public static HudPosition handHudPosition(int width, int height, int textWidth, int lineHeight) {
        if (width < 24 || height < 100 || lineHeight < 1) return null;
        int x = 6;
        int y = 54;
        if (y + lineHeight + 2 > height - 36) return null;
        return new HudPosition(Math.max(3, Math.min(x, width - textWidth - 3)), y);
    }

    /** No GUI overlay, menu, ghost, or uncommitted camera may show the aim cue. */
    public static boolean showAimCue(boolean committedCharacterCamera, boolean guiVisible, boolean screenClosed) {
        return committedCharacterCamera && guiVisible && screenClosed;
    }

    public long action(long tick) {
        if (!inputEnabled || snapshot == null || pendingQuery != 0 || pendingAction != 0
                || actionSequence == Long.MAX_VALUE) return 0;
        long sequence = next(ACTION_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        pendingAction = actionSequence = sequence;
        sentActionTick = tick;
        feedback = null;
        localFeedbackUntil = 0;
        return pendingAction;
    }

    public boolean result(BodyHandActionResult incoming, long tick) {
        if (!inputEnabled || pendingAction == 0 || incoming.sequence() != pendingAction || incoming.epoch() != epoch) return false;
        pendingAction = 0;
        snapshot = null;
        pendingQuery = 0;
        lastQueryTick = Long.MIN_VALUE;
        feedback = switch (incoming.reason()) {
            case DENIED -> "Hands: server DENIED";
            case RECOVERY_REQUIRED -> "Hands: RECOVERY_REQUIRED";
            case OK -> null;
            default -> "Hands: " + incoming.reason();
        };
        localFeedbackUntil = feedback == null ? 0 : tick + 60;
        return true;
    }

    public void timeout(long tick) {
        if (localFeedbackUntil != 0 && tick >= localFeedbackUntil) {
            feedback = null;
            localFeedbackUntil = 0;
        }
        if (pendingAction != 0 && tick - sentActionTick >= 80) {
            pendingAction = 0;
            snapshot = null;
            pendingQuery = 0;
            lastQueryTick = Long.MIN_VALUE;
            feedback = null;
            localFeedbackUntil = 0;
        }
    }
}
