package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TimedInteractionInventoryRefreshPolicyTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID BODY = UUID.randomUUID();
    private static TimedInteractionSnapshot view(long rev, TimedOperation.Phase phase) {
        return new TimedInteractionSnapshot(DIM, 3, BODY, rev, rev,
                List.of(new TimedInteractionSnapshot.View(1, 1, 10, phase,
                        phase == TimedOperation.Phase.ACTIVE ? -1 : 12, 4, Optional.of(new BlockPos(1,2,3)))));
    }
    @Test void ownedWorldTerminalRefreshesOnceButActiveOldRepeatedAndForeignDoNot() {
        var active = view(1, TimedOperation.Phase.ACTIVE);
        var done = view(2, TimedOperation.Phase.COMPLETED);
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(null, active, BODY, DIM));
        assertTrue(TimedInteractionInventoryRefreshPolicy.shouldRefresh(active, done, BODY, DIM));
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(done, done, BODY, DIM));
        var retained = new TimedInteractionSnapshot(DIM, 3, BODY, 3, 13, done.views());
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(done, retained, BODY, DIM));
        var secondTerminal = new TimedInteractionSnapshot(DIM, 3, BODY, 4, 14,
                List.of(done.views().getFirst(), new TimedInteractionSnapshot.View(2, 2, 10,
                        TimedOperation.Phase.COMPLETED, 14, 5, Optional.of(new BlockPos(4,5,6)))));
        assertTrue(TimedInteractionInventoryRefreshPolicy.shouldRefresh(retained, secondTerminal, BODY, DIM));
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(done, view(1, TimedOperation.Phase.CANCELLED), BODY, DIM));
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(active, done, UUID.randomUUID(), DIM));
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(active,
                new TimedInteractionSnapshot(ResourceLocation.withDefaultNamespace("the_nether"), 3, BODY, 2, 2,
                        done.views()), BODY, DIM));
        assertFalse(TimedInteractionInventoryRefreshPolicy.shouldRefresh(active,
                new TimedInteractionSnapshot(DIM, 4, UUID.randomUUID(), 2, 2, done.views()), BODY, DIM));
    }
}
