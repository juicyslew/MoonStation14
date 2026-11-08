package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TimedInteractionClockPolicyTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID BODY = UUID.randomUUID();
    private static TimedInteractionSnapshot snapshot(long revision, long tick, UUID body, ResourceLocation dim,
                                                      TimedOperation.Phase phase) {
        return new TimedInteractionSnapshot(dim, 7, body, revision, tick,
                List.of(new TimedInteractionSnapshot.View(12, 0, 20, phase,
                        phase == TimedOperation.Phase.ACTIVE ? -1 : tick, 12)));
    }

    @Test void duplicateAndSameTickRevisionKeepAnchorWhileNewTickAndContextReanchor() {
        var active = snapshot(1, 100, BODY, DIM, TimedOperation.Phase.ACTIVE);
        long anchor = TimedInteractionClockPolicy.receivedAt(null, 0, active, 100);
        double before = TimedInteractionClockPolicy.estimatedServerTick(100, anchor, 110, 0);
        assertEquals(110, before);
        float progressBefore = TimedInteractionMath.progress(100, 20, before);

        var duplicate = snapshot(1, 100, BODY, DIM, TimedOperation.Phase.ACTIVE);
        long duplicateAnchor = TimedInteractionClockPolicy.receivedAt(active, anchor, duplicate, 110);
        assertEquals(anchor, duplicateAnchor);
        assertEquals(before, TimedInteractionClockPolicy.estimatedServerTick(100, duplicateAnchor, 110, 0));
        assertEquals(progressBefore, TimedInteractionMath.progress(100, 20,
                TimedInteractionClockPolicy.estimatedServerTick(100, duplicateAnchor, 110, 0)));

        // Same server tick can carry a real state/revision transition; it must not be filtered or reanchored.
        var completed = snapshot(2, 100, BODY, DIM, TimedOperation.Phase.COMPLETED);
        long transitionAnchor = TimedInteractionClockPolicy.receivedAt(active, anchor, completed, 115);
        assertEquals(anchor, transitionAnchor);
        assertEquals(2, completed.revision());
        assertEquals(TimedOperation.Phase.COMPLETED, completed.views().getFirst().phase());

        var nextTick = snapshot(3, 101, BODY, DIM, TimedOperation.Phase.ACTIVE);
        assertEquals(130, TimedInteractionClockPolicy.receivedAt(completed, anchor, nextTick, 130));
        assertEquals(130, TimedInteractionClockPolicy.receivedAt(completed, anchor,
                snapshot(3, 100, UUID.randomUUID(), DIM, TimedOperation.Phase.ACTIVE), 130));
        assertEquals(130, TimedInteractionClockPolicy.receivedAt(completed, anchor,
                snapshot(3, 100, BODY, ResourceLocation.withDefaultNamespace("the_nether"), TimedOperation.Phase.ACTIVE), 130));
    }
}
