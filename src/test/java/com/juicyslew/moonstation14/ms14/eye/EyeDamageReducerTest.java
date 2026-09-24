package com.juicyslew.moonstation14.ms14.eye;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeDamageReducerTest {
    @Test
    void stateAndCodecsAreValidatedAndRoundTrip() {
        assertEquals(-1, new EffectData.EyeDamage().amount());
        assertEquals(EyeDamageComponent.EMPTY, new EyeDamageComponent());
        assertThrows(IllegalArgumentException.class, () -> new EyeDamageComponent(-1));
        assertThrows(IllegalArgumentException.class, () -> new EyeDamageComponent(10));
        assertTrue(EyeDamageComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"damage\":10}")).error().isPresent());

        EyeDamageAttachment value = new EyeDamageAttachment(new EyeDamageComponent(9));
        var encoded = EyeDamageAttachment.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, EyeDamageAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(value, EyeDamageAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"damage\":9}")).getOrThrow());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        EyeDamageAttachment.STREAM_CODEC.encode(buffer, value);
        assertEquals(value, EyeDamageAttachment.STREAM_CODEC.decode(buffer));
        buffer.release();

        RegistryFriendlyByteBuf invalid = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        invalid.writeVarInt(10);
        assertThrows(IllegalArgumentException.class, () -> EyeDamageAttachment.STREAM_CODEC.decode(invalid));
        invalid.release();
    }

    @Test
    void floorPreservesPositiveAndNegativeAsymmetry() {
        assertEquals(0L, EyeDamageReducer.scaledDelta(1, .5f));
        assertEquals(-1L, EyeDamageReducer.scaledDelta(-1, .5f));
        assertEquals(-4L, EyeDamageReducer.scaledDelta(-7, .5f));
        assertEquals(-1L, EyeDamageReducer.scaledDelta(-10, .1f));
        assertEquals(1L, EyeDamageReducer.scaledDelta(10, .1f));
    }

    @Test
    void zeroScaleAndZeroDeltaAreAppliedNoOpsWithoutReplacingState() {
        EyeDamageComponent current = new EyeDamageComponent(4);
        assertSame(current, EyeDamageReducer.apply(current, 7, 0f));
        assertSame(current, EyeDamageReducer.apply(current, 1, .5f));
        assertSame(EyeDamageComponent.EMPTY, EyeDamageReducer.apply(EyeDamageComponent.EMPTY, 0, 1f));
    }

    @Test
    void totalsClampWithoutIntegerOverflowAndUnchangedBoundariesReuseState() {
        EyeDamageComponent full = new EyeDamageComponent(9);
        assertSame(full, EyeDamageReducer.apply(full, Integer.MAX_VALUE, 1f));
        assertEquals(9, EyeDamageReducer.apply(new EyeDamageComponent(8), Integer.MAX_VALUE, 1f).damage());
        assertEquals(0, EyeDamageReducer.apply(new EyeDamageComponent(1), Integer.MIN_VALUE, 1f).damage());
        assertSame(EyeDamageComponent.EMPTY,
                EyeDamageReducer.apply(EyeDamageComponent.EMPTY, Integer.MIN_VALUE, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> EyeDamageReducer.apply(EyeDamageComponent.EMPTY, Integer.MAX_VALUE, Float.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> EyeDamageReducer.apply(EyeDamageComponent.EMPTY, 1, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> EyeDamageReducer.apply(EyeDamageComponent.EMPTY, 1, -1f));
    }

    @Test
    void thresholdAndHealingSemanticsAreDerivedFromDamageOnly() {
        assertFalse(new EyeDamageComponent(8).isBlind());
        assertTrue(new EyeDamageComponent(9).isBlind());
        assertEquals(8, EyeDamageReducer.apply(new EyeDamageComponent(9), -1, 1f).damage());
        assertEquals(0, EyeDamageReducer.apply(new EyeDamageComponent(4), -9, 1f).damage());
    }

    @Test
    void fogOnlyTightensEnvironment() {
        EyeDamageVision.FogPlanes blind = EyeDamageVision.tightenFog(2f, 20f, true);
        assertEquals(0f, blind.nearPlane());
        assertEquals(5f, blind.farPlane());
        assertEquals(new EyeDamageVision.FogPlanes(0f, 2f),
                EyeDamageVision.tightenFog(0f, 2f, true));
        assertEquals(new EyeDamageVision.FogPlanes(2f, 20f),
                EyeDamageVision.tightenFog(2f, 20f, false));
    }

    @Test
    void fogProjectionOnlyMutatesAndCancelsWhenBlindnessTightensPlanes() {
        EyeDamageVision.FogProjection blind = EyeDamageVision.projectFog(2f, 20f, true, false);
        assertTrue(blind.changed());
        assertTrue(blind.canceled());

        EyeDamageVision.FogProjection alreadyTight = EyeDamageVision.projectFog(0f, 2f, true, false);
        assertFalse(alreadyTight.changed());
        assertFalse(alreadyTight.canceled());

        EyeDamageVision.FogProjection nonBlind = EyeDamageVision.projectFog(2f, 20f, false, false);
        assertFalse(nonBlind.changed());
        assertFalse(nonBlind.canceled());

        EyeDamageVision.FogProjection preserved = EyeDamageVision.projectFog(2f, 20f, false, true);
        assertFalse(preserved.changed());
        assertTrue(preserved.canceled());

        EyeDamageVision.FogProjection preservedBlind = EyeDamageVision.projectFog(2f, 20f, true, true);
        assertTrue(preservedBlind.changed());
        assertTrue(preservedBlind.canceled());
    }
}
