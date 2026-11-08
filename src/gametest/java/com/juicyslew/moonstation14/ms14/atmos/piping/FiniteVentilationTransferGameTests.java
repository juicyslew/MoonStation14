package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@GameTestTags({"ventilation", "atmosphere", "atmos-finite-ventilation-boundary", "atmos-prototype-live-default"})
@PrefixGameTestTemplate(false)
public final class FiniteVentilationTransferGameTests {
    private FiniteVentilationTransferGameTests() { }

@GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void ventTransfersFinitePipeGasToRoomConservatively(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        GasMixture pipeBefore = f.pipeMixture();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        GasMixture upperBefore = f.cell(new BlockPos(2, 2, 2));
        GasMixture ventBefore = f.cell(new BlockPos(3, 1, 2));
        GasMixture pipeHostBefore = f.cell(new BlockPos(4, 1, 2));
        BlockPos wall = new BlockPos(4, 1, 2);
        var wallBefore = f.level.getBlockState(helper.absolutePos(wall));
        var claimedBefore = f.chunkData().isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15);

        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1.0).moved(),
                "trusted kernel invocation moves vent gas");
        GasMixture pipeAfter = f.pipeMixture();
        GasMixture roomAfter = f.atmosphere.sample(f.level, f.room).orElseThrow();
        assertConserved(pipeBefore, roomBefore, pipeAfter, roomAfter);
        require(same(upperBefore, f.cell(new BlockPos(2, 2, 2)))
                        && same(ventBefore, f.cell(new BlockPos(3, 1, 2)))
                        && same(pipeHostBefore, f.cell(new BlockPos(4, 1, 2))),
                "transfer leaves other initialized room cells, including pipe host cell, unchanged");
        require(pipeAfter.totalMoles() < pipeBefore.totalMoles(), "finite pipe inventory decreases");
        require(roomAfter.totalMoles() > roomBefore.totalMoles(), "finite room inventory increases");
        require(f.level.getBlockState(helper.absolutePos(wall)).equals(wallBefore), "sealed room wall remains unchanged");
        require(f.chunkData().isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15) == claimedBefore,
                "finite room claim remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void scrubberCapturesOnlySelectedGasIntoSelectedWastePipe(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.SCRUBBER);
        require(f.atmosphere.addGas(f.level, f.room, GasType.CARBON_DIOXIDE, 5, 300), "seed room CO2");
        require(f.atmosphere.addGas(f.level, f.room, GasType.OXYGEN, 3, 300), "seed room oxygen");
        require(f.atmosphere.addGas(f.level, f.room, GasType.NITROGEN, 7, 300), "seed room nitrogen");
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        GasMixture upperBefore = f.cell(new BlockPos(2, 2, 2));
        GasMixture ventBefore = f.cell(new BlockPos(3, 1, 2));
        GasMixture pipeHostBefore = f.cell(new BlockPos(4, 1, 2));
        GasMixture pipeBefore = f.pipeMixture();
        f.device.config().compareAndSet(0, new VentilationDeviceConfig.Settings(0, true, 0, 0, 0,
                1, VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(GasType.CARBON_DIOXIDE),
                VentilationDeviceConfig.Layer.PRIMARY));

        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1.0).moved(),
                "trusted kernel invocation moves scrubber gas");
        GasMixture roomAfter = f.atmosphere.sample(f.level, f.room).orElseThrow();
        GasMixture pipeAfter = f.pipeMixture();
        double captured = roomBefore.moles(GasType.CARBON_DIOXIDE) - roomAfter.moles(GasType.CARBON_DIOXIDE);
        require(captured > 0, "scrubber captures CO2");
        require(close(roomAfter.moles(GasType.OXYGEN), roomBefore.moles(GasType.OXYGEN)), "room oxygen unchanged");
        require(close(roomAfter.moles(GasType.NITROGEN), roomBefore.moles(GasType.NITROGEN)), "room nitrogen unchanged");
        require(close(pipeAfter.moles(GasType.CARBON_DIOXIDE) - pipeBefore.moles(GasType.CARBON_DIOXIDE), captured),
                "selected waste pipe receives exactly captured CO2");
        require(close(pipeAfter.moles(GasType.OXYGEN), pipeBefore.moles(GasType.OXYGEN)), "waste pipe oxygen unchanged");
        require(close(pipeAfter.moles(GasType.NITROGEN), pipeBefore.moles(GasType.NITROGEN)), "waste pipe nitrogen unchanged");
        double energyMoved = pipeAfter.thermalEnergy() - pipeBefore.thermalEnergy();
        require(energyMoved > 0 && close(roomBefore.thermalEnergy() - roomAfter.thermalEnergy(), energyMoved),
                "waste inventory gains exactly the room energy removed");
        require(same(upperBefore, f.cell(new BlockPos(2, 2, 2)))
                        && same(ventBefore, f.cell(new BlockPos(3, 1, 2)))
                        && same(pipeHostBefore, f.cell(new BlockPos(4, 1, 2))),
                "scrubber leaves other initialized room cells, including pipe host cell, unchanged");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void invalidOwnershipLayerAndDisabledSettingsDoNotMoveGas(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        GasMixture pipeBefore = f.pipeMixture();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        GasMixture upperBefore = f.cell(new BlockPos(2, 2, 2));
        GasMixture ventBefore = f.cell(new BlockPos(3, 1, 2));
        require(f.device.config().compareAndSet(0, new VentilationDeviceConfig.Settings(0, true,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA,
                Ss14VentilationDefaults.VENT_TARGET_PRESSURE_CHANGE_KPA_PER_SECOND,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.SECONDARY)),
                "select missing secondary layer");
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "wrong selected layer cannot move gas");
        require(f.device.config().compareAndSet(1, new VentilationDeviceConfig.Settings(1, true,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA,
                Ss14VentilationDefaults.VENT_TARGET_PRESSURE_CHANGE_KPA_PER_SECOND,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.PRIMARY)),
                "restore primary layer");
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
        require(f.atmosphere.finiteRoomEndpointForTrustedSimulation(f.level, f.room).isEmpty(),
                "adjacent unclaimed opening invalidates finite endpoint immediately");
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "exterior-connected room cannot move gas");
        require(same(f.pipeMixture(), pipeBefore), "opening rejection leaves pipe inventory unchanged");
        require(same(f.atmosphere.sample(f.level, f.room).orElseThrow(), roomBefore),
                "opening rejection leaves room gas unchanged");
        require(same(upperBefore, f.cell(new BlockPos(2, 2, 2))) && same(ventBefore, f.cell(new BlockPos(3, 1, 2))),
                "rejected transfer leaves other initialized finite cells unchanged");
        require(f.device.config().compareAndSet(2, new VentilationDeviceConfig.Settings(2, false,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0,
                Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.PRIMARY)),
                "disable device settings");
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "disabled device cannot move gas");
        require(same(f.pipeMixture(), pipeBefore), "rejected calls do not mutate pipe inventory");
        require(same(f.atmosphere.sample(f.level, f.room).orElseThrow(), roomBefore), "rejected calls do not mutate room gas");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void distantExteriorBreachRejectsVentTransfer(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "initial closed-room transfer succeeds");
        GasMixture pipeBefore = f.pipeMixture();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        GasMixture upperBefore = f.cell(new BlockPos(2, 2, 2));
        GasMixture ventBefore = f.cell(new BlockPos(3, 1, 2));
        // The two-cell corridor to the outside is opened in one topology change; validation must
        // discover beyond the endpoint's immediate face rather than trusting its saved claim.
        BlockPos breach = new BlockPos(2, 3, 2);
        require(boundaryCells().contains(breach), "distant breach is an authored boundary cell");
        require(breach.distManhattan(new BlockPos(2, 1, 2)) >= 2,
                "distant breach is not directly adjacent to device room cell");
        helper.setBlock(breach, Blocks.AIR);
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "exterior-connected room cannot transfer gas");
        require(same(f.pipeMixture(), pipeBefore), "breach rejection leaves pipe unchanged");
        require(same(f.atmosphere.sample(f.level, f.room).orElseThrow(), roomBefore), "breach rejection leaves room unchanged");
        require(same(upperBefore, f.cell(new BlockPos(2, 2, 2))) && same(ventBefore, f.cell(new BlockPos(3, 1, 2))),
                "breach rejection leaves other initialized finite cells unchanged");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmos-finite-ventilation-boundary", "atmos-prototype-live-default"})
    public static void resolvedDefaultPumpPowerBlocksLowPressureFiniteRoomTransfer(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        var pump = f.device.prototype().gasVentPump();
        require(pump != null && pump.pumpPower() == 2.0
                        && pump.pressureChecks() == com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.EXTERNAL,
                "live block entity consumes resolved default vent pump profile");
        double roomVolume = 1.0;
        double pipeVolume = Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3;
        double temperature = 310;
        double lowPressurePipeMoles = 10.0 * pipeVolume * 1000 / (8.31446261815324 * temperature);
        double highRoomMoles = 21.0 * roomVolume * 1000 / (8.31446261815324 * temperature);
        require(f.setPipe(Map.of(GasType.OXYGEN, lowPressurePipeMoles), temperature), "seed low-pressure pipe via exact owner CAS");
        require(f.atmosphere.addGas(f.level, f.room, GasType.NITROGEN, highRoomMoles, temperature), "seed finite room above doubled source pressure");
        var settings = f.device.config().settings();
        require(f.device.config().compareAndSet(settings.revision(), new VentilationDeviceConfig.Settings(settings.revision(), true,
                101.325, 101.325, 101.325, settings.volumeRateM3PerSecond(), settings.scrubberMode(),
                settings.gasesToScrub(), settings.selectedLayer())), "configure bound and target for resolved-profile pressure check");
        var pipeBefore = f.pipeMixture();
        var roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        require(pipeBefore.pressureKpa(pipeVolume) < 10.01 && roomBefore.pressureKpa(roomVolume) > 20.99,
                "independently authored source/destination pressures establish 2x pump rejection");
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "resolved 2x pump cannot release 10 kPa source into room above 20 kPa");
        require(same(pipeBefore, f.pipeMixture()) && same(roomBefore, f.atmosphere.sample(f.level, f.room).orElseThrow()),
                "pressure rejection leaves source and finite room unchanged");

        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmos-finite-ventilation-boundary", "atmos-prototype-live-default"})
    public static void resolvedDefaultPumpCompressesIntoHigherPressureFiniteRoom(GameTestHelper helper) {
        Fixture g = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        require(g.device.prototype().gasVentPump().pumpPower() == 2.0, "native vent resolves default pump power two");
        double roomVolume = 1.0;
        double pipeVolume = Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3;
        double temperature = 310;
        double room15kpaMoles = 15.0 * roomVolume * 1000 / (8.31446261815324 * temperature);
        double lowPressurePipeMoles = 10.0 * pipeVolume * 1000 / (8.31446261815324 * temperature);
        require(g.setPipe(Map.of(GasType.OXYGEN, lowPressurePipeMoles), temperature), "seed compression case pipe via exact owner CAS");
        require(g.atmosphere.addGas(g.level, g.room, GasType.NITROGEN, room15kpaMoles, temperature), "seed independent 15 kPa finite room");
        var gSettings = g.device.config().settings();
        require(g.device.config().compareAndSet(gSettings.revision(), new VentilationDeviceConfig.Settings(gSettings.revision(), true,
                101.325, 101.325, 101.325, gSettings.volumeRateM3PerSecond(), gSettings.scrubberMode(),
                gSettings.gasesToScrub(), gSettings.selectedLayer())), "configure compression bound");
        var compressed = FiniteVentilationTransfer.executeTrustedAfterPaidInterval(g.level, g.atmosphere, g.device, 1);
        require(compressed.moved(), "2x pump permits positive compression from 10 kPa pipe into 15 kPa finite room");
        require(g.pipeMixture().totalMoles() < lowPressurePipeMoles
                        && g.atmosphere.sample(g.level, g.room).orElseThrow().totalMoles() > room15kpaMoles,
                "compression consumes exact finite pipe inventory and increases room inventory");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmos-exterior-ventilation-boundary", "atmos-prototype-live-default"})
    public static void resolvedDefaultPumpExportsBoundedPacketWithExactReceipt(GameTestHelper helper) {
        Fixture f = exteriorFixture(helper);
        var boundary = f.atmosphere.exteriorBoundaryForImmediateTransfer(f.level, f.room).orElseThrow(
                () -> new GameTestAssertException("actual loaded exterior boundary admitted"));
        require(f.chunkData() == null || !f.chunkData().isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15),
                "actual exterior kernel endpoint has no finite-room claim");
        double ambient = boundary.ambient().pressureKpa(1.0);
        double bound = ambient + 0.05;
        double temperature = 310;
        double sourcePressure = bound / 2.0 + 10.0;
        double pipeMoles = sourcePressure * Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3 * 1000
                / (8.31446261815324 * temperature);
        require(f.setPipe(Map.of(GasType.OXYGEN, pipeMoles), temperature), "seed exterior source via exact pipe-owner CAS");
        var settings = f.device.config().settings();
        require(f.device.config().compareAndSet(settings.revision(), new VentilationDeviceConfig.Settings(settings.revision(), true,
                bound, bound, bound, settings.volumeRateM3PerSecond(), settings.scrubberMode(), settings.gasesToScrub(), settings.selectedLayer())),
                "configure actual ambient plus narrow external pressure bound and equal target");
        var before = f.pipeMixture();
        require(f.device.prototype().gasVentPump().pumpPower() == 2.0
                        && f.device.prototype().gasVentPump().externalPressureBoundKPa() > 0,
                "live block entity resolves native default pump profile");
        require(before.pressureKpa(Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) * 2 > bound,
                "resolved pump power supplies pressure above actual ambient bound");
        var result = FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1);
        require(result.moved() && result.export() != null && result.export().kind() == boundary.kind()
                        && boundary.validate(), "trusted-kernel exterior transfer returns valid actual-boundary receipt");
        var after = f.pipeMixture();
        require(after.totalMoles() < before.totalMoles(), "export depletes the owned pipe inventory");
        require(same(result.packet(), result.export().packet()), "export receipt records exactly the committed packet");
        for (GasType gas : GasType.values()) require(PipeGasTransfer.close(before.moles(gas),
                        after.moles(gas) + result.export().packet().moles(gas)),
                "exact pipe CAS loss matches receipt species " + gas);
        require(PipeGasTransfer.close(before.thermalEnergy(),
                        after.thermalEnergy() + result.export().thermalEnergyJoules()),
                "exact pipe CAS enthalpy loss matches export receipt");
        double roomVolume = 1.0;
        double packetMoleBudget = Math.max(0.0, bound - ambient) * roomVolume * 1000.0
                / (8.31446261815324 * temperature);
        double packetMoles = result.export().packet().totalMoles();
        double budgetAllowance = 8.0 * Math.max(Math.ulp((float) packetMoles),
                Math.ulp((float) packetMoleBudget));
        require(packetMoles <= packetMoleBudget + budgetAllowance,
                "receipt packet respects independently derived ambient/bound room pressure budget");
        require(after.pressureKpa(Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) >= 0,
                "post-export owned inventory remains physically valid");
        helper.succeed();
    }

    /** Native exterior endpoint fixture: the vent's actual room-facing cell is at the loaded surface. */
    private static Fixture exteriorFixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos ventPos = helper.absolutePos(new BlockPos(3, 1, 2));
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                ventPos.getX(), ventPos.getZ());
        BlockPos room = new BlockPos(ventPos.getX(), surface + 2, ventPos.getZ());
        ventPos = room.relative(Direction.DOWN);
        BlockPos pipePos = ventPos.relative(Direction.DOWN);
        require(level.hasChunkAt(room) && room.getY() < level.getMaxBuildHeight(), "surface endpoint is loaded and in build range");
        level.setBlockAndUpdate(room, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.UP));
        level.setBlockAndUpdate(pipePos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState());
        require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, room),
                "vent-facing surface cell is passable");
        var owner = LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("surface fixture pipe owner admitted"));
        var key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        var data = PipeGasInventorySavedData.existing(level).orElseThrow();
        var saved = data.snapshot();
        var entry = saved.get(key).orElseThrow();
        require(entry.incarnation().equals(owner), "surface fixture exact owner persisted");
        var device = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(device != null, "surface vent block entity exists");
        return new Fixture(level, atmosphere, room, device, pipePos);
    }

    private static Fixture fixture(GameTestHelper helper, VentilationDeviceConfig.Kind kind) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        var interior = java.util.List.of(new BlockPos(2, 1, 2), new BlockPos(2, 2, 2),
                new BlockPos(3, 1, 2), new BlockPos(4, 1, 2));
        var boundary = boundaryCells();
        for (BlockPos relative : boundary) helper.setBlock(relative, Blocks.STONE);
        for (BlockPos relative : interior) helper.setBlock(relative, Blocks.AIR);
        BlockPos room = absolute(helper, new BlockPos(2, 1, 2));
        // The block at (3,1,2) faces the room and has one exposed pipe directly behind it.
        helper.setBlock(new BlockPos(3, 1, 2), (kind == VentilationDeviceConfig.Kind.VENT
                ? ModBlocks.GAS_VENT : ModBlocks.GAS_SCRUBBER).get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, Direction.WEST));
        BlockPos pipePos = absolute(helper, new BlockPos(4, 1, 2));
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.GAS_PIPE_VISUAL.get());
        var cells = interior.stream().map(pos -> absolute(helper, pos)).toList();
        for (BlockPos cell : cells) require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, cell),
                "expected passable interior cell " + cell + "; state=" + level.getBlockState(cell));
        for (BlockPos wall : boundary) require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology
                        .isPassable(level, absolute(helper, wall)), "expected sealed boundary at " + wall);
        var rejection = new StringBuilder();
        require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level, cells, GasMixture.vacuum(),
                        reason -> rejection.append(reason)),
                "trusted setup proves complete sealed room; rejection=" + rejection + ", cells=" + cells);
        LevelChunk roomChunk = (LevelChunk) level.getChunk(room);
        var roomData = roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(roomData != null && roomData.isFiniteClaimed(room.getX() & 15, room.getY(), room.getZ() & 15),
                "room has finite claim");
        var owner = LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new IllegalStateException("pipe lane owner not admitted"));
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        GasMixture seedContents = kind == VentilationDeviceConfig.Kind.VENT
                ? new GasMixture(Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310)
                : GasMixture.vacuum();
        GasMixture seed = PipeGasFixtureTestSupport.atCurrentNetworkVolume(before, key,
                seedContents.gasMoles(), seedContents.temperatureKelvin());
        PipeGasInventory after = before.withMixture(key, owner, before.get(key).orElseThrow().version(), seed);
        require(saved.replace(before.revision(), before, after), "test-seed pipe saved data");
        var device = (VentilationDeviceBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 2)));
        require(device != null, "device block entity exists");
        return new Fixture(level, atmosphere, room, device, pipePos);
    }

    private static BlockPos absolute(GameTestHelper helper, BlockPos relative) {
        return helper.absolutePos(relative);
    }

    private static Set<BlockPos> boundaryCells() {
        Set<BlockPos> interior = Set.of(new BlockPos(2, 1, 2), new BlockPos(2, 2, 2),
                new BlockPos(3, 1, 2), new BlockPos(4, 1, 2));
        Set<BlockPos> boundary = new HashSet<>();
        for (BlockPos cell : interior) for (Direction direction : Direction.values()) {
            BlockPos neighbor = cell.relative(direction);
            if (!interior.contains(neighbor)) boundary.add(neighbor);
        }
        return boundary;
    }

    private record Fixture(ServerLevel level, AtmosphereService atmosphere, BlockPos room,
                           VentilationDeviceBlockEntity device, BlockPos pipePos) {
        GasMixture pipeMixture() {
            var key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
            return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().mixture(key);
        }
        boolean setPipe(Map<GasType, Double> moles, double temperature) {
            var key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
            var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
            var before = saved.snapshot();
            var entry = before.get(key).orElseThrow();
            var active = LoadedPipeGasOwner.activeSnapshot(level, pipePos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
            var networkBefore=before.network(key).orElseThrow();
            GasMixture mixture = PipeGasFixtureTestSupport.atCurrentNetworkVolume(before, key, moles, temperature);
            if (!entry.active() || !entry.incarnation().equals(active.incarnation())
                    || entry.version() != active.version() || Double.compare(entry.volume(), active.volume()) != 0)
                return false;
            boolean replaced = saved.replace(before.revision(), before,
                    before.withMixture(key, active.incarnation(), active.version(), mixture));
            if (!replaced) return false;
            var after=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var current = after.get(key).orElse(null);
            var currentOwner = LoadedPipeGasOwner.activeSnapshot(level, pipePos, VisualPipeBlockEntity.PRIMARY).orElse(null);
            var networkAfter=after.network(key).orElse(null);
            var share=after.nodeShare(key);
            return current != null && currentOwner != null
                    && current.incarnation().equals(active.incarnation())
                    && currentOwner.incarnation().equals(active.incarnation())
                    && current.version() == active.version()
                    && currentOwner.version() == current.version()
                    && Double.compare(current.volume(), active.volume()) == 0
                    && networkAfter!=null&&networkAfter.id().equals(networkBefore.id())
                    &&networkAfter.members().equals(networkBefore.members())
                    &&networkAfter.gasRevision()==networkBefore.gasRevision()+1
                    &&Double.compare(share.volumeCubicMeters(),(double)(float)active.volume())==0
                    && same(share, mixture);
        }
        GasMixture cell(BlockPos relative) {
            return atmosphere.sample(level, new BlockPos(room.getX() + relative.getX() - 2,
                    room.getY() + relative.getY() - 1, room.getZ() + relative.getZ() - 2)).orElseThrow(
                    () -> new GameTestAssertException("initialized finite cell sample exists at " + relative));
        }
        com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData chunkData() {
            return ((LevelChunk) level.getChunk(room)).getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        }
    }

    private static void assertConserved(GasMixture pipeBefore, GasMixture roomBefore,
                                        GasMixture pipeAfter, GasMixture roomAfter) {
        for (GasType gas : GasType.values()) require(close(pipeBefore.moles(gas) + roomBefore.moles(gas),
                pipeAfter.moles(gas) + roomAfter.moles(gas)), "conserves " + gas);
        require(close(pipeBefore.thermalEnergy() + roomBefore.thermalEnergy(),
                pipeAfter.thermalEnergy() + roomAfter.thermalEnergy()), "conserves thermal energy");
    }

    private static boolean same(GasMixture a, GasMixture b) {
        if (a == null || b == null || !close(a.thermalEnergy(), b.thermalEnergy())) return false;
        for (GasType gas : GasType.values()) if (!close(a.moles(gas), b.moles(gas))) return false;
        return true;
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
