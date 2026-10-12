package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageDataSyncCodecTest {
    @Test
    void explicitKnownZeroAndDamageSurvivePersistenceAndAttachmentStreamCodecs() {
        DamageData empty = new DamageData();
        var savedEmpty = DamageData.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals("{}", savedEmpty.toString(), "known zero must be serialized, not filtered as absent");
        assertTrue(DamageData.CODEC.parse(JsonOps.INSTANCE, savedEmpty).getOrThrow().isEmpty());
        DamageData decodedEmpty = streamRoundTrip(empty);
        assertNotNull(decodedEmpty, "decoding this payload means an attachment was actually present");
        assertTrue(decodedEmpty.isEmpty());

        DamageData damaged = new DamageData(new DamageMap(Map.of(DamageKeys.BLUNT, 5f)));
        DamageData decodedDamage = streamRoundTrip(damaged);
        assertEquals(Map.of(DamageKeys.BLUNT, 5f), decodedDamage.getMap());

        // Healing the same ledger back to zero must produce a present empty payload too.
        DamageData healed = new DamageData(new DamageMap(Map.of()));
        assertTrue(streamRoundTrip(healed).isEmpty());
    }

    private static DamageData streamRoundTrip(DamageData value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        DamageData.STREAM_CODEC.encode(buffer, value);
        return DamageData.STREAM_CODEC.decode(buffer);
    }
}
