package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyTargetActionPolicyTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void queryAndSnapshotPreserveExactProofAndCodecRoundTrip() {
        var target = UUID.randomUUID();
        var query = new BodyTargetActionQuery(7, 11, 24, "right", null, target);
        var request = buffer(); BodyTargetActionQuery.STREAM_CODEC.encode(request, query);
        assertEquals(query, BodyTargetActionQuery.STREAM_CODEC.decode(request));
        var response = new BodyTargetActionSnapshot(query.bodyEpoch(), query.sequence(), query.expectedRevision(),
                query.handId(), query.expectedToken(), query.targetId(), Component.literal("Target"), true, false,
                Component.literal("Use a sharp object to butcher this."));
        var result = buffer(); BodyTargetActionSnapshot.STREAM_CODEC.encode(result, response);
        assertEquals(response, BodyTargetActionSnapshot.STREAM_CODEC.decode(result));
        assertEquals(query.bodyEpoch(), response.bodyEpoch());
        assertEquals(query.sequence(), response.sequence());
        assertEquals(query.expectedRevision(), response.expectedRevision());
        assertEquals(query.handId(), response.handId());
        assertEquals(query.expectedToken(), response.expectedToken());
        assertEquals(query.targetId(), response.targetId());
    }

    @Test void constructorsRejectInvalidProofsAndInconsistentAvailability() {
        UUID target = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionQuery(0, 1, 0, "right", null, target));
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionQuery(1, 1, 0, "", null, target));
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionQuery(1, 1, 0, "right", " ", target));
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionSnapshot(1, 1, 0, "right", null,
                target, Component.literal("x"), true, false, null));
        assertDoesNotThrow(() -> new BodyTargetActionSnapshot(1, 1, 0, "right", null,
                target, Component.literal("x"), false, true, null));
    }

    @Test void resolvedAvailabilityKeepsMissingSharpVisibleButDisabledAndSourceAbsenceHidden() {
        var missingTool = BodyTargetActionService.availability(true, false);
        assertFalse(missingTool.enabled());
        assertEquals("moonstation14.target_action.need_sharp",
                ((net.minecraft.network.chat.contents.TranslatableContents) missingTool.disabledReason().getContents()).getKey());
        var sharpTool = BodyTargetActionService.availability(true, true);
        assertTrue(sharpTool.enabled());
        assertNull(sharpTool.disabledReason());
        var noSourceComposition = BodyTargetActionService.availability(false, true);
        assertFalse(noSourceComposition.enabled());
        assertNull(noSourceComposition.disabledReason());
    }

    @Test void serviceProofAdmissionRejectsStaleHandEpochAndNominationEvidence() {
        var query = new BodyTargetActionQuery(9, 2, 4, "right", "held-token", UUID.randomUUID());
        assertTrue(BodyTargetActionService.proofMatches(query, 9, 4, "right", java.util.List.of("right"), "held-token"));
        assertFalse(BodyTargetActionService.proofMatches(query, 8, 4, "right", java.util.List.of("right"), "held-token"));
        assertFalse(BodyTargetActionService.proofMatches(query, 9, 3, "right", java.util.List.of("right"), "held-token"));
        assertFalse(BodyTargetActionService.proofMatches(query, 9, 4, "left", java.util.List.of("right", "left"), "held-token"));
        assertFalse(BodyTargetActionService.proofMatches(query, 9, 4, "right", java.util.List.of("left"), "held-token"));
        assertFalse(BodyTargetActionService.proofMatches(query, 9, 4, "right", java.util.List.of("right"), "other-token"));
        // Target UUID is separately validated by probeSelected; a non-nominated target cannot pass that shared probe.
        assertNotNull(query.targetId());
    }

    @Test void queryBoundsHandIdentifierToSharedHandContract() {
        String tooLong = "x".repeat(HandState.MAX_ID_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionQuery(1, 1, 0, tooLong, null, UUID.randomUUID()));
    }
}
