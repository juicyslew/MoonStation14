package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereAnalyzerTargetTest {
    private static final AtmosphereReading AIR = new AtmosphereReading(
            GasMixture.breathableAir(), AtmosphereReading.Status.FINITE);

    @Test
    void usesPassableClickedCellWithoutSamplingAdjacentCell() {
        BlockPos clicked = new BlockPos(2, 3, 4);
        List<BlockPos> sampled = new ArrayList<>();

        var result = AtmosphereAnalyzerItem.sampleTarget(true, clicked, Direction.EAST,
                recordingSampler(sampled, pos -> Optional.of(AIR)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.SAMPLED, result.status());
        assertEquals(List.of(clicked), sampled);
        assertEquals(AtmosphereSampleFormatter.format(AIR), AtmosphereSampleFormatter.format(result.sample().orElseThrow()));
    }

    @Test
    void eyeCellWithoutFaceRemainsAValidSingleCellRead() {
        BlockPos eyeCell = new BlockPos(2, 3, 4);
        List<BlockPos> sampled = new ArrayList<>();

        var result = AtmosphereAnalyzerItem.sampleTarget(true, eyeCell, null,
                recordingSampler(sampled, ignored -> Optional.of(AIR)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.SAMPLED, result.status());
        assertEquals(List.of(eyeCell), sampled);
    }

    @Test
    void samplesOnlyClickedFaceNeighborWhenClickedCellIsSolid() {
        BlockPos clicked = new BlockPos(2, 3, 4);
        BlockPos adjacent = clicked.relative(Direction.UP);
        List<BlockPos> sampled = new ArrayList<>();

        var result = AtmosphereAnalyzerItem.sampleTarget(true, clicked, Direction.UP,
                recordingSampler(sampled, pos -> pos.equals(clicked) ? Optional.empty() : Optional.of(AIR)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.SAMPLED, result.status());
        assertEquals(List.of(clicked, adjacent), sampled);
    }

    @Test
    void unavailableClickedAndAdjacentCellsRemainUnavailableWithoutWorldReads() {
        BlockPos clicked = new BlockPos(2, 3, 4);
        List<BlockPos> sampleRequests = new ArrayList<>();
        // The sampler represents AtmosphereService.sample: unavailable/unloaded positions return empty;
        // no world state or chunk lookup is exposed to this target-selection policy.
        var result = AtmosphereAnalyzerItem.sampleTarget(true, clicked, Direction.NORTH,
                recordingSampler(sampleRequests, ignored -> Optional.empty()));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.UNAVAILABLE, result.status());
        assertTrue(result.sample().isEmpty());
        assertEquals(List.of(clicked, clicked.relative(Direction.NORTH)), sampleRequests);
    }

    @Test
    void disabledGateDoesNotInvokeSampler() {
        List<BlockPos> sampled = new ArrayList<>();

        var result = AtmosphereAnalyzerItem.sampleTarget(false, BlockPos.ZERO, Direction.EAST,
                recordingSampler(sampled, ignored -> Optional.of(AIR)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.DISABLED, result.status());
        assertTrue(sampled.isEmpty());
    }

    @Test
    void closedDoorTopologyUsesOnlyClickedSideAndCanShowProvisionalNeighbor() {
        BlockPos door = new BlockPos(2, 3, 4);
        BlockPos clickedSide = door.relative(Direction.EAST);
        AtmosphereReading provisional = new AtmosphereReading(
                GasMixture.breathableAir(), AtmosphereReading.Status.PROVISIONAL);
        List<BlockPos> sampled = new ArrayList<>();

        // A closed door's (possibly partial) collision shape does not make it a gas cell:
        // its sample is empty, while the passable clicked-side neighbor yields a reading.
        var result = AtmosphereAnalyzerItem.sampleTarget(true, door, Direction.EAST,
                recordingSampler(sampled, pos -> pos.equals(door) ? Optional.empty() : Optional.of(provisional)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.SAMPLED, result.status());
        assertEquals(List.of(door, clickedSide), sampled);
        assertEquals(provisional, result.sample().orElseThrow());
        assertTrue(AtmosphereSampleFormatter.format(result.sample().orElseThrow())
                .startsWith("Provisional / classification pending | P: "));
    }

    private static Function<BlockPos, Optional<AtmosphereReading>> recordingSampler(
            List<BlockPos> sampled, Function<BlockPos, Optional<AtmosphereReading>> result) {
        return pos -> {
            sampled.add(pos);
            return result.apply(pos);
        };
    }
}
