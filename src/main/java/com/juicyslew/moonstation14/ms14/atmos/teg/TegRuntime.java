package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Paid-interval TEG gas processor. Caller owns one transient State per loaded core; it is not NBT-persisted. */
public final class TegRuntime {
    private TegRuntime() { }

    public static State initial() { return new State(new TegSupplyPolicy.State(0, 0), 0, -1, Status.IDLE, 0, 0, 0, Observation.still(), Observation.still(), null); }

    public static State tick(ServerLevel level, AtmosMachineBlockEntity core, State previous) {
        Objects.requireNonNull(previous, "previous");
        if (level == null || core == null || level.isClientSide || !level.getServer().isSameThread()) return previous;
        AtmosMachineProfile profile = core.resolvedProfile();
        if (!core.settingsValid() || profile == null || profile.kind() != AtmosMachineProfile.Kind.TEG_CENTER
                || profile.teg() == null || !core.control("enabled", true)) {
            core.clearTegSupplyOffer();
            core.clearPaidMachineCadence();
            return previous.with(Status.DISABLED, 0);
        }
        TegAssembly.Assembly assembly = TegAssembly.resolve(level, core).orElse(null);
        if (assembly == null) {
            core.clearTegSupplyOffer();
            core.clearPaidMachineCadence();
            return previous.with(Status.INVALID_ASSEMBLY, 0);
        }
        long sampleTick = PowerRuntime.currentSampleTick(level);
        double dt = profile.simulation().deviceIntervalSeconds();
        double eventCost = profile.configuredReceiverLoadWatts() * dt;
        if (!core.paidMachineEventDue(profile, sampleTick, eventCost)) {
            long age = sampleTick - previous.lastPaidSampleTick;
            return age > PowerRuntime.TICKS_PER_SOLVE ? previous.with(Status.NO_POWER, 0) : previous;
        }
        var captured = LoadedPipeGasWork.capture(level, List.of(new LoadedPipeGasWork.Port(assembly.inletA()),
                new LoadedPipeGasWork.Port(assembly.outletA()), new LoadedPipeGasWork.Port(assembly.inletB()),
                new LoadedPipeGasWork.Port(assembly.outletB()))).orElse(null);
        if (captured == null || captured.aliases()) {
            core.clearTegSupplyOffer();
            return previous.with(Status.SHARED_ROOT_REJECTED, sampleTick);
        }
        var ia = captured.componentFor(assembly.inletA()).orElseThrow();
        var oa = captured.componentFor(assembly.outletA()).orElseThrow();
        var ib = captured.componentFor(assembly.inletB()).orElseThrow();
        var ob = captured.componentFor(assembly.outletB()).orElseThrow();
        var ca = TegCirculationPolicy.plan(ia.aggregate(), oa.aggregate(), volume(ia), volume(oa));
        var cb = TegCirculationPolicy.plan(ib.aggregate(), ob.aggregate(), volume(ib), volume(ob));
        var thermal = TegThermodynamics.generate(ca.packet(), cb.packet(), profile.teg().efficiency(), profile.teg().powerFactor());
        Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> proposed = new LinkedHashMap<>();
        proposed.put(ia.root(), ca.inletAfter());
         proposed.put(oa.root(), PipeGasLedger.merge(List.of(oa.aggregate(), thermal.first()), volume(oa)));
        proposed.put(ib.root(), cb.inletAfter());
         proposed.put(ob.root(), PipeGasLedger.merge(List.of(ob.aggregate(), thermal.second()), volume(ob)));
        double netThermal = thermal.coldThermalEnergyAddedJoules() - thermal.hotThermalEnergyRemovedJoules();
        LoadedPipeGasWork.Outcome committed = LoadedPipeGasWork.commit(level, captured, proposed, netThermal);
        boolean accepted = committed == LoadedPipeGasWork.Outcome.COMMITTED || committed == LoadedPipeGasWork.Outcome.UNCHANGED;
        boolean paidWorkCommitted = accepted && core.completePaidMachineEvent(sampleTick, eventCost);
        double generated = accepted ? thermal.generatedEnergyJoules() : 0;
        TegSupplyPolicy.Result offer = TegSupplyPolicy.update(previous.supply, generated, dt,
                Math.max(0, core.hvDeliveredWatts()), accepted, profile.teg().rampFactor(),
                profile.teg().minimumPowerWatts(), profile.teg().smoothing());
        boolean published = core.publishTegSupplyOffer(offer.offeredMaximumSupplyWatts(), offer.offeredMaximumSupplyWatts(),
                paidWorkCommitted ? generated : 0);
        if (!published) core.clearTegSupplyOffer();
        EventObservation event = accepted ? new EventObservation(ca.packet().temperatureKelvin(), cb.packet().temperatureKelvin(),
                ca.packet().heatCapacity(), cb.packet().heatCapacity(), thermal.maximumWorkJoules(), thermal.effectiveEfficiency(),
                profile.teg().efficiency(), profile.teg().powerFactor(), dt, sampleTick, paidWorkCommitted, published) : previous.lastCommittedEvent;
        return new State(offer.state(), previous.cumulativeGeneratedJoules + generated, sampleTick,
                accepted ? (published ? Status.PROCESSED : Status.OFFER_REJECTED) : Status.GAS_COMMIT_REJECTED,
                generated, generated / dt, paidWorkCommitted ? eventCost : 0,
                accepted ? new Observation(ca.pressureDeltaKpa(), ca.transferredMoles()) : Observation.still(),
                accepted ? new Observation(cb.pressureDeltaKpa(), cb.transferredMoles()) : Observation.still(), event);
    }

    private static double volume(LoadedPipeGasWork.Component component) {
        return component.aggregate().volumeCubicMeters();
    }

    public enum Status { IDLE, DISABLED, INVALID_ASSEMBLY, NO_POWER, SHARED_ROOT_REJECTED, PROCESSED, OFFER_REJECTED, GAS_COMMIT_REJECTED }
    public record Observation(double pressureDeltaKpa, double transferredMoles) {
        public Observation { if (!Double.isFinite(pressureDeltaKpa) || !Double.isFinite(transferredMoles) || transferredMoles < 0) throw new IllegalArgumentException("invalid circulator observation"); }
        public static Observation still() { return new Observation(0, 0); }
    }
    /** Exact kernel packet inputs and outcome flags for one accepted gas commit; may be stale in later states. */
    public record EventObservation(double temperatureAKelvin, double temperatureBKelvin, double heatCapacityAJoulesPerKelvin,
                                   double heatCapacityBJoulesPerKelvin, double maximumWorkJoules,
                                   double effectiveEfficiency, double configuredEfficiency, double powerFactor,
                                   double intervalSeconds, long sampleTick, boolean paidWorkCommitted, boolean offerPublished) { }
    /** lastInputPaidJoules is event-credit debited after a committed event, not the full APC grant meter. */
    public record State(TegSupplyPolicy.State supply, double cumulativeGeneratedJoules, long lastPaidSampleTick,
                        Status status, double lastGeneratedJoules, double lastGeneratedWatts, double lastInputPaidJoules,
                         Observation circulatorA, Observation circulatorB, EventObservation lastCommittedEvent) {
        public State {
            Objects.requireNonNull(supply); Objects.requireNonNull(status);
            if (!Double.isFinite(cumulativeGeneratedJoules) || cumulativeGeneratedJoules < 0
                    || !Double.isFinite(lastGeneratedJoules) || lastGeneratedJoules < 0
                    || !Double.isFinite(lastGeneratedWatts) || lastGeneratedWatts < 0
                    || !Double.isFinite(lastInputPaidJoules) || lastInputPaidJoules < 0) throw new IllegalArgumentException("invalid TEG state");
        }
        public State(TegSupplyPolicy.State supply, double total, long tick, Status status, double generatedJ, double generatedW, double paidJ) {
            this(supply, total, tick, status, generatedJ, generatedW, paidJ, Observation.still(), Observation.still(), null);
        }
        public State(TegSupplyPolicy.State supply, double total, long tick, Status status, double generatedJ, double generatedW, double paidJ,
                     Observation circulatorA, Observation circulatorB) {
            this(supply, total, tick, status, generatedJ, generatedW, paidJ, circulatorA, circulatorB, null);
        }
        State with(Status next, long tick) {
            boolean keep = next == Status.NO_POWER;
            return new State(new TegSupplyPolicy.State(0, 0), cumulativeGeneratedJoules, tick, next, 0, 0,
                    lastInputPaidJoules, keep ? circulatorA : Observation.still(), keep ? circulatorB : Observation.still(), lastCommittedEvent);
        }
    }
}
