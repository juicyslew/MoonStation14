package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedDevicePortGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "atmos-machine-ports", "atmos-machinery-runtime"})
public final class AtmosMachinePortGameTests {
    private AtmosMachinePortGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void registeredValvePortsReconcileAndExposeOnlyOpenInternalEdge(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        var valve = ModBlocks.GAS_VALVE.get();
        helper.setBlock(local, valve.defaultBlockState());
        // Exercise the first explicit reconciliation before a BE tick can retry missing ports.
        try {
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity firstHost)
                    || !firstHost.profileIdentity().equals("gas_valve"))
                throw new IllegalStateException("registered valve BE missing immediately after placement");
            firstHost.reconcileLoadedPorts((ServerLevel) level);
            if (firstHost.gasPorts().size() != 2 || firstHost.gasPorts().stream()
                    .anyMatch(port -> LoadedDevicePortGasOwner.activeSnapshot((ServerLevel) level, pos, port.id()).isEmpty()))
                throw new IllegalStateException("first explicit single-pass reconciliation must admit every declared valve port");
        } catch (RuntimeException | Error failure) {
            if (level.getBlockState(pos).getBlock() == valve) helper.setBlock(local, Blocks.AIR);
            lease.close();
            throw failure;
        }
        helper.runAfterDelay(3, () -> {
            try {
                var raw = level.getBlockEntity(pos);
                if (!(raw instanceof AtmosMachineBlockEntity machine)
                        || !machine.profileIdentity().equals("gas_valve"))
                    throw new IllegalStateException("registered valve block entity missing");
                var ports = machine.gasPorts();
                if (ports.size() != 2) throw new IllegalStateException("resolved valve profile did not admit both ports");
                var start = ports.getFirst().key(pos);
                var open = LoadedPipeComponent.discover(level, start).orElseThrow(
                        () -> new IllegalStateException("open valve component was not discoverable"));
                if (open.nodes().size() != 2 || Math.abs(open.totalVolume() - 0.2) > 1e-9)
                    throw new IllegalStateException("open valve must join its two finite-volume ports");
                var work = LoadedPipeGasWork.capture(level, ports.stream()
                        .map(port -> new LoadedPipeGasWork.Port(port.key(pos))).toList()).orElseThrow(
                        () -> new IllegalStateException("live valve gas-work snapshot unavailable"));
                machine.setControl("open", false);
                var closed = LoadedPipeComponent.discover(level, start).orElseThrow(
                        () -> new IllegalStateException("closed valve inlet should remain discoverable"));
                if (closed.nodes().size() != 1 || Math.abs(closed.totalVolume() - 0.1) > 1e-9)
                    throw new IllegalStateException("closed valve must split its ports");
                var component = work.componentFor(start).orElseThrow();
                if (LoadedPipeGasWork.commit(level, work, java.util.Map.of(component.root(), component.aggregate()), 0)
                        != LoadedPipeGasWork.Outcome.STALE)
                    throw new IllegalStateException("noop gas work must reject changed valve topology");
                if (level.getBlockState(pos).getBlock() == valve) helper.setBlock(local, Blocks.AIR);
                lease.close();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == valve) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void valvePassivelyEqualizesFiniteGasOnlyWhenOpen(GameTestHelper helper) {
        var level = (ServerLevel) helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        var valve = ModBlocks.GAS_VALVE.get();
        helper.setBlock(local, valve.defaultBlockState());
        try {
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine))
                throw new IllegalStateException("registered valve BE missing");
            machine.reconcileLoadedPorts(level);
            var ports = machine.gasPorts();
            if (ports.size() != 2) throw new IllegalStateException("valve must expose two ports");
            machine.setControl("open", false);
            var inlet = ports.getFirst();
            var outlet = ports.get(1);
            var initial = new GasMixture(Map.of(GasType.OXYGEN, 10.0, GasType.NITROGEN, 3.0), 320.0, inlet.volume());
            var initialized = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, java.util.List.of(
                    new TrustedPipeFixtureInitialization.PortSeed(pos, inlet.id(), inlet.incarnation(), initial)));
            if (!initialized.successful()) throw new IllegalStateException("trusted inlet initialization failed");
            var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
            var portWork = LoadedPipeGasWork.capture(level, ports.stream()
                    .map(port -> new LoadedPipeGasWork.Port(port.key(pos))).toList()).orElseThrow();
            if (portWork.components().size() != 2) throw new IllegalStateException("closed valve must capture two components");
            var closedInletNetwork = saved.snapshot().network(inlet.key(pos)).orElseThrow();
            var closedOutletNetwork = saved.snapshot().network(outlet.key(pos)).orElseThrow();
            if (closedInletNetwork.id().equals(closedOutletNetwork.id()))
                throw new IllegalStateException("closed valve ports must have distinct network identities");
            var unchanged = new java.util.LinkedHashMap<PipeTopology.NodeKey, GasMixture>();
            for (var component : portWork.components().values()) unchanged.put(component.root(), component.aggregate());
            var closedOutcome = LoadedPipeGasWork.commit(level, portWork, unchanged, 0);
            if (closedOutcome != LoadedPipeGasWork.Outcome.UNCHANGED)
                throw new IllegalStateException("closed valve unchanged work must remain unchanged: " + closedOutcome);
            var closedOutlet = saved.snapshot().nodeShare(outlet.key(pos));
            if (!closedOutlet.gasMoles().isEmpty() || closedOutlet.thermalEnergy() != 0)
                throw new IllegalStateException("closed valve must keep outlet vacuum");

            machine.setControl("open", true);
            var openWork = LoadedPipeGasWork.capture(level, ports.stream()
                    .map(port -> new LoadedPipeGasWork.Port(port.key(pos))).toList()).orElseThrow();
            if (openWork.components().size() != 1) throw new IllegalStateException("open valve must capture one component");
            var component = openWork.components().values().iterator().next();
            var sharedNetwork = saved.snapshot().network(inlet.key(pos)).orElseThrow();
            var openedOutletNetwork = saved.snapshot().network(outlet.key(pos)).orElseThrow();
            if (!sharedNetwork.id().equals(openedOutletNetwork.id())
                    || !sharedNetwork.id().equals(component.network().id())
                    || !sharedNetwork.members().equals(java.util.Set.of(inlet.key(pos), outlet.key(pos)))
                    || Double.compare(sharedNetwork.mixture().volumeCubicMeters(),
                    (double) (float) (inlet.volume() + outlet.volume())) != 0)
                throw new IllegalStateException("opening valve must immediately merge both port networks");
            long gasRevision = sharedNetwork.gasRevision();
            long inventoryRevision = saved.snapshot().revision();
            var sharedGas = sharedNetwork.mixture();
            if (LoadedPipeGasWork.commit(level, openWork, Map.of(component.root(), component.aggregate()), 0)
                    != LoadedPipeGasWork.Outcome.UNCHANGED) throw new IllegalStateException("open valve unchanged equalization must not commit gas writes");
            var after = saved.snapshot();
            var afterNetwork = after.network(inlet.key(pos)).orElseThrow();
            if (after.revision() != inventoryRevision || afterNetwork.gasRevision() != gasRevision
                    || !afterNetwork.mixture().gasMoles().equals(sharedGas.gasMoles())
                    || Double.compare(afterNetwork.mixture().temperatureKelvin(), sharedGas.temperatureKelvin()) != 0
                    || Double.compare(afterNetwork.mixture().volumeCubicMeters(), sharedGas.volumeCubicMeters()) != 0)
                throw new IllegalStateException("unchanged shared-net work must preserve revisions and gas");
            var outletMix = after.nodeShare(outlet.key(pos));
            var inletMix = after.nodeShare(inlet.key(pos));
            for (var gas : java.util.List.of(GasType.OXYGEN, GasType.NITROGEN)) {
                double expectedInlet = sharedGas.moles(gas) * (inlet.volume() / sharedGas.volumeCubicMeters());
                double expectedOutlet = sharedGas.moles(gas) * (outlet.volume() / sharedGas.volumeCubicMeters());
                if (Math.abs(inletMix.moles(gas) - expectedInlet) > 1e-9
                        || Math.abs(outletMix.moles(gas) - expectedOutlet) > 1e-9)
                    throw new IllegalStateException("open valve node shares must match canonical shared-gas projections");
            }
            if (!PipeGasLedger.conservedSpecies(initial.moles(GasType.OXYGEN), sharedGas.moles(GasType.OXYGEN))
                    || !PipeGasLedger.conservedSpecies(initial.moles(GasType.NITROGEN), sharedGas.moles(GasType.NITROGEN))
                    || !PipeGasLedger.conservedEnergy(initial.thermalEnergy(), sharedGas.thermalEnergy()))
                throw new IllegalStateException("valve equalization must conserve species and thermal energy");
            helper.succeed();
        } finally {
            if (level.getBlockState(pos).getBlock() == valve) helper.setBlock(local, Blocks.AIR);
            lease.close();
        }
    }
}
