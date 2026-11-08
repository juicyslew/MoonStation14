package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedDevicePortGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-machine-appearance"})
public final class AtmosMachineAppearanceGameTests {
    private AtmosMachineAppearanceGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void nativeDisplayProjectionPreservesHostState(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2), pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, local, local, 1);
        var block = ModBlocks.GAS_VALVE.get();
        var pumpBlock = ModBlocks.GAS_VOLUME_PUMP.get();
        BlockPos pumpLocal = new BlockPos(4, 1, 2), pumpPos = helper.absolutePos(pumpLocal);
        helper.setBlock(local, block.defaultBlockState().setValue(AtmosMachineBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(pumpLocal, pumpBlock.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
                var machine = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
                var pump = (AtmosMachineBlockEntity) level.getBlockEntity(pumpPos);
                if (level.getBlockState(pumpPos).getValue(AtmosMachineBlock.DISPLAY) != 15)
                    throw new IllegalStateException("volume pump off resource state must be DISPLAY 15");
                pump.setControl("enabled", true);
                if (level.getBlockState(pumpPos).getValue(AtmosMachineBlock.DISPLAY) != 15)
                    throw new IllegalStateException("enabled but unpowered volume pump must retain off resource state");
                pump.setControl("enabled", false);
                var facing = level.getBlockState(pos).getValue(AtmosMachineBlock.FACING);
                var ports = machine.gasPorts();
                var ids = ports.stream().map(AtmosMachineBlockEntity.Port::incarnation).toList();
                var seeds = ports.stream().map(p -> new TrustedPipeFixtureInitialization.PortSeed(pos, p.id(), p.incarnation(),
                        new GasMixture(java.util.Map.of(GasType.OXYGEN, 1.25), 300, p.volume()))).toList();
                var initialized = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seeds);
                if (!initialized.successful() || initialized.completedPorts() != ports.size())
                    throw new IllegalStateException("finite valve gas setup failed: " + initialized.detail());
                var gasInventory = com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).orElseThrow();
                var gasBeforeInventory = gasInventory.snapshot();
                var gasBefore = ports.stream().map(p -> LoadedDevicePortGasOwner.activeSnapshot(level, pos, p.id()).orElseThrow()).toList();
                var layer = machine.selectedPipeLayer();
                var settings = machine.settingsSnapshot();
                machine.setControl("open", false);
                if (level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY) != 23)
                    throw new IllegalStateException("closed valve display state not projected");
                machine.setControl("open", true);
                if (level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY) != 22)
                    throw new IllegalStateException("open valve display state not projected");
                var gasAfterDisplayInventory = gasInventory.snapshot();
                var gasAfterDisplay = machine.gasPorts().stream().map(p -> LoadedDevicePortGasOwner.activeSnapshot(level, pos, p.id()).orElseThrow()).toList();
                if (!samePortStates(gasBeforeInventory, gasAfterDisplayInventory, gasBefore, gasAfterDisplay))
                    throw new IllegalStateException("DISPLAY-only changes mutated gas identity/version/state");
                if (!machine.changePipeLayer(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY)
                        || level.getBlockState(pos).getValue(AtmosMachineBlock.PIPE_LAYER) != 1)
                    throw new IllegalStateException("derived secondary pipe-layer state not projected");
                if (!machine.changePipeLayer(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY)
                        || level.getBlockState(pos).getValue(AtmosMachineBlock.PIPE_LAYER) != 0)
                    throw new IllegalStateException("derived primary pipe-layer state not restored");
                if (level.getBlockState(pos).getValue(AtmosMachineBlock.FACING) != facing
                        || !machine.gasPorts().stream().map(AtmosMachineBlockEntity.Port::incarnation).toList().equals(ids)
                        || machine.selectedPipeLayer() != layer || machine.settingsSnapshot().revision() <= settings.revision()
                        || machine.apcAllocationKnown())
                    throw new IllegalStateException("display projection changed host identity/layer/settings or invented power");
                var gasAfter = machine.gasPorts().stream().map(p -> LoadedDevicePortGasOwner.activeSnapshot(level, pos, p.id()).orElseThrow()).toList();
                var gasAfterInventory = gasInventory.snapshot();
                for (int i = 0; i < gasBefore.size(); i++) {
                    var before = gasBefore.get(i); var after = gasAfter.get(i);
                    var beforeGas = gasBeforeInventory.nodeShare(before.node());
                    var afterGas = gasAfterInventory.nodeShare(after.node());
                    if (!before.node().equals(after.node()) || !before.incarnation().equals(after.incarnation())
                            || before.volume() != after.volume() || !beforeGas.gasMoles().equals(afterGas.gasMoles())
                            || Double.compare(beforeGas.temperatureKelvin(), afterGas.temperatureKelvin()) != 0
                            || Double.compare(beforeGas.thermalEnergy(), afterGas.thermalEnergy()) != 0
                            || after.version() != before.version() + 2)
                        throw new IllegalStateException("layer roundtrip must preserve exact finite gas identity/state and advance exactly two versions");
                }
                removeExactHost(level, pos, machine); removeExactHost(level, pumpPos, pump); lease.close(); helper.succeed();
            } catch (RuntimeException | Error failure) {
                if (level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine) removeExactHost(level, pos, machine);
                if (level.getBlockEntity(pumpPos) instanceof AtmosMachineBlockEntity pump) removeExactHost(level, pumpPos, pump);
                lease.close(); throw failure;
            }
        });
    }

    private static boolean samePortStates(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory beforeInventory,
            com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory afterInventory,
            java.util.List<com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory.Entry> before,
            java.util.List<com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory.Entry> after) {
        if (!before.equals(after)) return false;
        for (int i = 0; i < before.size(); i++) {
            var a = beforeInventory.nodeShare(before.get(i).node());
            var b = afterInventory.nodeShare(after.get(i).node());
            if (!a.gasMoles().equals(b.gasMoles())
                    || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0
                    || Double.compare(a.thermalEnergy(), b.thermalEnergy()) != 0
                    || Double.compare(a.volumeCubicMeters(), b.volumeCubicMeters()) != 0) return false;
        }
        return true;
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    @GameTestTags({"atmos-machine-unload-reload"})
    public static void powerChunkUnloadDoesNotProjectInvalidAllocation(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2), pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, local, local, 1);
        helper.setBlock(local, ModBlocks.GAS_VOLUME_PUMP.get().defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
            var machine = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
                if (!machine.applySettings(AtmosMachineSettings.Operation.SET_ENABLED, new AtmosMachineSettings.BooleanValue(true)))
                    throw new IllegalStateException("pump enable rejected");
                machine.receiveApcPowerAllocation(500, true, 1);
                int poweredDisplay = level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY);
                if (!machine.apcAllocationKnown() || poweredDisplay != 17)
                    throw new IllegalStateException("enabled volume pump must start from a known powered appearance; display=" + poweredDisplay);
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.onChunkUnload(
                        new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
                if (machine.apcAllocationKnown()) throw new IllegalStateException("unload must invalidate the allocation");
                if (level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY) != poweredDisplay)
                    throw new IllegalStateException("unload callback must not project the invalid allocation into native DISPLAY");
                machine.onChunkUnloaded(); // duplicate lifecycle callback is safe
                machine.onLoad(); // same host reloaded: normal derived projection resumes
                if (machine.apcAllocationKnown() || !machine.settingsSnapshot().enabled().orElse(false)
                        || level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY) != 15)
                    throw new IllegalStateException("reload must project enabled-but-unpowered volume pump appearance");
                machine.receiveApcPowerAllocation(500, true, 2);
                if (!machine.apcAllocationKnown() || level.getBlockState(pos).getValue(AtmosMachineBlock.DISPLAY) != poweredDisplay)
                    throw new IllegalStateException("valid allocation must restore powered appearance");
                lease.close(); helper.succeed();
            } catch (RuntimeException | Error failure) {
                lease.close(); throw failure;
            }
        });
    }
    private static void removeExactHost(net.minecraft.server.level.ServerLevel level, BlockPos pos, AtmosMachineBlockEntity host) {
        if (level.getBlockEntity(pos) == host && level.getBlockState(pos).getBlock() instanceof AtmosMachineBlock)
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
}
