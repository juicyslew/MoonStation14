package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.fixture.FixturePipeAdmission;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authored, bounded stationary cabin used by shuttle GameTests and later plot setup. */
public final class ShuttleCabinFixture {
    public static final int WIDTH = 8, HEIGHT = 7, DEPTH = 13;
    public static final BlockPos AIRLOCK = new BlockPos(3, 1, 0);
    public static final BlockPos WINDOW = new BlockPos(0, 2, 4);
    public static final BlockPos PIPE = new BlockPos(6, 1, 8);
    public static final BlockPos APC = new BlockPos(4, 4, 7);
    public static final BlockPos MACHINE = new BlockPos(5, 4, 11);
    public static final BlockPos VENT = new BlockPos(5, 2, 3);
    public static final BlockPos SCRUBBER = new BlockPos(5, 2, 5);
    private static final List<BlockPos> SUPPLY_UPPER = List.of(new BlockPos(5, 5, 3), new BlockPos(5, 5, 4),
            new BlockPos(5, 5, 5), new BlockPos(5, 5, 6), new BlockPos(5, 5, 7), new BlockPos(5, 5, 8));
    private static final List<BlockPos> SUPPLY_CONCEALED = List.of(new BlockPos(5, 3, 3), new BlockPos(5, 4, 3),
            new BlockPos(5, 4, 8));
    private static final List<BlockPos> RETURN_CONCEALED = List.of(new BlockPos(5, 3, 5), new BlockPos(5, 4, 5));
    private static final List<BlockPos> SUPPLY_LOWER = List.of(new BlockPos(5, 3, 8), new BlockPos(5, 2, 8), new BlockPos(5, 1, 8));
    public static final BlockPos MIN_ROOM_CELL = new BlockPos(1, 1, 1);
    private static final int MIN_X = 1, MAX_X = 6, MIN_Y = 1, MAX_Y = 2, MIN_Z = 1, MAX_Z = 6;

    private ShuttleCabinFixture() { }

    @FunctionalInterface
    public interface HullWriter {
        boolean set(BlockPos position, BlockState state);
    }

    public record Built(BlockPos origin, UUID pipeIdentity, UUID airlockIdentity, List<BlockPos> roomCells) {
        public Built { roomCells = List.copyOf(roomCells); }
        public BlockPos at(BlockPos local) { return origin.offset(local); }
        public List<PipeTopology.NodeKey> supplyPipeNodes() {
            var nodes = new ArrayList<PipeTopology.NodeKey>();
            SUPPLY_UPPER.forEach(p -> nodes.add(new PipeTopology.NodeKey(at(p), PipeTopology.Layer.PRIMARY)));
            SUPPLY_CONCEALED.forEach(p -> nodes.add(new PipeTopology.NodeKey(at(p), PipeTopology.Layer.PRIMARY)));
            SUPPLY_LOWER.forEach(p -> nodes.add(new PipeTopology.NodeKey(at(p), PipeTopology.Layer.PRIMARY)));
            nodes.add(new PipeTopology.NodeKey(at(PIPE), PipeTopology.Layer.PRIMARY));
            return List.copyOf(nodes);
        }
        public List<PipeTopology.NodeKey> returnPipeNodes() {
            var nodes = new ArrayList<PipeTopology.NodeKey>();
            RETURN_CONCEALED.forEach(p -> nodes.add(new PipeTopology.NodeKey(at(p), PipeTopology.Layer.SECONDARY)));
            nodes.add(new PipeTopology.NodeKey(at(SUPPLY_UPPER.get(2)), PipeTopology.Layer.SECONDARY));
            return List.copyOf(nodes);
        }
    }

    /** Requires the caller to have loaded the entire plot footprint; never loads or tickets chunks. */
    public static Built build(ServerLevel level, BlockPos origin) {
        return build(level, origin, null);
    }

    /** Same authored fixture, with engine identity included in admission diagnostics for Sable callers. */
    public static Built build(ServerLevel level, BlockPos origin, UUID engineId) {
        if (level == null || origin == null || level.isClientSide || !level.getServer().isSameThread())
            throw new IllegalArgumentException("cabin setup requires the server thread");
        preflight(level, origin);

        buildHull(level, origin);
        AirlockBlockEntity airlock = (AirlockBlockEntity) level.getBlockEntity(origin.offset(AIRLOCK));
        installPowerMachine(level, origin);

        BlockPos pipe = origin.offset(PIPE);
        var pipeLane = FixturePipeAdmission.exposed(level, pipe, VisualPipeBlockEntity.PRIMARY);
        if (pipeLane.isEmpty())
            throw new IllegalStateException("visual pipe host failed to initialize");
        UUID pipeId = pipeLane.get();
        if (!LoadedPipeGasOwner.setGasForTrustedFixture(level, pipe, VisualPipeBlockEntity.PRIMARY, pipeId,
                new GasMixture(Map.of(GasType.OXYGEN, 0.75, GasType.NITROGEN, 1.25), 307.0)))
            throw new IllegalStateException("pipe gas fixture update lost its owner revision");

        installVentilation(level, origin, engineId);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = MIN_X; x <= MAX_X; x++) for (int y = MIN_Y; y <= MAX_Y; y++) for (int z = MIN_Z; z <= MAX_Z; z++) {
            BlockPos pos = origin.offset(x, y, z);
            if (com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, pos)) cells.add(pos);
        }
        if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level, cells,
                new GasMixture(Map.of(GasType.OXYGEN, 7.0, GasType.NITROGEN, 26.0), 289.0)))
            throw new IllegalStateException("bounded cabin room failed finite-enclosure proof");
        return new Built(origin.immutable(), pipeId, airlock.identity(), cells);
    }

    private static void installVentilation(ServerLevel level, BlockPos origin, UUID engineId) {
        BlockPos ventPos = origin.offset(VENT), scrubPos = origin.offset(SCRUBBER), alarmPos = origin.offset(MACHINE);
        level.setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.DOWN), 3);
        level.setBlock(scrubPos, ModBlocks.GAS_SCRUBBER.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.DOWN), 3);
        if (!(level.getBlockEntity(ventPos) instanceof VentilationDeviceBlockEntity vent)
                || !(level.getBlockEntity(scrubPos) instanceof VentilationDeviceBlockEntity scrub)
                || vent.prototype() == null || scrub.prototype() == null)
            throw new IllegalStateException("cabin ventilation devices failed resolved-prototype placement");
        var settings = scrub.config().settings();
        var scrubSettings = new VentilationDeviceConfig.Settings(settings.revision(), settings.configuredEnabled(),
                settings.externalPressureBoundKPa(), settings.targetPressureChangeKPaPerSecond(),
                settings.targetPressureKPa(), settings.volumeRateM3PerSecond(), settings.scrubberMode(),
                settings.filteredGases(), VentilationDeviceConfig.Layer.SECONDARY);
        if (!scrub.compareAndSetSettings(settings.revision(), scrubSettings))
            throw new IllegalStateException("cabin scrubber return-lane configuration failed");

        for (BlockPos local : SUPPLY_UPPER) {
            BlockPos host = origin.offset(local);
            UUID primary = FixturePipeAdmission.exposed(level, host, VisualPipeBlockEntity.PRIMARY)
                    .orElseThrow(() -> new IllegalStateException("cabin supply lane admission failed at " + host));
            if (!LoadedPipeGasOwner.setGasForTrustedFixture(level, host, VisualPipeBlockEntity.PRIMARY,
                    primary, new GasMixture(Map.of(GasType.OXYGEN, 998.0 * .21 / 6,
                            GasType.NITROGEN, 998.0 * .79 / 6), 293.0)))
                throw new IllegalStateException("cabin supply charge failed at " + host);
            if (local.equals(SUPPLY_UPPER.get(2))
                    && FixturePipeAdmission.exposed(level, host, VisualPipeBlockEntity.SECONDARY).isEmpty())
                throw new IllegalStateException("cabin return lane admission failed at " + host);
        }
        for (BlockPos local : SUPPLY_CONCEALED)
            requireConcealedAdmission(level, origin, local, PipeTopology.Layer.PRIMARY, engineId);
        for (BlockPos local : RETURN_CONCEALED)
            requireConcealedAdmission(level, origin, local, PipeTopology.Layer.SECONDARY, engineId);
        for (BlockPos local : SUPPLY_LOWER) {
            var lane = FixturePipeAdmission.exposed(level, origin.offset(local), VisualPipeBlockEntity.PRIMARY);
            if (lane.isEmpty()) throw new IllegalStateException("cabin exposed supply drop admission failed");
        }
        VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(5, 5, 3));
        VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(5, 5, 8));
        if (!(level.getBlockEntity(alarmPos) instanceof AirAlarmBlockEntity alarm)
                || !alarm.addDeviceLink(scrub) || !alarm.addDeviceLink(vent))
            throw new IllegalStateException("cabin alarm could not link ventilation devices");
    }

    private static void requireConcealedAdmission(ServerLevel level, BlockPos origin, BlockPos local,
                                                   PipeTopology.Layer lane, UUID engineId) {
        BlockPos storage = origin.offset(local);
        var result = FixturePipeAdmission.concealedDetailed(level, storage, Direction.UP, lane);
        if (!result.success()) {
            String context = "local=" + local + ", storage=" + storage + ", lane=" + lane
                    + ", axis=UP, engine=" + engineId + ", stage=" + result.failedStage();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "Cabin concealed pipe admission failed ({}): {}", context, result.diagnostics());
            String message = "cabin concealed pipe admission failed: " + context
                    + "; full diagnostics in server log";
            if (message.length() > 900) message = message.substring(0, 900);
            throw new IllegalStateException(message);
        }
    }

    /** Authours the reusable shell, internal room ceiling/wall and real two-tile hatch. */
    public static void buildHull(ServerLevel level, BlockPos origin) {
        buildHull(level, origin, (position, state) -> level.setBlock(position, state, 3));
    }

    /** Same canonical hull geometry for Sable storage fixtures; caller owns storage lifecycle and cleanup. */
    public static void buildHull(ServerLevel level, BlockPos origin, HullWriter writer) {
        if (level == null || origin == null || writer == null || level.isClientSide
                || !level.getServer().isSameThread())
            throw new IllegalArgumentException("cabin hull setup requires the server thread and a writer");
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            BlockPos pos = origin.offset(x, y, z);
            boolean shell = x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1 || z == 0 || z == DEPTH - 1;
            if (!shell) continue;
            boolean window = pos.equals(origin.offset(WINDOW));
            writeHullBlock(writer, pos, window ? Blocks.GLASS.defaultBlockState()
                    : ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
        }
        // Separate the finite room from the machinery bay; the small gas claim is a genuinely
        // sealed cell component rather than a clipped portion of a larger connected hull.
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++)
            writeHullBlock(writer, origin.offset(x, 3, z), ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
        for (int y = 1; y <= 3; y++) for (int x = 1; x <= 6; x++)
            writeHullBlock(writer, origin.offset(x, y, 7), ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
        BlockPos door = origin.offset(AIRLOCK);
        var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
        writeHullBlock(writer, door, lower);
        writeHullBlock(writer, door.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER));
        if (!(level.getBlockEntity(door) instanceof AirlockBlockEntity airlock) || airlock.prototype() == null)
            throw new IllegalStateException("authored two-tile airlock did not resolve its live prototype");
    }

    private static void writeHullBlock(HullWriter writer, BlockPos position, BlockState state) {
        if (!writer.set(position, state))
            throw new IllegalStateException("cabin hull block write failed at " + position);
    }

    private static void installPowerMachine(ServerLevel level, BlockPos origin) {
        BlockPos source = origin.offset(4, 4, 3), substation = origin.offset(4, 4, 5), apc = origin.offset(APC);
        BlockPos alarm = origin.offset(MACHINE);
        for (int z = 3; z <= 8; z++) level.setBlock(origin.offset(5, 4, z), ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        for (int z = 5; z <= 7; z++) level.setBlock(origin.offset(3, 4, z), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(alarm.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source, powerBlock(PowerDeviceKind.HV_SOURCE).defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        level.setBlock(substation, powerBlock(PowerDeviceKind.HV_MV_SUBSTATION).defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        if (!PowerDeviceFixturePlacement.placeApc(level, apc, Direction.WEST))
            throw new IllegalStateException("authored APC support or placement failed");
        for (int z = 3; z <= 5; z++) cable(level, origin.offset(5, 4, z), Direction.WEST, CableTier.HV);
        for (int z = 5; z <= 7; z++) cable(level, origin.offset(3, 4, z), Direction.EAST, CableTier.MV);
        for (int z = 7; z <= 8; z++) cable(level, origin.offset(5, 4, z), Direction.WEST, CableTier.APC);
        // Continue the existing APC output over the sealed room ceiling to a real
        // extension within the authored hatch's inherited three-block receiver radius.
        for (int z = 1; z <= 7; z++) cable(level, origin.offset(5, 3, z), Direction.WEST, CableTier.APC);
        level.setBlock(alarm, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        if (!(level.getBlockEntity(alarm) instanceof AirAlarmBlockEntity machine) || machine.prototype() == null)
            throw new IllegalStateException("authored alarm machine did not resolve its live prototype");
    }

    private static PowerDeviceBlock powerBlock(PowerDeviceKind kind) {
        return switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            default -> throw new IllegalArgumentException("unsupported cabin power device " + kind);
        };
    }

    private static void cable(ServerLevel level, BlockPos host, Direction face, CableTier tier) {
        if (!CableStorage.place(level, host, face, tier))
            throw new IllegalStateException("cabin power cable placement failed at " + host);
    }

    /** Checks the complete bounded cabin footprint without loading chunks or changing the world. */
    public static void preflight(ServerLevel level, BlockPos origin) {
        preflight(level, origin, WIDTH, HEIGHT, DEPTH);
    }

    /** Checks a dimensioned loaded, empty footprint without loading chunks or changing the world. */
    public static void preflight(ServerLevel level, BlockPos origin, int width, int height, int depth) {
        if (level == null || origin == null || width < 1 || height < 1 || depth < 1)
            throw new IllegalArgumentException("fixture footprint dimensions and origin must be valid");
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) {
            BlockPos pos = origin.offset(x, y, z);
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    ChunkStatus.FULL, false) instanceof LevelChunk)
                    || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                    || level.getBlockEntity(pos) != null)
                throw new IllegalArgumentException("cabin footprint must be loaded, in bounds, and empty at " + pos);
        }
    }
}
