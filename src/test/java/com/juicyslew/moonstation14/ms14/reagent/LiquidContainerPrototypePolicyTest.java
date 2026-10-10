package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.item.custom.BeakerItem;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerData;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerMergeStrategy;
import com.juicyslew.moonstation14.ms14.jug.JugData;
import com.juicyslew.moonstation14.ms14.jug.JugMergeStrategy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LiquidContainerPrototypePolicyTest {
    private static JsonObject resource(String path) throws Exception {
        try (var stream = LiquidContainerPrototypePolicyTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void jugPoliciesResolveInheritanceAndFeedSameRuntimeProjections() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(id("jug_base"), resource("/data/moonstation14/moonstation14/jug/jug_base.json"));
        raw.put(id("jug"), resource("/data/moonstation14/moonstation14/jug/jug.json"));
        raw.put(id("policy-child"), json("""
                {"id":"policy-child","parent":"jug","solutionContainer":{"solutionName":"customDrink"},
                 "refillableSolution":{"maxRefill":12.5},
                 "solutionTransfer":{"transferAmount":2.5},
                 "drinkSolution":{"consumeAmount":1.25}}
                """));
        raw.put(id("policy-disabled"), json("""
                {"id":"policy-disabled","parent":"jug","refillableSolution":{"enabled":false},
                 "solutionTransfer":{"enabled":false},"drinkSolution":{"enabled":false}}
                """));
        raw.put(id("policy-transfer-inherited"), json("""
                {"id":"policy-transfer-inherited","parent":"jug","solutionTransfer":{"canSend":false}}
                """));
        raw.put(id("policy-absent"), json("{\"id\":\"policy-absent\"}"));
        var resolved = PrototypeResolver.resolve(raw, new JugMergeStrategy());
        var jug = decodeJug(resolved.asMap().get(id("jug")));
        assertTrue(jug.refillableSolution().enabled());
        assertEquals("drink", jug.refillableSolution().solutionName());
        assertEquals(5f, jug.solutionTransfer().transferAmount());
        assertTrue(jug.solutionTransfer().canSend());
        assertTrue(jug.solutionTransfer().canReceive());
        assertEquals(5f, jug.drinkSolution().consumeAmount());
        assertEquals(20, jug.drinkSolution().personalDelayTicks());
        assertEquals(jug.refillableSolution(), JugItem.refillableSolutionFromResolved(jug));
        assertEquals(jug.solutionTransfer(), JugItem.solutionTransferFromResolved(jug));
        assertEquals(jug.drinkSolution(), JugItem.drinkSolutionFromResolved(jug));

        var child = decodeJug(resolved.asMap().get(id("policy-child")));
        assertEquals("customDrink", child.solutionContainer().solutionName());
        assertEquals("drink", child.drinkSolution().solutionName(), "logical policy names inherit independently from the primary container");
        assertEquals(12.5f, child.refillableSolution().maxRefill().orElseThrow());
        assertEquals(2.5f, child.solutionTransfer().transferAmount(), "explicit nested transfer amount overrides the inherited component value");
        assertEquals(1.25f, child.drinkSolution().consumeAmount());

        var transferInherited = decodeJug(resolved.asMap().get(id("policy-transfer-inherited")));
        assertEquals(5f, transferInherited.solutionTransfer().transferAmount(), "omitted nested transfer amount keeps inherited value");

        var disabled = decodeJug(resolved.asMap().get(id("policy-disabled")));
        assertFalse(disabled.refillableSolution().enabled());
        assertFalse(disabled.solutionTransfer().enabled());
        assertFalse(disabled.drinkSolution().enabled());
        var absent = decodeJug(resolved.asMap().get(id("policy-absent")));
        assertFalse(absent.refillableSolution().enabled());
        assertFalse(absent.solutionTransfer().enabled());
        assertFalse(absent.drinkSolution().enabled());
        assertEquals("food", absent.drinkSolution().solutionName(), "Edible's upstream default logical solution is food even when disabled locally");
    }

    @Test void beakerPoliciesResolveAndUseBeakerGetterProjections() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(id("beaker_base"), resource("/data/moonstation14/moonstation14/beaker/beaker_base.json"));
        raw.put(id("beaker"), resource("/data/moonstation14/moonstation14/beaker/beaker.json"));
        raw.put(id("beaker-policy-child"), json("""
                {"id":"beaker-policy-child","parent":"beaker","solutionTransfer":{"canSend":false},
                 "drinkSolution":{"solutionName":"customDrink","personalDelayTicks":7}}
                """));
        raw.put(id("beaker-policy-disabled"), json("""
                {"id":"beaker-policy-disabled","parent":"beaker","refillableSolution":{"enabled":false},
                 "solutionTransfer":{"enabled":false},"drinkSolution":{"enabled":false}}
                """));
        var resolved = PrototypeResolver.resolve(raw, new BeakerMergeStrategy());
        var beaker = decodeBeaker(resolved.asMap().get(id("beaker")));
        assertEquals("beaker", beaker.refillableSolution().solutionName());
        assertEquals("beaker", beaker.drinkSolution().solutionName());
        assertEquals(5f, beaker.solutionTransfer().transferAmount());
        assertEquals(beaker.refillableSolution(), BeakerItem.refillableSolutionFromResolved(beaker));
        assertEquals(beaker.solutionTransfer(), BeakerItem.solutionTransferFromResolved(beaker));
        assertEquals(beaker.drinkSolution(), BeakerItem.drinkSolutionFromResolved(beaker));
        var child = decodeBeaker(resolved.asMap().get(id("beaker-policy-child")));
        assertTrue(child.solutionTransfer().enabled());
        assertFalse(child.solutionTransfer().canSend());
        assertTrue(child.solutionTransfer().canReceive());
        assertEquals("customDrink", child.drinkSolution().solutionName());
        assertEquals(7, child.drinkSolution().personalDelayTicks());
        var disabled = decodeBeaker(resolved.asMap().get(id("beaker-policy-disabled")));
        assertFalse(disabled.refillableSolution().enabled());
        assertFalse(disabled.solutionTransfer().enabled());
        assertFalse(disabled.drinkSolution().enabled());
        assertEquals("beaker", disabled.drinkSolution().solutionName(), "child override inherits BaseBeaker's explicit food solution name");
    }

    @Test void genericTraitDoesNotInferCapabilitiesFromContainerShape() {
        IReagentTrait generic = () -> 200f;
        assertFalse(generic.refillableSolution(null, null).enabled());
        assertFalse(generic.solutionTransfer(null, null).enabled());
        assertFalse(generic.drinkSolution(null, null).enabled());
    }

    @Test void missingDrinkSolutionNameUsesEdibleFoodComponentDefault() {
        var policy = DrinkSolutionPolicy.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                json("{\"enabled\":false}")).getOrThrow();
        assertFalse(policy.enabled());
        assertEquals("food", policy.solutionName());
        assertEquals(5f, policy.consumeAmount());
        assertEquals(20, policy.personalDelayTicks());
    }

    private static JugData decodeJug(JsonObject json) {
        return JugData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
    }
    private static BeakerData decodeBeaker(JsonObject json) {
        return BeakerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
