package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class LoadedPipeGasOwnerGameTests {
    private LoadedPipeGasOwnerGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void freshVacuumRollbackRemovesIdentityAndOwner(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        UUID id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(LoadedPipeGasOwner.rollbackFreshVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY, id), "rollback fresh vacuum");
        require(((VisualPipeBlockEntity) level.getBlockEntity(pos)).functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty(), "identity removed");
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        require(saved.snapshot().get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).isEmpty(), "owner removed");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).isPresent(), "new owner may now be admitted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void freshVacuumRollbackRejectsWrongIdentityAndSeededGas(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        UUID id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(!LoadedPipeGasOwner.rollbackFreshVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY, UUID.randomUUID()), "wrong identity rejected");
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var entry = before.get(key).orElseThrow();
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 120.0);
        require(saved.replace(before.revision(), before, before.withMixture(key, id, entry.version(), gas)), "seed gas");
        require(!LoadedPipeGasOwner.rollbackFreshVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY, id), "seeded lane rejected");
        var retained = saved.snapshot().get(key).orElseThrow();
        require(retained.mixture().gasMoles().equals(gas.gasMoles())
                && Double.compare(retained.mixture().thermalEnergy(), gas.thermalEnergy()) == 0, "seeded gas preserved");
        require(((VisualPipeBlockEntity) level.getBlockEntity(pos)).functionalIdentity(VisualPipeBlockEntity.PRIMARY).orElseThrow().equals(id), "identity preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void separateNonPrimaryLanesGetIndependentOwners(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForTest(VisualPipeBlockEntity.PRIMARY | VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY), "lane setup");
        var second = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.SECONDARY);
        var third = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.TERTIARY);
        require(second.isPresent() && third.isPresent() && !second.get().equals(third.get()), "independent lane IDs");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.SECONDARY).isEmpty(), "duplicate admission rejected");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.SECONDARY).isPresent(), "secondary active");
        var secondaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY);
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var before = saved.snapshot();
        var initial = before.get(secondaryKey).orElseThrow();
        var finite = new GasMixture(Map.of(GasType.OXYGEN, 2.5, GasType.NITROGEN, 7.5), 312.0);
        var seeded = before.withMixture(secondaryKey, second.orElseThrow(), initial.version(), finite);
        require(saved.replace(before.revision(), before, seeded), "seed finite mixture");
        var expected = seeded.get(secondaryKey).orElseThrow().mixture();
        require(LoadedPipeGasOwner.deactivate(level, pos, VisualPipeBlockEntity.SECONDARY), "deactivate");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.SECONDARY).isEmpty(), "dormant lane hidden");

        // Restart-like simulation: serialize both durable domains, then replace the BE instance
        // at the same position and reload its functional identities. This is not a process restart.
        var visualTag = new net.minecraft.nbt.CompoundTag();
        visual.saveAdditional(visualTag, level.registryAccess());
        var inventoryTag = saved.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        var reloadedVisual = new VisualPipeBlockEntity(pos, level.getBlockState(pos));
        reloadedVisual.loadAdditional(visualTag, level.registryAccess());
        require(reloadedVisual != visual, "block entity re-instantiated");
        level.getChunkAt(pos).setBlockEntity(reloadedVisual);
        require(reloadedVisual.functionalIdentity(VisualPipeBlockEntity.SECONDARY).equals(second), "secondary identity restored");
        require(reloadedVisual.functionalIdentity(VisualPipeBlockEntity.TERTIARY).equals(third), "tertiary identity restored");
        require(PipeGasInventorySavedData.load(inventoryTag, level.registryAccess()).snapshot()
                .get(secondaryKey).orElseThrow().mixture().gasMoles().equals(expected.gasMoles()), "saved-data gas decoded");
        require(LoadedPipeGasOwner.reconcileExisting(level, pos, VisualPipeBlockEntity.SECONDARY), "reconcile persisted lane");
        var restored = LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.SECONDARY).orElseThrow();
        require(restored.mixture().gasMoles().equals(expected.gasMoles())
                && Double.compare(restored.mixture().thermalEnergy(), expected.thermalEnergy()) == 0, "gas and energy retained");
        var thirdEntry = LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.TERTIARY).orElseThrow();
        require(thirdEntry.mixture().gasMoles().isEmpty() && thirdEntry.mixture().thermalEnergy() == 0.0, "third lane remains vacuum");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void replacedVisualPipeCannotReconnectStalePrimaryOwner(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var admitted = before.get(key).orElseThrow();
        var finite = new GasMixture(Map.of(GasType.OXYGEN, 2.5, GasType.NITROGEN, 7.5), 312.0);
        var seeded = before.withMixture(key, id, admitted.version(), finite);
        require(saved.replace(before.revision(), before, seeded), "seed finite primary mixture");

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity replacement = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(replacement != null && replacement.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty(), "replacement has no old identity");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty(), "stale owner inactive");
        require(!LoadedPipeGasOwner.reconcileExisting(level, pos, VisualPipeBlockEntity.PRIMARY), "stale owner not reconciled");
        UUID fresh = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(!fresh.equals(id), "fresh incarnation admitted after retirement");
        var snapshot = saved.snapshot();
        var retained = snapshot.retiredSnapshot().get(id);
        require(retained != null && !retained.active() && retained.mixture().gasMoles().equals(finite.gasMoles())
                && Double.compare(retained.mixture().thermalEnergy(), finite.thermalEnergy()) == 0, "stale gas archived intact");
        require(snapshot.get(key).orElseThrow().incarnation().equals(fresh), "new live owner does not reconnect stale primary");
        require(!LoadedPipeGasOwner.reconcileExisting(level, pos, VisualPipeBlockEntity.PRIMARY), "replacement identity absent, old owner never reconciles");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void mismatchedPrimaryIdentityLeavesSavedGasInert(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var admitted = before.get(key).orElseThrow();
        var finite = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 208.0);
        var seeded = before.withMixture(key, id, admitted.version(), finite);
        require(saved.replace(before.revision(), before, seeded), "seed mismatch mixture");

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity replacement = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(replacement != null && replacement.markFunctionalLayer(VisualPipeBlockEntity.PRIMARY, UUID.randomUUID()), "install different BE identity");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty(), "mismatched entry inactive");
        require(!LoadedPipeGasOwner.reconcileExisting(level, pos, VisualPipeBlockEntity.PRIMARY), "mismatched identity not reconciled");
        var snapshot = saved.snapshot();
        var retained = snapshot.retiredSnapshot().get(id);
        require(retained != null && retained.mixture().gasMoles().equals(finite.gasMoles())
                && Double.compare(retained.mixture().thermalEnergy(), finite.thermalEnergy()) == 0, "old gas remains archived intact");
        require(snapshot.get(key).isEmpty(), "wrong identity has no saved live key");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty(), "wrong BE identity denies fresh admission");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty(), "wrong identity remains inactive");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void physicalRemovalArchivesFiniteGasAndAllowsFreshIncarnation(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        UUID id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var entry = before.get(key).orElseThrow();
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 208.0);
        require(saved.replace(before.revision(), before, before.withMixture(key, id, entry.version(), gas)), "seed removal gas");
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var retired = saved.snapshot();
        require(retired.get(key).isEmpty(), "removed live owner absent");
        var archive = retired.retiredSnapshot().get(id);
        require(archive != null && !archive.active() && archive.mixture().gasMoles().equals(gas.gasMoles())
                && Double.compare(archive.mixture().thermalEnergy(), gas.thermalEnergy()) == 0, "removed gas archived intact");
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        UUID fresh = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(!fresh.equals(id), "replacement incarnation is fresh");
        var replacement = saved.snapshot().get(key).orElseThrow();
        require(replacement.mixture().gasMoles().isEmpty() && replacement.mixture().thermalEnergy() == 0.0,
                "archived gas not reused by replacement");
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_REGRESSION_PASS physicalRemovalArchivesFiniteGasAndAllowsFreshIncarnation");
    }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void creativeFakePlayerRemovalSpillsIntoProvenFiniteRoom(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Small sealed 5x5x3 probe volume, with the pipe at its center.
        var atmos = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var oldVacuumDimensions = atmos.vacuumDimensionsForTest();
        var fixtureVacuumDimensions = new java.util.HashSet<>(oldVacuumDimensions);
        fixtureVacuumDimensions.add(level.dimension());
        atmos.configureAtServerStart(fixtureVacuumDimensions, atmos.spaceDimensionsForTest());
        try {
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(new BlockPos(x, 4, z), net.minecraft.world.level.block.Blocks.STONE);
            for (int y = 1; y <= 3; y++) for (int xx = 1; xx <= 5; xx++) for (int zz = 1; zz <= 5; zz++) {
                if (xx == 1 || xx == 5 || zz == 1 || zz == 5)
                    helper.setBlock(new BlockPos(xx, y, zz), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        BlockPos room = pos;
        long syntheticTime = Math.floorDiv(level.getGameTime(), (long) atmos.TICK_CADENCE) * atmos.TICK_CADENCE;
        for (int i = 0; i < 12 && atmos.finiteRoomEndpointForTrustedSimulation(level, pos).isEmpty(); i++) {
            atmos.sample(level, pos);
            syntheticTime += atmos.TICK_CADENCE;
            atmos.tick(level, syntheticTime);
        }
        require(atmos.finiteRoomEndpointForTrustedSimulation(level, pos).isPresent(), "room proven finite");
        var roomBefore = atmos.sample(level, room).orElseThrow();
        UUID id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        var saved = PipeGasInventorySavedData.forMutation(level);
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot(); var entry = before.get(key).orElseThrow();
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.NITROGEN, 9.0), 208.0);
        require(saved.replace(before.revision(), before, before.withMixture(key, id, entry.version(), gas)), "seed finite removal gas");

        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "pipe-removal-engine-fixture"));
        player.setGameMode(GameType.CREATIVE);
        require(player.gameMode.destroyBlock(pos), "creative GameMode destroys target through player path");
        require(level.getBlockState(pos).isAir(), "pipe block removed");
        var retired = saved.snapshot();
        var archive = retired.retiredSnapshot().get(id);
        require(retired.get(key).isEmpty() && archive == null, "spilled gas is removed without archive");
        var roomAfter = atmos.sample(level, room).orElseThrow();
        require(Math.abs((roomAfter.moles(GasType.OXYGEN) - roomBefore.moles(GasType.OXYGEN)) - 4.0) < 1.0e-9
                && Math.abs((roomAfter.moles(GasType.NITROGEN) - roomBefore.moles(GasType.NITROGEN)) - 9.0) < 1.0e-9
                && Math.abs((roomAfter.thermalEnergy() - roomBefore.thermalEnergy()) - gas.thermalEnergy()) < 1.0e-6,
                "seeded gas and thermal energy conserved into room");
        // This test models a legacy visual host with no functional UUID, so its removal can spill
        // the saved owner; a subsequent host must receive a new vacuum incarnation, not that gas.
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        UUID replacementId = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(!replacementId.equals(id)
                && saved.snapshot().get(key).orElseThrow().mixture().gasMoles().isEmpty()
                && !saved.snapshot().retiredSnapshot().containsKey(id), "spilled gas is not reused or archived");
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_REGRESSION_PASS creativeFakePlayerRemovalSpillsIntoProvenFiniteRoom");
        } finally { atmos.configureAtServerStart(oldVacuumDimensions, atmos.spaceDimensionsForTest()); }
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void provenAirRetiresAllStaleLanesAtOnlyThatPosition(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos sibling = pos.east();
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        // Legacy saved records have no matching functional UUID in this replacement visual host.
        require(visual.setLayersForTest(VisualPipeBlockEntity.PRIMARY | VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY), "legacy lanes setup");
        UUID[] old = new UUID[3];
        PipeTopology.Layer[] layers = PipeTopology.Layer.values();
        int[] lanes = { VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY, VisualPipeBlockEntity.TERTIARY };
        var saved = PipeGasInventorySavedData.forMutation(level);
        for (int i = 0; i < lanes.length; i++) {
            old[i] = UUID.randomUUID();
            var key = new PipeTopology.NodeKey(pos, layers[i]);
            var before = saved.snapshot();
            var gas = new GasMixture(Map.of(GasType.OXYGEN, i + 1.0), 250 + i);
            require(saved.replace(before.revision(), before, before.admit(key, old[i], Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3)), "seed legacy owner " + i);
            var seeded = saved.snapshot(); var entry = seeded.get(key).orElseThrow();
            require(saved.replace(seeded.revision(), seeded, seeded.withMixture(key, old[i], entry.version(), gas)), "seed stale lane " + i);
        }
        UUID siblingId = UUID.randomUUID();
        var siblingKey = new PipeTopology.NodeKey(sibling, PipeTopology.Layer.PRIMARY);
        var beforeSibling = saved.snapshot();
        require(saved.replace(beforeSibling.revision(), beforeSibling,
                beforeSibling.admit(siblingKey, siblingId, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3)), "seed sibling owner");
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        require(!LoadedPipeGasOwner.retireOrphanAtAir(level, pos, 0), "invalid lane rejected");
        require(LoadedPipeGasOwner.retireOrphanAtAir(level, pos, VisualPipeBlockEntity.PRIMARY), "all lane records retired at proven AIR");
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual.setLayersForTest(VisualPipeBlockEntity.PRIMARY | VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY), "replacement lanes setup");
        UUID[] fresh = new UUID[3];
        for (int i = 0; i < lanes.length; i++) {
            fresh[i] = LoadedPipeGasOwner.createVacuumLane(level, pos, lanes[i]).orElseThrow();
            require(!fresh[i].equals(old[i]), "fresh lane UUID " + i);
        }
        var finalInventory = saved.snapshot();
        for (int i = 0; i < lanes.length; i++) {
            require(finalInventory.get(new PipeTopology.NodeKey(pos, layers[i])).orElseThrow().mixture().gasMoles().isEmpty(), "fresh vacuum lane " + i);
            require(finalInventory.retiredSnapshot().containsKey(old[i]), "stale gas archived " + i);
        }
        require(finalInventory.get(siblingKey).orElseThrow().incarnation().equals(siblingId), "sibling position owner unchanged");
        for (int i = 0; i < lanes.length; i++) {
            var key = new PipeTopology.NodeKey(pos, layers[i]);
            require(finalInventory.get(key).orElseThrow().incarnation().equals(fresh[i]), "exact fresh position key " + i);
            require(finalInventory.retiredSnapshot().get(old[i]).mixture().gasMoles()
                    .equals(Map.of(GasType.OXYGEN, i + 1.0)), "expected archived gas " + i);
        }
        require(finalInventory.get(siblingKey).orElseThrow().incarnation().equals(siblingId), "exact sibling entry retained");
        // A physically live host must never trigger orphan cleanup.
        UUID[] liveIds = fresh;
        for (int i = 0; i < lanes.length; i++) ((VisualPipeBlockEntity) level.getBlockEntity(pos)).markFunctionalLayer(lanes[i], liveIds[i]);
        require(!LoadedPipeGasOwner.retireOrphanAtAir(level, pos, VisualPipeBlockEntity.PRIMARY), "live visual host denies cleanup");
        require(saved.snapshot().get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).orElseThrow().incarnation().equals(liveIds[0]), "live owner retained");
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_REGRESSION_PASS provenAirRetiresAllStaleLanesAtOnlyThatPosition");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
