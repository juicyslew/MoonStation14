package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native world coverage for the BE consumer; not run by the focused JVM lane. */
@GameTestTags({"atmosphere", "atmos-machine-layer"})
@PrefixGameTestTemplate(false)
public final class AtmosMachineLayerGameTests {
    private AtmosMachineLayerGameTests() { }

    @GameTestTags({"pipe-machine-visual-joins"})
    @GameTest(template = "empty", timeoutTicks = 120, templateNamespace = "moonstation14")
    public static void functionalPipeVisualJoinTracksMachineLayerAndLifecycle(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos machinePos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos pipePos = machinePos.east();
        Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> owned = new LinkedHashMap<>();
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            for (var entry : owned.entrySet())
                if (level.getBlockEntity(entry.getKey()) == entry.getValue())
                    level.setBlock(entry.getKey(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        };
        onExit(helper, cleanup);
        level.setBlock(machinePos, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING, Direction.EAST), 3);
        var machine = (AtmosMachineBlockEntity) level.getBlockEntity(machinePos);
        require(machine != null, "real valve BE installed");
        owned.put(machinePos, machine);
        machine.reconcileLoadedPorts(level);
        require(machine.gasPorts().stream().anyMatch(p -> p.directions().contains(Direction.EAST)), "valve has actual east port");

        level.setBlock(pipePos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var pipe = (VisualPipeBlockEntity) level.getBlockEntity(pipePos);
        require(pipe != null && pipe.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "functional primary visual pipe");
        owned.put(pipePos, pipe);
        require(LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.PRIMARY).isPresent(), "admit zero-charge primary lane");
        require(pipe.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.WEST), "live primary lane joins matching valve port");
        require(level.getBlockState(pipePos).getValue(ExposedPipeVisualBlock.property(Direction.WEST)), "primary blockstate arm is connected");
        require(!pipe.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.NORTH), "wrong machine face does not join");
        PipeGasInventorySavedData inventoryData = PipeGasInventorySavedData.existing(level).orElseThrow();
        for (int lane : new int[]{VisualPipeBlockEntity.PRIMARY})
            require(inventoryData.snapshot().nodeShare(new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.values()[Integer.numberOfTrailingZeros(lane)])).totalMoles() == 0,
                    "functional lane starts vacuum");

        require(machine.changePipeLayer(PipeTopology.Layer.SECONDARY), "move valve ports to secondary");
        require(pipe.addFunctionalPlacementLayer(VisualPipeBlockEntity.SECONDARY), "add functional secondary lane");
        require(LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.SECONDARY).isPresent(), "admit secondary vacuum lane");
        require(!pipe.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.WEST)
                && pipe.hasLayerArm(VisualPipeBlockEntity.SECONDARY, Direction.WEST), "join transfers to secondary");
        require(machine.changePipeLayer(PipeTopology.Layer.TERTIARY), "move valve ports to tertiary");
        require(pipe.addFunctionalPlacementLayer(VisualPipeBlockEntity.TERTIARY), "add functional tertiary lane");
        require(LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.TERTIARY).isPresent(), "admit tertiary vacuum lane");
        require(!pipe.hasLayerArm(VisualPipeBlockEntity.SECONDARY, Direction.WEST)
                && pipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, Direction.WEST), "join transfers to tertiary");
        for (int lane : new int[]{VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY, VisualPipeBlockEntity.TERTIARY})
            require(inventoryData.snapshot().nodeShare(new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.values()[Integer.numberOfTrailingZeros(lane)])).totalMoles() == 0,
                    "ordinary lane relocation preserves vacuum");

        PipeGasInventory beforeDecode = inventoryData.snapshot();
        var tag = pipe.getUpdateTag(level.registryAccess());
        var replica = new VisualPipeBlockEntity(pipePos, pipe.getBlockState());
        replica.handleUpdateTag(tag, level.registryAccess());
        var machineReplica = new AtmosMachineBlockEntity(machinePos, level.getBlockState(machinePos));
        machineReplica.setLevel(level);
        machineReplica.loadWithComponents(machine.getUpdateTag(level.registryAccess()), level.registryAccess());
        require(machineReplica.selectedPipeLayer() == PipeTopology.Layer.TERTIARY
                && replica.hasLayer(VisualPipeBlockEntity.TERTIARY), "detached update tags decode layer/mask read-only");
        require(inventoryData.snapshot() == beforeDecode,
                "update-tag decoding does not allocate or mutate saved gas identities");

        // Rotate via a real blockstate update, keeping the same BE identity; reciprocal ports are reconciled by normal hooks.
        var oldHost = machine;
        var rotated = level.getBlockState(machinePos).setValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING, Direction.SOUTH);
        level.setBlock(machinePos, rotated, 3);
        machine = (AtmosMachineBlockEntity) level.getBlockEntity(machinePos);
        require(machine == oldHost, "facing state update preserves machine BE");
        machine.reconcileLoadedPorts(level);
        Direction rotatedPort = machine.gasPorts().getFirst().directions().iterator().next();
        BlockPos rotatedPipePos = machinePos.relative(rotatedPort);
        level.setBlock(rotatedPipePos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var rotatedPipe = (VisualPipeBlockEntity) level.getBlockEntity(rotatedPipePos);
        require(rotatedPipe != null && rotatedPipe.setLayersForPlacement(VisualPipeBlockEntity.TERTIARY), "rotated reciprocal visual pipe");
        owned.put(rotatedPipePos, rotatedPipe);
        require(LoadedPipeGasOwner.createVacuumLane(level, rotatedPipePos, VisualPipeBlockEntity.TERTIARY).isPresent(), "rotated vacuum owner");
        require(rotatedPipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, rotatedPort.getOpposite()), "rotated machine face joins");
        require(!pipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, Direction.WEST), "old facing no longer joins");

        var removedState = level.getBlockState(machinePos);
        var removedPortKeys = oldHost.gasPorts().stream().map(p -> new PipeTopology.NodeKey(machinePos, p.layer(), p.id()))
                .toList();
        require(!removedPortKeys.isEmpty(), "capture exact old machine port keys before removal");
        level.setBlock(machinePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        for (var key : removedPortKeys)
            require(inventoryData.snapshot().get(key).isEmpty(), "ordinary machine removal retires its exact live port key");
        require(!rotatedPipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, rotatedPort.getOpposite()), "machine removal clears live join");
        level.setBlock(machinePos, ModBlocks.GAS_VALVE.get().defaultBlockState(), 3);
        var replacement = (AtmosMachineBlockEntity) level.getBlockEntity(machinePos);
        require(replacement != null && replacement != oldHost, "replacement is a fresh machine BE");
        owned.put(machinePos, replacement);
        replacement.reconcileLoadedPorts(level);
        require(replacement.changePipeLayer(PipeTopology.Layer.TERTIARY), "replacement selects matching lane through ordinary API");
        require(rotatedPipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, rotatedPort.getOpposite()), "replacement joins from fresh server state");
        require(replacement.gasPorts().stream().noneMatch(p -> oldHost.gasPorts().stream()
                .anyMatch(old -> old.id().equals(p.id()) && old.incarnation().equals(p.incarnation()))), "replacement identities are fresh");

        var replacementPorts = replacement.gasPorts();
        Map<PipeTopology.NodeKey, java.util.UUID> replacementIdentities = new LinkedHashMap<>();
        for (var port : replacementPorts)
            replacementIdentities.put(port.key(machinePos), port.incarnation());
        require(!replacementIdentities.isEmpty(), "replacement has exact active port identities");
        var seededPort = replacementPorts.getFirst();
        var seededKey = seededPort.key(machinePos);
        PipeGasInventory current = inventoryData.snapshot();
        var seededEntry = current.get(seededKey).orElseThrow();
        GasMixture finitePacket = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 293.15, seededEntry.volume());
        PipeGasInventory seeded = current.withMixture(seededKey, seededPort.incarnation(), seededEntry.version(), finitePacket);
        require(inventoryData.replace(current.revision(), current, seeded), "seed finite gas into exact replacement-owned port");
        var archiveBefore = inventoryData.snapshot().retiredSnapshot();
        var captured = inventoryData.snapshot().get(seededKey).orElseThrow();
        var capturedGas=inventoryData.snapshot().mixture(seededKey);
        require(capturedGas.gasMoles().equals(finitePacket.gasMoles())
                        && capturedGas.thermalEnergy() == finitePacket.thermalEnergy(), "capture exact finite packet");
        level.setBlock(machinePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var afterLiveRetirement = inventoryData.snapshot();
        for (var key : replacementIdentities.keySet())
            require(afterLiveRetirement.get(key).isEmpty(), "ordinary removal retires replacement live key");
        var archived = afterLiveRetirement.retiredSnapshot().get(seededPort.incarnation());
        require(archived != null && archived.volume() == captured.volume()
                        && archived.mixture().gasMoles().equals(capturedGas.gasMoles())
                        && archived.mixture().thermalEnergy() == capturedGas.thermalEnergy(),
                "ordinary removal archives finite gas and energy under exact incarnation");
        require(afterLiveRetirement.retiredSnapshot().entrySet().containsAll(archiveBefore.entrySet()),
                "removal preserves unrelated archived entries");
        PipeGasInventory afterNormalCallback = afterLiveRetirement;
        oldHost.retireRemovedPorts(level, removedState);
        require(inventoryData.snapshot() == afterNormalCallback, "stale detached callback cannot mutate replacement retirement state");
        for (var identity : replacementIdentities.entrySet())
            require(inventoryData.snapshot().get(identity.getKey()).isEmpty(), "stale callback leaves replacement identities inactive");

        level.setBlock(machinePos, ModBlocks.GAS_VALVE.get().defaultBlockState(), 3);
        var freshReplacement = (AtmosMachineBlockEntity) level.getBlockEntity(machinePos);
        require(freshReplacement != null && freshReplacement != replacement, "second replacement is fresh");
        owned.put(machinePos, freshReplacement);
        freshReplacement.reconcileLoadedPorts(level);
        for (var port : freshReplacement.gasPorts()) {
            var key = port.key(machinePos);
            require(!replacementIdentities.containsValue(port.incarnation()), "reinstated port incarnation is fresh");
            var entry = inventoryData.snapshot().get(key).orElseThrow();
            require(entry.active() && inventoryData.snapshot().nodeShare(key).totalMoles() == 0, "fresh reinstated port starts active vacuum");
        }
        var retainedArchive = inventoryData.snapshot().retiredSnapshot().get(seededPort.incarnation());
        require(retainedArchive != null && retainedArchive.mixture().gasMoles().equals(capturedGas.gasMoles())
                        && retainedArchive.mixture().thermalEnergy() == capturedGas.thermalEnergy(),
                "fresh replacement does not alter finite retired archive");
        cleanup.run();

        level.setBlock(pipePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            require(!pipe.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.WEST)
                    && !pipe.hasLayerArm(VisualPipeBlockEntity.SECONDARY, Direction.WEST)
                    && !pipe.hasLayerArm(VisualPipeBlockEntity.TERTIARY, Direction.WEST), "removed pipe has no joined arms");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30, templateNamespace = "moonstation14")
    public static void layerCommitMovesFinitePortsAndRejectsOccupiedDestination(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        AtomicBoolean cleaned = new AtomicBoolean();
        java.util.Map<PipeTopology.NodeKey, java.util.Set<java.util.UUID>> fixtureOwners = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicReference<AtmosMachineBlockEntity> ownedHost = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<java.util.UUID> ownedPlacement = new java.util.concurrent.atomic.AtomicReference<>();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
            if (data != null) {
                PipeGasInventory current = data.snapshot();
                Map<PipeTopology.NodeKey, java.util.UUID> owned = new LinkedHashMap<>();
                for (var exact : fixtureOwners.entrySet()) {
                    var entry = current.get(exact.getKey()).orElse(null);
                    if (entry != null && exact.getValue().contains(entry.incarnation()))
                        owned.put(exact.getKey(), entry.incarnation());
                }
                if (!owned.isEmpty()) {
                    PipeGasInventory retired = current.retireBatch(owned);
                    data.replace(current.revision(), current, retired);
                }
            }
            if (ownedHost.get() != null && level.getBlockEntity(pos) == ownedHost.get()
                    && java.util.Objects.equals(ownedHost.get().placementIdentity(), ownedPlacement.get()))
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        };
        onExit(helper, cleanup);
        level.setBlock(pos, ModBlocks.GAS_VALVE.get().defaultBlockState(), 3);
        var host = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
        require(host != null, "valve BE exists");
        ownedHost.set(host);
        host.reconcileLoadedPorts(level);
        require(host.applySettings(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.SET_VALVE_OPEN,
                new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.BooleanValue(false)),
                "close default-open valve so fixture ports remain independent");
        ownedPlacement.set(host.placementIdentity());
        var ports = host.gasPorts();
        require(ports.size() == 2 && host.selectedPipeLayer() == PipeTopology.Layer.PRIMARY, "primary descriptors");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        java.util.List<TrustedPipeFixtureInitialization.PortSeed> seed = new java.util.ArrayList<>();
        Map<String, GasMixture> expected = new LinkedHashMap<>();
        int index = 0;
        for (var port : ports) {
            fixtureOwners.computeIfAbsent(port.key(pos), ignored -> new java.util.LinkedHashSet<>()).add(port.incarnation());
            PipeTopology.NodeKey destination = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY, port.id());
            fixtureOwners.computeIfAbsent(destination, ignored -> new java.util.LinkedHashSet<>()).add(port.incarnation());
            var entry = before.get(port.key(pos)).orElseThrow();
            require(Double.compare(entry.volume(), port.volume()) == 0, "seed uses canonical finite port volume");
            var mixture = new GasMixture(index++ == 0
                    ? Map.of(GasType.OXYGEN, 3.5, GasType.NITROGEN, 1.25)
                    : Map.of(GasType.CARBON_DIOXIDE, 2.75), 287.25, entry.volume());
            seed.add(new TrustedPipeFixtureInitialization.PortSeed(pos, port.id(), port.incarnation(), mixture));
            expected.put(port.id(), mixture);
        }
        var initialized = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seed);
        require(initialized.successful() && initialized.completedPorts() == ports.size(),
                "finite gas seeded through trusted one-shot vacuum initializer: " + initialized.failure() + "/" + initialized.detail());
        var first = ports.getFirst();
        var occupiedKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY, first.id());
        PipeGasInventory current = saved.snapshot();
        java.util.UUID occupiedIdentity = java.util.UUID.randomUUID();
        fixtureOwners.computeIfAbsent(occupiedKey, ignored -> new java.util.LinkedHashSet<>()).add(occupiedIdentity);
        PipeGasInventory occupied = current.admit(occupiedKey, occupiedIdentity, first.volume());
        require(saved.replace(current.revision(), current, occupied), "destination occupied for rejection case");
        PipeGasInventory occupiedImage = saved.snapshot();
        long settingsRevision = host.settingsRevision();
        require(!host.changePipeLayer(PipeTopology.Layer.SECONDARY), "occupied destination rejected");
        require(host.selectedPipeLayer() == PipeTopology.Layer.PRIMARY && saved.snapshot() == occupiedImage,
                "occupied destination caused no host/inventory mutation");
        PipeGasInventory cleared = occupied.tombstoneEmpty(occupiedKey, occupied.get(occupiedKey).orElseThrow().incarnation());
        require(saved.replace(occupied.revision(), occupied, cleared), "occupied fixture record removed");
        var gasWork = com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork.capture(level,
                host.gasPorts().stream().map(p -> new com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork.Port(p.key(pos))).toList()).orElseThrow();
        long revision = saved.snapshot().revision();
        require(host.changePipeLayer(PipeTopology.Layer.SECONDARY), "layer change accepted");
        require(com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork.commit(level, gasWork, Map.of(), 0)
                == com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork.Outcome.STALE, "captured gas work rejected after relocation");
        require(saved.snapshot().revision() == revision + 1, "one inventory revision");
        require(host.selectedPipeLayer() == PipeTopology.Layer.SECONDARY, "host lane published");
        require(host.settingsRevision() == settingsRevision, "settings retained");
         for (var port : host.gasPorts()) {
            var oldKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY, port.id());
            var newKey = port.key(pos);
            require(saved.snapshot().get(oldKey).isEmpty(), "old port key removed");
            var moved = saved.snapshot().get(newKey).orElseThrow();
            require(moved.active() && moved.incarnation().equals(port.incarnation()), "identity moved and remains active");
            var movedGas=saved.snapshot().mixture(newKey);
            require(movedGas.gasMoles().equals(expected.get(port.id()).gasMoles())
                    && Double.compare(movedGas.thermalEnergy(), expected.get(port.id()).thermalEnergy()) == 0,
                     "finite species and energy preserved");
        }

        var updateTag = host.getUpdateTag(level.registryAccess());
        require(updateTag.getString("SelectedPipeLayer").equals("SECONDARY"), "native BE update tag carries selected layer");
        var encodedPorts = updateTag.getList("PortIncarnations", net.minecraft.nbt.Tag.TAG_COMPOUND);
        require(encodedPorts.size() == ports.size(), "native update tag carries every port identity");
        for (var descriptor : ports) {
            boolean found = false;
            for (int i = 0; i < encodedPorts.size(); i++) {
                var row = encodedPorts.getCompound(i);
                if (row.getString("Id").equals(descriptor.id()) && row.hasUUID("Uuid")
                        && row.getUUID("Uuid").equals(descriptor.incarnation())) found = true;
            }
            require(found, "native update tag contains read-only port identity " + descriptor.id());
        }
        require(host.getUpdatePacket() != null, "BE publishes native update packet");
        PipeGasInventory inventoryBeforeDecode = saved.snapshot();
        var restored = new AtmosMachineBlockEntity(pos, level.getBlockState(pos));
        restored.setLevel(level);
        restored.loadWithComponents(updateTag, level.registryAccess());
        require(restored.selectedPipeLayer() == PipeTopology.Layer.SECONDARY, "detached update-tag decode restores selected layer");
        require(restored.gasPorts().size() == ports.size(), "detached update-tag decode restores descriptors");
        for (int i = 0; i < ports.size(); i++)
            require(restored.gasPorts().get(i).id().equals(ports.get(i).id())
                            && restored.gasPorts().get(i).incarnation().equals(ports.get(i).incarnation()),
                    "detached update-tag decode preserves port UUIDs");
        require(saved.snapshot() == inventoryBeforeDecode, "read-only tag decode does not mutate gas inventory");
        var persisted = host.saveWithFullMetadata(level.registryAccess());
        var restoredSave = new AtmosMachineBlockEntity(pos, level.getBlockState(pos));
        restoredSave.setLevel(level);
        restoredSave.loadWithComponents(persisted, level.registryAccess());
        require(restoredSave.selectedPipeLayer() == PipeTopology.Layer.SECONDARY
                        && restoredSave.gasPorts().stream().map(PipeGasPortHost.Port::incarnation).toList()
                        .equals(host.gasPorts().stream().map(PipeGasPortHost.Port::incarnation).toList()),
                "persistent BE roundtrip retains layer and port identities");
        require(saved.snapshot() == inventoryBeforeDecode, "persistent BE serialization remains separate from gas inventory");

        level.setBlock(pos, ModBlocks.TEG_CIRCULATOR.get().defaultBlockState(), 3);
        var unsupported = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
        ownedHost.set(unsupported);
        if (unsupported != null) {
            unsupported.reconcileLoadedPorts(level);
            ownedPlacement.set(unsupported.placementIdentity());
            for (var port : unsupported.gasPorts())
                fixtureOwners.computeIfAbsent(port.key(pos), ignored -> new java.util.LinkedHashSet<>()).add(port.incarnation());
        }
        require(unsupported != null && !unsupported.changePipeLayer(PipeTopology.Layer.SECONDARY), "unsupported profile rejected");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void onExit(GameTestHelper helper, Runnable cleanup) {
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
    }
}
