package com.juicyslew.moonstation14.ms14.chemistry;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterDumpAudioTest {
    @Test void soundIsRegisteredAndImportedPinnedSampleMatches() throws Exception {
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/assets/moonstation14/sounds.json"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var event = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("chemistry/chemmaster/dump");
            assertEquals("moonstation14:chemistry/chemmaster/dump",
                    event.getAsJsonArray("sounds").get(0).getAsString());
        }
        byte[] audio;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/moonstation14/sounds/chemistry/chemmaster/dump.ogg"))) { audio = in.readAllBytes(); }
        assertEquals("b1ff48ceb87bd5672f8458680d9cf40aa2afd60bdf46bd91cd65449c12aa8e3c",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(audio)));
        assertEquals((float) Math.pow(10, -3.0 / 20.0), ChemMasterDumpAudio.GAIN);
    }
}
