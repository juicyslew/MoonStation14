package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.ms14.character.components.TetherSettings;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RopeConstraintAttachmentTest {
    @Test
    void streamCodecRoundTripsIncomingOutgoingAndChainedEndpointRoles() {
        var expected = new RopeConstraintAttachment(List.of(
                constraint(RopeConstraintAttachment.EndpointRole.PULLER),
                constraint(RopeConstraintAttachment.EndpointRole.PULLED)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            RopeConstraintAttachment.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, RopeConstraintAttachment.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RopeConstraintAttachment.Constraint constraint(RopeConstraintAttachment.EndpointRole role) {
        var snapshot = new RopeConstraintAttachment.MotionSnapshot(Vec3.ZERO, Vec3.ZERO);
        return new RopeConstraintAttachment.Constraint(UUID.randomUUID(), snapshot, snapshot,
                2, TetherSettings.DEFAULT, role);
    }
}
