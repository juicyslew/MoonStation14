package com.juicyslew.moonstation14.ms14.chat.server;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechServerHooksTest {
    @Test
    void lifecycleAdmissionUsesBoundCharacterSpeakCapability() throws IOException {
        CharacterData mouse = character("data/moonstation14/moonstation14/character/mouse.json");
        assertTrue(mouse.canSpeakText());
        assertTrue(LocalSpeechServerHooks.lifecycleSpeechAllowed(ResourceLocation.parse("moonstation14:mouse"), Optional.of(mouse)));
        assertFalse(LocalSpeechServerHooks.lifecycleSpeechAllowed(ResourceLocation.parse("moonstation14:mouse"), Optional.empty()));

        CharacterData human = character("data/moonstation14/moonstation14/character/human.json");
        assertFalse(human.hostEntityTypes().contains(ResourceLocation.parse("moonstation14:player_character_harness")));
        assertTrue(LocalSpeechServerHooks.lifecycleSpeechAllowed(ResourceLocation.parse("moonstation14:human"), Optional.of(human)));
        CharacterData silent = new CharacterData(human.hostEntityTypes(), java.util.List.of());
        assertFalse(LocalSpeechServerHooks.lifecycleSpeechAllowed(ResourceLocation.parse("moonstation14:human"), Optional.of(silent)));
    }

    @Test
    void committedSpeakerRouteKeepsRoleAndLifecycleGatesAndConsumesMouseSpeech() throws IOException {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/ms14/chat/server/LocalSpeechServerHooks.java");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "LocalSpeechServerHooks source must be available for route audit");
        String source = Files.readString(root.resolve(relative));
        assertTrue(source.contains("LifecycleCharacterSessionControl.activeCharacterBody(player)"));
        assertTrue(source.contains("character.owned() instanceof Mob body"));
        assertTrue(source.contains("PlayerCharacterHarnessBinder.binding(body)"));
        assertTrue(source.contains("LifecycleCharacterSessionControl.roleSpeechAllowed(player, body, snapshot.epoch())"));
        assertTrue(source.contains("lifecycleSpeechAllowed(boundIdentity.characterId(), CharacterIdentitySystem.resolve(body))"));
        assertTrue(source.contains("if (ghost.committed() || character.committed() == debug.committed()) return null;"));
        assertTrue(source.contains("SpeechAccentPolicy.replace(speech.accent(), parsed.body())"));
        assertTrue(source.contains("\"mouse_squeak\".equals(speech.speechSound()) ? \"mouse_squeak\" : \"\""));
        assertTrue(source.contains("spokenText, position.x, position.y, position.z"));

        CharacterData mouse = character("data/moonstation14/moonstation14/character/mouse.json");
        assertTrue(mouse.component(com.juicyslew.moonstation14.ms14.character.components.GhostRoleComponent.class)
                .orElseThrow().allowSpeech());
        var speech = mouse.component(com.juicyslew.moonstation14.ms14.character.components.SpeechComponent.class).orElseThrow();
        assertEquals("mouse_squeak", speech.speechSound());
        assertEquals("Squeak!", SpeechAccentPolicy.replacement(speech.accent(), 0));
    }

    private CharacterData character(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("parent"); // CharacterData is the composed payload; parent resolution is covered by prototype tests.
            return CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    json).getOrThrow();
        }
    }
}
