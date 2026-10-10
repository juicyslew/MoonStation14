package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterDumpPayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void allPhasesRoundTripWithCanonicalTargets() {
        UUID gesture = UUID.randomUUID(), source = UUID.randomUUID();
        for (var phase : ChemMasterDumpRequest.Phase.values()) {
            var request = new ChemMasterDumpRequest(gesture, phase, 4, 9, source,
                    ResourceLocation.withDefaultNamespace("overworld"),
                    phase == ChemMasterDumpRequest.Phase.RELEASE ? Optional.of(new BlockPos(2, 3, 4)) : Optional.empty());
            var buf = buffer();
            ChemMasterDumpRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, ChemMasterDumpRequest.STREAM_CODEC.decode(buf));
        }
    }

    @Test void malformedFieldsAndNoncanonicalTargetsAreRejected() {
        UUID id = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        assertThrows(IllegalArgumentException.class, () -> new ChemMasterDumpRequest(id,
                ChemMasterDumpRequest.Phase.BEGIN, 0, 0, id, dim, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ChemMasterDumpRequest(id,
                ChemMasterDumpRequest.Phase.CANCEL, 1, -1, id, dim, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ChemMasterDumpRequest(id,
                ChemMasterDumpRequest.Phase.BEGIN, 1, 0, id, dim, Optional.of(BlockPos.ZERO)));
    }

    @Test void resultsRoundTripAndCorrelateIndependentlyByGestureAndPhase() {
        var oldGesture = UUID.randomUUID();
        var currentGesture = UUID.randomUUID();
        var old = new ChemMasterDumpResult(oldGesture, ChemMasterDumpRequest.Phase.RELEASE,
                false, Optional.of(Component.literal("stale source")));
        var accepted = new ChemMasterDumpResult(currentGesture, ChemMasterDumpRequest.Phase.BEGIN,
                true, Optional.empty());
        for (var result : new ChemMasterDumpResult[]{old, accepted}) {
            var buf = buffer();
            ChemMasterDumpResult.STREAM_CODEC.encode(buf, result);
            assertEquals(result, ChemMasterDumpResult.STREAM_CODEC.decode(buf));
        }
        assertNotEquals(old.gestureId(), accepted.gestureId());
        assertThrows(IllegalArgumentException.class, () -> new ChemMasterDumpResult(currentGesture,
                ChemMasterDumpRequest.Phase.RELEASE, true, Optional.of(Component.literal("invalid"))));
    }
}
