package com.juicyslew.moonstation14.ms14.examine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class ExamineResponseContractTest {
    @Test void unauthorizedDetailCannotBeSerializedIntoAnAcceptedResult() {
        assertThrows(IllegalArgumentException.class, () -> new ExamineResponse(1, ExamineTarget.block(BlockPos.ZERO),
                UUID.randomUUID(), 1, true, Component.literal("target"), Component.empty(),
                List.of(ExamineEntry.detail(Component.literal("secret"), "medical", 0)), false));
        assertThrows(IllegalArgumentException.class, () -> new ExamineResponse(1, ExamineTarget.block(BlockPos.ZERO),
                UUID.randomUUID(), 1, false, Component.literal("target"), Component.empty(),
                List.of(), true));
    }

    @Test void entitySelectionUsesBothRuntimeIdAndUuid() {
        var first = ExamineTarget.entity(12, UUID.randomUUID());
        assertEquals(first, ExamineTarget.entity(12, first.entityUuid()));
        assertNotEquals(first, ExamineTarget.entity(13, first.entityUuid()));
        assertNotEquals(first, ExamineTarget.entity(12, UUID.randomUUID()));
    }

    @Test void initialAndStampedRequestsRoundTripOptionalControllerIncarnation() {
        UUID actor = UUID.randomUUID();
        var target = ExamineTarget.block(new BlockPos(3, 4, 5));
        var initial = new ExamineRequest(7, actor, 9, target);
        assertNull(roundTrip(ExamineRequest.STREAM_CODEC, initial).controllerIncarnation());

        UUID controller = UUID.randomUUID();
        var refresh = new ExamineRequest(8, actor, 9, target, controller);
        assertEquals(controller, roundTrip(ExamineRequest.STREAM_CODEC, refresh).controllerIncarnation());
    }

    @Test void responseRoundTripCarriesControllerIncarnationWithoutChangingTargetIdentity() {
        UUID actor = UUID.randomUUID(), controller = UUID.randomUUID();
        var target = ExamineTarget.block(new BlockPos(3, 4, 5));
        var response = new ExamineResponse(8, target, actor, 9, true, Component.literal("airlock"),
                Component.empty(), List.of(), true, controller);
        var decoded = roundTrip(ExamineResponse.STREAM_CODEC, response);
        assertEquals(target, decoded.target());
        assertEquals(actor, decoded.actorUuid());
        assertEquals(controller, decoded.controllerIncarnation());
    }

    @Test void sceneTargetIsStableAcrossNetworkAndUsesNullNonControllerStamp() {
        var sun = ExamineTarget.scene(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN);
        assertEquals(sun, ExamineTarget.scene(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN));
        assertNotEquals(sun, ExamineTarget.scene(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.EARTH));
        UUID actor = UUID.randomUUID();
        var request = roundTrip(ExamineRequest.STREAM_CODEC, new ExamineRequest(3, actor, 7, sun));
        assertEquals(sun, request.target());
        var response = new ExamineResponse(3, sun, actor, 7, true, Component.literal("Sun"),
                Component.literal("A great big ball of flame."), List.of(), false, null);
        var decoded = roundTrip(ExamineResponse.STREAM_CODEC, response);
        assertEquals(sun, decoded.target());
        assertNull(decoded.controllerIncarnation());
        assertTrue(com.juicyslew.moonstation14.ms14.examine.client.ExamineResponseStamp.matches(decoded, 3,
                sun, sun, actor, 7, null, false));
        assertThrows(IllegalArgumentException.class, () -> ExamineTarget.scene(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "moon")));
    }

    @Test void responseRoundTripPreservesNestedStyledExamineComponents() {
        var styled = Component.translatable("examine.moonstation14.puddle.physical.singular",
                Component.translatable("reagent-physical-desc-translucent.article"),
                Component.translatable("reagent-physical-desc-translucent").withStyle(s -> s.withColor(0x75B1F0)));
        var response = new ExamineResponse(9, ExamineTarget.block(BlockPos.ZERO), UUID.randomUUID(), 1,
                true, Component.literal("puddle"), Component.empty(),
                List.of(ExamineEntry.detail(styled, "puddle-solution", 1)), true);
        var decoded = roundTrip(ExamineResponse.STREAM_CODEC, response);
        var result = decoded.entries().getFirst().text();
        assertEquals(styled.getString(), result.getString());
        assertEquals(0x75B1F0, ((Component) ((net.minecraft.network.chat.contents.TranslatableContents)
                result.getContents()).getArgs()[1]).getStyle().getColor().getValue());
    }

    @Test void serviceStampGateAllowsInitialQueryButRejectsControllerReplacement() {
        UUID oldStamp = UUID.randomUUID(), newStamp = UUID.randomUUID();
        assertTrue(ExamineService.matchesControllerIncarnation(null, newStamp));
        assertTrue(ExamineService.matchesControllerIncarnation(oldStamp, oldStamp));
        assertFalse(ExamineService.matchesControllerIncarnation(oldStamp, newStamp));
        assertFalse(ExamineService.matchesControllerIncarnation(oldStamp, null));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T source) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, source);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
