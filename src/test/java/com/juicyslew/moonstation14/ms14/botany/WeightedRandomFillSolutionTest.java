package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Resource mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, randomChemicals.yml. */
class WeightedRandomFillSolutionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static Map<ResourceLocation, JsonObject> loadResources() {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : new String[]{"randompickbotanyreagent", "evilrandomfillsolution"}) {
            String path = "data/moonstation14/moonstation14/weighted_random_fill_solution/" + name + ".json";
            try (var stream = WeightedRandomFillSolutionTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                raw.put(ModWeightedRandomFillSolutions.solutionId(name), JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        return raw;
    }
    @Test void resourcesResolveWithExactSourceGroupsAndMercuryRoutes() {
        PrototypeRuntime.serverManager().reload(ModWeightedRandomFillSolutions.TYPE, loadResources());
        var normal = ModWeightedRandomFillSolutions.serverCatalog().get(id("randompickbotanyreagent"));
        var evil = ModWeightedRandomFillSolutions.serverCatalog().get(id("evilrandomfillsolution"));
        assertEquals(java.util.List.of(6,21,25,34,18), normal.fills().stream().map(g -> g.reagents().size()).toList());
        assertEquals(java.util.List.of(5f,5f,5f,10f,10f), normal.fills().stream().map(WeightedRandomFillSolution.Group::quantity).toList());
        assertEquals(java.util.List.of(.5f,1f,2f,3f,3.5f), normal.fills().stream().map(WeightedRandomFillSolution.Group::weight).toList());
        assertEquals(java.util.List.of(3,1,3), evil.fills().stream().map(g -> g.reagents().size()).toList());
        assertEquals(2, normal.fills().stream().filter(g -> g.reagents().contains(id("mercury"))).count());
        assertEquals(5f, normal.fills().get(2).quantity());
        assertEquals(10f, normal.fills().get(4).quantity());
        assertSame(normal, PrototypeRuntime.serverWeightedRandomFillSolutions().get(id("randompickbotanyreagent")));
    }
    @Test void everySavedNormalAndEvilRouteResolvesToCanonicalLocalReagent() throws Exception {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE);
        manager.reload(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE,
                com.juicyslew.moonstation14.ms14.reagent.ReagentResourceSmokeTest.loadResources());
        var reagents = manager.snapshot(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE);
        PrototypeRuntime.serverManager().reload(ModWeightedRandomFillSolutions.TYPE, loadResources());
        int routes = 0;
        for (String tableName : new String[]{"randompickbotanyreagent", "evilrandomfillsolution"}) {
            var table = ModWeightedRandomFillSolutions.serverCatalog().get(id(tableName));
            for (var group : table.fills()) for (var reagent : group.reagents()) {
                routes++;
                assertNotNull(reagents.get(reagent), tableName + " route " + reagent);
            }
        }
        assertEquals(111, routes);
        var descriptor = reagents.get(id("vestine")).plantMetabolism().stream()
                .filter(com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData.PlantMutateChemicals.class::isInstance)
                .map(com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData.PlantMutateChemicals.class::cast)
                .findFirst().orElseThrow();
        assertEquals(id("evilrandomfillsolution"), ModWeightedRandomFillSolutions.canonicalId(descriptor.randomPickBotanyReagent()));
    }
    @Test void choosesWeightedGroupThenUniformOrderedMemberAtUpstreamBoundaries() {
        var solution = new WeightedRandomFillSolution(java.util.List.of(
                new WeightedRandomFillSolution.Group(5, 1, java.util.List.of(id("a"), id("b"))),
                new WeightedRandomFillSolution.Group(10, 3, java.util.List.of(id("c"), id("d")))));
        final float[] initialRolls = {0.25f, 0.25f}; AtomicInteger i = new AtomicInteger();
        var selected = solution.select(() -> initialRolls[i.getAndIncrement()]);
        assertEquals(id("a"), selected.reagent()); assertEquals(5f, selected.quantity());
        final float[] boundary = {0.25f, 0.75f}; // exact group boundary belongs to preceding group (upstream >=)
        i.set(0);
        selected = solution.select(() -> boundary[i.getAndIncrement()]);
        assertEquals(id("b"), selected.reagent());
        // Source integer-boundary quirk: index+1 < reagentRoll leaves the exact 1.0 roll at index 0.
        final float[] integerMemberBoundary = {0.25f, 0.5f}; i.set(0);
        selected = solution.select(() -> integerMemberBoundary[i.getAndIncrement()]);
        assertEquals(id("a"), selected.reagent());
        // Same group, uniform member endpoint routes to the final ordered reagent.
        final float[] within = {0.5f, 0.999f}; i.set(0);
        selected = solution.select(() -> within[i.getAndIncrement()]);
        assertEquals(id("d"), selected.reagent());
    }
    @Test void malformedTablesRejectedAndChildFillListReplacesParentInOrder() {
        var raw = loadResources();
        JsonObject parent = new JsonObject(); parent.addProperty("abstract", true);
        parent.add("fills", raw.get(id("randompickbotanyreagent")).getAsJsonArray("fills").deepCopy());
        JsonObject child = new JsonObject(); child.addProperty("parent", "base");
        JsonObject replacement = new JsonObject(); replacement.addProperty("quantity", 2); replacement.addProperty("weight", 1);
        var ids = new com.google.gson.JsonArray(); ids.add("moonstation14:z"); ids.add("moonstation14:y");
        replacement.add("reagents", ids); var fills = new com.google.gson.JsonArray(); fills.add(replacement); child.add("fills", fills);
        raw.put(id("base"), parent); raw.put(id("child"), child);
        PrototypeRuntime.serverManager().reload(ModWeightedRandomFillSolutions.TYPE, raw);
        var resolved = ModWeightedRandomFillSolutions.serverCatalog().get(id("child"));
        assertEquals(java.util.List.of(id("z"), id("y")), resolved.fills().get(0).reagents());
        assertEquals(1, resolved.fills().size());
        var malformed = loadResources();
        malformed.get(id("evilrandomfillsolution")).getAsJsonArray("fills").get(0).getAsJsonObject().addProperty("weight", -1);
        assertThrows(PrototypeLoadException.class, () -> PrototypeRuntime.serverManager().reload(ModWeightedRandomFillSolutions.TYPE, malformed));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomFillSolution(java.util.List.of(
                new WeightedRandomFillSolution.Group(1, 0, java.util.List.of(id("x"))))));
    }
}
