package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Shared cadence-fixture power supply geometry for atmos machinery. */
public final class AtmosMachineryFixturePower {
    private static final List<BlockPos> OCCUPIED_OFFSETS = createOccupiedOffsets();

    private AtmosMachineryFixturePower() { }

    /** All positions touched, relative to the pump; suitable for caller preflight. */
    public static List<BlockPos> occupiedOffsets() {
        return OCCUPIED_OFFSETS;
    }

    public static List<BlockPos> supportOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int z = -6; z <= 0; z++) offsets.add(new BlockPos(1, 0, z));
        for (int z = -4; z <= -2; z++) offsets.add(new BlockPos(-1, 0, z));
        return List.copyOf(offsets);
    }

    public static List<CablePlacement> cablePlacements(BlockPos origin, Direction face, CableTier tier) {
        List<CablePlacement> placements = new ArrayList<>();
        int min = tier == CableTier.MV ? -4 : -6;
        int max = tier == CableTier.MV ? -2 : tier == CableTier.HV ? -4 : 0;
        int x = tier == CableTier.MV ? -1 : 1;
        Direction cableFace = tier == CableTier.MV ? face.getOpposite() : face;
        for (int z = min; z <= max; z++) placements.add(new CablePlacement(origin.offset(x, 0, z), cableFace, tier));
        return List.copyOf(placements);
    }

    public record CablePlacement(BlockPos pos, Direction face, CableTier tier) { }

    /** Installs the cadence fixture's ordinary HV-to-MV-to-APC supply beside a pump. */
    public static boolean installPumpSupply(ServerLevel level, BlockPos pump) {
        for (BlockPos offset : supportOffsets())
            level.setBlock(pump.offset(offset), Blocks.STONE.defaultBlockState(), 3);

        BlockPos source = pump.offset(0, 0, -6);
        BlockPos substation = pump.offset(0, 0, -4);
        BlockPos apc = pump.offset(0, 0, -2);
        level.setBlock(source, ModBlocks.HV_SOURCE.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        level.setBlock(substation, ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, Direction.EAST), 3);
        if (!PowerDeviceFixturePlacement.placeApc(level, apc, Direction.WEST)) return false;

        for (CableTier tier : CableTier.values())
            for (CablePlacement cable : cablePlacements(pump, Direction.WEST, tier))
                if (!CableStorage.place(level, cable.pos(), cable.face(), cable.tier())) return false;
        return true;
    }

    /** Three aisle supplies for the laboratory; anchors are deliberately beyond the glass bays. */
    public static List<BlockPos> laboratoryAnchors(BlockPos origin) {
        return List.of(origin.offset(60, 1, 8), origin.offset(60, 1, 23), origin.offset(60, 1, 38));
    }

    public static boolean installLaboratorySupplies(ServerLevel level, BlockPos origin) {
        for (BlockPos anchor : laboratoryAnchors(origin))
            if (!installPumpSupply(level, anchor)) return false;
        return true;
    }

    /** Adds ordinary floor cable trees from each aisle APC to the resolved powered appliance pins. */
    public static boolean connectLaboratoryAppliances(ServerLevel level, BlockPos origin,
            List<? extends com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode> devices) {
        List<BlockPos> anchors = laboratoryAnchors(origin);
        for (var device : devices) {
            var ports = device.powerRuntimePorts();
            if (ports == null) continue;
            for (DevicePort port : ports) {
                if (port == null || port.tier() != CableTier.APC) continue;
                int row = nearestRow(port.device().getZ(), anchors);
                BlockPos anchor = anchors.get(row);
                var apcWall = cablePlacements(anchor, Direction.WEST, CableTier.APC).getFirst().pos();
                // A wall cable's folded edge into a floor cable is one block diagonally down/inward.
                BlockPos start = apcWall.relative(Direction.WEST).below();
                BlockPos targetHost = port.device().relative(port.face());
                if (level.getBlockState(targetHost).isAir())
                    level.setBlock(targetHost, Blocks.STONE.defaultBlockState(), 3);
                CableFaceNode endpoint = new CableFaceNode(targetHost, port.face().getOpposite(), CableTier.APC);
                if (!PowerTopology.deviceAdjacent(port, endpoint)) return false;
                if (!CableStorage.place(level, targetHost, endpoint.face(), CableTier.APC)) return false;
                BlockPos floorTurnHost = targetHost.relative(endpoint.face()).below();
                if (!PowerTopology.cableEdge(endpoint,
                        new CableFaceNode(floorTurnHost, Direction.UP, CableTier.APC)).isPresent()) return false;
                if (!routeFloor(level, start, floorTurnHost)) return false;
            }
        }
        return true;
    }

    /** Routes room ventilation extension receivers without inventing a physical device pin. */
    public static boolean connectLaboratoryRoomVentReceivers(ServerLevel level, BlockPos origin,
                                                              List<BlockPos> roomVentHosts) {
        return connectLaboratoryRoomVentReceiversFailure(level, origin, roomVentHosts) == null;
    }

    /** Circuit endpoint coordinates are already world coordinates, including for translated fixtures. */
    public static List<BlockPos> laboratoryRoomVentHosts(
            com.juicyslew.moonstation14.ms14.atmos.machine.fixture.AtmosMachineryRoomCircuitPlan circuits) {
        return circuits.endpoints().stream().flatMap(endpoint -> endpoint.ventHosts().stream()).toList();
    }

    /** Returns the first failed cable host, preserving the boolean API for existing callers. */
    public static BlockPos connectLaboratoryRoomVentReceiversFailure(ServerLevel level, BlockPos origin,
                                                                      List<BlockPos> roomVentHosts) {
        List<BlockPos> anchors = laboratoryAnchors(origin);
        for (BlockPos vent : roomVentHosts) {
            int row = nearestRow(vent.getZ(), anchors);
            BlockPos anchor = anchors.get(row);
            BlockPos apcWall = cablePlacements(anchor, Direction.WEST, CableTier.APC).getFirst().pos();
            BlockPos failed = routeFloorFailure(level, apcWall.relative(Direction.WEST).below(), vent.below());
            if (failed != null) return failed;
        }
        return null;
    }

    private static boolean routeFloor(ServerLevel level, BlockPos start, BlockPos targetHost) {
        return routeFloorFailure(level, start, targetHost) == null;
    }

    private static BlockPos routeFloorFailure(ServerLevel level, BlockPos start, BlockPos targetHost) {
        if (level.getBlockState(targetHost).isAir()) level.setBlock(targetHost, Blocks.STONE.defaultBlockState(), 3);
        BlockPos cursor = start;
        if (!placeOrPresent(level, cursor)) return cursor;
        while (cursor.getX() != targetHost.getX()) {
            cursor = cursor.offset(Integer.signum(targetHost.getX() - cursor.getX()), 0, 0);
            if (!placeOrPresent(level, cursor)) return cursor;
        }
        while (cursor.getZ() != targetHost.getZ()) {
            cursor = cursor.offset(0, 0, Integer.signum(targetHost.getZ() - cursor.getZ()));
            if (!placeOrPresent(level, cursor)) return cursor;
        }
        return null;
    }

    private static boolean placeOrPresent(ServerLevel level, BlockPos host) {
        return CableStorage.has(level, host, Direction.UP, CableTier.APC)
                || CableStorage.place(level, host, Direction.UP, CableTier.APC);
    }

    private static int nearestRow(int z, List<BlockPos> anchors) {
        int best = 0;
        for (int i = 1; i < anchors.size(); i++)
            if (Math.abs(z - anchors.get(i).getZ()) < Math.abs(z - anchors.get(best).getZ())) best = i;
        return best;
    }

    private static List<BlockPos> createOccupiedOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        offsets.add(new BlockPos(0, 0, -6));
        offsets.add(new BlockPos(0, 0, -4));
        offsets.add(new BlockPos(0, 0, -2));
        for (int z = -6; z <= 0; z++) offsets.add(new BlockPos(1, 0, z));
        for (int z = -4; z <= -2; z++) offsets.add(new BlockPos(-1, 0, z));
        return List.copyOf(offsets);
    }
}
