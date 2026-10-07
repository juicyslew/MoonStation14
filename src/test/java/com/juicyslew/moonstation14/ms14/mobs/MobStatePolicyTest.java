package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent;
import com.juicyslew.moonstation14.ms14.character.components.CrawlerComponent;
import com.juicyslew.moonstation14.ms14.character.components.PullableComponent;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MobStatePolicyTest {
    private JsonObject resource(String id) throws IOException {
        String path = "data/moonstation14/moonstation14/character/" + id + ".json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void bundledResolvedCatalogDrivesPoliciesAndCrawlerValues() throws IOException {
        var pigId = ResourceLocation.parse("moonstation14:pig");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, resource("human"), pigId, resource("pig")));
        var catalog = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (var entry : List.of(Map.entry(ModCharacters.HUMAN_ID, true), Map.entry(pigId, false))) {
            var data = catalog.get(entry.getKey());
            var policy = MobStatePolicy.from(data).orElseThrow();
            assertEquals(MobStateComponent.State.ALIVE, MobStateReducer.damageOrHealing(new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 0, policy.states(), policy.thresholds()).state().value());
            assertEquals(MobStateComponent.State.CRITICAL, MobStateReducer.damageOrHealing(new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 100, policy.states(), policy.thresholds()).state().value());
            assertEquals(MobStateComponent.State.DEAD, MobStateReducer.damageOrHealing(new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 200, policy.states(), policy.thresholds()).state().value());
            var crawler = data.component(CrawlerComponent.class);
            assertEquals(entry.getValue(), crawler.isPresent());
            if (crawler.isPresent()) {
                assertEquals(.4, crawler.get().speedModifier()); assertEquals(.5, crawler.get().defaultKnockedDurationSeconds());
                assertEquals(2, crawler.get().standTimeSeconds()); assertEquals(5, crawler.get().damageThreshold());
                assertEquals(1, crawler.get().frictionModifier());
            }
        }
    }

    @Test void pigResolvedThresholdsDriveTheSameReducerUsedByTypedDamageRuntime() throws IOException {
        var pigId = ResourceLocation.parse("moonstation14:pig");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(pigId, resource("pig")));
        var pig = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(pigId);
        var policy = MobStatePolicy.from(pig).orElseThrow();
        assertEquals(java.util.Set.of(MobStateComponent.State.ALIVE, MobStateComponent.State.CRITICAL,
                MobStateComponent.State.DEAD), policy.states().allowedStates());
        assertEquals(Map.of(0d, MobStateComponent.State.ALIVE, 100d, MobStateComponent.State.CRITICAL,
                200d, MobStateComponent.State.DEAD), policy.thresholds().thresholds());
        for (var sample : List.of(Map.entry(99d, MobStateComponent.State.ALIVE),
                Map.entry(100d, MobStateComponent.State.CRITICAL),
                Map.entry(199d, MobStateComponent.State.CRITICAL),
                Map.entry(200d, MobStateComponent.State.DEAD))) {
            assertEquals(sample.getValue(), MobStateReducer.damageOrHealing(
                    new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), sample.getKey(),
                    policy.states(), policy.thresholds()).state().value());
        }
        // A direct jump to the highest crossed threshold is intentional; no intermediate state is required.
        assertEquals(MobStateComponent.State.DEAD, MobStateReducer.damageOrHealing(
                new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 200,
                policy.states(), policy.thresholds()).state().value());

        // Prove that runtime's shared reducer honors resolved values, not pig-specific 100/200 constants.
        var derivedId = ResourceLocation.parse("moonstation14:test_derived_pig");
        JsonObject derived = JsonParser.parseString("""
                {"parent":"pig","host_entity_types":["test:derived_pig"],"components":[
                  {"type":"MobState","allowed_states":["ALIVE","DEAD"]},
                  {"type":"MobThresholds","thresholds":{"0":"ALIVE","60":"DEAD"}}]}
                """).getAsJsonObject();
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(pigId, resource("pig"), derivedId, derived));
        var derivedData = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(derivedId);
        assertTrue(derivedData.component(PullableComponent.class).isPresent(), "the derived fixture retains inherited pig components");
        var derivedPolicy = MobStatePolicy.from(derivedData).orElseThrow();
        assertEquals(MobStateComponent.State.ALIVE, MobStateReducer.damageOrHealing(
                new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 59,
                derivedPolicy.states(), derivedPolicy.thresholds()).state().value());
        assertEquals(MobStateComponent.State.DEAD, MobStateReducer.damageOrHealing(
                new MobStateReducer.State(MobStateComponent.State.ALIVE, 0), 60,
                derivedPolicy.states(), derivedPolicy.thresholds()).state().value());
        assertFalse(derivedPolicy.states().allowedStates().contains(MobStateComponent.State.CRITICAL));
    }

    @Test void inheritanceMergesMobComponentsAndDisallowedDeadIsUnchanged() {
        var parentId = ResourceLocation.parse("moonstation14:mob_parent");
        var childId = ResourceLocation.parse("moonstation14:mob_child");
        JsonObject parent = JsonParser.parseString("""
                {"components":[{"type":"MobState","allowed_states":["ALIVE","CRITICAL","DEAD"]},{"type":"MobThresholds","thresholds":{"0":"ALIVE","10":"CRITICAL","20":"DEAD"}}]}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"parent":"mob_parent","components":[{"type":"MobState","allowed_states":["ALIVE"]},{"type":"MobThresholds","thresholds":{"0":"ALIVE","10":"DEAD"}}]}
                """).getAsJsonObject();
        PrototypeManager manager = new PrototypeManager(); manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));
        var catalog = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        var parentPolicy = MobStatePolicy.from(catalog.get(parentId)).orElseThrow();
        assertEquals(MobStateComponent.State.CRITICAL, parentPolicy.thresholds().thresholds().get(10d));
        assertEquals(MobStateComponent.State.DEAD, parentPolicy.thresholds().thresholds().get(20d));
        var policy = MobStatePolicy.from(catalog.get(childId)).orElseThrow();
        assertEquals(java.util.Set.of(MobStateComponent.State.ALIVE), policy.states().allowedStates());
        assertFalse(policy.thresholds().thresholds().containsKey(20d));
        assertEquals(MobStateComponent.State.DEAD, policy.thresholds().thresholds().get(10d));
        var current = new MobStateReducer.State(MobStateComponent.State.ALIVE, 0);
        var result = MobStateReducer.damageOrHealing(current, 10, policy.states(), policy.thresholds());
        assertEquals(current, result.state()); assertFalse(result.changed());
    }

    @Test void schemaAuditMatchesCaseInsensitiveStateCodec() {
        var raw = JsonParser.parseString("{\"components\":[{\"type\":\"MobState\",\"allowed_states\":[\"alive\"]},{\"type\":\"MobThresholds\",\"thresholds\":{\"0\":\"alive\"}}]}").getAsJsonObject();
        CharacterSchemaAudit.audit(raw);
        assertEquals(MobStateComponent.State.ALIVE, MobStateComponent.STATE_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"alive\"")).getOrThrow());
    }

    @Test void thresholdCodecReadsAndWritesNumericObjectKeys() {
        var input = JsonParser.parseString("{\"type\":\"MobThresholds\",\"thresholds\":{\"0\":\"ALIVE\",\"100\":\"CRITICAL\",\"200\":\"DEAD\"}}");
        var decoded = MobThresholdsComponent.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(MobStateComponent.State.CRITICAL, decoded.thresholds().get(100d));
        var encoded = MobThresholdsComponent.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
        assertEquals("DEAD", encoded.getAsJsonObject().getAsJsonObject("thresholds").get("200.0").getAsString());
        assertTrue(MobThresholdsComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\":\"MobThresholds\",\"thresholds\":{\"0\":\"ALIVE\",\"NaN\":\"DEAD\"}}")).error().isPresent());
        assertTrue(MobThresholdsComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\":\"MobThresholds\",\"thresholds\":{\"0\":\"ALIVE\",\"-1\":\"DEAD\"}}" )).error().isPresent());
        assertTrue(MobThresholdsComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\":\"MobThresholds\",\"thresholds\":{}}" )).error().isPresent());
        assertTrue(MobThresholdsComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\":\"MobThresholds\",\"thresholds\":{\"10\":\"DEAD\"}}" )).error().isPresent());
    }

    @Test void stateMarkerWithoutThresholdsIsValidButDoesNotEnablePolicy() {
        var character = new CharacterData(List.of(), List.of(
                new MobStateComponent(MobStateComponent.DEFAULT_ALLOWED_STATES)));
        assertTrue(MobStatePolicy.from(character).isEmpty());
    }

    @Test void attachmentDefaultsAliveAndRoundTripsRevision() {
        assertEquals(MobStateComponent.State.ALIVE, MobStateData.DEFAULT.state());
        var value = new MobStateData(MobStateComponent.State.DEAD, 7);
        assertEquals(value, MobStateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                MobStateData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()).getOrThrow());
    }
}
