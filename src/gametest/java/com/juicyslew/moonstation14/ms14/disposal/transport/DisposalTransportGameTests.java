package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitLedger;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;

import java.util.UUID;

/** Live server coverage for the resolved tube-to-holder admission boundary. */
@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "containment"})
@PrefixGameTestTemplate(false)
public final class DisposalTransportGameTests {
    private DisposalTransportGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", batch = "disposals", timeoutTicks = 20)
    public static void resolvedAnchoredTrunkAdmitsRealTransitHolder(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos entry = helper.absolutePos(new BlockPos(3, 1, 3));
        var world = DisposalTubeWorldData.forMutation(level);
        level.setBlock(entry, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
        require(world.add(level, new DisposalTubeRecord(UUID.randomUUID(), entry,
                "moonstation14:disposal_trunk", Direction.NORTH, true, "", "", 0), p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())),
                "anchored trunk record installed in live world data");
        var holder = DisposalTransitHolder.spawnFlushed((net.minecraft.server.level.ServerLevel) level,
                entry, new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15));
        require(holder.isPresent(), "resolved anchored trunk admits actual holder entity");
        require(level.getEntity(holder.orElseThrow().getUUID()) == holder.orElseThrow(),
                "holder is the live entity registered in the server level");
        require(holder.orElseThrow().currentPos().equals(entry), "holder starts at the exact recorded trunk");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", batch = "disposals", timeoutTicks = 20)
    public static void holderCapturesBodyFlagsAtAdmissionBeforeAnySave(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos entry = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(entry, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
        require(DisposalTubeWorldData.forMutation(level).add(level,
                new DisposalTubeRecord(UUID.randomUUID(), entry, "moonstation14:disposal_trunk", Direction.NORTH, true, "", "", 0), p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())),
                "anchored trunk record installed");
        var holder = DisposalTransitHolder.spawnFlushed(level, entry, new GasMixture(java.util.Map.of(), 293.15)).orElseThrow();
        var body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        require(body != null, "body created");
        body.setInvisible(false);
        body.noPhysics = true;
        body.setNoGravity(false);
        require(level.addFreshEntity(body), "body added to level");
        var source = new TestTraversalOwner(level, UUID.randomUUID());
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        require(containment.bind(body, source), "body initially contained by source owner");
        var original = containment.export(body, source).orElseThrow();
        require(holder.addBody(level, body, source), "holder admits body");
        require(holder.originalBodyFlags().get(body.getUUID()).equals(original),
                "holder snapshots pre-entry flags immediately, without waiting for holder save");
        helper.succeed();
    }

    /** Live traversal regression: selector time budget 30 ticks; each resolved segment is two ticks. */
    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", batch = "disposals", timeoutTicks = 40)
    public static void liveHolderTraversesBendAndStagesPayloadOnce(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var world = DisposalTubeWorldData.forMutation(level);
        BlockPos trunk = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos straight = trunk.east(), bend = straight.east(), exit = bend.south();
        var disposalCatalog = PrototypeRuntime.serverDisposals();
        require(disposalCatalog.get(ModDisposals.id("disposal_pipe")) != null
                        && disposalCatalog.get(ModDisposals.id("disposal_bend")) != null
                        && disposalCatalog.get(ModDisposals.id("disposal_trunk")) != null,
                "all fixture tube prototypes exist in resolved disposal catalog");
        // Tube owners occupy the cells directly; payloads eject into clear space above them.
        for (BlockPos p : java.util.List.of(trunk, straight, bend, exit)) {
            level.setBlock(p, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        var records = java.util.List.of(
                new DisposalTubeRecord(UUID.randomUUID(), trunk, "disposal_trunk", Direction.EAST, true, "", "", 0),
                new DisposalTubeRecord(UUID.randomUUID(), straight, "disposal_pipe", Direction.EAST, true, "", "", 0),
                new DisposalTubeRecord(UUID.randomUUID(), bend, "disposal_bend", Direction.WEST, true, "", "", 0),
                new DisposalTubeRecord(UUID.randomUUID(), exit, "disposal_pipe", Direction.SOUTH, true, "", "", 0));
        for (var record : records) require(world.add(level, record, pos -> {
            return level.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get())
                    && level.getBlockState(pos.above()).isAir();
        }), "resolved tube record owns actual tube host block");
        var routing = new LoadedWorldDisposalRouting(level);
        require(routing.reciprocal(routing.record(trunk), routing.record(straight), Direction.EAST)
                        && routing.reciprocal(routing.record(straight), routing.record(bend), Direction.EAST)
                        && routing.reciprocal(routing.record(bend), routing.record(exit), Direction.SOUTH),
                "record orientations plus resolved prototype rotations mutually connect the live E/E/S path");

        var ledger = DisposalUnitLedger.forLevel(level);
        UUID owner = UUID.randomUUID();
        var source = new DisposalContents(owner, ledger);
        var token = new ItemToken(UUID.randomUUID().toString());
        var sourceLocation = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, sourceLocation).accepted(), "payload token registered");
        ItemStack stack = new ItemStack(Items.BREAD, 3);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("bend"));
        require(source.accept(token, sourceLocation, "traversal", stack, ledger.snapshot().revision()), "whole payload enters source store");
        var holder = DisposalTransitHolder.spawnFlushed(level, trunk, new GasMixture(java.util.Map.of(), 293.15),
                source, source.snapshot().keySet(), java.util.List.of(), new TestTraversalOwner(level, owner))
                .orElseThrow(() -> new GameTestAssertException("real flushed holder admitted"));
        require(holder.currentPos().equals(trunk), "holder begins at trunk");

        // Checkpoints are driven by GameTest's natural server ticks, never direct callbacks/tick calls.
        awaitFirstSegmentSelection(helper, holder, straight, bend, exit, 0, source, ledger, token, level, trunk);
    }

    private static void awaitFirstSegmentSelection(GameTestHelper helper, DisposalTransitHolder holder,
                                                   BlockPos straight, BlockPos bend, BlockPos exit, int waited,
                                                   DisposalContents source, com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger ledger, ItemToken token,
                                                   net.minecraft.server.level.ServerLevel level, BlockPos trunk) {
        helper.runAfterDelay(1, () -> {
            if (!holder.currentPos().equals(straight)) {
                if (waited + 1 < 8) {
                    awaitFirstSegmentSelection(helper, holder, straight, bend, exit, waited + 1, source, ledger, token, level, trunk);
                    return;
                }
                throw new GameTestAssertException("first segment not selected within bounded wait; position="
                        + holder.position() + ", currentTube=" + holder.currentPos() + ", removed=" + holder.isRemoved());
            }
            long selectedAt = helper.getLevel().getGameTime();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                require(holder.currentPos().equals(bend) && atCellCenter(holder, straight),
                        "straight physical arrival expected at gameTime " + (selectedAt + 2) + "; observed="
                                + helper.getLevel().getGameTime() + ", position=" + holder.position()
                                + ", currentTube=" + holder.currentPos() + ", removed=" + holder.isRemoved());
                require(!holder.isRemoved(), "actual bend turn continues rather than bouncing");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                    require(holder.currentPos().equals(exit) && atCellCenter(holder, bend),
                            "bend physical arrival expected at gameTime " + (selectedAt + 4) + "; observed="
                                    + helper.getLevel().getGameTime() + ", position=" + holder.position()
                                    + ", currentTube=" + holder.currentPos() + ", removed=" + holder.isRemoved());
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                        require(holder.isRemoved(), "open endpoint exits holder after bounded release");
                        require(source.snapshot().isEmpty(), "source store relinquished payload");
                        require(ledger.snapshot().locations().get(token) instanceof ItemLocation.WorldEntity,
                                "token ends at world entity location");
                        var staged = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(exit).inflate(3));
                        require(staged.size() == 1 && staged.get(0).getItem().getCount() == 3
                                && staged.get(0).getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null
                                && staged.get(0).getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                .getString().equals("bend"), "one whole metadata-preserving stack staged above floor");
                        require(level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(trunk).inflate(8)).size() == 1,
                                "payload has exactly one world entity, with no duplicate");
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static boolean atCellCenter(DisposalTransitHolder holder, BlockPos cell) {
        var p = holder.position();
        return Math.abs(p.x - (cell.getX() + .5)) < 1e-6
                && Math.abs(p.z - (cell.getZ() + .5)) < 1e-6;
    }

    private record TestTraversalOwner(net.minecraft.server.level.ServerLevel level, UUID identity)
            implements com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner {
        @Override public net.minecraft.world.phys.Vec3 position() { return new net.minecraft.world.phys.Vec3(0, 0, 0); }
        @Override public Stage stage() { return Stage.TRANSIT; }
        @Override public boolean isValid() { return true; }
        @Override public boolean tryEscape(net.minecraft.world.entity.LivingEntity body) { return false; }
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", batch = "disposals", timeoutTicks = 100)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"atmosphere"})
    public static void finiteRoomUnitAndHolderGasConserved(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var atmos = AtmosphereService.INSTANCE;
        var oldVacuum = atmos.vacuumDimensionsForTest();
        var dimensions = new java.util.HashSet<>(oldVacuum);
        dimensions.add(level.dimension());
        atmos.configureAtServerStart(dimensions, atmos.spaceDimensionsForTest());
        try {
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) if (x == 1 || x == 5 || z == 1 || z == 5)
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            BlockPos unitPos = helper.absolutePos(new BlockPos(3, 1, 3));
            BlockPos entry = unitPos.east();
            level.setBlock(unitPos, ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
            var unit = (com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
            unit.setPowerSwitch(false);
            require(unit != null && unit.prototype() != null && unit.reservoirAir() != null, "real resolved unit reservoir exists");
            long synthetic = Math.floorDiv(level.getGameTime(), (long) atmos.TICK_CADENCE) * atmos.TICK_CADENCE;
            for (int i = 0; i < 12 && atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isEmpty(); i++) {
                atmos.sample(level, unitPos.above());
                atmos.tick(level, synthetic += atmos.TICK_CADENCE);
            }
            require(atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isPresent(), "sealed room proven finite by atmosphere service");
            require(atmos.addGas(level, unitPos.above(), GasType.OXYGEN, 8, 300), "seed finite room oxygen");
            require(atmos.addGas(level, unitPos.above(), GasType.NITROGEN, 24, 300), "seed finite room nitrogen");
            GasMixture roomBefore = atmos.sample(level, unitPos.above()).orElseThrow();
            GasMixture reservoirBefore = unit.reservoirAir().sample().orElseThrow().mixture();
            GasMixture holderAir = com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitFlushGas.prepare(level, unit);
            GasMixture roomAfterPrepare = atmos.sample(level, unitPos.above()).orElseThrow();
            GasMixture reservoirAfterPrepare = unit.reservoirAir().sample().orElseThrow().mixture();
            assertConserved(roomBefore, reservoirBefore, roomAfterPrepare, reservoirAfterPrepare);

            var records = DisposalTubeWorldData.forMutation(level);
            require(level.getBlockState(entry).isAir(), "room entry begins as passable air");
            require(records.installExposed(level, new DisposalTubeRecord(UUID.randomUUID(), entry, "disposal_trunk",
                    new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.NORTH, Direction.UP), true, "", "", 0,
                    DisposalTubeRecord.HostKind.EXPOSED, "", null)), "real exposed trunk installed into room air");
            require(level.getBlockState(entry).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get()),
                    "entry has actual disposal tube owner block");
            var holder = DisposalTransitHolder.spawnFlushed(level, entry, holderAir).orElseThrow(() -> new GameTestAssertException("actual holder spawned from recorded trunk"));
            require(close(holder.finiteAir().orElseThrow().sample().orElseThrow().volumeCubicMeters(), 0.07), "resolved holder owns 0.07 cubic metre finite sample");
            var reservoirSample = unit.reservoirAir().sample().orElseThrow();
            require(unit.reservoirAir().replaceInventory(reservoirSample.mixture(), new GasMixture(java.util.Map.of(), reservoirSample.temperatureKelvin())), "flush transfers owned reservoir into holder");
            BlockPos sourceAirPos = unitPos.above();
            BlockPos destinationAirPos = entry.above();
            GasMixture beforeReleaseRooms = sampleRoomSum(atmos, level, sourceAirPos, destinationAirPos);
            GasMixture beforeReleaseHolder = holder.finiteAir().orElseThrow().sample().orElseThrow().mixture();
            holder.disconnectAt(holder.position(), entry, null);
            require(holder.isRemoved(), "holder releases into proven finite room and exits");
            require(atmos.finiteRoomEndpointForTrustedSimulation(level, destinationAirPos).isPresent(),
                    "release destination remains a known finite endpoint");
            var retainedInventory = holder.finiteAir().orElseThrow(() -> new GameTestAssertException(
                    "removed holder retains its finite-air inventory object"));
            GasMixture emptied = retainedInventory.sample().orElseThrow().mixture();
            require(emptied.totalMoles() == 0.0 && emptied.thermalEnergy() == 0.0,
                    "removed holder finite-air inventory is empty after release; moles=" + emptied.totalMoles()
                            + ", energy=" + emptied.thermalEnergy());
            GasMixture afterReleaseRooms = sampleRoomSum(atmos, level, sourceAirPos, destinationAirPos);
            GasMixture afterReleaseHolder = retainedInventory.sample().orElseThrow().mixture();
            assertConserved(beforeReleaseRooms, beforeReleaseHolder, afterReleaseRooms, afterReleaseHolder);
            require(close(holderAir.totalMoles(), beforeReleaseHolder.totalMoles()), "holder uses unit flush sample");
            helper.succeed();
        } finally { atmos.configureAtServerStart(oldVacuum, atmos.spaceDimensionsForTest()); }
    }

    private static GasMixture sampleRoomSum(AtmosphereService atmos, net.minecraft.server.level.ServerLevel level,
                                             BlockPos first, BlockPos second) {
        var moles = new java.util.EnumMap<GasType, Double>(GasType.class);
        double energy = 0;
        var positions = new java.util.LinkedHashSet<>(java.util.List.of(first, second));
        for (BlockPos pos : positions) {
            var sample = atmos.sample(level, pos).orElseThrow();
            for (GasType gas : GasType.values()) {
                double amount = sample.moles(gas);
                if (amount > 0) moles.merge(gas, amount, Double::sum);
            }
            energy += sample.thermalEnergy();
        }
        return new GasMixture(moles, 293.15).withThermalEnergy(energy);
    }

    private static void assertConserved(GasMixture roomBefore, GasMixture inventoryBefore,
                                        GasMixture roomAfter, GasMixture inventoryAfter) {
        for (GasType gas : GasType.values()) require(close(roomBefore.moles(gas) + inventoryBefore.moles(gas),
                roomAfter.moles(gas) + inventoryAfter.moles(gas)), "conserves " + gas);
        require(close(roomBefore.thermalEnergy() + inventoryBefore.thermalEnergy(),
                roomAfter.thermalEnergy() + inventoryAfter.thermalEnergy()), "conserves thermal energy");
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-7 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
