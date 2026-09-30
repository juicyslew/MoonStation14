package com.juicyslew.moonstation14.ms14.chat.radio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RadioChannelPrototypeTest {
    private static final ResourceLocation COMMON = ModRadioChannels.COMMON_ID;

    @Test
    void bundledCommonIsAKeyedDisplayDefinitionAndRoundTrips() throws Exception {
        String path = "data/moonstation14/moonstation14/radio_channel/common.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            PrototypeManager manager = manager();
            manager.reload(ModRadioChannels.RADIO_CHANNEL_TYPE, Map.of(COMMON, json));
            RadioChannelData common = manager.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).get(COMMON);
            assertEquals("radio-channel.moonstation14.common", common.labelTranslationKey());
            assertEquals('h', common.keycode());
            assertEquals(0x98C5E3, common.color());
            assertFalse(manager.encodePublishedCatalogs().get(ModRadioChannels.RADIO_CHANNEL_TYPE.typeId())
                    .get(COMMON).has("id"));

            PrototypeManager client = manager();
            client.publishEncodedCatalogs(manager.encodePublishedCatalogs());
            assertEquals(common, client.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).get(COMMON));
        }
    }

    @Test
    void codecRejectsUnknownFieldsWrongTypesAndInvalidValues() {
        JsonObject valid = channel("radio-channel.moonstation14.common", "h", "0x98C5E3");
        for (String extra : new String[]{"id", "parent", "abstract", "transmit", "receive"}) {
            JsonObject invalid = valid.deepCopy();
            invalid.addProperty(extra, "ignored");
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), extra);
        }
        for (String field : new String[]{"label_translation_key", "keycode", "color"}) {
            JsonObject missing = valid.deepCopy();
            missing.remove(field);
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE, missing).error().isPresent(), field);
            JsonObject wrongType = valid.deepCopy();
            wrongType.addProperty(field, 42);
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE, wrongType).error().isPresent(), field);
        }
        for (String key : new String[]{"", "hh", "H", ";", "é", " "}) {
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE,
                    channel("radio-channel.moonstation14.common", key, "0x98C5E3")).error().isPresent(), key);
        }
        for (String color : new String[]{"", "0x12345", "0x12345678", "#123456", "0xGGGGGG"}) {
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE,
                    channel("radio-channel.moonstation14.common", "h", color)).error().isPresent(), color);
        }
        for (String label : new String[]{"", "  ", "Common", "radio channel", "radio-channel."}) {
            assertTrue(RadioChannelData.CODEC.parse(JsonOps.INSTANCE,
                    channel(label, "h", "0x98C5E3")).error().isPresent(), label);
        }
    }

    @Test
    void duplicateKeycodesAndInvalidReloadsRetainPublishedSnapshot() {
        PrototypeManager manager = manager();
        JsonObject common = channel("radio-channel.moonstation14.common", "h", "0x98C5E3");
        manager.reload(ModRadioChannels.RADIO_CHANNEL_TYPE, Map.of(COMMON, common));
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModRadioChannels.RADIO_CHANNEL_TYPE,
                Map.of(COMMON, common, id("other"), channel("radio-channel.moonstation14.other", "h", "0xFFFFFF"))));
        assertEquals(1, manager.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).asMap().size());
        JsonObject unauthorized = common.deepCopy();
        unauthorized.addProperty("transmit", true);
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModRadioChannels.RADIO_CHANNEL_TYPE,
                Map.of(COMMON, unauthorized)));
        assertEquals('h', manager.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).get(COMMON).keycode());
    }

    @Test
    void authoritativeImportRejectsDuplicateKeysWithoutReplacingPreviousCatalog() {
        PrototypeManager client = manager();
        client.publishDecoded(ModRadioChannels.RADIO_CHANNEL_TYPE,
                Map.of(COMMON, new RadioChannelData("radio-channel.moonstation14.common", 'h', 0x98C5E3)));
        assertThrows(PrototypeLoadException.class, () -> client.publishEncodedCatalogs(Map.of(
                ModRadioChannels.RADIO_CHANNEL_TYPE.typeId(), Map.of(COMMON,
                        channel("radio-channel.moonstation14.common", "h", "0x98C5E3"), id("duplicate"),
                        channel("radio-channel.moonstation14.duplicate", "h", "0xFFFFFF")))));
        assertEquals(1, client.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).asMap().size());
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModRadioChannels.RADIO_CHANNEL_TYPE);
        return manager;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static JsonObject channel(String label, String key, String color) {
        JsonObject json = new JsonObject();
        json.addProperty("label_translation_key", label);
        json.addProperty("keycode", key);
        json.addProperty("color", color);
        return json;
    }
}
