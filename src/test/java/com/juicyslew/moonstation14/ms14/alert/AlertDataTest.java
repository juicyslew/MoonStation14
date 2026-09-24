package com.juicyslew.moonstation14.ms14.alert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.component.codec.json.AlertSchemaAudit;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertDataTest {
    @Test
    void strictCodecRoundTripsCanonicalAlertData() {
        JsonObject json = JsonParser.parseString("""
                {
                  "name_translation_key":"alerts-high-toxin-name",
                  "description_translation_key":"alerts-high-toxin-desc",
                  "color":"0xff0000",
                  "order":0,
                  "category":"moonstation14:toxins"
                }
                """).getAsJsonObject();

        AlertData decoded = AlertData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("alerts-high-toxin-name", decoded.nameTranslationKey());
        assertEquals("alerts-high-toxin-desc", decoded.descriptionTranslationKey());
        assertEquals(0xff0000, decoded.color());
        assertEquals(0, decoded.order());
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "toxins")),
                decoded.category());
        assertEquals(decoded, AlertData.CODEC.parse(JsonOps.INSTANCE,
                AlertData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow());
    }

    @Test
    void schemaRejectsUnknownFieldsAndMalformedValues() {
        JsonObject valid = JsonParser.parseString("""
                {"name_translation_key":"name","description_translation_key":"desc",
                 "color":"0xff0000","order":1}
                """).getAsJsonObject();
        valid.addProperty("icon", "not-supported");
        assertTrue(AlertData.CODEC.parse(JsonOps.INSTANCE, valid).error().isPresent());

        JsonObject malformed = valid.deepCopy();
        malformed.remove("icon");
        malformed.addProperty("color", "#ff0000");
        assertTrue(AlertData.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());

        JsonObject category = valid.deepCopy();
        category.addProperty("category", "Toxins");
        assertTrue(AlertData.CODEC.parse(JsonOps.INSTANCE, category).error().isPresent());

        assertThrows(IllegalArgumentException.class, () -> AlertSchemaAudit.audit(valid));
    }
}
