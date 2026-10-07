package com.juicyslew.moonstation14.ms14.timed_interaction;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimedInteractionSnapshotTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("test", "dimension");

    @Test
    void streamCodecRoundTripsAllPhasesAndSnapshotMetadata() {
        for (TimedOperation.Phase phase : TimedOperation.Phase.values()) {
            long terminal = phase == TimedOperation.Phase.ACTIVE ? -1 : 20;
            var input = new TimedInteractionSnapshot(DIMENSION, 42, new UUID(7, 9), 12, 30,
                    List.of(new TimedInteractionSnapshot.View(5, 10, 11, phase, terminal, 3)));
            assertEquals(input, roundTrip(input));
        }
        var repeated = new TimedInteractionSnapshot(DIMENSION, 42, new UUID(7, 9), 13, 31,
                List.of(new TimedInteractionSnapshot.View(5, 11, 11, TimedOperation.Phase.ACTIVE, -1, 4)));
        assertEquals(repeated, roundTrip(repeated));
    }

    @Test
    void validatesViewAndDefensivelyCopiesBoundedViews() {
        List<TimedInteractionSnapshot.View> views = new ArrayList<>();
        views.add(new TimedInteractionSnapshot.View(1, 0, 1, TimedOperation.Phase.ACTIVE, -1, 0));
        var snapshot = new TimedInteractionSnapshot(DIMENSION, 1, UUID.randomUUID(), 0, 0, views);
        views.clear();
        assertEquals(1, snapshot.views().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.views().clear());
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot(DIMENSION, 1,
                UUID.randomUUID(), 0, 0, java.util.Collections.nCopies(33, snapshot.views().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(0, 0, 1,
                TimedOperation.Phase.ACTIVE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, -1, 1,
                TimedOperation.Phase.ACTIVE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, 0, 0,
                TimedOperation.Phase.ACTIVE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, 0, 1,
                null, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, 0, 1,
                TimedOperation.Phase.ACTIVE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, 4, 1,
                TimedOperation.Phase.COMPLETED, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimedInteractionSnapshot.View(1, 0, 1,
                TimedOperation.Phase.CANCELLED, 0, -1));
        assertFalse(java.util.Arrays.stream(TimedInteractionSnapshot.View.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("source")
                        || component.getName().toLowerCase().contains("owner")));
    }

    private static TimedInteractionSnapshot roundTrip(TimedInteractionSnapshot value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            TimedInteractionSnapshot.STREAM_CODEC.encode(buffer, value);
            return TimedInteractionSnapshot.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
