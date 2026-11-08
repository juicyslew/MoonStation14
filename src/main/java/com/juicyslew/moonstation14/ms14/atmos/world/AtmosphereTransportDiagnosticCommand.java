package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** Operator-only, bounded loaded-cell transport evidence; does not trigger ownership discovery. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class AtmosphereTransportDiagnosticCommand {
    private AtmosphereTransportDiagnosticCommand() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14atmoscheck").requires(s -> s.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> check(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))));
    }

    private static int check(CommandSourceStack source, BlockPos pos) {
        if (!source.hasPermission(2) || !(source.getLevel() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return 0;
        AtmosphereService service = AtmosphereService.INSTANCE;
        AtmosphereService.CellDiagnostic center = service.diagnosticCell(level, pos);
        send(source, "Atmosphere active; cell=" + pos.toShortString());
        send(source, cell("center", center));
        var owner = service.diagnosticOwnership(level, pos);
        send(source, "Ownership target: status=" + owner.status() + " reason=" + owner.reason()
                + " active-seed=" + owner.activeSeed() + " candidates=" + owner.candidateCount()
                + " pending=" + owner.pendingSource() + " retry-work=" + owner.retryWorkItems()
                + " last-rejected-seed=" + owner.lastRejectedSeed() + " reject-reason=" + owner.lastRejectedReason()
                + " witness-size=" + owner.lastRejectedWitnessSize());
        var ignitionEvents = service.diagnosticIgnitions(level, pos);
        var globalIgnitions = service.diagnosticIgnitions(level, null);
        var firstCycleIgnitions = service.diagnosticFirstIgnitions(level);
        send(source, "Ignition diagnostics: target-last=" + (ignitionEvents.isEmpty() ? "none" : ignitionEvents.getLast())
                + "; bounded-global-last=" + (globalIgnitions.isEmpty() ? "none" : globalIgnitions.getLast())
                + " (global event may be another cell; max 64 recent new ignition records)");
        send(source, "First ignition of recent cycle (global, may be another cell): "
                + (firstCycleIgnitions.isEmpty() ? "none" : firstCycleIgnitions.getLast())
                + " (one root per cycle, max 64 cycles)");
        for (Direction d : Direction.values()) {
            BlockPos neighbor = pos.relative(d);
            var face = service.diagnosticCell(level, neighbor);
            boolean open = center.gas() != null && face.gas() != null && !face.status().startsWith("unknown")
                    && AtmosphereTopology.canExchange(level, pos, neighbor);
            boolean boundary = center.status().equals("finite") && face.status().equals("exterior") && open;
            send(source, cell(d.getName(), face) + " open-face=" + open + " boundary-eligible=" + boundary);
        }
        send(source, "Exterior policy=" + (center.exteriorConfiguredVacuum() ? "vacuum" : "ambient")
                + "; ambient=" + gas(center.ambient()));
        var timing = service.diagnosticTiming(level);
        send(source, timing == null ? "Due-step timing: no completed due step recorded"
                : String.format(Locale.ROOT, "Due step %d ms ownership=%.3f discovery/solve+commit=%.3f LINDA=%.3f reactions=%.3f total=%.3f",
                        timing.gameTime(), timing.ownershipMs(), timing.discoverySolveCommitMs(), timing.lindaMs(), timing.reactionsMs(), timing.totalMs()));
        var runtime = service.diagnosticRuntime(level);
        send(source, runtime == null ? "Runtime telemetry: no scheduler callback recorded" : String.format(Locale.ROOT,
                "Runtime lifetime: elapsed-wall-ms=%.3f elapsed-game-ticks=%d callbacks=%d admissions=%d resumptions=%d completed-cycles=%d callback-Hz=%.3f admission-Hz=%.3f callbacks/game-second=%.3f admissions/game-second=%.3f stage=%s stage-age-ticks=%d LINDA-sources-processed=%d equalization-patches-committed=%d equalization-outer-finite-cells=%d equalization-included-finite-cells=%d ownership-discovery-probes=%d ownership-claimed-cells=%d active-search-candidates=%d retry-candidates-pending=%d saturated-chunks=%d unknown-seeds-parked=%d monstermos-discovery-probes=%d queued=%d ownership-pending=%d equalization-jobs-pending=%d",
                 runtime.elapsedWallMs(), runtime.elapsedGameTicks(), runtime.callbacks(), runtime.admissions(), runtime.resumptions(), runtime.completedCycles(), runtime.callbacksPerWallSecond(), runtime.admissionsPerWallSecond(), runtime.callbacksPerGameSecond(), runtime.admissionsPerGameSecond(), runtime.stage(), runtime.stageAgeTicks(), runtime.lindaSources(), runtime.equalizationPatches(), runtime.equalizationCells(), runtime.equalizationIncludedCells(), runtime.ownershipProbes(), runtime.ownershipClaimedCells(), runtime.activeCandidates(), runtime.retryPending(), runtime.saturatedChunks(), runtime.unknownParked(), runtime.monstermosProbes(), runtime.queued(), runtime.pendingOwnership(), runtime.pendingEqualizations()));
        if (runtime != null) send(source, "Committed transport ops: pressure-face-transfers=" + runtime.pressureFaceTransfers()
                + " LINDA-shared-pairs=" + runtime.lindaSharedPairs()
                + " group-averaged-finite-members=" + runtime.groupAveragedMembers());
        if (runtime != null) send(source, "Rates above are lifetime averages: Hz uses monotonic wall time; per-game-second uses game ticks (20 ticks/s), not TPS. 15 Hz is nominal due-stage admission cadence, not cycle throughput; elapsed stage age identifies current stalls.");
        if (runtime != null) send(source, String.format(Locale.ROOT,
                "Excited groups: operations=%d member-reads=%d group-averages=%d unavailable-groups=%d capture-ms=%.3f average-ms=%.3f transaction-publication-ms=%.3f cross-admission-snapshot-replays=0",
                runtime.groupOperations(), runtime.groupMembersCaptured(), runtime.groupAverages(), runtime.groupUnavailable(),
                 runtime.groupCaptureMs(), runtime.groupAverageMs(), runtime.groupPublicationMs()));
        var pressure = service.diagnosticPressure(level);
        send(source, pressure == null ? "Last committed normal pressure patch (level): none recorded" : String.format(Locale.ROOT,
                "Last committed normal pressure patch (level): PRESSURE_MONSTERMS span=%.3f→%.3f kPa converged=%s selected=%d routes=%d preview-steps=%d search+preview-work=%d face-transfers=%d solver-ms=%.3f",
                pressure.pressureSpanBeforeKpa(), pressure.pressureSpanAfterKpa(), pressure.converged(), pressure.selectedCells(),
                pressure.routes(), pressure.scalarPreviewSteps(), pressure.searchWork(), pressure.faceTransfers(), pressure.solverMs()));
        if (timing != null) send(source, "Work snapshot: queue=" + timing.queued() + " dirty=" + timing.dirty() + " ownership=" + timing.ownershipPending()
                + "; boundary ledger cumulative signed gas=" + AtmosphereService.INSTANCE.boundaryGasLedger(level)
                + " energyJ=" + AtmosphereService.INSTANCE.boundaryEnergyLedger(level));
        var patch = service.diagnosticPatch(level);
        send(source, patch == null ? "Kernel: no recorded attempt" : "Kernel last attempt=" + patch.attempted() + " committed=" + patch.committed()
                + " status=" + patch.status() + " patch=" + patch.size() + " work=" + patch.expansions() + "/" + patch.calls()
                + " signed-export-moles=" + String.format(Locale.ROOT, "%.6g", patch.exportMoles()) + " energyJ=" + String.format(Locale.ROOT, "%.6g", patch.exportEnergy()));
        return 1;
    }

    private static String cell(String label, AtmosphereService.CellDiagnostic c) {
        return label + ": " + c.status() + (c.gas() == null ? "" : " " + gas(c.gas()));
    }
    private static String gas(GasMixture gas) {
        if (gas == null) return "unavailable";
        return String.format(Locale.ROOT, "P=%.3f kPa T=%.2f K total=%.6g mol O2=%.6g mol Trit=%.6g mol",
                gas.pressureKpa(1.0), gas.temperatureKelvin(), gas.totalMoles(), gas.moles(GasType.OXYGEN), gas.moles(GasType.TRITIUM));
    }
    private static void send(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}

