package com.juicyslew.moonstation14.ms14.singularity.client;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SingularityNativeDrawProbeBufferTest {
    @Test void rejectsInsufficientReadableRange() {
        FloatBuffer source = FloatBuffer.allocate(20);
        source.position(3);
        source.limit(18); // 15 readable values despite capacity 20

        assertNull(SingularityNativeDrawProbe.readAppliedModelView(source));
        assertEquals(3, source.position());
        assertEquals(18, source.limit());
    }

    @Test void readsSixteenValuesFromNonzeroPositionWithoutMutatingSource() {
        FloatBuffer source = FloatBuffer.allocate(20);
        for (int i = 0; i < source.capacity(); i++) source.put(i, i + 1);
        source.position(2);
        source.limit(18);

        var matrix = SingularityNativeDrawProbe.readAppliedModelView(source);

        assertNotNull(matrix);
        assertEquals(3f, matrix.m00()); // first value at source position, not absolute index zero
        assertEquals(2, source.position());
        assertEquals(18, source.limit());
    }
}
