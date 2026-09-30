package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StunnableComponent;
import com.juicyslew.moonstation14.ms14.character.components.BodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.BlindablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.MetabolizerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CharacterIdentityTest {
    @Test
    void packagedHumanCatalogDoesNotMapLifecycleHarnessOrUnsupportedHosts() throws IOException {
        String path = "data/moonstation14/moonstation14/character/human.json";
        CharacterData human;
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "missing packaged character resource: " + path);
            human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var catalog = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, human));
        ResourceLocation harness = ResourceLocation.parse("moonstation14:player_character_harness");
        ResourceLocation unsupported = ResourceLocation.parse("minecraft:cow");
        assertTrue(human.canSpeakText());
        assertFalse(human.hostEntityTypes().contains(harness));
        assertTrue(ModCharacters.characterForHost(catalog, harness).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, harness).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, unsupported).isEmpty());
        assertTrue(ModCharacters.characterForHost(catalog, unsupported).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ResourceLocation.parse("test:wrong"), harness).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(new PrototypeCatalog<>(Map.of()),
                ModCharacters.HUMAN_ID, harness).isEmpty());
    }

    @Test
    void attachmentCodecRoundTripsOnlyThePrototypeKey() {
        CharacterIdentityComponent component = new CharacterIdentityComponent(ModCharacters.HUMAN_ID);
        var encoded = CharacterIdentityAttachment.CODEC.encodeStart(JsonOps.INSTANCE, component.toAttachment()).getOrThrow();
        assertEquals("\"moonstation14:human\"", encoded.toString());
        assertEquals(component, CharacterIdentityAttachment.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow().toComponent());
        assertEquals(component, CharacterIdentityComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"moonstation14:human\"")).getOrThrow());
    }

    @Test
    void bindingIsIdempotentAndNeverReplacesAnExistingDifferentKey() {
        CharacterIdentityAttachment identity = new CharacterIdentityAttachment();
        identity.bind(ModCharacters.HUMAN_ID);
        identity.bind(ModCharacters.HUMAN_ID);
        assertEquals(ModCharacters.HUMAN_ID, identity.characterId());

        ResourceLocation wrong = ResourceLocation.fromNamespaceAndPath("test", "unknown");
        CharacterIdentityAttachment invalid = new CharacterIdentityAttachment();
        invalid.bind(wrong);
        assertThrows(IllegalStateException.class, () -> invalid.bind(ModCharacters.HUMAN_ID));
        assertEquals(wrong, invalid.characterId());
        assertThrows(IllegalStateException.class, () -> new CharacterIdentityAttachment().toComponent());
    }

    @Test
    void unresolvedWarningIsOncePerCatalogAndMissingId() {
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("test", "missing-warning-test");
        PrototypeCatalog<CharacterData> catalog = new PrototypeCatalog<>(Map.of());
        PrototypeCatalog<CharacterData> reloadedCatalog = new PrototypeCatalog<>(Map.of());

        assertTrue(CharacterIdentitySystem.shouldWarnUnresolved(catalog, missing));
        assertFalse(CharacterIdentitySystem.shouldWarnUnresolved(catalog, missing));
        assertTrue(CharacterIdentitySystem.shouldWarnUnresolved(reloadedCatalog, missing));
    }

    @Test
    void hostResolutionRequiresCurrentOwnerAndMatchingBoundIdentity() {
        ResourceLocation player = ResourceLocation.parse("minecraft:player");
        ResourceLocation pig = ResourceLocation.parse("minecraft:pig");
        ResourceLocation pigId = ResourceLocation.parse("test:pig");
        CharacterData human = new CharacterData(List.of(player));
        CharacterData animal = new CharacterData(List.of(pig));
        var catalog = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, human, pigId, animal));

        assertEquals(java.util.Optional.of(human), CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, player));
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, pig).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID,
                ResourceLocation.parse("minecraft:cow")).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ResourceLocation.parse("test:deleted"), player).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(new PrototypeCatalog<>(Map.of(pigId, animal)),
                ModCharacters.HUMAN_ID, player).isEmpty());
    }

    @Test
    void explicitHarnessRequiresHumanBindingCurrentCatalogAndUnclaimedHost() {
        ResourceLocation harness = ResourceLocation.parse("moonstation14:player_character_harness");
        CharacterData human = new CharacterData(List.of(ResourceLocation.parse("minecraft:player")));
        var catalog = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, human));
        assertEquals(java.util.Optional.of(human), CharacterIdentitySystem.resolveExplicitHarness(
                catalog, ModCharacters.HUMAN_ID, harness, true));
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, harness).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ModCharacters.HUMAN_ID, harness, false).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ResourceLocation.parse("test:other"), harness, true).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ModCharacters.HUMAN_ID,
                ResourceLocation.parse("minecraft:pig"), true).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(new PrototypeCatalog<>(Map.of()),
                ModCharacters.HUMAN_ID, harness, true).isEmpty());
        var claimed = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, new CharacterData(List.of(harness))));
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(claimed, ModCharacters.HUMAN_ID, harness, true).isEmpty());
    }

    @Test
    void explicitHumanProjectionCarriesDeclaredComponentsButNeverGrantsHostOwnership() {
        var harness = ResourceLocation.parse("moonstation14:player_character_harness");
        CharacterData human = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"host_entity_types":["minecraft:player"],"components":[
                  {"type":"Hands","hands":["left","right"]}, {"type":"Stunnable"},
                  {"type":"Body"}, {"type":"Blindable"},
                  {"type":"Metabolizer","types":["human"]}]}
                """)).getOrThrow();
        var catalog = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, human));
        var explicit = CharacterIdentitySystem.resolveExplicitHarness(catalog, ModCharacters.HUMAN_ID, harness, true);
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, harness).isEmpty());
        assertEquals(List.of("left", "right"), explicit.flatMap(c -> c.component(HandsPrototypeComponent.class))
                .orElseThrow().hands());
        assertTrue(explicit.flatMap(c -> c.component(StunnableComponent.class)).isPresent());
        assertTrue(explicit.flatMap(c -> c.component(BodyComponent.class)).isPresent());
        assertTrue(explicit.flatMap(c -> c.component(BlindablePrototypeComponent.class)).isPresent());
        assertTrue(explicit.flatMap(c -> c.component(MetabolizerPrototypeComponent.class)).isPresent());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ModCharacters.HUMAN_ID, harness, false).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ResourceLocation.parse("test:wrong"), harness, true).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveExplicitHarness(catalog, ModCharacters.HUMAN_ID,
                ResourceLocation.parse("minecraft:cow"), true).isEmpty());
    }

    @Test
    void mismatchedHostCannotExposeHandsOrStunEvenWithBoundComponentIdentity() {
        ResourceLocation player = ResourceLocation.parse("minecraft:player");
        ResourceLocation pig = ResourceLocation.parse("minecraft:pig");
        CharacterData human = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"host_entity_types":["minecraft:player"],"components":[
                {"type":"Hands","hands":["left","right"]},{"type":"Stunnable"}]}
                """)).getOrThrow();
        var catalog = new PrototypeCatalog<>(Map.of(ModCharacters.HUMAN_ID, human));
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, player)
                .flatMap(data -> data.component(HandsPrototypeComponent.class)).isPresent());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, player)
                .flatMap(data -> data.component(StunnableComponent.class)).isPresent());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, pig)
                .flatMap(data -> data.component(HandsPrototypeComponent.class)).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog, ModCharacters.HUMAN_ID, pig)
                .flatMap(data -> data.component(StunnableComponent.class)).isEmpty());
    }

    @Test
    void boundHostUsesOnlyTheCurrentlyPublishedBarotraumaComponent() {
        ResourceLocation host = ResourceLocation.parse("minecraft:player");
        ResourceLocation identity = ModCharacters.HUMAN_ID;
        CharacterData initial = barotraumaHost(host, 0.5);
        var first = new PrototypeCatalog<>(Map.of(identity, initial));
        assertEquals(Map.of("blunt", 0.5), CharacterIdentitySystem.resolveForHost(first, identity, host)
                .flatMap(data -> data.component(BarotraumaComponent.class)).orElseThrow().damage().types());

        CharacterData without = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"host_entity_types":["minecraft:player"]}
                """)).getOrThrow();
        var removed = new PrototypeCatalog<>(Map.of(identity, without));
        assertTrue(CharacterIdentitySystem.resolveForHost(removed, identity, host)
                .flatMap(data -> data.component(BarotraumaComponent.class)).isEmpty());

        var changed = new PrototypeCatalog<>(Map.of(identity, barotraumaHost(host, 0.8)));
        assertEquals(Map.of("blunt", 0.8), CharacterIdentitySystem.resolveForHost(changed, identity, host)
                .flatMap(data -> data.component(BarotraumaComponent.class)).orElseThrow().damage().types());
        assertEquals(Map.of("blunt", 0.5), CharacterIdentitySystem.resolveForHost(first, identity, host)
                .flatMap(data -> data.component(BarotraumaComponent.class)).orElseThrow().damage().types());
    }

    private static CharacterData barotraumaHost(ResourceLocation host, double blunt) {
        return CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"host_entity_types":["%s"],
                "components":[{"type":"Barotrauma","damage":{"types":{"blunt":%s}},"maxDamage":200}]}
                """.formatted(host, blunt))).getOrThrow();
    }
}
