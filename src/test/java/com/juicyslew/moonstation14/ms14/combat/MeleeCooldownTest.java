package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeCooldownTest {
    @Test void deadlineUsesCeilingAndAtLeastOneTick() {
        assertEquals(20, MeleeCooldownData.deadline(0, 1f).orElseThrow());
        assertEquals(10, MeleeCooldownData.deadline(3, 3f).orElseThrow());
        assertEquals(5, MeleeCooldownData.deadline(4, 100f).orElseThrow());
    }
    @Test void invalidRatesAndOverflowAreDenied() {
        for (float rate : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY})
            assertTrue(MeleeCooldownData.deadline(0, rate).isEmpty());
        assertTrue(MeleeCooldownData.deadline(Long.MAX_VALUE - 1, 1f).isEmpty());
    }
    @Test void deadlineBoundaryAndStoredCooldownBounds() {
        assertEquals(5, MeleeCooldownData.deadline(4, 100f).orElseThrow());
        assertTrue(MeleeCooldownData.deadline(Long.MAX_VALUE - 1, 1f).isEmpty());
        var existing = new MeleeCooldownData(10);
        assertEquals(10, existing.nextAttackTick());
        assertTrue(9 < existing.nextAttackTick());
        assertFalse(10 < existing.nextAttackTick());
        assertFalse(11 < existing.nextAttackTick());
    }
    @Test void defaultAndCodecsRoundTrip() {
        assertEquals(0, new MeleeCooldownAttachment().data().nextAttackTick());
        var value = new MeleeCooldownData(123456L);
        var json = MeleeCooldownData.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, MeleeCooldownData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MeleeCooldownData.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, MeleeCooldownData.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void directionCodecRoundTripsBothValuesAndDeadlinePreservesDirection() {
        for (boolean left : new boolean[]{false, true}) {
            var value = new MeleeCooldownData(123456L, left);
            var json = MeleeCooldownData.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            assertEquals(value, MeleeCooldownData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try { MeleeCooldownData.STREAM_CODEC.encode(buffer, value); assertEquals(value, MeleeCooldownData.STREAM_CODEC.decode(buffer)); }
            finally { buffer.release(); }
        }
        assertTrue(new MeleeCooldownData(8, true).withDeadline(99).swingLeft());
        assertFalse(new MeleeCooldownData(8).swingLeft());
        assertTrue(MeleeCooldownSystem.toggleSwingLeft(MeleeCooldownSystem.toggleSwingLeft(false)) == false);
        assertTrue(MeleeCooldownSystem.toggleSwingLeft(false));
    }
}
