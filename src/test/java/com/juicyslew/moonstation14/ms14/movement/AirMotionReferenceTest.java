package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AirMotionReferenceTest {
    @Test
    void translatedSupportIsFrozenAndOnlyRelativeHorizontalVelocityIsConditioned() {
        var reference = new AirMotionReference(UUID.randomUUID(), new MovementVector(2, 7, -3));
        var world = new MovementVector(2, 7, -3);
        var relative = reference.relativeVelocity(world);
        assertEquals(MovementVector.ZERO, relative);
        assertEquals(new MovementVector(2, 7, -3), reference.worldVelocity(relative));
        // A later support velocity is irrelevant: the immutable launch reference remains fixed.
        assertEquals(new MovementVector(2, 7, -3), reference.worldVelocity(relative));
    }
}
