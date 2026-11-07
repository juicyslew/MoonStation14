package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import net.minecraft.core.BlockPos;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixture-only summary of separately committed chemistry channels observed during a bounded window. */
final class TegWholeAirEventObservation {
    enum Channel { WHOLE_AIR, HOTSPOT }
    private final Set<Key> seen = new HashSet<>();
    private final Map<Key,Batch> batches = new java.util.HashMap<>();
    private final Map<GasReactionData.EffectType, Integer> counts = new EnumMap<>(GasReactionData.EffectType.class);
    private final Map<GasReactionData.EffectType, Double> energy = new EnumMap<>(GasReactionData.EffectType.class);
    private final Map<GasReactionData.EffectType, Map<GasType, Double>> species = new EnumMap<>(GasReactionData.EffectType.class);
    private final TegFixtureCombustionAccounting combustion = new TegFixtureCombustionAccounting();

    record Key(BlockPos position, long cycleIdentity, Channel channel) {
        Key { position = position.immutable(); }
    }
    private record Batch(List<GasReactionEvaluator.Event> events, Map<GasType,Double> cleanup) { }
    record Summary(int cells, int cycles, int eventCount, Map<GasReactionData.EffectType, Integer> counts,
                   Map<GasReactionData.EffectType, Double> energyJoules,
                   Map<GasReactionData.EffectType, Map<GasType, Double>> speciesMoles) { }

    private final Map<GasType, Double> cleanup = new EnumMap<>(GasType.class);
    void markBaseline(BlockPos position, long cycleIdentity, List<GasReactionEvaluator.Event> events) {
        markBaseline(position, cycleIdentity, Channel.WHOLE_AIR, events);
    }
    void markBaseline(BlockPos position, long cycleIdentity, Channel channel, List<GasReactionEvaluator.Event> events) {
        markBaseline(position,cycleIdentity,channel,events,Map.of());
    }
    void markBaseline(BlockPos position, long cycleIdentity, Channel channel, List<GasReactionEvaluator.Event> events,
                      Map<GasType,Double> actualCleanup) {
        var key=new Key(position,cycleIdentity,java.util.Objects.requireNonNull(channel,"channel"));
        var batch=new Batch(events==null?List.of():List.copyOf(events),validatedCleanup(channel,actualCleanup));
        var prior=batches.putIfAbsent(key,batch);
        if(prior!=null&&!prior.equals(batch))throw new IllegalArgumentException("committed whole-air event batch changed for "+key);
        seen.add(key);
    }

    void observe(BlockPos position, long cycleIdentity, List<GasReactionEvaluator.Event> events) {
        observe(position,cycleIdentity,Channel.WHOLE_AIR,events,Map.of());
    }
    void observe(BlockPos position, long cycleIdentity, Channel channel, List<GasReactionEvaluator.Event> events,
                 Map<GasType, Double> actualCleanup) {
        var key=new Key(position,cycleIdentity,java.util.Objects.requireNonNull(channel,"channel"));
        var acceptedCleanup=validatedCleanup(channel,actualCleanup);
        var batch=new Batch(events==null?List.of():List.copyOf(events),acceptedCleanup);
        var prior=batches.get(key);
        if(prior!=null){if(!prior.equals(batch))throw new IllegalArgumentException("committed event/cleanup batch changed for "+key);return;}
        batches.put(key,batch);
        if(!seen.add(key))return;
        acceptedCleanup.forEach((gas,amount)->cleanup.merge(gas,amount,Double::sum));
        for (var event : batch.events()) {
            var effect = event.effect();
            counts.merge(effect, 1, Integer::sum);
            energy.merge(effect, event.energyDeltaJoules(), Double::sum);
            var delta = species.computeIfAbsent(effect, ignored -> new EnumMap<>(GasType.class));
            event.speciesDelta().forEach((gas, amount) -> delta.merge(gas, amount, Double::sum));
        }
        combustion.observe(batch.events());
    }

    private static Map<GasType,Double> validatedCleanup(Channel channel, Map<GasType,Double> actualCleanup) {
        if (actualCleanup == null) throw new IllegalArgumentException("missing actual split cleanup receipt");
        var acceptedCleanup = new EnumMap<GasType,Double>(GasType.class);
        actualCleanup.forEach((gas, amount) -> {
            if (gas == null || amount == null || !Double.isFinite(amount) || amount <= 0
                    || amount.floatValue() >= 2 * com.juicyslew.moonstation14.ms14.atmos.core.GasMixture.GAS_MIN_MOLES)
                throw new IllegalArgumentException("invalid actual split cleanup receipt");
            acceptedCleanup.put(gas,amount);
        });
        if (channel != Channel.HOTSPOT && !acceptedCleanup.isEmpty()) throw new IllegalArgumentException("cleanup receipt is hotspot-only");
        return Map.copyOf(acceptedCleanup);
    }

    Summary summary() {
        Map<GasReactionData.EffectType, Map<GasType, Double>> frozen = new EnumMap<>(GasReactionData.EffectType.class);
        species.forEach((effect, values) -> frozen.put(effect, Map.copyOf(values)));
        return new Summary(seen.size(), seen.stream().map(Key::cycleIdentity).collect(java.util.stream.Collectors.toSet()).size(),
                counts.values().stream().mapToInt(Integer::intValue).sum(), Map.copyOf(counts), Map.copyOf(energy), Map.copyOf(frozen));
    }

    TegFixtureCombustionAccounting.Snapshot combustionSnapshot() { return combustion.snapshot(); }
    Map<GasType, Double> cleanupSnapshot() { return Map.copyOf(cleanup); }
}
