package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TegWholeAirEventObservationTest {
    private static GasReactionEvaluator.Event event(double plasma, double energy) {
        return new GasReactionEvaluator.Event(ResourceLocation.withDefaultNamespace("test"),
                GasReactionData.EffectType.PLASMA_FIRE, 99, Map.of(GasType.PLASMA, plasma,GasType.CARBON_DIOXIDE,-plasma,GasType.OXYGEN,plasma), energy);
    }

    @Test void excludesBaselineAndDeduplicatesOnlySameCellAndCycle() {
        var collector = new TegWholeAirEventObservation(); var cell = BlockPos.ZERO;
        var initial = List.of(event(-1, 160000)); collector.markBaseline(cell, 1, initial);
        collector.observe(cell, 1, initial);
        collector.observe(cell, 2, List.of(event(-2, 320000)));
        collector.observe(new BlockPos(1, 0, 0), 1, List.of(event(-3, 480000)));
        collector.observe(new BlockPos(1, 0, 0), 1, List.of(event(-3, 480000)));
        var result = collector.summary();
        assertEquals(2, result.eventCount()); assertEquals(-5, result.speciesMoles().get(GasReactionData.EffectType.PLASMA_FIRE).get(GasType.PLASMA));
        assertEquals(800000, result.energyJoules().get(GasReactionData.EffectType.PLASMA_FIRE));
    }

    @Test void summariesAreDeeplyImmutable() {
        var collector = new TegWholeAirEventObservation(); collector.observe(BlockPos.ZERO, 4, List.of(event(-1, 160000)));
        var result = collector.summary();
        assertThrows(UnsupportedOperationException.class, () -> result.counts().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.speciesMoles().get(GasReactionData.EffectType.PLASMA_FIRE).clear());
    }
    @Test void repeatedSamplingDeduplicatesOnlyAnIdenticalCommittedBatch() {
        var collector=new TegWholeAirEventObservation();var pos=BlockPos.ZERO;
        var first=List.of(event(-1,160000));collector.observe(pos,7,first);collector.observe(pos,7,first);
        assertEquals(1,collector.summary().eventCount());
        assertThrows(IllegalArgumentException.class,()->collector.observe(pos,7,List.of(event(-2,320000))));
    }

    @Test void channelsAreIndependentAndHotspotCleanupOnlyAccumulatesOnce() {
        var collector=new TegWholeAirEventObservation();var pos=BlockPos.ZERO;var batch=List.of(event(-1,160000));
        collector.markBaseline(pos,5,TegWholeAirEventObservation.Channel.WHOLE_AIR,batch);
        collector.markBaseline(pos,5,TegWholeAirEventObservation.Channel.HOTSPOT,List.of());
        collector.observe(pos,6,TegWholeAirEventObservation.Channel.WHOLE_AIR,batch,Map.of());
        collector.observe(pos,6,TegWholeAirEventObservation.Channel.HOTSPOT,batch,Map.of(GasType.WATER_VAPOR,8e-8));
        collector.observe(pos,6,TegWholeAirEventObservation.Channel.HOTSPOT,batch,Map.of(GasType.WATER_VAPOR,8e-8));
        assertEquals(2,collector.summary().eventCount());
        assertEquals(Map.of(GasType.WATER_VAPOR,8e-8),collector.cleanupSnapshot());
        assertThrows(IllegalArgumentException.class,()->collector.observe(pos,6,TegWholeAirEventObservation.Channel.HOTSPOT,
                List.of(event(-2,320000)),Map.of()));
        assertThrows(IllegalArgumentException.class,()->collector.observe(pos,6,TegWholeAirEventObservation.Channel.HOTSPOT,
                batch,Map.of(GasType.WATER_VAPOR,7e-8)));
        assertThrows(IllegalArgumentException.class,()->collector.observe(new BlockPos(2,0,0),6,
                TegWholeAirEventObservation.Channel.HOTSPOT,batch,Map.of(GasType.WATER_VAPOR,1e-6)));
        assertEquals(8e-8,collector.cleanupSnapshot().get(GasType.WATER_VAPOR));
    }
}
