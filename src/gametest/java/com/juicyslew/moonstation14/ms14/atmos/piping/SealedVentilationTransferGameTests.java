package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class SealedVentilationTransferGameTests {
    private static long syntheticGameTime;
    private SealedVentilationTransferGameTests() { }

@GameTest(template = "atmos_large_empty", batch = "sealed_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void resolvedFixtureGeometryIsFlushAndNonBlockingOnEveryHorizontalFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 2));
        for (var device : java.util.List.of(ModBlocks.GAS_VENT.get(), ModBlocks.GAS_SCRUBBER.get())) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = device.defaultBlockState().setValue(VentilationDeviceBlock.FACING, facing);
                helper.setBlock(new BlockPos(3, 1, 2), state);
                require(level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity, "real device block entity installed");
                var shape = device.getShape(state, level, pos, CollisionContext.empty());
                var bounds = shape.bounds();
                double x = bounds.maxX - bounds.minX;
                double y = bounds.maxY - bounds.minY;
                double z = bounds.maxZ - bounds.minZ;
                require(close(x + z, 0.875) && close(y, 0.75)
                                && close(facing.getAxis() == Direction.Axis.X ? x : z, 0.125)
                                && close(facing.getAxis() == Direction.Axis.X ? z : x, 0.75),
                        "fixture has declared 0.75 width, 0.75 height, and 0.125 depth");
                require(device.getCollisionShape(state, level, pos, CollisionContext.empty()).isEmpty(), "device collision is empty");
                BlockGetter delegate = new BlockGetter() {
                    @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos query) { return level.getBlockState(query); }
                    @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos query) { return level.getBlockEntity(query); }
                    @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos query) { return level.getFluidState(query); }
                    @Override public int getHeight() { return level.getHeight(); }
                    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
                };
                require(device.getShape(state, delegate, pos, CollisionContext.empty()).bounds().equals(bounds),
                        "delegate BlockGetter resolves fixture shape through installed device entity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "sealed_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void ventUsesLiveSealedWallLaneAndRejectsReplacementOrWrongAxis(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        GasMixture pipeBefore = f.pipe();
        GasMixture roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.vent, 1).moved(),
                "trusted invocation moves gas through already-paid sealed lane");
        GasMixture pipeAfter = f.pipe();
        GasMixture roomAfter = f.atmosphere.sample(f.level, f.room).orElseThrow();
        conserve(pipeBefore, roomBefore, pipeAfter, roomAfter);
        require(pipeAfter.moles(GasType.OXYGEN) < pipeBefore.moles(GasType.OXYGEN)
                && roomAfter.moles(GasType.OXYGEN) > roomBefore.moles(GasType.OXYGEN), "oxygen reaches finite room");

        GasMixture retained = pipeAfter;
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.STEEL_WALL_BLOCK.get());
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.vent, 1).moved(),
                "replacement host invalidates old sealed owner");
        require(same(retained, f.savedGas()), "replacement preserves old finite sealed gas");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "sealed_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void wrongWallAxisDoesNotTransfer(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        GasMixture pipe = f.savedGas();
        GasMixture room = f.atmosphere.sample(f.level, f.room).orElseThrow();
        require(!FiniteVentilationTransfer.executeTrustedAfterPaidInterval(f.level, f.atmosphere, f.vent, 1).moved(),
                "perpendicular sealed wall lane is not a vent endpoint");
        require(same(pipe, f.savedGas()) && same(room, f.atmosphere.sample(f.level, f.room).orElseThrow()),
                "wrong axis leaves both inventories unchanged");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "sealed_ventilation_transfer", timeoutTicks = 100, templateNamespace = "moonstation14")
    public static void visualPipeComponentFeedsSealedVentilationLane(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        ServerLevel level = f.level;
        BlockPos first = helper.absolutePos(new BlockPos(5, 1, 2));
        placePrimaryPipe(level, first);
        require(LoadedPipeGasOwner.createVacuumLane(level, first, VisualPipeBlockEntity.PRIMARY).isPresent(),
                "create exposed primary lane owner");
        require(LoadedPipeGasOwner.activeSnapshot(level, first, VisualPipeBlockEntity.PRIMARY).isPresent(),
                "exposed primary lane owner active");
        var exposed = (VisualPipeBlockEntity) level.getBlockEntity(first);
        exposed.refreshConcealedJoins();
        require(exposed.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.WEST),
                "live matching exposed owner renders concealed wall join");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var before = saved.snapshot();
        var key = new PipeTopology.NodeKey(first, PipeTopology.Layer.PRIMARY);
        var entry = before.get(key).orElseThrow();
        GasMixture seed = new GasMixture(Map.of(GasType.OXYGEN, 12.0), 240.0);
        require(saved.replace(before.revision(), before, before.withMixture(key, entry.incarnation(), entry.version(), seed)), "seed exposed pipe oxygen");

        var device = f.vent;
        var defaults = device.config().settings();
        require(device.compareAndSetSettings(defaults.revision(), new VentilationDeviceConfig.Settings(
                defaults.revision(), true, defaults.externalPressureBoundKPa(), defaults.targetPressureChangeKPaPerSecond(),
                defaults.targetPressureKPa(), defaults.volumeRateM3PerSecond(), defaults.scrubberMode(),
                defaults.filteredGases(), VentilationDeviceConfig.Layer.PRIMARY)), "enable device primary lane");
        var component = LoadedPipeComponent.discover(level, f.wall, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(component.nodes().size() == 2, "one exposed visual node connects to sealed-wall owner");
        GasMixture equalizationBefore = componentMixture(saved.snapshot(), component);
        require(LoadedPipeEqualization.equalize(level, device), "live device consumer equalizes exposed and sealed wall lanes");
        var equalized = saved.snapshot();
        GasMixture equalizationAfter = componentMixture(equalized, component);
        for (GasType gas : GasType.values())
            require(close(equalizationBefore.moles(gas), equalizationAfter.moles(gas)), "equalization conserves " + gas);
        require(close(equalizationBefore.thermalEnergy(), equalizationAfter.thermalEnergy()), "equalization conserves enthalpy");
        require(close(equalized.get(key).orElseThrow().mixture().moles(GasType.OXYGEN), 12.0)
                        && close(equalized.get(key).orElseThrow().mixture().moles(GasType.NITROGEN), 22.5),
                "sealed primary lane receives equal share from exposed component");
        require(equalized.get(new PipeTopology.NodeKey(first, PipeTopology.Layer.PRIMARY)).orElseThrow().mixture()
                        .gasMoles().equals(equalized.get(key).orElseThrow().mixture().gasMoles()),
                "exposed primary lane has the same equalized gas mixture");
        require(equalized.get(new PipeTopology.NodeKey(first, PipeTopology.Layer.SECONDARY)).isEmpty(),
                "consumer does not create or mutate another lane");
        GasMixture pipeBefore = componentMixture(saved.snapshot(), component);
        GasMixture roomBefore = f.atmosphere.sample(level, f.room).orElseThrow();
        require(FiniteVentilationTransfer.executeTrustedAfterPaidInterval(level, f.atmosphere, f.vent, 1).moved(),
                "trusted invocation transfers from sealed side into finite room");
        var afterTransfer = saved.snapshot();
        GasMixture pipeAfter = componentMixture(afterTransfer, component);
        GasMixture roomAfter = f.atmosphere.sample(level, f.room).orElseThrow();
        conserve(pipeBefore, roomBefore, pipeAfter, roomAfter);

        var hostState = level.getBlockState(f.wall);
        var hostShape = hostState.getCollisionShape(level, f.wall);
        require(hostState.is(ModBlocks.STEEL_WALL_BLOCK.get()), "host remains steel wall");
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedPipeComponent.discover(level, f.wall, VisualPipeBlockEntity.PRIMARY).isEmpty(), "same-type host replacement invalidates graph");
        require(level.getBlockState(f.wall).getCollisionShape(level, f.wall).equals(hostShape), "steel host collision unchanged");
        require(same(pipeAfter, componentMixture(saved.snapshot(), component)), "replacement retains saved oxygen");
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_JOIN_PASS actualDeviceEqualization");
    }

    private static void placePrimaryPipe(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "enable primary visual lane");
    }

    private static GasMixture componentMixture(PipeGasInventory inventory, LoadedPipeComponent.Snapshot component) {
        var moles = new java.util.EnumMap<GasType, Double>(GasType.class);
        double energy = 0;
        for (var node : component.nodes()) {
            GasMixture mixture = inventory.get(node.key()).orElseThrow().mixture();
            for (GasType gas : GasType.values()) moles.merge(gas, mixture.moles(gas), Double::sum);
            energy += mixture.thermalEnergy();
        }
        GasMixture result = new GasMixture(moles, 300.0);
        return result.totalMoles() == 0 ? result : result.withThermalEnergy(energy);
    }

    private static Fixture fixture(GameTestHelper helper, boolean wrongAxis) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
        }
        BlockPos ventPos = helper.absolutePos(new BlockPos(3, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.WEST));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 1, 2));
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedSealedWallSegment.prepareOne(level, wall, wrongAxis ? Direction.NORTH : Direction.EAST,
                PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(),
                "admit straight sealed wall lane");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, wall, PipeTopology.Layer.PRIMARY), "create finite owner");
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(wall, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var owner = before.get(key).orElseThrow();
        GasMixture seed = new GasMixture(Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310);
        require(saved.replace(before.revision(), before, before.withMixture(key, owner.incarnation(), owner.version(), seed)), "seed sealed inventory");
        BlockPos room = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int i = 0; i < 12 && atmosphere.sample(level, room).isEmpty(); i++)
            atmosphere.tick(level, syntheticGameTime += AtmosphereService.TICK_CADENCE);
        require(atmosphere.sample(level, room).isPresent(), "finite room classified");
        var vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(vent != null, "vent exists");
        return new Fixture(level, atmosphere, room, wall, vent, saved);
    }

    private record Fixture(ServerLevel level, AtmosphereService atmosphere, BlockPos room, BlockPos wall,
                           VentilationDeviceBlockEntity vent, PipeGasInventorySavedData saved) {
        GasMixture pipe() { return LoadedSealedWallGasOwner.activeSnapshot(level, wall, PipeTopology.Layer.PRIMARY).orElseThrow().mixture(); }
        GasMixture savedGas() { return saved.snapshot().get(new PipeTopology.NodeKey(wall, PipeTopology.Layer.PRIMARY)).orElseThrow().mixture(); }
    }
    private static void conserve(GasMixture p0, GasMixture r0, GasMixture p1, GasMixture r1) {
        for (GasType gas : GasType.values()) require(close(p0.moles(gas) + r0.moles(gas), p1.moles(gas) + r1.moles(gas)), "conserve " + gas);
        require(close(p0.thermalEnergy() + r0.thermalEnergy(), p1.thermalEnergy() + r1.thermalEnergy()), "conserve energy");
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (!close(a.thermalEnergy(), b.thermalEnergy())) return false;
        for (GasType gas : GasType.values()) if (!close(a.moles(gas), b.moles(gas))) return false;
        return true;
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
