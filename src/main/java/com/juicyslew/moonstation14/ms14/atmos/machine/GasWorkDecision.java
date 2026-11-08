package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;

import java.util.List;
import java.util.Map;

/** Immutable, transient receipt of one pump/mixer policy attempt; it has no authority over gas work. */
public record GasWorkDecision(long sequence, long worldTick, long sampleTick, long intervalTicks,
                              double intervalSeconds, String kind,
                              List<Input> inputs, Map<String, PipeTopology.NodeKey> roots,
                              double targetKpa, double ratio, double rateM3PerSecond,
                              double lowerBoundKpa, double upperBoundKpa, double overclockBoundKpa,
                              double pumpSpeedup, double actualPaidInputJoules,
                              boolean planPresent, double plannedMoles, Outcome outcome,
                              double acceptedMoles, Map<PipeTopology.NodeKey, GasMixture> plannedAfter,
                              Map<PipeTopology.NodeKey, GasMixture> committedAfter) {
    public enum Outcome { UNAVAILABLE, NO_PLAN, COMMITTED, UNCHANGED, STALE, REJECTED }
    public record Input(String portId, PipeTopology.NodeKey root, double volumeM3,
                        GasMixture gas, double pressureKpa) { }
    public GasWorkDecision {
        if (sequence < 1 || kind == null || outcome == null || inputs == null || roots == null || plannedAfter == null || committedAfter == null)
            throw new IllegalArgumentException("invalid gas work decision");
        inputs = List.copyOf(inputs); roots = Map.copyOf(roots); plannedAfter = Map.copyOf(plannedAfter); committedAfter = Map.copyOf(committedAfter);
    }
}
