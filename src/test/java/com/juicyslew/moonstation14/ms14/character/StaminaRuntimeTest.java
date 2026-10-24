package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaAttachment;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaState;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaRuntimeTest {
    @Test void authoritativeStateSurvivesCodecAndStreamCodecBoundaries() {
        var state = new StaminaState(74.5f, false, true, 9123);
        assertEquals(state, StaminaState.CODEC.parse(JsonOps.INSTANCE,
                StaminaState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        StaminaState.STREAM_CODEC.encode(buffer, state);
        assertEquals(state, StaminaState.STREAM_CODEC.decode(buffer));
        assertEquals(new StaminaAttachment(state), StaminaState.CODEC.xmap(StaminaAttachment::new,
                StaminaAttachment::state).parse(JsonOps.INSTANCE,
                StaminaState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
        assertTrue(StaminaState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"damage\":-1,\"critical\":false,\"after_critical\":false,\"next_update_tick\":0}")).error().isPresent());
        assertEquals(StaminaState.EMPTY, StaminaState.CODEC.parse(JsonOps.INSTANCE,
                StaminaState.CODEC.encodeStart(JsonOps.INSTANCE, StaminaState.EMPTY).getOrThrow()).getOrThrow());
    }

    @Test void movementStatusUpdateCarriesTheDerivedThresholdMultiplier() {
        var current = java.util.Optional.of(com.juicyslew.moonstation14.ms14.status_effect.StatusEffectInstance
                .activePermanent(new com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload.MovementSpeedModifier(0.5)));
        var updated = com.juicyslew.moonstation14.ms14.status_effect.StatusEffectReducer.reduce(
                com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation.UPDATE, current,
                java.util.OptionalInt.empty(), 0,
                new com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload.MovementSpeedModifier(0.7));
        assertEquals(0.7f, ((com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload.MovementSpeedModifier)
                updated.next().orElseThrow().payload()).multiplier());
    }
}
