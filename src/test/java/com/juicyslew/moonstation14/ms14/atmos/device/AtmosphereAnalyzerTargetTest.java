package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
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
    private static final GasMixture AIR = GasMixture.breathableAir();

    @Test
    void usesPassableClickedCellWithoutSamplingAdjacentCell() {
        BlockPos clicked = new BlockPos(2, 3, 4);
        List<BlockPos> sampled = new ArrayList<>();

        var result = AtmosphereAnalyzerItem.sampleTarget(true, clicked, Direction.EAST,
                recordingSampler(sampled, pos -> Optional.of(AIR)));

        assertEquals(AtmosphereAnalyzerItem.TargetStatus.SAMPLED, result.status());
        assertEquals(List.of(clicked), sampled);
        assertEquals(AtmosphereSampleFormatter.format(AIR), AtmosphereSampleFormatter.format(result.mixture().orElseThrow()));
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
        assertTrue(result.mixture().isEmpty());
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

    private static Function<BlockPos, Optional<GasMixture>> recordingSampler(
            List<BlockPos> sampled, Function<BlockPos, Optional<GasMixture>> result) {
        return pos -> {
            sampled.add(pos);
            return result.apply(pos);
        };
    }
}
