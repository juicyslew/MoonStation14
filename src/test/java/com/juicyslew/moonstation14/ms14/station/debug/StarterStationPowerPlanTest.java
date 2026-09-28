package com.juicyslew.moonstation14.ms14.station.debug;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StarterStationPowerPlanTest {
    private final StarterStationPowerPlan plan = StarterStationPowerPlan.standard();

    @Test void planIsImmutableBoundedAndHasExactDevicesAndSteelBayHosts() {
        assertSame(plan, StarterStationPowerPlan.standard());
        assertEquals(7, plan.devices().size());
        assertEquals(4, plan.devices().stream().filter(device -> device.kind() == PowerDeviceKind.LAMP).count());
        assertEquals(List.of(
                new BlockPos(2, 1, 15), new BlockPos(2, 1, 16), new BlockPos(2, 1, 17)),
                plan.hostBlocks().stream().map(StarterStationPowerPlan.HostBlock::position).toList());
        assertTrue(plan.hostBlocks().stream().allMatch(host -> host.material() == StarterStationLayout.Material.STEEL));
        assertTrue(plan.cables().size() <= StarterStationPowerPlan.MAX_CABLE_NODES);
        assertEquals(plan.cables().size(), new HashSet<>(plan.cables()).size());
        assertThrows(UnsupportedOperationException.class, () -> plan.devices().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.hostBlocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.cables().clear());
    }

    @Test void cableHostsAreSteelOrExistingStationFloorAndCableFacesDoNotConflict() {
        StarterStationLayout layout = StarterStationLayout.standard();
        Set<BlockPos> extraHosts = new HashSet<>();
        plan.hostBlocks().forEach(host -> extraHosts.add(host.position()));
        Set<CableFaceNode> nodes = new HashSet<>();
        for (CableFaceNode cable : plan.cables()) {
            BlockPos host = cable.host();
            assertTrue(host.getX() >= 0 && host.getX() <= 19 && host.getZ() >= 0 && host.getZ() <= 19);
            assertTrue(host.getY() >= 0 && host.getY() <= 4);
            StarterStationLayout.Material material = layout.materialAt(host.getX(), host.getY(), host.getZ());
            assertTrue(material == StarterStationLayout.Material.STEEL
                            || material == StarterStationLayout.Material.WHITE_TILE || extraHosts.contains(host),
                    "cable host must be a planned station floor or steel block: " + cable);
            assertTrue(nodes.add(cable), "duplicate full cable node: " + cable);
        }
        assertTrue(plan.cables().stream().anyMatch(node -> node.host().equals(new BlockPos(2, 1, 15))
                && node.face() == Direction.WEST && node.tier() == CableTier.MV));
    }

    @Test void fullNodeUniquenessAllowsSameFaceAcrossTiers() {
        BlockPos host = new BlockPos(3, 2, 7);
        Set<CableFaceNode> fixture = new HashSet<>();
        CableFaceNode hv = new CableFaceNode(host, Direction.UP, CableTier.HV);
        CableFaceNode mv = new CableFaceNode(host, Direction.UP, CableTier.MV);
        CableFaceNode apc = new CableFaceNode(host, Direction.UP, CableTier.APC);
        assertTrue(fixture.add(hv));
        assertTrue(fixture.add(mv));
        assertTrue(fixture.add(apc));
        assertFalse(fixture.add(new CableFaceNode(host, Direction.UP, CableTier.HV)),
                "only a duplicate full node is rejected");
    }

    @Test void plannedWhiteTileAndReplaceablePredictedHostsAreAuthorized() {
        StarterStationLayout layout = StarterStationLayout.standard();
        BlockPos whiteFloor = plan.cables().stream().map(CableFaceNode::host)
                .filter(host -> layout.materialAt(host.getX(), host.getY(), host.getZ())
                        == StarterStationLayout.Material.WHITE_TILE)
                .findFirst().orElseThrow();
        assertEquals(StarterStationLayout.Material.WHITE_TILE, plan.plannedCableHostMaterial(whiteFloor));
        assertTrue(plan.authorizesCableHost(whiteFloor, false, false, true),
                "dry replaceable terrain is authorized when the planned structure supplies a white floor");
        assertTrue(plan.authorizesCableHost(whiteFloor, true, false, false),
                "the exact installed white floor is an authorized cable host");
        assertFalse(plan.authorizesCableHost(whiteFloor, false, true, true), "fluid is never an authorized host");
        assertFalse(plan.authorizesCableHost(whiteFloor, false, false, false),
                "a nonreplaceable wrong host is rejected");
        assertFalse(plan.authorizesCableHost(new BlockPos(6, 1, 5), false, false, true),
                "planned structural air is not a cable host");
    }

    @Test void allDevicePortsTouchTheirTierCableAndEachTierIsOneRealTopologyComponent() {
        for (StarterStationPowerPlan.Device device : plan.devices()) {
            for (var port : device.ports()) {
                assertTrue(plan.cables().stream().anyMatch(cable -> PowerTopology.deviceAdjacent(port, cable)),
                        "unwired " + device.kind() + " port " + port);
            }
        }
        for (CableTier tier : CableTier.values()) {
            List<CableFaceNode> nodes = plan.cables().stream().filter(node -> node.tier() == tier).toList();
            assertFalse(nodes.isEmpty());
            Set<CableFaceNode> reached = new HashSet<>();
            ArrayDeque<CableFaceNode> pending = new ArrayDeque<>();
            pending.add(nodes.get(0));
            while (!pending.isEmpty()) {
                CableFaceNode current = pending.removeFirst();
                if (!reached.add(current)) continue;
                for (CableFaceNode candidate : nodes) {
                    if (PowerTopology.cableEdge(current, candidate).isPresent() && !reached.contains(candidate)) {
                        pending.addLast(candidate);
                    }
                }
            }
            assertEquals(nodes.size(), reached.size(), tier + " cable network must be physically connected");
        }
        for (CableFaceNode left : plan.cables()) {
            for (CableFaceNode right : plan.cables()) {
                if (left.tier() != right.tier()) assertTrue(PowerTopology.cableEdge(left, right).isEmpty());
            }
        }
    }

    @Test void lampsAreAtSpecifiedCeilingHeightAndWallsAndFloorDropsAreExplicit() {
        List<StarterStationPowerPlan.Device> lamps = plan.devices().stream()
                .filter(device -> device.kind() == PowerDeviceKind.LAMP).toList();
        assertEquals(Set.of(new BlockPos(6, 4, 5), new BlockPos(13, 4, 5),
                new BlockPos(6, 4, 14), new BlockPos(13, 4, 14)),
                lamps.stream().map(StarterStationPowerPlan.Device::position).collect(java.util.stream.Collectors.toSet()));
        assertTrue(lamps.stream().allMatch(lamp -> StarterStationLayout.standard().materialAt(
                lamp.position().getX(), 4, lamp.position().getZ()) == StarterStationLayout.Material.AIR));
        for (int z : new int[]{5, 14}) {
            for (int x : new int[]{7, 12}) {
                for (int y = 1; y <= 4; y++) {
                    assertTrue(hasCable(x, y, z, x == 7 ? Direction.WEST : Direction.EAST, CableTier.APC));
                }
            }
        }
        assertTrue(hasCable(1, 0, 17, Direction.UP, CableTier.APC));
        for (BlockPos floor : List.of(new BlockPos(6, 0, 5), new BlockPos(13, 0, 5),
                new BlockPos(6, 0, 14), new BlockPos(13, 0, 14))) {
            assertTrue(hasCable(floor.getX(), floor.getY(), floor.getZ(), Direction.UP, CableTier.APC));
        }
    }

    @Test void structureAirIsOverlaidByPlannedDevicesAndSteelHosts() {
        StarterStationLayout layout = StarterStationLayout.standard();
        for (StarterStationPowerPlan.Device device : plan.devices()) {
            BlockPos pos = device.position();
            assertEquals(StarterStationLayout.Material.AIR,
                    layout.materialAt(pos.getX(), pos.getY(), pos.getZ()),
                    "power device overlay must take precedence over structural air: " + device);
        }
        for (StarterStationPowerPlan.HostBlock host : plan.hostBlocks()) {
            BlockPos pos = host.position();
            assertEquals(StarterStationLayout.Material.AIR,
                    layout.materialAt(pos.getX(), pos.getY(), pos.getZ()),
                    "additional steel host overlay must take precedence over structural air: " + host);
        }
    }

    @Test void retryAcceptanceUsesDeviceKindAndFacingRatherThanMutableLampLitState() {
        var lamp = plan.devices().stream().filter(device -> device.kind() == PowerDeviceKind.LAMP).findFirst().orElseThrow();
        assertTrue(StarterStationService.acceptsExpectedDevice(PowerDeviceKind.LAMP, lamp.facing(),
                lamp.kind(), lamp.facing()));
        assertFalse(StarterStationService.acceptsExpectedDevice(PowerDeviceKind.LAMP, lamp.facing().getOpposite(),
                lamp.kind(), lamp.facing()));
        assertFalse(StarterStationService.acceptsExpectedDevice(PowerDeviceKind.APC, lamp.facing(),
                lamp.kind(), lamp.facing()));
    }

    private boolean hasCable(int x, int y, int z, Direction face, CableTier tier) {
        return plan.cables().contains(new CableFaceNode(new BlockPos(x, y, z), face, tier));
    }
}
