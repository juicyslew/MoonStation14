package com.juicyslew.moonstation14.ms14.station.debug;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, world-independent starter-station electrical geometry. */
public final class StarterStationPowerPlan {
    public static final int MAX_CABLE_NODES = 96;

    public record Device(PowerDeviceKind kind, BlockPos position, Direction facing) {
        public Device {
            position = new BlockPos(position);
        }
        @Override public BlockPos position() { return position; }
        public List<DevicePort> ports() { return kind.ports(position, facing); }
    }

    /** Additional full-cube steel hosts required in otherwise clear interior space. */
    public record HostBlock(BlockPos position, StarterStationLayout.Material material) {
        public HostBlock { position = new BlockPos(position); }
        @Override public BlockPos position() { return position; }
    }

    private static final StarterStationPowerPlan STANDARD = new StarterStationPowerPlan();
    private final List<Device> devices;
    private final List<HostBlock> hostBlocks;
    private final List<CableFaceNode> cables;

    private StarterStationPowerPlan() {
        List<Device> devicePlan = new ArrayList<>();
        devicePlan.add(device(PowerDeviceKind.HV_SOURCE, 1, 1, 13, Direction.WEST));
        devicePlan.add(device(PowerDeviceKind.HV_MV_SUBSTATION, 1, 1, 15, Direction.WEST));
        devicePlan.add(device(PowerDeviceKind.APC, 1, 1, 17, Direction.EAST));
        devicePlan.add(device(PowerDeviceKind.LAMP, 6, 4, 5, Direction.EAST));
        devicePlan.add(device(PowerDeviceKind.LAMP, 13, 4, 5, Direction.WEST));
        devicePlan.add(device(PowerDeviceKind.LAMP, 6, 4, 14, Direction.EAST));
        devicePlan.add(device(PowerDeviceKind.LAMP, 13, 4, 14, Direction.WEST));
        devices = List.copyOf(devicePlan);

        List<HostBlock> hosts = new ArrayList<>();
        for (int z = 15; z <= 17; z++) hosts.add(new HostBlock(pos(2, 1, z), StarterStationLayout.Material.STEEL));
        hostBlocks = List.copyOf(hosts);

        LinkedHashSet<CableFaceNode> nodes = new LinkedHashSet<>();
        // HV source to substation: west perimeter wall.
        for (int z = 13; z <= 15; z++) add(nodes, 0, 1, z, Direction.EAST, CableTier.HV);
        // Substation to APC: steel-mounted bay run.
        for (int z = 15; z <= 17; z++) add(nodes, 2, 1, z, Direction.WEST, CableTier.MV);
        // APC output rises on the west wall, then turns onto the floor.
        for (int y = 1; y <= 4; y++) add(nodes, 0, y, 17, Direction.EAST, CableTier.APC);
        add(nodes, 1, 0, 17, Direction.UP, CableTier.APC);

        // Each ceiling lamp gets a wall drop and an explicit wall-to-floor edge turn.
        int[][] lamps = {{6, 5, 7}, {13, 5, 12}, {6, 14, 7}, {13, 14, 12}};
        List<GridPoint> floorTargets = new ArrayList<>();
        for (int[] lamp : lamps) {
            int x = lamp[0], z = lamp[1], wallX = lamp[2];
            Direction wallFace = wallX < x ? Direction.EAST : Direction.WEST;
            for (int y = 1; y <= 4; y++) add(nodes, wallX, y, z, wallFace, CableTier.APC);
            add(nodes, x, 0, z, Direction.UP, CableTier.APC);
            floorTargets.add(new GridPoint(x, z));
        }

        // Join those drops by deterministic shortest paths over existing interior floor cells.
        LinkedHashSet<GridPoint> routed = new LinkedHashSet<>();
        routed.add(new GridPoint(1, 17));
        for (GridPoint target : floorTargets) {
            List<GridPoint> path = shortestPath(routed, target);
            for (GridPoint point : path) {
                routed.add(point);
                add(nodes, point.x, 0, point.z, Direction.UP, CableTier.APC);
            }
        }
        if (nodes.size() > MAX_CABLE_NODES) throw new IllegalStateException("starter station cable plan exceeds bound");
        cables = List.copyOf(nodes);
    }

    public static StarterStationPowerPlan standard() { return STANDARD; }
    public List<Device> devices() { return devices; }
    public List<HostBlock> hostBlocks() { return hostBlocks; }
    public List<CableFaceNode> cables() { return cables; }

    /** Returns the planned material beneath a cable face, including explicit extra hosts. */
    StarterStationLayout.Material plannedCableHostMaterial(BlockPos position) {
        for (HostBlock host : hostBlocks)
            if (host.position().equals(position)) return host.material();
        return StarterStationLayout.standard().materialAt(position.getX(), position.getY(), position.getZ());
    }

    boolean authorizesCableHost(BlockPos position, boolean exactPlannedHost,
                                boolean hasFluid, boolean replaceable) {
        StarterStationLayout.Material material = plannedCableHostMaterial(position);
        if (material != StarterStationLayout.Material.STEEL
                && material != StarterStationLayout.Material.WHITE_TILE) return false;
        return exactPlannedHost || (!hasFluid && replaceable);
    }

    private static Device device(PowerDeviceKind kind, int x, int y, int z, Direction facing) {
        return new Device(kind, pos(x, y, z), facing);
    }

    private static BlockPos pos(int x, int y, int z) { return new BlockPos(x, y, z); }

    private static void add(Set<CableFaceNode> nodes, int x, int y, int z, Direction face, CableTier tier) {
        nodes.add(new CableFaceNode(pos(x, y, z), face, tier));
    }

    private record GridPoint(int x, int z) { }
    private record Search(GridPoint point, List<GridPoint> path) { }

    private static List<GridPoint> shortestPath(Set<GridPoint> connected, GridPoint target) {
        ArrayDeque<Search> pending = new ArrayDeque<>();
        Set<GridPoint> seen = new LinkedHashSet<>();
        for (GridPoint point : connected) {
            pending.addLast(new Search(point, List.of()));
            seen.add(point);
        }
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        StarterStationLayout layout = StarterStationLayout.standard();
        while (!pending.isEmpty()) {
            Search current = pending.removeFirst();
            if (current.point.equals(target)) return current.path;
            for (int[] step : steps) {
                GridPoint next = new GridPoint(current.point.x + step[0], current.point.z + step[1]);
                if (!seen.add(next) || layout.classify(next.x, next.z) != StarterStationLayout.Classification.INTERIOR
                        || layout.materialAt(next.x, 0, next.z) == null) continue;
                List<GridPoint> path = new ArrayList<>(current.path);
                path.add(next);
                pending.addLast(new Search(next, List.copyOf(path)));
            }
        }
        throw new IllegalStateException("lamp floor is unreachable over station floor: " + target);
    }
}
