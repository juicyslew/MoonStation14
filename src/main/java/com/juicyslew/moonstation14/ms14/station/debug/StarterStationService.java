package com.juicyslew.moonstation14.ms14.station.debug;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** New-world-only trigger and loaded-chunk-only bounded placement retries. */
public final class StarterStationService {
    private static final int MAX_CANDIDATES = 8;
    private static final int RETRY_INTERVAL_TICKS = 40;
    private static final int MAX_TERRAIN_VARIATION = 3;
    private static final StarterStationLayout LAYOUT = StarterStationLayout.standard();

    private StarterStationService() { }

    public static void register(IEventBus bus) {
        // CreateSpawnPosition is cancellable, but this listener observes only: never cancel it.
        bus.addListener(StarterStationService::onCreateSpawnPosition);
        bus.addListener(StarterStationService::onPostServerTick);
    }

    /** This event is emitted by vanilla only while initializing an uninitialized level. */
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        level.getDataStorage().computeIfAbsent(StarterStationSavedData.FACTORY, StarterStationSavedData.DATA_NAME);
        StarterStationSavedData data = level.getDataStorage().get(StarterStationSavedData.FACTORY,
                StarterStationSavedData.DATA_NAME);
        if (data != null && data.state() == StarterStationPlacementState.State.DISABLED)
            data.markPendingFromNewWorld();
    }

    public static void onPostServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % RETRY_INTERVAL_TICKS != 0) return;
        // get(factory, name) is intentionally non-creating: legacy worlds without this marker remain untouched.
        StarterStationSavedData data = level.getDataStorage().get(StarterStationSavedData.FACTORY,
                StarterStationSavedData.DATA_NAME);
        if (data == null || !StarterStationPlacementState.shouldAttempt(data.state())) return;

        if (data.state() == StarterStationPlacementState.State.PENDING) {
            BlockPos origin = selectCandidate(level);
            if (origin == null) return;
            data.select(origin); // Persist origin before any world mutation so retries are deterministic.
        }
        if (data.state() != StarterStationPlacementState.State.SELECTED) return;
        BlockPos origin = data.selectedOrigin();
        if (origin == null) return;
        Optional<List<StarterStationPlacementGeometry.SupportBlock>> supportPlan = preflight(level, origin);
        if (supportPlan.isEmpty()) return;
        if (place(level, origin, supportPlan.get())) data.complete();
    }

    private static BlockPos selectCandidate(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        // A west-facing entrance lies on the footprint's west edge. These nearby candidates
        // keep spawn outside the structure while placing that entrance 5-12 blocks east of it.
        int[][] origins = {{5, -4}, {8, -4}, {12, -4}, {20, -4}, {24, -4}, {-20, -4}, {-24, -4}, {0, 20}};
        for (int i = 0; i < Math.min(MAX_CANDIDATES, origins.length); i++) {
            int x = spawn.getX() + origins[i][0];
            int z = spawn.getZ() + origins[i][1];
            BlockPos footprint = new BlockPos(x, 0, z);
            if (StarterStationPlacementGeometry.containsSpawn(x, z, spawn.getX(), spawn.getZ(), LAYOUT)
                    || !isFootprintLoaded(level, footprint)) continue;
            int floorY = maximumLoadedSurface(level, x, z);
            if (floorY == Integer.MIN_VALUE) continue;
            BlockPos origin = new BlockPos(x, floorY, z);
            if (preflight(level, origin).isPresent()) return origin;
        }
        return null;
    }

    private static int maximumLoadedSurface(ServerLevel level, int x, int z) {
        int maximum = Integer.MIN_VALUE;
        for (int dz = LAYOUT.minZ(); dz <= LAYOUT.maxZ(); dz++) {
            for (int dx = LAYOUT.minX(); dx <= LAYOUT.maxX(); dx++) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                maximum = Math.max(maximum, surfaceY);
            }
        }
        return maximum;
    }

    private static boolean isFootprintLoaded(ServerLevel level, BlockPos origin) {
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + LAYOUT.maxX()) >> 4; cx++)
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + LAYOUT.maxZ()) >> 4; cz++)
                if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) return false;
        return true;
    }

    private static Optional<List<StarterStationPlacementGeometry.SupportBlock>> preflight(ServerLevel level, BlockPos origin) {
        if (!isFootprintLoaded(level, origin)) return Optional.empty();
        if (StarterStationPlacementGeometry.containsSpawn(origin.getX(), origin.getZ(),
                level.getSharedSpawnPos().getX(), level.getSharedSpawnPos().getZ(), LAYOUT)) return Optional.empty();
        if (origin.getY() <= level.getMinBuildHeight()
                || origin.getY() + LAYOUT.roofY() >= level.getMaxBuildHeight()) return Optional.empty();
        List<StarterStationPlacementGeometry.SupportColumn> columns = new ArrayList<>();
        for (int z = LAYOUT.minZ(); z <= LAYOUT.maxZ(); z++) {
            for (int x = LAYOUT.minX(); x <= LAYOUT.maxX(); x++) {
                int terrainSurfaceY = terrainSurfaceBelowFloor(level, origin, x, z);
                if (terrainSurfaceY == Integer.MIN_VALUE) return Optional.empty();
                Set<Integer> installed = new java.util.HashSet<>();
                for (int sy = terrainSurfaceY; sy < origin.getY(); sy++) {
                    BlockPos supportPos = new BlockPos(origin.getX() + x, sy, origin.getZ() + z);
                    if (level.getBlockState(supportPos).is(ModBlocks.STEEL_WALL_BLOCK.get())) installed.add(sy);
                }
                columns.add(new StarterStationPlacementGeometry.SupportColumn(x, z, terrainSurfaceY, installed));
                BlockPos groundPos = origin.offset(x, terrainSurfaceY - origin.getY() - 1, z);
                if (groundPos.getY() < level.getMinBuildHeight()
                        || groundPos.getY() >= level.getMaxBuildHeight()) return Optional.empty();
                var ground = level.getBlockState(groundPos);
                if (!ground.getFluidState().isEmpty()
                        || !ground.isFaceSturdy(level, groundPos, Direction.UP)) return Optional.empty();
                for (int y = 0; y <= LAYOUT.roofY(); y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!canReplaceWriteTarget(isIntendedState(level, origin, x, y, z, state)
                                    || isExpectedOverlay(level, origin, pos, state),
                            !state.getFluidState().isEmpty(), state.canBeReplaced())) return Optional.empty();
                }
            }
        }
        Optional<List<StarterStationPlacementGeometry.SupportBlock>> supportPlan =
                StarterStationPlacementGeometry.planSupports(origin.getY(), MAX_TERRAIN_VARIATION, columns);
        if (supportPlan.isEmpty()) return Optional.empty();
        for (StarterStationPlacementGeometry.SupportBlock planned : supportPlan.get()) {
            BlockPos pos = origin.offset(planned.x(), planned.y() - origin.getY(), planned.z());
            var state = level.getBlockState(pos);
            if (!planned.alreadyInstalled() && (!state.getFluidState().isEmpty()
                    || (!state.isAir() && !state.canBeReplaced()))) return Optional.empty();
            if (planned.alreadyInstalled() && !state.is(ModBlocks.STEEL_WALL_BLOCK.get())) return Optional.empty();
        }
        if (!preflightPower(level, origin)) return Optional.empty();
        List<Entity> entities = level.getEntities((Entity) null,
                new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + LAYOUT.maxX() + 1, origin.getY() + LAYOUT.roofY() + 1,
                        origin.getZ() + LAYOUT.maxZ() + 1),
                entity -> entity.isAlive());
        return entities.isEmpty() ? supportPlan : Optional.empty();
    }

    private static int terrainSurfaceBelowFloor(ServerLevel level, BlockPos origin, int x, int z) {
        int lowestTerrainY = StarterStationPlacementGeometry.terrainScanMinY(origin.getY(), MAX_TERRAIN_VARIATION);
        for (int y = origin.getY() - 1; y >= lowestTerrainY; y--) {
            BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEEL_WALL_BLOCK.get())) continue;
            if (state.isAir() || state.canBeReplaced()) continue;
            if (state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP)) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean place(ServerLevel level, BlockPos origin,
                                 List<StarterStationPlacementGeometry.SupportBlock> supportPlan) {
        for (StarterStationLayout.Cell cell : LAYOUT.plan()) {
            BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
            if (isPowerOverlay(origin, pos)) continue;
            net.minecraft.world.level.block.state.BlockState target = null;
            switch (StarterStationBlockMapping.role(cell)) {
                case AIR -> target = Blocks.AIR.defaultBlockState();
                case STEEL_WALL -> target = ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState();
                case STEEL_FLOOR -> target = ModBlocks.STATION_FLOOR.get().defaultBlockState()
                        .setValue(StationFloorBlock.TILE_FINISH, StationFloorBlock.TileFinish.STEEL);
                case WHITE_FLOOR -> target = ModBlocks.STATION_FLOOR.get().defaultBlockState()
                        .setValue(StationFloorBlock.TILE_FINISH, StationFloorBlock.TileFinish.WHITE);
            }
            if (target == null) return false;
            if (level.getBlockState(pos).equals(target)) continue;
            level.setBlock(pos, target, 3);
            if (!level.getBlockState(pos).equals(target)) return false;
        }
        for (StarterStationPlacementGeometry.SupportBlock planned : supportPlan) {
            if (planned.alreadyInstalled()) continue;
            BlockPos supportPos = origin.offset(planned.x(), planned.y() - origin.getY(), planned.z());
            level.setBlock(supportPos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
            if (!level.getBlockState(supportPos).is(ModBlocks.STEEL_WALL_BLOCK.get())) return false;
        }
        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        for (StarterStationPowerPlan.HostBlock host : power.hostBlocks()) {
            BlockPos pos = origin.offset(host.position());
            if (!level.getBlockState(pos).is(ModBlocks.STEEL_WALL_BLOCK.get())) {
                level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
            }
            if (!level.getBlockState(pos).is(ModBlocks.STEEL_WALL_BLOCK.get())) return false;
        }
        for (StarterStationPowerPlan.Device device : power.devices()) {
            BlockPos pos = origin.offset(device.position());
            var expected = powerDeviceBlock(device.kind()).get().defaultBlockState()
                    .setValue(PowerDeviceBlock.FACING, device.facing());
            var existing = level.getBlockState(pos);
            if (!isExpectedDeviceState(existing, expected)) level.setBlock(pos, expected, 3);
            if (!isExpectedDeviceState(level.getBlockState(pos), expected)) return false;
        }
        for (CableFaceNode cable : power.cables()) {
            BlockPos pos = origin.offset(cable.host());
            var present = exactStoredTier(level, pos, cable);
            if (present == cable.tier()) continue;
            if (!CableStorage.place(level, pos, cable.face(), cable.tier())) return false;
        }
        return verifyPower(level, origin);
    }

    private static boolean preflightPower(ServerLevel level, BlockPos origin) {
        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        for (StarterStationPowerPlan.HostBlock host : power.hostBlocks()) {
            BlockPos pos = origin.offset(host.position());
            var state = level.getBlockState(pos);
            if (!state.is(ModBlocks.STEEL_WALL_BLOCK.get()) && !state.isAir() && !state.canBeReplaced()) return false;
            if (!state.getFluidState().isEmpty()) return false;
        }
        for (StarterStationPowerPlan.Device device : power.devices()) {
            BlockPos pos = origin.offset(device.position());
            var state = level.getBlockState(pos);
            var expected = powerDeviceBlock(device.kind()).get().defaultBlockState()
                    .setValue(PowerDeviceBlock.FACING, device.facing());
            if (!isExpectedDeviceState(state, expected) && (!state.getFluidState().isEmpty()
                    || (!state.isAir() && !state.canBeReplaced()))) return false;
        }
        for (CableFaceNode cable : power.cables()) {
            BlockPos pos = origin.offset(cable.host());
            var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk loaded)) return false;
            // Exact face lookup performs stale host-identity pruning before inspecting the tier key.
            CableStorage.get(level, pos, cable.face());
            var state = level.getBlockState(pos);
            boolean exactHost = isExpectedOverlay(level, origin, pos, state)
                    || (power.plannedCableHostMaterial(cable.host()) != StarterStationLayout.Material.AIR
                    && isIntendedState(level, origin, pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ(), state));
            if (!power.authorizesCableHost(cable.host(), exactHost,
                    !state.getFluidState().isEmpty(), state.canBeReplaced())) return false;
        }
        return true;
    }

    private static boolean verifyPower(ServerLevel level, BlockPos origin) {
        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        for (StarterStationPowerPlan.HostBlock host : power.hostBlocks())
            if (!level.getBlockState(origin.offset(host.position())).is(ModBlocks.STEEL_WALL_BLOCK.get())) return false;
        for (StarterStationPowerPlan.Device device : power.devices()) {
            var expected = powerDeviceBlock(device.kind()).get().defaultBlockState().setValue(PowerDeviceBlock.FACING, device.facing());
            if (!isExpectedDeviceState(level.getBlockState(origin.offset(device.position())), expected)) return false;
        }
        for (CableFaceNode cable : power.cables())
            if (exactStoredTier(level, origin.offset(cable.host()), cable) != cable.tier()) return false;
        return true;
    }

    private static CableTier exactStoredTier(ServerLevel level, BlockPos pos, CableFaceNode cable) {
        // The legacy face-only getter is retained here solely to prune records whose host
        // block identity changed; the authoritative lookup below includes the planned tier.
        CableStorage.get(level, pos, cable.face());
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk loaded)) return null;
        CableChunkData data = loaded.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        return data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15,
                cable.face(), cable.tier());
    }

    private static boolean isPowerOverlay(BlockPos origin, BlockPos pos) {
        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        return power.devices().stream().anyMatch(device -> origin.offset(device.position()).equals(pos))
                || power.hostBlocks().stream().anyMatch(host -> origin.offset(host.position()).equals(pos));
    }

    private static boolean isExpectedOverlay(ServerLevel level, BlockPos origin, BlockPos pos,
                                             net.minecraft.world.level.block.state.BlockState state) {
        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        for (StarterStationPowerPlan.Device device : power.devices()) {
            if (origin.offset(device.position()).equals(pos)) {
                var expected = powerDeviceBlock(device.kind()).get().defaultBlockState()
                        .setValue(PowerDeviceBlock.FACING, device.facing());
                return isExpectedDeviceState(state, expected);
            }
        }
        for (StarterStationPowerPlan.HostBlock host : power.hostBlocks())
            if (origin.offset(host.position()).equals(pos)) return state.is(ModBlocks.STEEL_WALL_BLOCK.get());
        return false;
    }

    private static boolean isExpectedDeviceState(net.minecraft.world.level.block.state.BlockState actual,
                                                 net.minecraft.world.level.block.state.BlockState expected) {
        return actual.getBlock() instanceof PowerDeviceBlock actualBlock
                && expected.getBlock() instanceof PowerDeviceBlock expectedBlock
                && acceptsExpectedDevice(actualBlock.kind(), actual.getValue(PowerDeviceBlock.FACING),
                expectedBlock.kind(), expected.getValue(PowerDeviceBlock.FACING));
    }

    static boolean acceptsExpectedDevice(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind actualKind,
                                         Direction actualFacing,
                                         com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind expectedKind,
                                         Direction expectedFacing) {
        return actualKind == expectedKind && actualFacing == expectedFacing;
    }

    private static net.neoforged.neoforge.registries.DeferredBlock<com.juicyslew.moonstation14.block.custom.PowerDeviceBlock>
    powerDeviceBlock(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind kind) {
        return switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE;
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION;
            case APC -> ModBlocks.APC;
            case LAMP -> ModBlocks.POWER_LAMP;
        };
    }

    private static boolean isIntendedState(ServerLevel level, BlockPos origin, int x, int y, int z,
                                           net.minecraft.world.level.block.state.BlockState state) {
        StarterStationLayout.Material material = LAYOUT.materialAt(x, y, z);
        if (material == null) return false;
        return switch (StarterStationBlockMapping.role(y, material)) {
            case AIR -> state.isAir();
            case STEEL_FLOOR -> state.is(ModBlocks.STATION_FLOOR.get())
                    && state.getValue(StationFloorBlock.TILE_FINISH) == StationFloorBlock.TileFinish.STEEL;
            case WHITE_FLOOR -> state.is(ModBlocks.STATION_FLOOR.get())
                    && state.getValue(StationFloorBlock.TILE_FINISH) == StationFloorBlock.TileFinish.WHITE;
            case STEEL_WALL -> state.is(ModBlocks.STEEL_WALL_BLOCK.get());
        };
    }

    static boolean canReplaceWriteTarget(boolean exactIntendedState, boolean hasFluid, boolean replaceable) {
        return exactIntendedState || (!hasFluid && replaceable);
    }
}
