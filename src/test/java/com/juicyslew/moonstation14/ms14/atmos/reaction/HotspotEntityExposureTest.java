package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.fire.FireStackComponent;
import com.juicyslew.moonstation14.ms14.fire.FireStackReducer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HotspotEntityExposureTest {
    @Test
    void stableCrowdRotatesAcrossEveryOccupantWithAtMostTwoBoundedQueries() {
        List<Integer> crowd = new ArrayList<>();
        for (int i = 0; i < 129; i++) crowd.add(i);
        boolean[] served = new boolean[crowd.size()];
        int cursor = 0;
        for (int event = 0; event < 6; event++) {
            AtomicInteger queries = new AtomicInteger();
            var selection = HotspotEntityExposure.<Integer>select(cursor, 64, (predicate, cap) -> {
                queries.incrementAndGet();
                assertTrue(cap <= 64);
                List<Integer> found = new ArrayList<>();
                for (Integer entity : crowd)
                    if (predicate.test(entity) && found.size() < cap) found.add(entity);
                return found;
            });
            assertTrue(queries.get() <= 2);
            assertEquals(64, selection.occupants().size());
            selection.occupants().forEach(index -> served[index] = true);
            cursor = selection.nextCursor();
        }
        for (boolean occupant : served) assertTrue(occupant, "No stable occupant can starve");
    }

    @Test
    void sparseFiresAdvanceCursorRatherThanAliasingEventTime() {
        List<Integer> crowd = new ArrayList<>();
        for (int i = 0; i < 65; i++) crowd.add(i);
        int cursor = 0;
        for (int event = 0; event < 2; event++) {
            var selection = HotspotEntityExposure.<Integer>select(cursor, 64, (predicate, cap) -> {
                List<Integer> found = new ArrayList<>();
                for (Integer entity : crowd)
                    if (predicate.test(entity) && found.size() < cap) found.add(entity);
                return found;
            });
            if (event == 1) assertTrue(selection.occupants().contains(64));
            cursor = selection.nextCursor();
        }
    }

    @Test
    void thresholdLogarithmAndBounds() {
        assertEquals(0f, HotspotEntityExposure.target(373.15));
        assertEquals(0f, HotspotEntityExposure.target(423.15));
        assertEquals(1f, HotspotEntityExposure.target(473.15), 0.00001f);
        assertEquals(2f, HotspotEntityExposure.target(573.15), 0.00001f);
        assertEquals(FireStackComponent.MAX_STACKS, HotspotEntityExposure.target(1.0e20));
        assertEquals(0f, HotspotEntityExposure.target(Double.NaN));
    }

    @Test
    void repeatedExposureDoesNotAccumulateAndLargerStacksSurvive() {
        FireStackComponent initial = FireStackComponent.EMPTY;
        FireStackComponent raised = FireStackReducer.flammable(initial,
                HotspotEntityExposure.increase(573.15, initial.stacks()), null, 1f);
        FireStackComponent ignited = FireStackReducer.ignite(raised);
        assertEquals(new FireStackComponent(2f, true), ignited);
        assertEquals(0f, HotspotEntityExposure.increase(573.15, ignited.stacks()), 0.00001f);
        assertEquals(0f, HotspotEntityExposure.increase(473.15, 4f));
        assertEquals(4f, FireStackReducer.ignite(new FireStackComponent(4f, false)).stacks());
    }

    @Test
    void wetnessConsumesTargetBeforePositiveIgnition() {
        FireStackComponent wet = new FireStackComponent(-3f, false);
        FireStackComponent warmed = FireStackReducer.flammable(wet,
                HotspotEntityExposure.increase(473.15, wet.stacks()), null, 1f);
        assertEquals(new FireStackComponent(1f, true), FireStackReducer.ignite(warmed));
        assertEquals(FireStackComponent.EMPTY, FireStackReducer.ignite(FireStackComponent.EMPTY));
    }
}
