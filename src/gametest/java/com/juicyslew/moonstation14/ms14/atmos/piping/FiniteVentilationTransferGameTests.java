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

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class FiniteVentilationTransferGameTests {
    private static long syntheticGameTime;

    private FiniteVentilationTransferGameTests() { }

@GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void ventTransfersFinitePipeGasToRoomConservatively(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        GasMixture pipeBefore = f.pipeMixture();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        BlockPos wall = new BlockPos(4, 1, 2);
        var wallBefore = f.level.getBlockState(helper.absolutePos(wall));
        var claimedBefore = f.chunkData().isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15);

        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1.0).moved(),
                "trusted kernel invocation moves vent gas");
        GasMixture pipeAfter = f.pipeMixture();
        GasMixture roomAfter = f.atmosphere.sample(f.level, f.room).orElseThrow();
        assertConserved(pipeBefore, roomBefore, pipeAfter, roomAfter);
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
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void invalidOwnershipLayerAndDisabledSettingsDoNotMoveGas(GameTestHelper helper) {
        Fixture f = fixture(helper, VentilationDeviceConfig.Kind.VENT);
        GasMixture pipeBefore = f.pipeMixture();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
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
        // The two-cell corridor to the outside is opened in one topology change; validation must
        // discover beyond the endpoint's immediate face rather than trusting its saved claim.
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.AIR);
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.device, 1).moved(),
                "exterior-connected room cannot transfer gas");
        require(same(f.pipeMixture(), pipeBefore), "breach rejection leaves pipe unchanged");
        require(same(f.atmosphere.sample(f.level, f.room).orElseThrow(), roomBefore), "breach rejection leaves room unchanged");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, VentilationDeviceConfig.Kind kind) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(x, y, z),
                    x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        // The block at (3,1,2) faces the room and has one exposed pipe directly behind it.
        helper.setBlock(new BlockPos(3, 1, 2), (kind == VentilationDeviceConfig.Kind.VENT
                ? ModBlocks.GAS_VENT : ModBlocks.GAS_SCRUBBER).get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, Direction.WEST));
        BlockPos pipePos = helper.absolutePos(new BlockPos(4, 1, 2));
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.GAS_PIPE_VISUAL.get());
        BlockPos room = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int pass = 1; pass <= 12 && atmosphere.sample(level, room).isEmpty(); pass++)
            atmosphere.tick(level, syntheticGameTime += AtmosphereService.TICK_CADENCE);
        require(atmosphere.sample(level, room).isPresent(), "sealed room classified");
        LevelChunk roomChunk = (LevelChunk) level.getChunk(room);
        var roomData = roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        require(roomData != null && roomData.isFiniteClaimed(room.getX() & 15, room.getY(), room.getZ() & 15),
                "room has finite claim");
        var owner = LoadedPipeGasOwner.createVacuumLane(level, pipePos, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new IllegalStateException("pipe lane owner not admitted"));
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        GasMixture seed = kind == VentilationDeviceConfig.Kind.VENT
                ? new GasMixture(Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310)
                : GasMixture.vacuum();
        PipeGasInventory after = before.withMixture(key, owner, before.get(key).orElseThrow().version(), seed);
        require(saved.replace(before.revision(), before, after), "test-seed pipe saved data");
        var device = (VentilationDeviceBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 2)));
        require(device != null, "device block entity exists");
        return new Fixture(level, atmosphere, room, device, pipePos);
    }

    private record Fixture(ServerLevel level, AtmosphereService atmosphere, BlockPos room,
                           VentilationDeviceBlockEntity device, BlockPos pipePos) {
        GasMixture pipeMixture() {
            int lane = VisualPipeBlockEntity.PRIMARY;
            return LoadedPipeGasOwner.activeSnapshot(level, pipePos, lane).orElseThrow().mixture();
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
