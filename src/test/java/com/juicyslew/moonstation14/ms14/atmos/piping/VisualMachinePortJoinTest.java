package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePortDirections;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousFuelPlanTest;

import static org.junit.jupiter.api.Assertions.*;

class VisualMachinePortJoinTest {
    private PipeGasPortHost.Port port(PipeTopology.Layer layer, Direction direction) {
        return new PipeGasPortHost.Port("port", layer, Set.of(direction), .2, UUID.randomUUID());
    }

    @Test void joinsOnlyReciprocalDirectionAndMatchingLayer() {
        for (Direction machinePort : Direction.values()) {
            var descriptor = port(PipeTopology.Layer.PRIMARY, machinePort);
            assertTrue(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.PRIMARY, machinePort));
            assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.PRIMARY, machinePort.getOpposite()));
            assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.SECONDARY, machinePort));
        }
        assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(), VisualPipeBlockEntity.PRIMARY, Direction.NORTH));
    }

    @Test void endpointMaskKeepsPipeLayerLaneMatchingIndependent() {
        var descriptor = port(PipeTopology.Layer.TERTIARY, Direction.WEST);
        assertTrue(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.TERTIARY, Direction.WEST));
        assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.SECONDARY, Direction.WEST));
        assertTrue(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), VisualPipeBlockEntity.PRIMARY, Direction.WEST)
                == false);
    }

    @Test void resolvedProfilesJoinOnTheirRotatedReciprocalPortsAcrossEveryLayer() {
        var catalog = TegContinuousFuelPlanTest.machineCatalog();
        for (String id : List.of("gas_volume_pump", "gas_thermo_heater", "gas_mixer", "gas_valve")) {
            var profile = catalog.get(ModAtmosMachines.id(id));
            assertNotNull(profile, id);
            assertFalse(profile.ports().isEmpty(), id);
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                for (var layer : List.of(PipeTopology.Layer.PRIMARY, PipeTopology.Layer.SECONDARY,
                        PipeTopology.Layer.TERTIARY)) {
                    int lane = switch (layer) {
                        case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
                        case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
                        case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
                    };
                    for (var definition : profile.ports()) {
                        Direction portDirection = AtmosMachinePortDirections.worldDirection(definition.direction(), facing);
                        var descriptor = port(layer, portDirection);
                        assertTrue(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), lane, portDirection));
                        assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), lane, portDirection.getOpposite()));
                        int wrongLayer = lane == VisualPipeBlockEntity.PRIMARY ? VisualPipeBlockEntity.SECONDARY : VisualPipeBlockEntity.PRIMARY;
                        assertFalse(VisualPipeBlockEntity.matchesMachinePort(List.of(descriptor), wrongLayer, portDirection));
                    }
                }
            }
        }
    }
}
