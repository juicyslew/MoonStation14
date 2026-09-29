package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CharacterIdentityTest {
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
        CharacterData base = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,"standing_eligible":true,
                 "prone_eligible":true,"reactive_groups":[],"reactive_methods":[]}}
                """)).getOrThrow();
        ResourceLocation player = ResourceLocation.parse("minecraft:player");
        ResourceLocation pig = ResourceLocation.parse("minecraft:pig");
        ResourceLocation pigId = ResourceLocation.parse("test:pig");
        CharacterData human = new CharacterData(base.slipData(), java.util.Optional.empty(), List.of(player));
        CharacterData animal = new CharacterData(base.slipData(), java.util.Optional.empty(), List.of(pig));
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
    void boundHostUsesOnlyTheCurrentlyPublishedBarotraumaComponent() {
        ResourceLocation host = ResourceLocation.parse("minecraft:player");
        ResourceLocation identity = ModCharacters.HUMAN_ID;
        CharacterData initial = barotraumaHost(host, 0.5);
        var first = new PrototypeCatalog<>(Map.of(identity, initial));
        assertEquals(Map.of("blunt", 0.5), CharacterIdentitySystem.resolveForHost(first, identity, host)
                .flatMap(data -> data.component(BarotraumaComponent.class)).orElseThrow().damage().types());

        CharacterData without = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,"standing_eligible":true,
                "prone_eligible":true,"reactive_groups":[],"reactive_methods":[]},
                "host_entity_types":["minecraft:player"]}
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
                {"slip_data":{"can_receive_stun":true,"no_slip":false,"standing_eligible":true,
                "prone_eligible":true,"reactive_groups":[],"reactive_methods":[]},
                "host_entity_types":["%s"],
                "components":[{"type":"Barotrauma","damage":{"types":{"blunt":%s}},"maxDamage":200}]}
                """.formatted(host, blunt))).getOrThrow();
    }
}
