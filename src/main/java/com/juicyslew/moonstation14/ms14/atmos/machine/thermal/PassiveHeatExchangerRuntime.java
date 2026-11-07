package com.juicyslew.moonstation14.ms14.atmos.machine.thermal;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/** Callable live passive-exchanger processor. Scheduler/cadence ownership remains with AtmosDeviceRuntime. */
public final class PassiveHeatExchangerRuntime {
    public enum Status { COMMITTED, UNCHANGED, STALE, REJECTED }
    public record Result(Status status, double movedMoles, double pipeEnergyDeltaJoules,
                         double environmentEnergyDeltaJoules, double radiatedSinkJoules,
                         AtmosphereService.ExteriorBoundary.Kind boundaryKind) {
        public Result(Status status, double movedMoles, double pipeEnergyDeltaJoules,
                      double environmentEnergyDeltaJoules, double radiatedSinkJoules) {
            this(status, movedMoles, pipeEnergyDeltaJoules, environmentEnergyDeltaJoules, radiatedSinkJoules, null);
        }
    }
    private PassiveHeatExchangerRuntime() { }

    /** Processes one resolved inlet/outlet pair; finite-cell exchange is prepared and committed atomically. */
    public static Result process(ServerLevel level, AtmosMachineBlockEntity host,
                                 AtmosMachineProfile profile, double dtSeconds) {
        if (level == null || host == null || profile == null || profile.kind() != AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER
                || profile.heatExchanger() == null || !Double.isFinite(dtSeconds) || dtSeconds < 0
                || level.isClientSide || !level.getServer().isSameThread() || host.isRemoved()) return rejected();
        var ports = host.gasPorts();
        var inPort = ports.stream().filter(p -> p.id().equals("inlet")).findFirst().orElse(null);
        var outPort = ports.stream().filter(p -> p.id().equals("outlet")).findFirst().orElse(null);
        if (inPort == null || outPort == null) return rejected();
        var work = LoadedPipeGasWork.capture(level, List.of(
                new LoadedPipeGasWork.Port(new PipeTopology.NodeKey(host.getBlockPos(), inPort.layer(), inPort.id())),
                new LoadedPipeGasWork.Port(new PipeTopology.NodeKey(host.getBlockPos(), outPort.layer(), outPort.id())))).orElse(null);
        if (work == null || work.aliases()) return rejected();
        var inlet = work.componentFor(new PipeTopology.NodeKey(host.getBlockPos(), inPort.layer(), inPort.id())).orElse(null);
        var outlet = work.componentFor(new PipeTopology.NodeKey(host.getBlockPos(), outPort.layer(), outPort.id())).orElse(null);
        if (inlet == null || outlet == null || inlet.root().equals(outlet.root())) return rejected();
        var exchanger = profile.heatExchanger();
        var flow = PassiveHeatExchangerFlowPolicy.plan(inlet.aggregate(), outlet.aggregate(),
                exchanger.conductivityMolesPerKpaPerS(), dtSeconds).orElse(null);
        if (flow == null) return new Result(Status.UNCHANGED, 0, 0, 0, 0);

        var environment = AtmosphereService.INSTANCE.finiteCellEndpointForImmediateTransfer(level, host.getBlockPos()).orElse(null);
        var boundary = environment == null
                ? AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, host.getBlockPos()).orElse(null) : null;
        boolean cosmicAllowed = boundary != null && boundary.kind() == AtmosphereService.ExteriorBoundary.Kind.SPACE;
        boolean planetary = boundary != null && boundary.kind() == AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT;
        if (environment == null && boundary == null) return new Result(Status.REJECTED, 0, 0, 0, 0);
        var environmentBefore = environment != null ? environment.snapshot()
                : planetary ? boundary.ambient() : null;
        if (environment != null && environmentBefore == null) return new Result(Status.REJECTED, 0, 0, 0, 0);
        // A finite claimed vacuum has no thermal reservoir. Cosmic radiation is only
        // modeled for a verified exterior SPACE boundary, never as a finite-cell fallback.
        boolean unsupportedFiniteVacuum = environment != null
                && (environmentBefore.heatCapacity() <= 0 || environmentBefore.totalMoles() <= 0);
        var exchange = PassiveHeatExchangePolicy.exchange(flow.packet(), environmentBefore, dtSeconds,
                profile.simulation().tileRadiationLossWatts(), profile.heatExchanger().radiationCoefficient(),
                profile.heatExchanger().convectionCoefficientJPerKPerS(), unsupportedFiniteVacuum);
        // Keep extracted packet thermalized before merging at the receiving side.
        // Flow plans expose the merged destination; apply only the extracted packet's thermal delta,
        // preserving transferred species and all pre-existing receiver moles.
        var changed = flow.movedMoles();
        if (changed <= 0) return new Result(Status.UNCHANGED, 0, 0, 0, 0);
        double packetEnergyDelta = exchange.packetAfter().thermalEnergy() - flow.packet().thermalEnergy();
        var inletAfter = flow.inletAfter();
        var outletAfter = flow.outletAfter();
        if (flow.receivingInlet()) inletAfter = flow.inletAfter().withEnergyDelta(packetEnergyDelta);
        else outletAfter = flow.outletAfter().withEnergyDelta(packetEnergyDelta);
        double pipeDelta = inletAfter.thermalEnergy() + outletAfter.thermalEnergy()
                - inlet.aggregate().thermalEnergy() - outlet.aggregate().thermalEnergy();
        double envDelta = environmentBefore == null ? 0
                : exchange.environmentAfter().thermalEnergy() - environmentBefore.thermalEnergy();
        if (environmentBefore != null && environmentBefore.heatCapacity() > 0 && environmentBefore.totalMoles() > 0) {
            if (!exchange.environmentAfter().gasMoles().equals(environmentBefore.gasMoles())
                    || Math.abs(pipeDelta + envDelta) > Math.max(0.1, Math.ulp((float) (Math.abs(pipeDelta) + Math.abs(envDelta))) * 8))
                return rejected();
        }
        double sink = environmentBefore == null || environmentBefore.heatCapacity() <= 0
                || environmentBefore.totalMoles() <= 0 ? exchange.radiatedEnergyJoules() : 0;
        if (environmentBefore == null && !cosmicAllowed) return rejected();
        if (planetary && Math.abs(pipeDelta + envDelta) > Math.max(0.1, Math.ulp((float) (Math.abs(pipeDelta) + Math.abs(envDelta))) * 8)) return rejected();
        if (sink > 0 && Math.abs(pipeDelta + sink) > Math.max(0.1, Math.ulp((float) Math.abs(pipeDelta)) * 8)) return rejected();
        var pipeMutation = LoadedPipeGasWork.prepare(level, work,
                Map.of(inlet.root(), inletAfter, outlet.root(), outletAfter), pipeDelta);
        if (pipeMutation.isEmpty()) return new Result(Status.STALE, 0, 0, 0, 0);
        try {
            if (environment == null || sink > 0) {
                if (environment == null && (boundary == null || !boundary.validate()))
                    return new Result(Status.STALE, 0, 0, 0, 0);
                if (environment != null) {
                    var current = environment.snapshot();
                    if (!environment.eligible() || !current.gasMoles().equals(environmentBefore.gasMoles())
                            || Double.compare(current.temperatureKelvin(), environmentBefore.temperatureKelvin()) != 0)
                        return new Result(Status.STALE, 0, 0, 0, 0);
                }
                PipeGasTransaction.commit(pipeMutation.get());
            } else {
                var envMutation = PipeGasTransaction.prepare(environment, environmentBefore, exchange.environmentAfter());
                PipeGasTransaction.commit(pipeMutation.get(), envMutation);
            }
        } catch (PipeGasTransaction.RecoveryRequiredException recovery) { throw recovery; }
        catch (RuntimeException failure) { return new Result(Status.STALE, 0, 0, 0, 0); }
        // For planetary ambient, envDelta is the signed heat receipt of the fixed reservoir;
        // its temperature/state is intentionally not persisted or mutated.
        return new Result(Status.COMMITTED, changed, pipeDelta, envDelta, sink,
                environment == null ? boundary.kind() : null);
    }
    private static Result rejected() { return new Result(Status.REJECTED, 0, 0, 0, 0); }
}
