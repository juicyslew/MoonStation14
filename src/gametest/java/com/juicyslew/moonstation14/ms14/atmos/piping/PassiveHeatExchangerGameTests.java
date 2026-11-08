package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime;
import com.juicyslew.moonstation14.ms14.atmos.machine.thermal.PassiveHeatExchangerRuntime;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "atmos-radiator-work", "exterior-exchange-runtime"})
public final class PassiveHeatExchangerGameTests {
    private PassiveHeatExchangerGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void finiteEnvironmentRadiatorMovesGasAndConservesThermalEnergy(GameTestHelper helper) {
        var level = helper.getLevel();
        buildRoom(helper);
        BlockPos local = new BlockPos(4, 2, 4), pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        helper.setBlock(local, ModBlocks.PASSIVE_HEAT_EXCHANGER.get().defaultBlockState());
        awaitPorts(helper, pos, 40, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || machine.resolvedProfile() == null)
                    throw new IllegalStateException("resolved passive exchanger host unavailable");
                var ports = machine.gasPorts();
                var inlet = ports.stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow();
                var outlet = ports.stream().filter(p -> p.id().equals("outlet")).findFirst().orElseThrow();
                var saved = PipeGasInventorySavedData.forMutation(level);
                var hot = new GasMixture(Map.of(GasType.NITROGEN, 10d), 600, inlet.volume());
                var vacuum = new GasMixture(Map.of(), 2.7, outlet.volume());
                seedPort(saved, inlet.key(pos), hot);
                seedPort(saved, outlet.key(pos), vacuum);
                var pipeBefore = captureDistinctPortNetworks(level, inlet.key(pos), outlet.key(pos));
                var envPos = pos;
                var service = AtmosphereService.INSTANCE;
                var chunk = (LevelChunk) level.getChunk(envPos);
                if (!chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()).isFiniteClaimed(
                        envPos.getX() & 15, envPos.getY(), envPos.getZ() & 15))
                    throw new IllegalStateException("finite fixture cell was not claimed");
                var environment = new GasMixture(Map.of(GasType.NITROGEN, 2d), 250, 1);
                if (!service.initializeFiniteMixtureForTrustedSetup(level, envPos, environment))
                    throw new IllegalStateException("finite environment setup rejected");
                var envEndpoint = service.finiteCellEndpointForImmediateTransfer(level, envPos).orElseThrow();
                var envBefore = envEndpoint.snapshot();
                long revisionBefore = pipeBefore.inventory().revision();
                var result = PassiveHeatExchangerRuntime.process(level, machine, machine.resolvedProfile(), .6);
                var after = saved.snapshot();
                var inAfter = after.nodeShare(inlet.key(pos));
                var outAfter = after.nodeShare(outlet.key(pos));
                var envAfter = envEndpoint.snapshot();
                if (result.status() != PassiveHeatExchangerRuntime.Status.COMMITTED || result.movedMoles() <= 0
                        || after.revision() != revisionBefore + 1)
                    throw new IllegalStateException("live radiator commit failed: status=" + result.status()
                            + ", moved=" + result.movedMoles() + ", pipeJ=" + result.pipeEnergyDeltaJoules()
                            + ", envJ=" + result.environmentEnergyDeltaJoules() + ", revision="
                            + revisionBefore + "->" + after.revision() + ", envEligible=" + envEndpoint.eligible());
                for (GasType species : GasType.values()) {
                    double beforeMoles = hot.moles(species) + vacuum.moles(species);
                    double afterMoles = inAfter.moles(species) + outAfter.moles(species);
                    if (Math.abs(afterMoles - beforeMoles) > 1e-4)
                        throw new IllegalStateException("radiator changed pipe species inventory for " + species);
                }
                double pipeDelta = inAfter.thermalEnergy() + outAfter.thermalEnergy()
                        - hot.thermalEnergy() - vacuum.thermalEnergy();
                double envDelta = envAfter.thermalEnergy() - envBefore.thermalEnergy();
                if (!(pipeDelta < 0 && envDelta > 0) || Math.abs(pipeDelta + envDelta) > .2
                        || Math.abs(result.pipeEnergyDeltaJoules() - pipeDelta) > .2
                        || Math.abs(result.environmentEnergyDeltaJoules() - envDelta) > .2
                        || result.radiatedSinkJoules() != 0 || envAfter.totalMoles() != envBefore.totalMoles()
                        || !envAfter.gasMoles().equals(envBefore.gasMoles()))
                    throw new IllegalStateException("finite paired thermal exchange/accounting did not conserve energy/species");
                // Exercise the live runtime's finite-vacuum boundary separately: unlike
                // verified exterior space, this sealed cell has no modeled heat sink.
                seedPort(saved, inlet.key(pos), hot);
                seedPort(saved, outlet.key(pos), vacuum);
                var vacuumPipeBefore = captureDistinctPortNetworks(level, inlet.key(pos), outlet.key(pos));
                if (!service.initializeFiniteMixtureForTrustedSetup(level, envPos, GasMixture.vacuum()))
                    throw new IllegalStateException("finite vacuum setup rejected");
                envBefore = envEndpoint.snapshot();
                long vacuumRevision = vacuumPipeBefore.inventory().revision();
                var vacuumResult = PassiveHeatExchangerRuntime.process(level, machine, machine.resolvedProfile(), .6);
                after = saved.snapshot();
                inAfter = after.nodeShare(inlet.key(pos));
                outAfter = after.nodeShare(outlet.key(pos));
                envAfter = envEndpoint.snapshot();
                double vacuumPipeDelta = inAfter.thermalEnergy() + outAfter.thermalEnergy()
                        - hot.thermalEnergy() - vacuum.thermalEnergy();
                if (vacuumResult.status() != PassiveHeatExchangerRuntime.Status.COMMITTED
                        || vacuumResult.movedMoles() <= 0 || after.revision() != vacuumRevision + 1
                        || Math.abs(vacuumPipeDelta) > .2 || Math.abs(vacuumResult.pipeEnergyDeltaJoules()) > .2
                        || vacuumResult.radiatedSinkJoules() != 0
                        || vacuumResult.environmentEnergyDeltaJoules() != 0
                        || !envAfter.gasMoles().equals(envBefore.gasMoles())
                        || envAfter.thermalEnergy() != envBefore.thermalEnergy())
                    throw new IllegalStateException("finite vacuum live flow removed or received thermal energy");
                helper.setBlock(local, Blocks.AIR);
                lease.close();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == ModBlocks.PASSIVE_HEAT_EXCHANGER.get()) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void unownedAmbientIsRejectedWithLivePressurizedPipeGas(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2), pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, local, local, 1);
        helper.setBlock(local, ModBlocks.PASSIVE_HEAT_EXCHANGER.get().defaultBlockState());
        awaitPorts(helper, pos, 40, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || machine.resolvedProfile() == null)
                    throw new IllegalStateException("resolved passive exchanger host unavailable");
                var ports = machine.gasPorts();
                var inlet = ports.stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow();
                var outlet = ports.stream().filter(p -> p.id().equals("outlet")).findFirst().orElseThrow();
                var saved = PipeGasInventorySavedData.forMutation(level);
                var hot = new GasMixture(Map.of(GasType.NITROGEN, 10d), 600, inlet.volume());
                var cold = new GasMixture(Map.of(), 2.7, outlet.volume());
                seedPort(saved, inlet.key(pos), hot);
                seedPort(saved, outlet.key(pos), cold);
                long revision = saved.snapshot().revision();
                var result = PassiveHeatExchangerRuntime.process(level, machine, machine.resolvedProfile(), .6);
                if (result.status() != PassiveHeatExchangerRuntime.Status.REJECTED || result.movedMoles() != 0
                        || saved.snapshot().revision() != revision
                        || !saved.snapshot().nodeShare(inlet.key(pos)).gasMoles().equals(hot.gasMoles()))
                    throw new IllegalStateException("unowned ambient rejection did not preserve pressurized pipe inventory");
                helper.setBlock(local, Blocks.AIR);
                lease.close();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == ModBlocks.PASSIVE_HEAT_EXCHANGER.get()) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    @GameTestTags({"exterior-exchange-runtime"})
    public static void planetaryExteriorNormalRuntimeAccountsHotPipeHeat(GameTestHelper helper) {
        planetaryExteriorRuntimeCase(helper, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    @GameTestTags({"exterior-exchange-runtime"})
    public static void planetaryExteriorNormalRuntimeHeatsColdPipe(GameTestHelper helper) {
        planetaryExteriorRuntimeCase(helper, false);
    }

    private static void planetaryExteriorRuntimeCase(GameTestHelper helper, boolean hot) {
        var level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(4, 1, 4));
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                column.getX(), column.getZ()) + 2;
        BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
        BlockPos local = new BlockPos(pos.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                pos.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                pos.getZ() - helper.absolutePos(BlockPos.ZERO).getZ());
        var lease = FixtureChunkTickets.around(helper, local, local, 1);
        helper.setBlock(local, ModBlocks.PASSIVE_HEAT_EXCHANGER.get().defaultBlockState());
        awaitPorts(helper, pos, 40, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || machine.resolvedProfile() == null)
                    throw new IllegalStateException("resolved passive exchanger host unavailable");
                machine.reconcileLoadedPorts(level);
                var service = AtmosphereService.INSTANCE;
                var boundary = service.exteriorBoundaryForImmediateTransfer(level, pos).orElseThrow(
                        () -> new IllegalStateException("loaded planetary exterior boundary unavailable"));
                if (boundary.kind() != AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT
                        || !boundary.validate()) throw new IllegalStateException("expected valid planetary exterior");
                var inlet = machine.gasPorts().stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow();
                var outlet = machine.gasPorts().stream().filter(p -> p.id().equals("outlet")).findFirst().orElseThrow();
                var saved = PipeGasInventorySavedData.forMutation(level);
                double ambientK = boundary.ambient().temperatureKelvin();
                double pipeK = hot ? Math.max(ambientK + 150, 450) : Math.max(2.7, ambientK - 100);
                var inletGas = new GasMixture(Map.of(GasType.NITROGEN, 10d), pipeK, inlet.volume());
                var outletGas = new GasMixture(Map.of(), pipeK, outlet.volume());
                seedPort(saved, inlet.key(pos), inletGas);
                seedPort(saved, outlet.key(pos), outletGas);
                var before = saved.snapshot();
                double beforeEnergy = before.nodeShare(inlet.key(pos)).thermalEnergy()
                        + before.nodeShare(outlet.key(pos)).thermalEnergy();
                double ambientReceipt = machine.ambientHeatReceiptJoules();
                double spaceReceipt = machine.spaceHeatReceiptJoules();
                // Poll only the normal block-entity cadence; no direct processor call or clock adjustment.
                awaitExteriorReceipt(helper, machine, saved, inlet.key(pos), outlet.key(pos), beforeEnergy,
                        inletGas, outletGas, ambientReceipt, spaceReceipt, 60, () -> {
                            helper.setBlock(local, Blocks.AIR);
                            lease.close();
                            helper.succeed();
                        });
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == ModBlocks.PASSIVE_HEAT_EXCHANGER.get()) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    private static void awaitExteriorReceipt(GameTestHelper helper, AtmosMachineBlockEntity machine,
            PipeGasInventorySavedData saved, PipeTopology.NodeKey inlet, PipeTopology.NodeKey outlet,
            double beforeEnergy, GasMixture inletBefore, GasMixture outletBefore,
            double ambientBefore, double spaceBefore, int remaining, Runnable done) {
        var snapshot = saved.snapshot();
        var in = snapshot.nodeShare(inlet);
        var out = snapshot.nodeShare(outlet);
        double receipt = machine.ambientHeatReceiptJoules() - ambientBefore;
        if (Math.abs(receipt) > 1e-9) {
            double pipeDelta = in.thermalEnergy() + out.thermalEnergy() - beforeEnergy;
            for (GasType gas : GasType.values()) {
                if (Math.abs(in.moles(gas) + out.moles(gas) - inletBefore.moles(gas) - outletBefore.moles(gas)) > 1e-4)
                    throw new IllegalStateException("planetary exchanger changed pipe species " + gas);
            }
            if ((receipt > 0) != (pipeDelta < 0) || Math.abs(pipeDelta + receipt) > .2
                    || Double.compare(machine.spaceHeatReceiptJoules(), spaceBefore) != 0)
                throw new IllegalStateException("normal planetary runtime receipt did not match cumulative pipe energy delta");
            done.run();
            return;
        }
        if (remaining <= 0) throw new IllegalStateException("normal exchanger cadence produced no planetary ambient receipt");
        helper.runAfterDelay(1, () -> awaitExteriorReceipt(helper, machine, saved, inlet, outlet,
                beforeEnergy, inletBefore, outletBefore, ambientBefore, spaceBefore, remaining - 1, done));
    }

    private static void seedPort(PipeGasInventorySavedData saved, PipeTopology.NodeKey key, GasMixture mixture) {
        var before = saved.snapshot();
        var entry = before.get(key).orElseThrow(() -> new IllegalStateException("machine port not registered: " + key));
        var after = before.withMixture(key, entry.incarnation(), entry.version(), mixture);
        if (!saved.replace(before.revision(), before, after))
            throw new IllegalStateException("single-port seed CAS failed: " + key);
    }

    private static LoadedPipeGasWork.Snapshot captureDistinctPortNetworks(
            net.minecraft.server.level.ServerLevel level, PipeTopology.NodeKey inlet, PipeTopology.NodeKey outlet) {
        var captured = LoadedPipeGasWork.capture(level, java.util.List.of(
                new LoadedPipeGasWork.Port(inlet), new LoadedPipeGasWork.Port(outlet)))
                .orElseThrow(() -> new IllegalStateException("radiator pipe networks were not ready before baseline"));
        var inletComponent = captured.componentFor(inlet).orElseThrow();
        var outletComponent = captured.componentFor(outlet).orElseThrow();
        if (captured.aliases() || inletComponent.network().id().equals(outletComponent.network().id())
                || !inletComponent.network().members().equals(java.util.Set.of(inlet))
                || !outletComponent.network().members().equals(java.util.Set.of(outlet)))
            throw new IllegalStateException("radiator fixture ports must each own a distinct singleton network");
        return captured;
    }

    private static void awaitPorts(GameTestHelper helper, BlockPos pos, int remaining, Runnable ready) {
        var level = helper.getLevel();
        if (level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine
                && machine.resolvedProfile() != null) {
            var ports = machine.gasPorts();
            var in = ports.stream().filter(port -> port.id().equals("inlet")).findFirst().orElse(null);
            var out = ports.stream().filter(port -> port.id().equals("outlet")).findFirst().orElse(null);
            var saved = PipeGasInventorySavedData.existing(level).orElse(null);
            if (in != null && out != null && saved != null
                    && saved.snapshot().get(in.key(pos)).isPresent() && saved.snapshot().get(out.key(pos)).isPresent()) {
                ready.run();
                return;
            }
        }
        if (remaining <= 0) throw new IllegalStateException("passive exchanger host/ports did not become ready before fixture bound");
        helper.runAfterDelay(1, () -> awaitPorts(helper, pos, remaining - 1, ready));
    }

    private static void buildRoom(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) for (int z = 1; z <= 8; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) {
                boolean inside = x >= 2 && x <= 7 && z >= 2 && z <= 7;
                BlockPos cell = new BlockPos(x, y, z);
                helper.setBlock(cell, inside ? Blocks.AIR : Blocks.STONE);
                if (inside) {
                    BlockPos world = helper.absolutePos(cell);
                    var chunk = (LevelChunk) helper.getLevel().getChunk(world);
                    chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                            .claimFinite(world.getX() & 15, world.getY(), world.getZ() & 15);
                    chunk.setUnsaved(true);
                }
            }
        }
    }
}
