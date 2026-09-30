package com.juicyslew.moonstation14.ms14.chat.server;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechServerHooksTest {
    @Test
    void lifecycleExceptionRequiresBoundHumanAndCurrentlySpeakablePrototype() throws IOException {
        String path = "data/moonstation14/moonstation14/character/human.json";
        CharacterData human;
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        assertFalse(human.hostEntityTypes().contains(ResourceLocation.parse("moonstation14:player_character_harness")));
        assertTrue(LocalSpeechServerHooks.lifecycleSpeechAllowed(ModCharacters.HUMAN_ID, Optional.of(human)));
        assertFalse(LocalSpeechServerHooks.lifecycleSpeechAllowed(ResourceLocation.parse("test:other"), Optional.of(human)));
        assertFalse(LocalSpeechServerHooks.lifecycleSpeechAllowed(ModCharacters.HUMAN_ID, Optional.empty()));
        CharacterData silent = new CharacterData(human.slipData());
        assertFalse(LocalSpeechServerHooks.lifecycleSpeechAllowed(ModCharacters.HUMAN_ID, Optional.of(silent)));
    }
}
