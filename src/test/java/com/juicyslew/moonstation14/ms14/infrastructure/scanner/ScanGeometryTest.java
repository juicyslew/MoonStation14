package com.juicyslew.moonstation14.ms14.infrastructure.scanner;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScanGeometryTest {
    private static ScanGeometry.Candidate pipe(double x, double y, double z) {
        return new ScanGeometry.Candidate(ScanGeometry.Type.PIPE, new Vec3(x, y, z));
    }

    @Test
    void selectsExactThreeDimensionalSphereAndBoundary() {
        List<ScanGeometry.Result> results = ScanGeometry.select(Vec3.ZERO, 5,
                List.of(pipe(3, 4, 0), pipe(0, 0, 5), pipe(0, 0, 5.001), pipe(0, 6, 0)));
        assertEquals(2, results.size());
        assertEquals(25, results.get(0).distanceSquared());
        assertEquals(25, results.get(1).distanceSquared());
    }

    @Test
    void includesNegativeCoordinatesAndDeduplicates() {
        var candidate = pipe(-1, -2, -2);
        var results = ScanGeometry.select(new Vec3(-1, -2, -3), 1, List.of(candidate, candidate));
        assertEquals(1, results.size());
    }

    @Test
    void orderIsStableAndResultsAreImmutableWithoutMutatingInput() {
        var a = pipe(1, 0, 0);
        var b = new ScanGeometry.Candidate(ScanGeometry.Type.CABLE, new Vec3(-1, 0, 0));
        var input = new ArrayList<>(List.of(a, b));
        var first = ScanGeometry.select(Vec3.ZERO, 2, input);
        var second = ScanGeometry.select(Vec3.ZERO, 2, List.of(b, a));
        assertEquals(first, second);
        assertEquals(List.of(a, b), input);
        assertThrows(UnsupportedOperationException.class, () -> first.add(first.get(0)));
    }

    @Test
    void handlesMaximumDuplicateInputAndDistinguishesTypesAtSameCenter() {
        var duplicate = pipe(1, 0, 0);
        var candidates = new ArrayList<ScanGeometry.Candidate>();
        for (int i = 0; i < ScanGeometry.MAX_CANDIDATES; i++) candidates.add(duplicate);
        var results = ScanGeometry.select(Vec3.ZERO, 2, candidates);
        assertEquals(1, results.size());

        var sameCenterCable = new ScanGeometry.Candidate(ScanGeometry.Type.CABLE, duplicate.center());
        assertEquals(2, ScanGeometry.select(Vec3.ZERO, 2, List.of(sameCenterCable, duplicate)).size());
    }

    @Test
    void rejectsAsSoonAsUniqueResultsExceedOutputBudget() {
        var candidates = new ArrayList<ScanGeometry.Candidate>();
        for (int i = 0; i <= ScanGeometry.MAX_RESULTS; i++) candidates.add(pipe(i / 10000.0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ScanGeometry.select(Vec3.ZERO, 1, candidates));
    }

    @Test
    void rejectsInvalidGeometryAndBudgetsRatherThanReturningPartialResults() {
        assertThrows(IllegalArgumentException.class, () -> ScanGeometry.select(Vec3.ZERO, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ScanGeometry.select(Vec3.ZERO, 1,
                List.of(pipe(Double.NaN, 0, 0))));
        var tooMany = new ArrayList<ScanGeometry.Candidate>();
        for (int i = 0; i <= ScanGeometry.MAX_CANDIDATES; i++) tooMany.add(pipe(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ScanGeometry.select(Vec3.ZERO, 1, tooMany));
        var tooManyResults = new ArrayList<ScanGeometry.Candidate>();
        for (int i = 0; i <= ScanGeometry.MAX_RESULTS; i++) tooManyResults.add(pipe(i / 10000.0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ScanGeometry.select(Vec3.ZERO, 1, tooManyResults));
    }
}
