package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.machine.flow.PressurePumpFlowPolicy;
import com.juicyslew.moonstation14.ms14.atmos.machine.flow.GasMixerFlowPolicy;
import com.juicyslew.moonstation14.ms14.atmos.machine.flow.VolumePumpFlowPolicy;
import com.juicyslew.moonstation14.ms14.atmos.machine.flow.GasFilterFlowPolicy;
import com.juicyslew.moonstation14.ms14.atmos.machine.thermal.GasThermostatPolicy;
import com.juicyslew.moonstation14.ms14.atmos.machine.thermal.PassiveHeatExchangerRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Server-side processor for resolved atmos-machine compositions. Pipe inventory remains owner-managed. */
public final class AtmosDeviceRuntime {
    public enum WorkStatus { IDLE, PASSIVE_EQUALIZATION, COMMITTED, UNCHANGED, STALE, REJECTED }

    /** Runtime measurements only; gas is inventory-owner state. Last-work values persist across idle ticks.
     * lastActualPaidInputJoules is per-event budget actually debited after an accepted operation, not
     * the full APC allocation/settlement meter (which remains separately owned by PowerRuntime).
     */
    public record State(long lastPassiveWorldTick, long lastUnpoweredProcessWorldTick,
                        boolean thermostatHysteresisOn, WorkStatus lastWorkStatus, boolean lastPumpBlocked,
                        double lastMoles, double lastThermalJoules, double lastActualPaidInputJoules) {
        public State {
            if (lastWorkStatus == null) throw new IllegalArgumentException("work status required");
            if (!Double.isFinite(lastMoles) || lastMoles < 0 || !Double.isFinite(lastThermalJoules)
                    || !Double.isFinite(lastActualPaidInputJoules) || lastActualPaidInputJoules < 0)
                throw new IllegalArgumentException("runtime work measurements must be finite and nonnegative where applicable");
        }
        public State(long passive, long unpowered, boolean hysteresis, WorkStatus status,
                     double moles, double thermal, double paid) {
            this(passive, unpowered, hysteresis, status, false, moles, thermal, paid);
        }
        public static State initial() {
            return new State(-1, -1, false, WorkStatus.IDLE, false, 0, 0, 0);
        }
    }

    private AtmosDeviceRuntime() { }

    /** Drops only the short-lived accepted-transfer observation when an operator disables a device. */
    public static State clearAcceptedTransfer(State state) {
        if (state == null) return State.initial();
        return new State(state.lastPassiveWorldTick(), state.lastUnpoweredProcessWorldTick(),
                state.thermostatHysteresisOn(), state.lastWorkStatus(), state.lastPumpBlocked(),
                0, state.lastThermalJoules(), state.lastActualPaidInputJoules());
    }

    /** Cadence adapter uses world time only for passive pipe relaxation; paid APC intervals are never inferred here. */
    public static State tick(ServerLevel level, AtmosMachineBlockEntity host, State previous, long currentWorldTick) {
        State state = previous == null ? State.initial() : previous;
        if (level == null || host == null || host.isRemoved() || !level.getServer().isSameThread()) return state;
        AtmosMachineProfile profile = host.resolvedProfile();
        if (profile == null || !host.settingsValid()) return state;
        // The TEG core processor owns its paid interval and gas work independently.
        if (profile.teg() != null) return state;
        List<AtmosMachineBlockEntity.Port> actualPorts = host.gasPorts();
        if (actualPorts.isEmpty()) return clearAcceptedTransfer(state);
        long sampleTick = PowerRuntime.currentSampleTick(level);
        boolean unpoweredDue = (profile.kind() == AtmosMachineProfile.Kind.MIXER
                || profile.kind() == AtmosMachineProfile.Kind.FILTER
                || profile.kind() == AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER)
                && due(state.lastUnpoweredProcessWorldTick(), sampleTick, profile.simulation().intervalTicks());
        boolean passiveDue = profile.kind() != AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER
                && due(state.lastPassiveWorldTick(), currentWorldTick, PowerRuntime.TICKS_PER_SOLVE);
        boolean poweredProcessor = profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP
                || profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP
                || profile.thermostat() != null;
        double eventDt = profile.simulation().deviceIntervalSeconds();
        double eventCost = profile.configuredReceiverLoadWatts() * eventDt;
        boolean paidDue = poweredProcessor && host.paidMachineEventDue(profile, sampleTick, eventCost);
        if (paidDue) host.completePaidMachineAttempt();
        double inputJ = 0;
        long passiveTick = state.lastPassiveWorldTick();
        long unpoweredTick = state.lastUnpoweredProcessWorldTick();
        if (!passiveDue && !unpoweredDue && !paidDue) return state;
        boolean pumpBlocked = state.lastPumpBlocked();

        // The exchanger owns its paired pipe/environment transaction and must not enter the
        // generic pipe equalization/capture path above or below.
        if (profile.kind() == AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER && unpoweredDue) {
            PassiveHeatExchangerRuntime.Result result = PassiveHeatExchangerRuntime.process(
                    level, host, profile, eventDt);
            if (result.status() == PassiveHeatExchangerRuntime.Status.COMMITTED) {
                host.recordCommittedUnpoweredWork(sampleTick, result.movedMoles(), result.pipeEnergyDeltaJoules());
                host.recordThermalReceipt(result.boundaryKind(), result.environmentEnergyDeltaJoules(), result.radiatedSinkJoules());
            }
            return passiveExchangerState(state, passiveTick, sampleTick, result);
        }

        List<LoadedPipeGasWork.Port> ports = actualPorts.stream().map(port -> new LoadedPipeGasWork.Port(
                new PipeTopology.NodeKey(host.getBlockPos(), port.layer(), port.id()))).toList();
        if (passiveDue) {
            LoadedPipeGasWork.Snapshot passive = LoadedPipeGasWork.capture(level, ports).orElse(null);
            if (passive != null) {
                Map<PipeTopology.NodeKey, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> equalized = new LinkedHashMap<>();
                for (LoadedPipeGasWork.Component c : passive.components().values()) equalized.put(c.root(), c.aggregate());
                LoadedPipeGasWork.Outcome result = LoadedPipeGasWork.commit(level, passive, equalized, 0);
                if (result == LoadedPipeGasWork.Outcome.STALE || result == LoadedPipeGasWork.Outcome.REJECTED)
                    return new State(currentWorldTick, unpoweredDue ? currentWorldTick : unpoweredTick, state.thermostatHysteresisOn(),
                            result == LoadedPipeGasWork.Outcome.STALE ? WorkStatus.STALE : WorkStatus.REJECTED, 0, 0, inputJ);
            }
            passiveTick = currentWorldTick;
        }
        if (unpoweredDue) unpoweredTick = sampleTick;
        // Re-capture after passive CAS: the old inventory snapshot is intentionally never reused.
        boolean motorDue = paidDue && (profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP
                || profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP);
        boolean filterDue = profile.kind() == AtmosMachineProfile.Kind.FILTER && unpoweredDue
                && host.control("enabled", profile.filter().enabled());
        if (profile.thermostat() != null && paidDue) {
            LoadedPipeGasWork.Snapshot work = LoadedPipeGasWork.capture(level, ports).orElse(null);
            if (work == null)
                return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), WorkStatus.REJECTED, 0, 0, inputJ);
            var pipe = work.componentFor(key(host, "pipe")).orElse(null);
            if (pipe == null) return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), WorkStatus.REJECTED, 0, 0, inputJ);
            AtmosMachineProfile.Thermostat thermo = profile.thermostat();
            var settings = host.settingsSnapshot();
            double setpoint = settings.thermostatSetpointK().orElse(thermo.targetK());
            var thermostatProfile = new GasThermostatPolicy.Profile(thermo.inputPowerWatts(), thermo.coefficientOfPerformance(),
                    thermo.minimumTemperatureK(), thermo.maximumTemperatureK(), thermo.toleranceK());
            var thermal = GasThermostatPolicy.step(pipe.aggregate(), thermostatProfile,
                    new GasThermostatPolicy.State(setpoint, state.thermostatHysteresisOn()),
                    eventDt, eventCost);
            boolean enabled = host.control("enabled", thermo.enabled());
            var gasAfter = enabled ? thermal.gasAfter() : pipe.aggregate();
            double thermalDelta = gasAfter.thermalEnergy() - pipe.aggregate().thermalEnergy();
            LoadedPipeGasWork.Outcome result = LoadedPipeGasWork.commit(level, work, Map.of(pipe.root(), gasAfter), thermalDelta);
            WorkStatus status = outcomeStatus(result);
            boolean committedState = result == LoadedPipeGasWork.Outcome.COMMITTED || result == LoadedPipeGasWork.Outcome.UNCHANGED;
            if (result == LoadedPipeGasWork.Outcome.COMMITTED && enabled
                    && host.completePaidMachineEvent(sampleTick, eventCost, 0, thermalDelta)) inputJ = eventCost;
            return new State(passiveTick, unpoweredTick, committedState && enabled ? thermal.stateAfter().on() : state.thermostatHysteresisOn(),
                    status, 0, committedState ? thermalDelta : 0, inputJ);
        }
        if (motorDue || filterDue || (profile.kind() == AtmosMachineProfile.Kind.MIXER && unpoweredDue
                && host.control("enabled", false))) {
            LoadedPipeGasWork.Snapshot work = LoadedPipeGasWork.capture(level, ports).orElse(null);
            if (work == null) {
                if (profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP
                        || profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP || profile.kind() == AtmosMachineProfile.Kind.MIXER)
                    recordFlowDecision(host, profile, null, Map.of(), Map.of(), Double.NaN, Double.NaN, Double.NaN,
                            Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                            0, LoadedPipeGasWork.Outcome.REJECTED, 0, sampleTick, currentWorldTick);
                return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), WorkStatus.REJECTED, 0, 0, inputJ);
            }
            Map<PipeTopology.NodeKey, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> proposed = new LinkedHashMap<>();
            double moved = 0;
            Map<String, LoadedPipeGasWork.Component> decisionComponents = new LinkedHashMap<>();
            double decisionTarget = Double.NaN, decisionRatio = Double.NaN, decisionRate = Double.NaN,
                    decisionLower = Double.NaN, decisionUpper = Double.NaN, decisionOverclock = Double.NaN,
                    decisionSpeedup = Double.NaN;
            if (profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP && host.control("enabled", profile.pump().enabled())) {
                PipeTopology.NodeKey in = key(host, "inlet"), out = key(host, "outlet");
                var a = work.componentFor(in).orElse(null); var b = work.componentFor(out).orElse(null);
                if (a != null) decisionComponents.put("inlet", a); if (b != null) decisionComponents.put("outlet", b);
                var settings = host.settingsSnapshot();
                double target = settings.targetPressureKpa().orElse(profile.pump().targetPressureKpa().orElseThrow());
                double maximum = profile.pump().maxPressureKpa().orElseThrow();
                decisionTarget = target; decisionUpper = maximum;
                if (a != null && b != null && !a.root().equals(b.root())) {
                    var plan = PressurePumpFlowPolicy.plan(a.aggregate(), b.aggregate(), a.aggregate().volumeCubicMeters(), b.aggregate().volumeCubicMeters(), target, maximum);
                    if (plan.isPresent()) { proposed.put(a.root(), plan.get().sourceAfter()); proposed.put(b.root(), plan.get().destinationAfter()); moved = plan.get().packet().totalMoles(); }
                }
            } else if (profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP && host.control("enabled", profile.pump().enabled())) {
                var a = work.componentFor(key(host, "inlet")).orElse(null); var b = work.componentFor(key(host, "outlet")).orElse(null);
                if (a != null) decisionComponents.put("inlet", a); if (b != null) decisionComponents.put("outlet", b);
                var p = profile.pump(); var settings = host.settingsSnapshot();
                double rate = settings.transferRateM3PerSecond().orElse(p.transferRate().orElseThrow() / 1000d);
                decisionRate = rate; decisionLower = p.lowerThresholdKpa().orElseThrow(); decisionUpper = p.higherThresholdKpa().orElseThrow();
                decisionOverclock = p.overclockThresholdKpa().orElseThrow();
                decisionSpeedup = profile.simulation().pumpSpeedup();
                if (a != null && b != null && !a.root().equals(b.root())) {
                    pumpBlocked = a.aggregate().pressureKpa(a.aggregate().volumeCubicMeters()) <= p.lowerThresholdKpa().orElseThrow()
                            || b.aggregate().pressureKpa(b.aggregate().volumeCubicMeters()) >= p.higherThresholdKpa().orElseThrow();
                    var plan = VolumePumpFlowPolicy.plan(a.aggregate(), b.aggregate(), java.util.Optional.empty(),
                            a.aggregate().volumeCubicMeters(), b.aggregate().volumeCubicMeters(), rate, profile.simulation().pumpSpeedup(), eventDt,
                            p.lowerThresholdKpa().orElseThrow(), p.higherThresholdKpa().orElseThrow(), false,
                            p.overclockThresholdKpa().orElseThrow(), 0);
                    if (plan.isPresent()) {
                        proposed.put(a.root(), plan.get().inletAfter()); proposed.put(b.root(), plan.get().outletAfter());
                        moved = plan.get().movedByGas().values().stream().mapToDouble(Double::doubleValue).sum();
                    }
                }
            } else if (profile.kind() == AtmosMachineProfile.Kind.MIXER) {
                PipeTopology.NodeKey oneKey = key(host, "inlet"), twoKey = key(host, "filter"), outKey = key(host, "outlet");
                var a = work.componentFor(oneKey).orElse(null); var b = work.componentFor(twoKey).orElse(null); var c = work.componentFor(outKey).orElse(null);
                if (a != null) decisionComponents.put("inlet", a); if (b != null) decisionComponents.put("filter", b); if (c != null) decisionComponents.put("outlet", c);
                var settings = host.settingsSnapshot();
                double ratio = settings.mixerFractionA().orElse(profile.mixer().ratios().getFirst());
                double target = settings.targetPressureKpa().orElse(profile.mixer().targetPressureKpa());
                decisionRatio = ratio; decisionTarget = target; decisionUpper = profile.mixer().maxPressureKpa();
                if (a != null && b != null && c != null && !a.root().equals(b.root()) && !a.root().equals(c.root()) && !b.root().equals(c.root())) {
                    var plan = GasMixerFlowPolicy.plan(a.aggregate(), b.aggregate(), c.aggregate(), a.aggregate().volumeCubicMeters(), b.aggregate().volumeCubicMeters(), c.aggregate().volumeCubicMeters(), ratio, target, profile.mixer().maxPressureKpa());
                    if (plan.isPresent()) { proposed.put(a.root(), plan.get().inletOneAfter()); proposed.put(b.root(), plan.get().inletTwoAfter()); proposed.put(c.root(), plan.get().outletAfter()); moved = plan.get().inletOneMoles() + plan.get().inletTwoMoles(); }
                }
            } else if (profile.kind() == AtmosMachineProfile.Kind.FILTER) {
                var a = work.componentFor(key(host, "inlet")).orElse(null); var f = work.componentFor(key(host, "filter")).orElse(null);
                var c = work.componentFor(key(host, "outlet")).orElse(null);
                if (a == null || f == null || c == null || a.root().equals(f.root()) || a.root().equals(c.root()) || f.root().equals(c.root()))
                    return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), WorkStatus.REJECTED, 0, 0, inputJ);
                var settings = host.settingsSnapshot();
                double rate = settings.transferRateM3PerSecond().orElse(profile.filter().transferRate() / 1000d);
                double dt = eventDt;
                var plan = GasFilterFlowPolicy.plan(a.aggregate(), f.aggregate(), c.aggregate(), settings.filterGas(),
                        a.aggregate().volumeCubicMeters(), f.aggregate().volumeCubicMeters(), c.aggregate().volumeCubicMeters(),
                        rate, profile.simulation().pumpSpeedup(), dt, Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA);
                if (plan.isPresent()) {
                    proposed.put(a.root(), plan.get().inletAfter()); proposed.put(f.root(), plan.get().filterAfter()); proposed.put(c.root(), plan.get().outletAfter());
                    moved = plan.get().filteredByGas().values().stream().mapToDouble(Double::doubleValue).sum()
                            + plan.get().passedByGas().values().stream().mapToDouble(Double::doubleValue).sum();
                }
            }
            if (!proposed.isEmpty()) {
                LoadedPipeGasWork.Outcome result = LoadedPipeGasWork.commit(level, work, proposed, 0);
                WorkStatus status = outcomeStatus(result);
                if (result == LoadedPipeGasWork.Outcome.COMMITTED || result == LoadedPipeGasWork.Outcome.UNCHANGED) {
                    if (paidDue && result == LoadedPipeGasWork.Outcome.COMMITTED
                            && host.completePaidMachineEvent(sampleTick, eventCost, moved, 0)) inputJ = eventCost;
                    else if (!paidDue && result == LoadedPipeGasWork.Outcome.COMMITTED
                        && (profile.kind() == AtmosMachineProfile.Kind.MIXER || profile.kind() == AtmosMachineProfile.Kind.FILTER))
                        host.recordCommittedUnpoweredWork(sampleTick, moved);
                }
                recordFlowDecision(host, profile, work, decisionComponents, proposed, decisionTarget, decisionRatio,
                        decisionRate, decisionLower, decisionUpper, decisionOverclock, decisionSpeedup, moved,
                        result, inputJ, sampleTick, currentWorldTick);
                double accepted = result == LoadedPipeGasWork.Outcome.COMMITTED ? moved : 0;
                return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), status, pumpBlocked,
                        accepted > 0 ? accepted : 0, 0, inputJ);
            }
            if (profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP
                    || profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP || profile.kind() == AtmosMachineProfile.Kind.MIXER)
                recordFlowDecision(host, profile, work, decisionComponents, proposed, decisionTarget, decisionRatio,
                        decisionRate, decisionLower, decisionUpper, decisionOverclock, decisionSpeedup, moved,
                        LoadedPipeGasWork.Outcome.UNCHANGED, 0, sampleTick, currentWorldTick);
            return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), WorkStatus.UNCHANGED, pumpBlocked, 0, 0, inputJ);
        }
        return passiveEqualizationState(state, passiveTick, unpoweredTick, passiveDue, inputJ);
    }

    private static void recordFlowDecision(AtmosMachineBlockEntity host, AtmosMachineProfile profile,
            LoadedPipeGasWork.Snapshot work, Map<String, LoadedPipeGasWork.Component> components,
            Map<PipeTopology.NodeKey, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> proposed,
            double target, double ratio, double rate, double lower, double upper, double overclock, double speedup,
            double moved, LoadedPipeGasWork.Outcome outcome, double actualPaidInputJoules,
            long sampleTick, long worldTick) {
        List<GasWorkDecision.Input> inputs = new java.util.ArrayList<>();
        Map<String, PipeTopology.NodeKey> roots = new LinkedHashMap<>();
        for (var port : components.entrySet()) {
            var c = port.getValue(); var gas = c.aggregate(); var root = c.root();
            roots.put(port.getKey(), root);
            inputs.add(new GasWorkDecision.Input(port.getKey(), root, gas.volumeCubicMeters(), gas,
                    gas.pressureKpa(gas.volumeCubicMeters())));
        }
        int expectedInputs = profile.kind() == AtmosMachineProfile.Kind.MIXER ? 3 : 2;
        var status = work == null || components.size() < expectedInputs ? GasWorkDecision.Outcome.UNAVAILABLE
                : proposed.isEmpty() && outcome == LoadedPipeGasWork.Outcome.UNCHANGED ? GasWorkDecision.Outcome.NO_PLAN : switch (outcome) {
            case COMMITTED -> GasWorkDecision.Outcome.COMMITTED;
            case UNCHANGED -> GasWorkDecision.Outcome.UNCHANGED;
            case STALE -> GasWorkDecision.Outcome.STALE;
            case REJECTED -> GasWorkDecision.Outcome.REJECTED;
        };
        Map<PipeTopology.NodeKey, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> committedAfter =
                outcome == LoadedPipeGasWork.Outcome.COMMITTED ? proposed : Map.of();
        host.recordFlowDecision(new GasWorkDecision(host.nextFlowDecisionSequence(), worldTick, sampleTick,
                profile.simulation().intervalTicks(), profile.simulation().deviceIntervalSeconds(), profile.kind().name(),
                inputs, roots, target, ratio, rate, lower, upper, overclock, speedup, actualPaidInputJoules,
                !proposed.isEmpty(), status == GasWorkDecision.Outcome.UNAVAILABLE || proposed.isEmpty()
                        ? Double.NaN : moved, status,
                status == GasWorkDecision.Outcome.UNAVAILABLE ? Double.NaN
                        : outcome == LoadedPipeGasWork.Outcome.COMMITTED ? moved : 0, proposed, committedAfter));
    }

    static State passiveEqualizationState(State state, long passiveTick, long unpoweredTick,
                                          boolean equalized, double inputJ) {
        WorkStatus status = equalized ? WorkStatus.PASSIVE_EQUALIZATION : WorkStatus.IDLE;
        return new State(passiveTick, unpoweredTick, state.thermostatHysteresisOn(), status,
                state.lastPumpBlocked(), equalized ? state.lastMoles() : 0, 0, inputJ);
    }

    private static WorkStatus outcomeStatus(LoadedPipeGasWork.Outcome result) {
        return switch (result) { case COMMITTED -> WorkStatus.COMMITTED; case UNCHANGED -> WorkStatus.UNCHANGED; case STALE -> WorkStatus.STALE; case REJECTED -> WorkStatus.REJECTED; };
    }

    static State passiveExchangerState(State previous, long passiveTick, long sampleTick,
                                       PassiveHeatExchangerRuntime.Result result) {
        WorkStatus status = switch (result.status()) {
            case COMMITTED -> WorkStatus.COMMITTED;
            case UNCHANGED -> WorkStatus.UNCHANGED;
            case STALE -> WorkStatus.STALE;
            case REJECTED -> WorkStatus.REJECTED;
        };
        return new State(passiveTick, sampleTick, previous.thermostatHysteresisOn(), status,
                result.movedMoles(), result.pipeEnergyDeltaJoules(), 0);
    }

    private static boolean due(long previous, long now, long interval) {
        return previous < 0 || now >= previous && now - previous >= interval;
    }
    private static PipeTopology.NodeKey key(AtmosMachineBlockEntity host, String name) {
        var port = host.gasPorts().stream().filter(p -> p.id().equals(name)).findFirst().orElse(null);
        if (port == null) throw new IllegalArgumentException("resolved machine is missing port " + name);
        return new PipeTopology.NodeKey(host.getBlockPos(), port.layer(), port.id());
    }

}
