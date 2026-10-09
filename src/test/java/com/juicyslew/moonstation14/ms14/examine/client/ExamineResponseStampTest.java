package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineResponse;
import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExamineResponseStampTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID INCARNATION = UUID.randomUUID();
    private static final ExamineTarget WALL = ExamineTarget.block(new BlockPos(1, 2, 3));
    private static final ExamineTarget OTHER_WALL = ExamineTarget.block(new BlockPos(1, 2, 4));

    private ExamineResponse accepted(ExamineTarget target, UUID stamp, long id, UUID actor, long epoch) {
        return new ExamineResponse(id, target, actor, epoch, true, Component.literal("target"),
                Component.empty(), List.of(), false, stamp);
    }
    private boolean accepts(ExamineResponse response, ExamineTarget pending, ExamineTarget selected,
                            UUID expected, boolean initial) {
        return ExamineResponseStamp.acceptsBoundary(response, 7, pending, selected, ACTOR, 9, expected, initial);
    }

    @Test void acceptedTargetsWithoutControllersRequireNullAndRemainPresentable() {
        assertTrue(accepts(accepted(WALL, null, 7, ACTOR, 9), WALL, WALL, null, false));
        ExamineTarget entity = ExamineTarget.entity(42, UUID.randomUUID());
        assertTrue(accepts(accepted(entity, null, 7, ACTOR, 9), entity, entity, null, false));
        assertFalse(accepts(accepted(WALL, INCARNATION, 7, ACTOR, 9), WALL, WALL, null, false));
    }

    @Test void codecPreservesNullIncarnationForOrdinaryBlockAndEntityResults() {
        ExamineTarget entity = ExamineTarget.entity(42, UUID.randomUUID());
        for (ExamineTarget target : List.of(WALL, entity)) {
            var sent = accepted(target, null, 7, ACTOR, 9);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                ExamineResponse.STREAM_CODEC.encode(buffer, sent);
                var received = ExamineResponse.STREAM_CODEC.decode(buffer);
                assertEquals(sent, received);
                assertTrue(accepts(received, target, target, null, false));
            } finally {
                buffer.release();
            }
        }
    }

    @Test void controllerIncarnationsDistinguishInitialAndRefreshExpectations() {
        assertTrue(accepts(accepted(WALL, INCARNATION, 7, ACTOR, 9), WALL, WALL, null, true));
        assertFalse(accepts(accepted(WALL, null, 7, ACTOR, 9), WALL, WALL, null, true));
        assertTrue(accepts(accepted(WALL, INCARNATION, 7, ACTOR, 9), WALL, WALL, INCARNATION, false));
        assertFalse(accepts(accepted(WALL, UUID.randomUUID(), 7, ACTOR, 9), WALL, WALL, INCARNATION, false));
    }

    @Test void allAcceptedResponsesRemainCorrelatedToRequestActorEpochAndTarget() {
        var valid = accepted(WALL, null, 7, ACTOR, 9);
        assertFalse(accepts(valid, OTHER_WALL, WALL, null, false));
        assertFalse(accepts(valid, WALL, OTHER_WALL, null, false));
        assertFalse(accepts(accepted(WALL, null, 8, ACTOR, 9), WALL, WALL, null, false));
        assertFalse(accepts(accepted(WALL, null, 7, UUID.randomUUID(), 9), WALL, WALL, null, false));
        assertFalse(accepts(accepted(WALL, null, 7, ACTOR, 10), WALL, WALL, null, false));
    }
}
