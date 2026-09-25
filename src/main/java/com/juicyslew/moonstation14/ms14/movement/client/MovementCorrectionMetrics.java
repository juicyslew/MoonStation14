package com.juicyslew.moonstation14.ms14.movement.client;

/** O(1) diagnostic counters for client-side authoritative snapshot corrections. */
final class MovementCorrectionMetrics {
    static final int WINDOW_TICKS = 100;

    record Window(long startTick, long endTick, long snapshots, long projectedCorrections, long hardAuthoritySnaps,
                  long duplicateAcksMissingHistoryWhilePending, long appliedCorrectionSamples,
                  double maximumCorrection, double meanCorrection, long serverGroundedSnapshots,
                  long clientGroundedSnapshots, int pendingSamples) { }

    private long startTick;
    private boolean windowStarted;
    private long snapshots;
    private long projectedCorrections;
    private long hardAuthoritySnaps;
    private long duplicateAcksMissingHistoryWhilePending;
    private long appliedCorrectionSamples;
    private double totalCorrection;
    private double maximumCorrection;
    private long serverGroundedSnapshots;
    private long clientGroundedSnapshots;
    private int pendingSamples;

    void reset(long tick) {
        clearCounts();
        startTick = tick;
        windowStarted = true;
    }

    void record(boolean projected, boolean hardAuthoritySnap, boolean duplicateAckMissingHistoryWhilePending,
                double appliedCorrection, boolean serverGrounded, boolean clientGrounded, int pendingSampleCount) {
        snapshots++;
        if (projected) projectedCorrections++;
        if (hardAuthoritySnap) hardAuthoritySnaps++;
        if (duplicateAckMissingHistoryWhilePending) duplicateAcksMissingHistoryWhilePending++;
        if (appliedCorrection > 0d) {
            appliedCorrectionSamples++;
            totalCorrection += appliedCorrection;
            maximumCorrection = Math.max(maximumCorrection, appliedCorrection);
        }
        if (serverGrounded) serverGroundedSnapshots++;
        if (clientGrounded) clientGroundedSnapshots++;
        pendingSamples = pendingSampleCount;
    }

    /** Returns and clears a completed reporting window, or null until 100 local ticks have elapsed. */
    Window takeWindow(long tick) {
        if (!windowStarted) {
            reset(tick);
            return null;
        }
        if (tick - startTick < WINDOW_TICKS) return null;
        Window result = new Window(startTick, tick, snapshots, projectedCorrections, hardAuthoritySnaps,
                duplicateAcksMissingHistoryWhilePending, appliedCorrectionSamples, maximumCorrection,
                appliedCorrectionSamples == 0 ? 0d : totalCorrection / appliedCorrectionSamples,
                serverGroundedSnapshots, clientGroundedSnapshots, pendingSamples);
        reset(tick);
        return result;
    }

    private void clearCounts() {
        snapshots = 0;
        projectedCorrections = 0;
        hardAuthoritySnaps = 0;
        duplicateAcksMissingHistoryWhilePending = 0;
        appliedCorrectionSamples = 0;
        totalCorrection = 0d;
        maximumCorrection = 0d;
        serverGroundedSnapshots = 0;
        clientGroundedSnapshots = 0;
        pendingSamples = 0;
    }
}
