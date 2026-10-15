package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.radiation.control.RadiationCollectorControlSnapshot;
import com.juicyslew.moonstation14.ms14.radiation.control.client.RadiationCollectorContextState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorContextStateTest {
    private static final UUID BODY = UUID.randomUUID();
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final BlockPos POS = new BlockPos(1, 2, 3);

    private static RadiationCollectorControlSnapshot snapshot(long sequence, UUID body, long epoch,
            ResourceLocation dimension, BlockPos position, boolean accepted) {
        return new RadiationCollectorControlSnapshot(sequence, body, epoch, dimension, position,
                UUID.randomUUID(), ResourceLocation.withDefaultNamespace("collector"), 1, 2, true, accepted);
    }

    @Test void onlyMatchingPendingQueryIsAcceptedAndDuplicateIsRejected() {
        var state = new RadiationCollectorContextState();
        state.expectQuery(4, BODY, 7, DIM, POS);
        state.receive(snapshot(4, BODY, 7, DIM, POS, true));
        assertNotNull(state.ready());
        state.receive(snapshot(4, BODY, 7, DIM, POS, true));
        assertNotNull(state.ready());
        state.close();
        assertNull(state.ready());
    }

    @Test void replacementDecisionAndStaleScreenClosePreserveNewReadySnapshot() {
        var state = new RadiationCollectorContextState();
        var oldSnapshot = snapshot(4, BODY, 7, DIM, POS, true);
        var newSnapshot = snapshot(5, BODY, 7, DIM, POS, true);
        assertEquals(RadiationCollectorContextState.ScreenDecision.REPLACE,
                RadiationCollectorContextState.screenDecision(true, true));
        assertEquals(RadiationCollectorContextState.ScreenDecision.NO_OPEN,
                RadiationCollectorContextState.screenDecision(true, false));
        assertEquals(RadiationCollectorContextState.ScreenDecision.OPEN,
                RadiationCollectorContextState.screenDecision(false, false));
        state.expectQuery(4, BODY, 7, DIM, POS);
        state.receive(oldSnapshot);
        state.expectQuery(5, BODY, 7, DIM, POS);
        state.receive(newSnapshot);
        state.closeOwned(oldSnapshot);
        assertSame(newSnapshot, state.ready());
    }

    @Test void rejectsMismatchedQueryIdentityAndNotAcceptedReply() {
        for (var reply : new RadiationCollectorControlSnapshot[] {
                snapshot(5, BODY, 7, DIM, POS, true), snapshot(4, UUID.randomUUID(), 7, DIM, POS, true),
                snapshot(4, BODY, 8, DIM, POS, true), snapshot(4, BODY, 7, ResourceLocation.withDefaultNamespace("nether"), POS, true),
                snapshot(4, BODY, 7, DIM, POS.offset(1, 0, 0), true), snapshot(4, BODY, 7, DIM, POS, false) }) {
            var state = new RadiationCollectorContextState();
            state.expectQuery(4, BODY, 7, DIM, POS);
            state.receive(reply);
            assertNull(state.ready());
        }
    }

    @Test void closeActionAndAuthoritySwitchClearPendingAndRejectDelayedReply() {
        var state = new RadiationCollectorContextState();
        state.expectQuery(4, BODY, 7, DIM, POS);
        state.close();
        state.receive(snapshot(4, BODY, 7, DIM, POS, true));
        assertNull(state.ready());
        state.expectQuery(5, BODY, 7, DIM, POS);
        state.beginAction();
        state.receive(snapshot(5, BODY, 7, DIM, POS, true));
        assertNull(state.ready());
        state.expectQuery(6, BODY, 7, DIM, POS);
        state.cancelIfAuthorityChanged(UUID.randomUUID(), 7, DIM);
        state.receive(snapshot(6, BODY, 7, DIM, POS, true));
        assertNull(state.ready());
    }
}
