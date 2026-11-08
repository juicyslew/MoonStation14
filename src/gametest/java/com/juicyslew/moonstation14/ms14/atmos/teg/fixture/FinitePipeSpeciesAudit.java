package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;

/** Operation-derived audit allowance for versioned float-ledger pipe transactions. */
public final class FinitePipeSpeciesAudit {
    private FinitePipeSpeciesAudit() { }

    public record NetworkCounter(long topologyGeneration, Set<PipeTopology.NodeKey> members, long gasRevision) {
        public NetworkCounter { members=Set.copyOf(members); }
    }

    public record Report(long nodeWrites, double operandMagnitude, double expected, double current,
                         double signedResidual, double absoluteError, double bound, boolean conserved) { }

    public static <K> Report assess(Map<K, Long> baselineVersions, Map<K, Long> currentVersions,
                                    double expectedMoles, double currentMoles, double maxOperandMagnitude) {
        Objects.requireNonNull(baselineVersions);Objects.requireNonNull(currentVersions);
        if(!baselineVersions.keySet().equals(currentVersions.keySet())) throw new IllegalArgumentException("tracked node set changed");
        long writes=0;
        for(K key:baselineVersions.keySet()) {
            long before=baselineVersions.get(key),after=currentVersions.get(key);
            if(before<0||after<before) throw new IllegalArgumentException("pipe version regressed");
            writes=Math.addExact(writes,after-before);
        }
        if(!Double.isFinite(expectedMoles)||!Double.isFinite(currentMoles)||!Double.isFinite(maxOperandMagnitude)||maxOperandMagnitude<0)
            throw new IllegalArgumentException("invalid species audit input");
        float operand=(float)maxOperandMagnitude;
        if(!Float.isFinite(operand)) throw new IllegalArgumentException("unrepresentable species operand");
        return report(writes,expectedMoles,currentMoles,maxOperandMagnitude,operand);
    }

    public static Report assessNetworks(Map<UUID,NetworkCounter> baseline, Map<UUID,NetworkCounter> current,
                                        double expectedMoles,double currentMoles,double maxOperandMagnitude) {
        Objects.requireNonNull(baseline); Objects.requireNonNull(current);
        if(!baseline.keySet().equals(current.keySet())) throw new IllegalArgumentException("tracked network identity set changed");
        long writes=0;
        for(UUID id:baseline.keySet()) {
            NetworkCounter before=baseline.get(id),after=current.get(id);
            if(before.topologyGeneration()!=after.topologyGeneration()||!before.members().equals(after.members()))
                throw new IllegalArgumentException("tracked network topology changed");
            if(before.gasRevision()<0||after.gasRevision()<before.gasRevision()) throw new IllegalArgumentException("network gas revision regressed");
            writes=Math.addExact(writes,after.gasRevision()-before.gasRevision());
        }
        float operand=validateInputs(expectedMoles,currentMoles,maxOperandMagnitude);
        return report(writes,expectedMoles,currentMoles,maxOperandMagnitude,operand);
    }

    private static float validateInputs(double expectedMoles,double currentMoles,double maxOperandMagnitude) {
        if(!Double.isFinite(expectedMoles)||!Double.isFinite(currentMoles)||!Double.isFinite(maxOperandMagnitude)||maxOperandMagnitude<0)
            throw new IllegalArgumentException("invalid species audit input");
        float operand=(float)maxOperandMagnitude;
        if(!Float.isFinite(operand)) throw new IllegalArgumentException("unrepresentable species operand");
        return operand;
    }
    private static Report report(long writes,double expected,double current,double magnitude,float operand) {
        double bound=writes==0?0d:writes*8d*Math.ulp(operand),residual=current-expected,error=Math.abs(residual);
        return new Report(writes,magnitude,expected,current,residual,error,bound,error<=bound);
    }
}
