package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttachedItemRemovalSystemTest {
    @Test void activeSelfOperationIsInProgressWithoutReplacingItsPinnedOperation() {
        var pinned = new TimedOperation(1, "self-attachment-removal", "selected-uuid", 40, 70,
                TimedOperation.Phase.ACTIVE, -1, false, false);
        var active = new TimedOperationState(List.of(pinned), 1, 2);
        assertEquals(AttachedItemRemovalSystem.SelfRemovalResult.IN_PROGRESS,
                AttachedItemRemovalSystem.activeOperationDisposition(active));
        assertSame(pinned, active.operations().getFirst());
        assertEquals(40, active.operations().getFirst().startTick());
        assertEquals(110, active.operations().getFirst().dueTick());
        assertEquals("selected-uuid", active.operations().getFirst().sourceKey());
    }

    @Test void unrelatedActiveOperationDeniesButNoActiveOperationAllowsSelection() {
        var unrelated = new TimedOperation(1, "strip", "source", 40, 20,
                TimedOperation.Phase.ACTIVE, -1, false, false);
        assertEquals(AttachedItemRemovalSystem.SelfRemovalResult.DENIED,
                AttachedItemRemovalSystem.activeOperationDisposition(new TimedOperationState(List.of(unrelated), 1, 2)));
        assertNull(AttachedItemRemovalSystem.activeOperationDisposition(TimedOperationState.EMPTY));
        var terminal = new TimedOperation(1, "self-attachment-removal", "old", 40, 20,
                TimedOperation.Phase.COMPLETED, 60, false, false);
        assertNull(AttachedItemRemovalSystem.activeOperationDisposition(new TimedOperationState(List.of(terminal), 1, 2)));
    }
}
