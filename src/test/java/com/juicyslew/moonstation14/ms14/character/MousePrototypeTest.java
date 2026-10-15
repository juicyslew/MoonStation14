package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MousePrototypeTest {
    @Test
    void collisionProfileDiscriminatorRoundTripsAndResolvedMouseKeepsInheritedFixturePairing() throws Exception {
        var codec=com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent.CODEC;
        var original=new com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent(18,65);
        var encoded=codec.encodeStart(JsonOps.INSTANCE,original).getOrThrow();
        assertEquals("CollisionProfile",encoded.getAsJsonObject().get("type").getAsString());
        assertEquals(original,codec.parse(JsonOps.INSTANCE,encoded).getOrThrow());
        var manager=new PrototypeManager();manager.register(ModCharacters.CHARACTER_TYPE);
         manager.reload(ModCharacters.CHARACTER_TYPE,java.util.Map.of(id("supported_simple_mob"),read("supported_simple_mob"),
                 id("supported_animal"),read("supported_animal"),id("supported_npc"),read("supported_npc"),id("mouse"),read("mouse")));
        var resolved=manager.snapshot(ModCharacters.CHARACTER_TYPE).get(id("mouse"));
        assertEquals(original,resolved.component(com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent.class).orElseThrow());
        var fixture=new com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData.Collision(158,205);
        assertFalse(original.collidesWith(new com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent(fixture.mask(),fixture.layer())));
    }
    @Test
    void deterministicMouseAppearanceSelectionCoversEveryAllowedVariant() {
        JsonObject defaultJson = new JsonObject();
        defaultJson.addProperty("type", "MouseAppearance");
        var defaultAppearance = com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.CODEC
                .parse(JsonOps.INSTANCE, defaultJson).getOrThrow();
        assertEquals(java.util.List.of(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.WHITE),
                defaultAppearance.variants());
        var allowed = java.util.List.of(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.WHITE,
                com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.BROWN,
                com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.GRAY);
        for (int index = 0; index < allowed.size(); index++)
            assertEquals(allowed.get(index), com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.select(allowed, index));
    }

    @Test
    void mouseResolvesSupportedPhysiologyPolicies() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, java.util.Map.of(
                id("supported_simple_mob"), read("supported_simple_mob"),
                  id("supported_animal"), read("supported_animal"),
                  id("supported_npc"), read("supported_npc"),
                  id("mouse"), read("mouse"), id("human"), read("human")));
         var data = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(id("mouse"));
         assertTrue(com.juicyslew.moonstation14.ms14.hands.client.PortableLivingClientTargetPolicy
                         .portableCapability(data).isPresent(),
                 "client nomination consumes the same resolved mouse portable-body projection");
        var appearance = data.component(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.class).orElseThrow();
        assertEquals(java.util.List.of(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.WHITE,
                com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.BROWN,
                com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant.GRAY), appearance.variants());
        assertEquals(50.0, data.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class)
                .orElseThrow().policy().referenceSolution().get("moonstation14:blood"));
        assertEquals(0.25, data.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class)
                .orElseThrow().policy().suffocationDamagePerUpdate());
        assertEquals(0.25, data.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class)
                .orElseThrow().policy().suffocationRecoveryPerUpdate());
        assertEquals(0.1, data.barotrauma().orElseThrow().damage().types().get("blunt"));
        var temperature = data.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(325.0, temperature.heatDamageThresholdKelvin());
        assertEquals(260.0, temperature.coldDamageThresholdKelvin());
        assertEquals(1.5, temperature.heatDamagePerSecond());
        assertEquals(1.0, temperature.coldDamagePerSecond());
        assertEquals("animal", data.component(com.juicyslew.moonstation14.ms14.character.components.MetabolizerPrototypeComponent.class)
                .orElseThrow().types().iterator().next().name().toLowerCase());
        var stomach = data.component(com.juicyslew.moonstation14.ms14.character.components.StomachPrototypeComponent.class);
        assertTrue(stomach.isPresent(), "animal base composition supplies stomach enrollment");
        // StomachSystem.isEligible consumes this resolved enrollment alongside Bloodstream.
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).isPresent());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.HumanoidProfileComponent.class).isEmpty());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.BodyComponent.class).isPresent());
        assertEquals("moonstation14:organ_lungs_pig", data.component(
                com.juicyslew.moonstation14.ms14.character.components.InitialBodyComponent.class).orElseThrow()
                .organs().get(com.juicyslew.moonstation14.ms14.organ.OrganCategory.LUNGS).toString());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.DamageableComponent.class).isPresent(),
                "resolved supported_simple_mob composition supplies damageability");
        assertEquals(0.1, data.barotrauma().orElseThrow().damage().types().get("blunt"));
        assertEquals(20.0, data.barotrauma().orElseThrow().maxDamage());
        // This is the same resolved component policy read by BarotraumaSystem's runtime path.
        assertEquals(data.barotrauma().orElseThrow(), data.component(
                com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent.class).orElseThrow());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.GhostRoleComponent.class)
                .orElseThrow().takeover());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent.class).isPresent());
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent.class).isPresent());
        var speech = data.component(com.juicyslew.moonstation14.ms14.character.components.SpeechComponent.class).orElseThrow();
        assertEquals("mouse", speech.accent());
        assertEquals("mouse_squeak", speech.speechSound());
        assertEquals(java.util.Set.of("squeak"), speech.allowedEmotes());
        var attention = data.component(com.juicyslew.moonstation14.ms14.character.components.NpcIdleAttentionComponent.class).orElseThrow();
        assertEquals(5f, attention.range());
        assertEquals(40, attention.minimumProbeTicks());
        assertEquals(100, attention.maximumProbeTicks());
        var human = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(id("human"));
        assertTrue(com.juicyslew.moonstation14.ms14.hands.client.PortableLivingClientTargetPolicy
                        .portableCapability(human).isEmpty(),
                "ordinary human projection is not a portable-body pickup target");
        assertTrue(human.component(com.juicyslew.moonstation14.ms14.character.components.NpcIdleAttentionComponent.class).isEmpty(),
                "ordinary human composition does not opt into NPC attention");
        assertTrue(human.component(com.juicyslew.moonstation14.ms14.character.components.SpeechComponent.class)
                .orElseThrow().allowedEmotes().isEmpty(), "ordinary human composition cannot use closed mouse squeak capability");
        var lungResource = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/organ/organ_lungs_pig.json");
        assertNotNull(lungResource);
        var lung = com.juicyslew.moonstation14.ms14.organ.OrganData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseReader(new InputStreamReader(lungResource, StandardCharsets.UTF_8))).getOrThrow().lung().orElseThrow();
        assertEquals(4.2, lung.maxLungMoles());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }

    private JsonObject read(String name) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/" + name + ".json");
        assertNotNull(stream, name);
        try (stream) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
