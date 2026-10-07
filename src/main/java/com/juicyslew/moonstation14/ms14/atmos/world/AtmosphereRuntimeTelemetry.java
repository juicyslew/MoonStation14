package com.juicyslew.moonstation14.ms14.atmos.world;

/** Small server-thread-owned lifetime counters. Snapshotting never changes the observation window. */
public final class AtmosphereRuntimeTelemetry {
    private final long startGameTick, startNanos;
    private long callbacks, admissions, resumptions, lindaSources, equalizationPatches, equalizationCells, equalizationIncludedCells, ownershipClaimedCells;
    private long pressureFaceTransfers, lindaSharedPairs, groupAveragedMembers;
    private long ownershipProbes, monstermosProbes;
    private long groupOperations, groupMembersCaptured, groupAverages, groupUnavailable;
    private long groupCaptureNanos, groupAverageNanos, groupPublicationNanos;
    private AtmosphereCycle.Stage lastAdmittedStage;
    private AtmosphereCycle.Stage observedStage;
    private long stageStartTick;

    AtmosphereRuntimeTelemetry(long gameTick, long nanoTime) { startGameTick = gameTick; startNanos = nanoTime; }
    void callback() { callbacks++; }
    void admitted(AtmosphereCycle.Stage stage) {
        admissions++;
        if (stage == lastAdmittedStage) resumptions++;
        lastAdmittedStage = stage;
    }
    void lindaSources(long count) { lindaSources += Math.max(0, count); }
    void equalizationCommitted(long cells, long includedCells) { equalizationPatches++; equalizationCells += Math.max(0, cells); equalizationIncludedCells += Math.max(0, includedCells); }
    void pressureFaceTransfers(long count) { pressureFaceTransfers += Math.max(0, count); }
    void lindaSharedPairs(long count) { lindaSharedPairs += Math.max(0, count); }
    void groupAveragedMembers(long count) { groupAveragedMembers += Math.max(0, count); }
    void ownershipClaimed(long cells) { ownershipClaimedCells += Math.max(0, cells); }
    void ownershipProbes(long count) { ownershipProbes += Math.max(0, count); }
    void monstermosProbes(long count) { monstermosProbes += Math.max(0, count); }
    void groupWork(long operations, long members, long averages, long unavailable, long captureNanos, long averageNanos) {
        groupOperations += operations; groupMembersCaptured += members; groupAverages += averages;
        groupUnavailable += unavailable; groupCaptureNanos += captureNanos; groupAverageNanos += averageNanos;
    }
    void groupPublication(long elapsedNanos) { groupPublicationNanos += elapsedNanos; }
    void observeStage(AtmosphereCycle.Stage stage, long gameTick) {
        if (stage != observedStage) { observedStage = stage; stageStartTick = gameTick; }
    }
    Snapshot snapshot(long gameTick, long nanoTime, long cycles, String stage, long stageAgeTicks,
                      int queued, int pendingOwnership, int pendingEqualizations, int activeCandidates,
                      int retryPending, int saturatedChunks, int unknownParked) {
        long gameTicks = Math.max(0, gameTick - startGameTick), wallNanos = Math.max(0, nanoTime - startNanos);
        double wallSeconds = wallNanos / 1_000_000_000.0;
        return new Snapshot(startGameTick, gameTicks, wallNanos / 1_000_000.0, callbacks, admissions, resumptions,
                cycles, stage, observedStage == null ? 0 : Math.max(0, gameTick - stageStartTick), queued, pendingOwnership, pendingEqualizations,
                 lindaSources, equalizationPatches, equalizationCells, equalizationIncludedCells, ownershipClaimedCells,
                 pressureFaceTransfers, lindaSharedPairs, groupAveragedMembers, ownershipProbes, monstermosProbes,
                activeCandidates, retryPending, saturatedChunks, unknownParked,
                groupOperations, groupMembersCaptured, groupAverages, groupUnavailable,
                groupCaptureNanos / 1_000_000.0, groupAverageNanos / 1_000_000.0,
                groupPublicationNanos / 1_000_000.0,
                wallSeconds == 0 ? 0 : callbacks / wallSeconds, wallSeconds == 0 ? 0 : admissions / wallSeconds,
                gameTicks == 0 ? 0 : callbacks * 20.0 / gameTicks, gameTicks == 0 ? 0 : admissions * 20.0 / gameTicks);
    }
    public record Snapshot(long startGameTick, long elapsedGameTicks, double elapsedWallMs, long callbacks, long admissions,
                    long resumptions, long completedCycles, String stage, long stageAgeTicks, int queued,
                    int pendingOwnership, int pendingEqualizations, long lindaSources, long equalizationPatches,
                     long equalizationCells, long equalizationIncludedCells, long ownershipClaimedCells,
                     long pressureFaceTransfers, long lindaSharedPairs, long groupAveragedMembers,
                     long ownershipProbes, long monstermosProbes,
                    int activeCandidates, int retryPending, int saturatedChunks, int unknownParked,
                    long groupOperations, long groupMembersCaptured, long groupAverages, long groupUnavailable,
                    double groupCaptureMs, double groupAverageMs, double groupPublicationMs, double callbacksPerWallSecond,
                    double admissionsPerWallSecond, double callbacksPerGameSecond, double admissionsPerGameSecond) { }
}
