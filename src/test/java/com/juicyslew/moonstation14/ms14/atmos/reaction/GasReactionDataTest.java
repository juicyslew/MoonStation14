package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasReactionDataTest {
    private static final List<String> IDS = List.of("frezon_production", "ammonia_oxygen", "frezon_coolant",
            "n2o_decomposition", "tritium_fire", "plasma_fire");
    private static final int[] PRIORITIES = {2, 2, 1, 0, -1, -2};
    private static final String[] EFFECTS = {"FrezonProductionReaction", "AmmoniaOxygenReaction",
            "FrezonCoolantReaction", "N2ODecompositionReaction", "TritiumFireReaction", "PlasmaFireReaction"};

    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
        try (var stream = GasReactionDataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static Map<ResourceLocation, JsonObject> bundled() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String id : IDS) raw.put(ResourceLocation.parse("moonstation14:" + id), resource(id));
        return raw;
    }

    @Test void packagedSixLoadThroughManagerAndRoundTrip() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, bundled());
        assertEquals(6, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE).keys().size());
        for (int i = 0; i < IDS.size(); i++) {
            var reaction = manager.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(ResourceLocation.parse("moonstation14:" + IDS.get(i)));
            assertEquals(PRIORITIES[i], reaction.priority());
            assertEquals(EFFECTS[i], reaction.effects().getFirst().type().id());
            assertTrue(reaction.minimumRequirements().values().stream().allMatch(n -> n == 0.01));
            assertEquals(reaction, GasReactionData.CODEC.parse(JsonOps.INSTANCE,
                    GasReactionData.CODEC.encodeStart(JsonOps.INSTANCE, reaction).getOrThrow()).getOrThrow());
        }
        assertEquals(73.15, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE)
                .get(ResourceLocation.parse("moonstation14:frezon_production")).maximumTemperature());
        assertEquals(2.7, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE)
                .get(ResourceLocation.parse("moonstation14:frezon_production")).minimumTemperature());
        assertEquals(0.01, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE)
                .get(ResourceLocation.parse("moonstation14:n2o_decomposition")).minimumRequirements().get(GasType.NITROUS_OXIDE));
        assertEquals(0, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE)
                .get(ResourceLocation.parse("moonstation14:plasma_fire")).minimumEnergy());
        assertThrows(UnsupportedOperationException.class, () -> manager.snapshot(ModGasReactions.GAS_REACTION_TYPE)
                .get(ResourceLocation.parse("moonstation14:plasma_fire")).effects().clear());
    }

    @Test void runtimeRegistersIndependentServerAndClientCatalogs() {
        assertTrue(PrototypeRuntime.serverManager().registeredTypes().contains(ModGasReactions.GAS_REACTION_TYPE));
        assertTrue(PrototypeRuntime.clientManager().registeredTypes().contains(ModGasReactions.GAS_REACTION_TYPE));
        assertNotSame(PrototypeRuntime.serverGasReactions(), PrototypeRuntime.clientGasReactions());
        assertEquals("moonstation14/gas_reaction", ModGasReactions.GAS_REACTION_TYPE.resourceDirectory());
        assertEquals(ResourceLocation.parse("moonstation14:gas_reaction"), ModGasReactions.GAS_REACTION_TYPE.typeId());
    }

    @Test void malformedFieldsAndDiscriminatorsFailClosed() {
        String base = resource("plasma_fire").toString();
        String[] invalid = {
                base.replace("\"priority\":-2", "\"priority\":1001"),
                base.replace("\"priority\":-2", "\"priority\":-1001"),
                base.replace("\"priority\":-2", "\"priority\":null"),
                base.replace("\"priority\":-2", "\"priority\":1.5"),
                base.replace("\"minimumTemperature\":373.149", "\"minimumTemperature\":-1"),
                base.replace("\"minimumTemperature\":373.149", "\"minimumTemperature\":1e999"),
                base.replace("\"minimumTemperature\":373.149", "\"minimumTemperature\":null"),
                base.replace("\"minimumTemperature\":373.149", "\"minimumTemperature\":400,\"maximumTemperature\":399"),
                base.replace("\"minimumTemperature\":373.149", "\"minimumEnergy\":-1"),
                base.replace("\"oxygen\":0.01", "\"oxygen\":-0.01"),
                base.replace("\"oxygen\":0.01", "\"Oxygen\":0.01"),
                base.replace("\"oxygen\":0.01", "\"unobtainium\":0.01"),
                base.replace("\"oxygen\":0.01", "\"oxygen\":null"),
                base.replace("\"PlasmaFireReaction\"", "\"WaterVaporReaction\""),
                base.replace("\"PlasmaFireReaction\"", "\"PlasmaFireReaction\",\"extra\":1"),
                base.replace("[{\"type\":\"PlasmaFireReaction\"}]", "[{\"type\":\"PlasmaFireReaction\"},{\"type\":\"PlasmaFireReaction\"}]"),
                base.replace("\"priority\":-2", "\"priority\":-2,\"extra\":1")
        };
        for (String text : invalid) {
            assertNotEquals(base, text, "test mutation must change JSON");
            assertFalse(GasReactionData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).isSuccess(), text);
        }
    }

    @Test void rejectedReloadPreservesPublishedSnapshot() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        ResourceLocation id = ResourceLocation.parse("moonstation14:plasma_fire");
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, bundled());
        var before = manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
        JsonObject bad = resource("plasma_fire");
        bad.addProperty("priority", 1001);
        Map<ResourceLocation, JsonObject> candidate = bundled();
        candidate.put(id, bad);
        assertThrows(RuntimeException.class, () -> manager.reload(ModGasReactions.GAS_REACTION_TYPE, candidate));
        assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));
    }

    @Test void stagedAndReloadedCandidatesRequireSixCorrectIdentitiesWithoutRejectingExtras() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        var baseline = bundled();
        ResourceLocation extra = ResourceLocation.parse("thirdparty:custom_fire");
        baseline.put(extra, resource("plasma_fire"));
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, baseline);
        var before = manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
        assertTrue(before.contains(extra));

        for (String name : IDS) {
            ResourceLocation id = ResourceLocation.parse("moonstation14:" + name);
            Map<ResourceLocation, JsonObject> missing = bundled();
            missing.remove(id);
            var error = assertThrows(RuntimeException.class,
                    () -> manager.stage(Map.of(ModGasReactions.GAS_REACTION_TYPE, missing)));
            assertTrue(error.getMessage().contains(id.toString()), error.getMessage());
            assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));
            assertFalse(manager.hasStagedReload());
        }

        ResourceLocation id = ResourceLocation.parse("moonstation14:plasma_fire");
        for (String field : List.of("priority", "effects")) {
            Map<ResourceLocation, JsonObject> overridden = bundled();
            JsonObject changed = resource("plasma_fire");
            if (field.equals("priority")) changed.addProperty("priority", 3);
            else changed.add("effects", resource("tritium_fire").get("effects"));
            overridden.put(id, changed);
            var error = assertThrows(RuntimeException.class,
                    () -> manager.reload(ModGasReactions.GAS_REACTION_TYPE, overridden));
            assertTrue(error.getMessage().contains(id.toString()), error.getMessage());
            assertTrue(error.getMessage().contains("PlasmaFireReaction"), error.getMessage());
            assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));
        }

        Map<ResourceLocation, JsonObject> multiEffect = bundled();
        JsonObject changed = resource("plasma_fire");
        changed.getAsJsonArray("effects").add(resource("tritium_fire").getAsJsonArray("effects").get(0));
        multiEffect.put(id, changed);
        assertThrows(RuntimeException.class, () -> manager.reload(ModGasReactions.GAS_REACTION_TYPE, multiEffect));
        assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));

        Map<ResourceLocation, JsonObject> wire = new LinkedHashMap<>(manager.encodePublishedCatalogs()
                .get(ModGasReactions.GAS_REACTION_TYPE.typeId()));
        wire.remove(id);
        var encodedError = assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(
                Map.of(ModGasReactions.GAS_REACTION_TYPE.typeId(), wire)));
        assertTrue(encodedError.getMessage().contains(id.toString()), encodedError.getMessage());
        assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));

        wire.put(id, resource("tritium_fire"));
        var misboundError = assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(
                Map.of(ModGasReactions.GAS_REACTION_TYPE.typeId(), wire)));
        assertTrue(misboundError.getMessage().contains("PlasmaFireReaction"), misboundError.getMessage());
        assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));

        Map<ResourceLocation, GasReactionData> decoded = new LinkedHashMap<>(before.asMap());
        decoded.remove(id);
        assertThrows(RuntimeException.class, () -> manager.publishDecoded(ModGasReactions.GAS_REACTION_TYPE, decoded));
        assertSame(before, manager.snapshot(ModGasReactions.GAS_REACTION_TYPE));
    }
}
