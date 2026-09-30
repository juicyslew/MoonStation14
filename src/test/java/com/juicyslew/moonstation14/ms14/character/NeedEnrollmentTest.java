package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponent;
import com.juicyslew.moonstation14.ms14.character.components.StomachPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.ComplexInteractionComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NeedEnrollmentTest {
    private static final ResourceLocation PLAYER = ResourceLocation.parse("minecraft:player");
    private static final ResourceLocation PIG = ResourceLocation.parse("minecraft:pig");

    @Test void markersAreStrictIndependentAndRoundTrip() {
        for (String type : List.of("Hunger", "Thirst")) {
            String marker = "{\"type\":\"" + type + "\"}";
            CharacterData data = parse("{\"components\":[" + marker + "]}");
            assertEquals(List.of(type), data.components().stream().map(c -> c.type()).toList());
            assertEquals(data, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow());
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"components\":[{\"type\":\"" + type + "\",\"extra\":1}]}"))
                    .error().isPresent());
        }
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"components\":[{\"type\":\"Hunger\"},{\"type\":\"Hunger\"}]}"))
                .error().isPresent());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"components\":[{\"type\":\"UnknownNeed\"}]}"))
                .error().isPresent());
        var orphan = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"components\":[{\"type\":\"Stomach\"}]}"));
        assertTrue(orphan.error().orElseThrow().message().contains("Stomach requires Bloodstream"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterData(List.of(),
                List.of(new StomachPrototypeComponent())));
    }

    @Test void currentSnapshotBoundHostAndMembershipAreAllRequired() {
        var identity = ModCharacters.HUMAN_ID;
        var hungerOnly = new PrototypeCatalog<>(Map.of(identity, parse("""
                {"host_entity_types":["minecraft:player"],"components":[{"type":"Hunger"}]}
                """)));
        var thirstOnly = new PrototypeCatalog<>(Map.of(identity, parse("""
                {"host_entity_types":["minecraft:player"],"components":[{"type":"Thirst"}]}
                """)));
        var stomachOnly = new PrototypeCatalog<>(Map.of(identity, new CharacterData(List.of(PLAYER),
                List.of(bloodPolicy(), new StomachPrototypeComponent()))));
        assertTrue(has(hungerOnly, identity, PLAYER, HungerPrototypeComponent.class));
        assertFalse(has(hungerOnly, identity, PLAYER, ThirstPrototypeComponent.class));
        assertFalse(has(hungerOnly, identity, PLAYER, StomachPrototypeComponent.class));
        assertTrue(has(thirstOnly, identity, PLAYER, ThirstPrototypeComponent.class));
        assertFalse(has(thirstOnly, identity, PLAYER, HungerPrototypeComponent.class));
        assertTrue(has(stomachOnly, identity, PLAYER, StomachPrototypeComponent.class));
        assertTrue(has(stomachOnly, identity, PLAYER, BloodstreamComponent.class));
        assertFalse(has(stomachOnly, identity, PLAYER, ThirstPrototypeComponent.class));
        assertFalse(has(hungerOnly, identity, PIG, HungerPrototypeComponent.class));
        assertFalse(has(hungerOnly, null, PLAYER, HungerPrototypeComponent.class));
        assertFalse(has(hungerOnly, ResourceLocation.parse("test:wrong"), PLAYER, HungerPrototypeComponent.class));
        assertFalse(has(new PrototypeCatalog<>(Map.of()), identity, PLAYER, HungerPrototypeComponent.class));
        // A new candidate changes eligibility; old saved scalar values are still readable.
        assertFalse(has(thirstOnly, identity, PLAYER, HungerPrototypeComponent.class));
        assertEquals(72f, HungerAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"hunger\":72}")).getOrThrow().hunger());
        assertEquals(313f, ThirstAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"thirst\":313}")).getOrThrow().thirst());
    }

    @Test void rejectedStomachWithoutBloodstreamDoesNotPublishOrCommitPendingStage() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        JsonObject valid = CharacterData.CODEC.encodeStart(JsonOps.INSTANCE,
                new CharacterData(List.of(PLAYER), List.of(bloodPolicy(), new StomachPrototypeComponent())))
                .getOrThrow().getAsJsonObject();
        var first = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, valid)));
        assertTrue(manager.commit(first));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        JsonObject orphan = JsonParser.parseString("""
                {"host_entity_types":["minecraft:player"],"components":[{"type":"Stomach"}]}
                """).getAsJsonObject();
        var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, orphan))));
        assertTrue(failure.getMessage().contains("Stomach requires Bloodstream"), failure.getMessage());
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        assertFalse(manager.commitStagedReload());
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
    }

    @Test void shippedHumanHasExpectedComponentsAndPigHasNoNeedMarkers() throws Exception {
        var loader = getClass().getClassLoader();
        CharacterData human;
        CharacterData pig;
        try (var input = loader.getResourceAsStream("data/moonstation14/moonstation14/character/human.json")) {
            assertNotNull(input);
            human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
        }
        try (var input = loader.getResourceAsStream("data/moonstation14/moonstation14/character/pig.json")) {
            assertNotNull(input);
            pig = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
        }
        assertEquals(21, human.components().size());
        assertTrue(human.components().stream().anyMatch(component -> component.type().equals("Speech")),
                "human speech is explicitly opted in");
        assertTrue(human.component(ComplexInteractionComponent.class).isPresent());
        assertEquals(List.of(PLAYER, ResourceLocation.parse("minecraft:villager")), human.hostEntityTypes());
        for (var type : List.of(HungerPrototypeComponent.class, ThirstPrototypeComponent.class, StomachPrototypeComponent.class)) {
            assertTrue(human.component(type).isPresent());
            assertTrue(pig.component(type).isEmpty());
        }
    }

    @Test void componentBoundIsThirtyTwoWhileHostsRemainLimitedToSixteen() {
        String marker = "{\"type\":\"Hunger\"}";
        String repeated = String.join(",", java.util.Collections.nCopies(33, marker));
        var tooMany = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"components\":[" + repeated + "]}"));
        assertTrue(tooMany.error().isPresent());
        assertTrue(tooMany.error().orElseThrow().message().contains("at most 32"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterData(List.of(),
                java.util.Collections.<CharacterComponent>nCopies(33, new HungerPrototypeComponent())));
        assertThrows(IllegalArgumentException.class, () -> new CharacterData(List.of(),
                List.of(new HungerPrototypeComponent(), new HungerPrototypeComponent())));
        String hosts = String.join(",", java.util.Collections.nCopies(17, "\"minecraft:player\""));
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"host_entity_types\":[" + hosts + "]}")).error().isPresent());
    }

    private static CharacterData parse(String json) {
        return CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static BloodstreamComponent bloodPolicy() {
        return new BloodstreamComponent(parseResourceHuman().component(BloodstreamComponent.class).orElseThrow().policy());
    }

    private static CharacterData parseResourceHuman() {
        try (var input = NeedEnrollmentTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json")) {
            assertNotNull(input);
            return CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static <T extends com.juicyslew.moonstation14.ms14.character.components.CharacterComponent> boolean has(
            PrototypeCatalog<CharacterData> snapshot, ResourceLocation bound, ResourceLocation actualHost, Class<T> type) {
        return CharacterIdentitySystem.resolveForHost(snapshot, bound, actualHost)
                .flatMap(data -> data.component(type)).isPresent();
    }
}
