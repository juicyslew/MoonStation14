package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import java.util.Map;
import java.util.EnumMap;
import java.util.Set;

/** Queued, source-addressed atmospheric monitor report; measurement absence is explicit. */
public sealed interface AtmosAlarmPayload extends DevicePayload permits AtmosAlarmPayload.Measurement, AtmosAlarmPayload.Reset, AtmosAlarmPayload.Relay, AtmosAlarmPayload.RelayRemove, AtmosAlarmPayload.RelayReset, AtmosAlarmPayload.RelaySync {
    record Reset() implements AtmosAlarmPayload { }
    /** Alarm relay is deliberately distinct from physical monitor evidence. */
    record Relay(java.util.UUID source, java.util.Map<java.util.UUID,AtmosAlarmSeverity> reports,
                 Set<String> sourceTags) implements AtmosAlarmPayload {
        public Relay { if (source == null || reports == null || sourceTags == null) throw new IllegalArgumentException("incomplete alarm relay"); reports=java.util.Map.copyOf(reports); sourceTags=Set.copyOf(sourceTags); if(reports.values().stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("invalid alarm relay state"); }
    }
    /** Removes one original report identity from a relayed map without resetting other sources. */
    record RelayRemove(java.util.UUID source, java.util.UUID removedSource, Set<String> sourceTags) implements AtmosAlarmPayload {
        public RelayRemove { if(source==null||removedSource==null||sourceTags==null)throw new IllegalArgumentException("incomplete relay removal");sourceTags=Set.copyOf(sourceTags); }
    }
    record RelayReset(java.util.UUID source) implements AtmosAlarmPayload { public RelayReset { if(source==null)throw new IllegalArgumentException("missing relay source"); } }
    record RelaySync(java.util.UUID requester) implements AtmosAlarmPayload { public RelaySync { if(requester==null)throw new IllegalArgumentException("missing sync requester"); } }
    record Measurement(AtmosAlarmSeverity severity, double pressureKpa, double temperatureKelvin,
                       double totalMoles, Set<String> sourceTags, boolean valid, boolean fireEvidence,
                       Map<GasType, Double> gasMoles) implements AtmosAlarmPayload {
        public Measurement(AtmosAlarmSeverity severity, double pressureKpa, double temperatureKelvin,
                           double totalMoles, Set<String> sourceTags, boolean valid, boolean fireEvidence) {
            this(severity, pressureKpa, temperatureKelvin, totalMoles, sourceTags, valid, fireEvidence, Map.of());
        }
        public Measurement {
            if (severity == null || sourceTags == null) throw new IllegalArgumentException("incomplete alarm measurement");
            sourceTags = Set.copyOf(sourceTags);
            if (gasMoles == null || gasMoles.size() > GasType.values().length)
                throw new IllegalArgumentException("invalid gas measurement");
            var copy = new EnumMap<GasType, Double>(GasType.class);
            gasMoles.forEach((gas, amount) -> {
                if (gas == null || amount == null || !Double.isFinite(amount) || amount < 0)
                    throw new IllegalArgumentException("invalid gas measurement");
                copy.put(gas, amount);
            });
            gasMoles = java.util.Collections.unmodifiableMap(copy);
            if (valid && (!Double.isFinite(pressureKpa) || !Double.isFinite(temperatureKelvin)
                    || !Double.isFinite(totalMoles) || totalMoles < 0))
                throw new IllegalArgumentException("invalid alarm measurement");
        }
    }
}
