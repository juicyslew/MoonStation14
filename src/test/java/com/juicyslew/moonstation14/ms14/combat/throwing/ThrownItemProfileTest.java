package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrownItemProfileTest {
    @Test void codecRoundTripAndOptionalDefaults() {
        var json = JsonParser.parseString("""
                {"damage_on_hit":{"slash":10,"blunt":15},"ignore_resistances":true,
                 "embedding":{"remove_seconds":1.5,"offset_x":-0.15},
                 "inject_on_embed":{"transfer_cents":200,"block_slots":["OUTERCLOTHING"]},
                 "ensnaring":{"free_seconds":2,"breakout_seconds":3.5,"walk_multiplier":0.5,"max_ensnares":1}}
                """);
        var decoded = ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(10f, decoded.damageOnHit().get("slash"));
        assertEquals(1.5f, decoded.embedding().orElseThrow().removeSeconds());
        assertEquals(200, decoded.injectOnEmbed().orElseThrow().transferCents());
        assertTrue(decoded.ensnaring().orElseThrow().canMoveBreakout());
        var encoded = ThrownItemProfile.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
        assertEquals(decoded, ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var defaults = ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertTrue(defaults.embedding().isEmpty());
        assertTrue(defaults.injectOnEmbed().isEmpty());
        assertFalse(defaults.ignoreResistances());
        assertFalse(defaults.alignToVelocity());
        assertTrue(decoded.alignToVelocity() == false); // legacy data defaults the presentation flag off
        var aligned = ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"align_to_velocity\":true}")).getOrThrow();
        assertTrue(aligned.alignToVelocity());
        assertTrue(ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE,
                ThrownItemProfile.CODEC.encodeStart(JsonOps.INSTANCE, aligned).getOrThrow()).getOrThrow().alignToVelocity());
    }

    @Test void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new ThrownItemProfile.EnsnaringData(Float.NaN,1,1,1,1,true,true,0));
        assertThrows(IllegalArgumentException.class, () -> new ThrownItemProfile.EnsnaringData(1,-1,1,1,1,true,true,0));
        assertThrows(IllegalArgumentException.class, () -> new ThrownItemProfile.EnsnaringData(1,1,1,1,0,true,true,0));
        assertThrows(NullPointerException.class, () -> new ThrownItemProfile.InjectOnEmbedData(null, java.util.Set.of(), 1));
    }
}
