package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerR6MachineBindingsTest {
    private static JsonObject json(String path) throws Exception {
        try (var stream = PowerR6MachineBindingsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void allTenMachineBlocksAndItemsResolveToAcceptedModels() throws Exception {
        var ids = List.of("pa_control_box", "pa_end_cap", "pa_fuel_chamber", "pa_power_box",
                "pa_emitter_port", "pa_emitter_fore", "pa_emitter_starboard", "emitter",
                "field_generator", "singularity_generator");
        for (String id : ids) {
            JsonObject item = json("assets/moonstation14/models/item/" + id + ".json");
            assertTrue(item.has("parent"), id + " item parent");
            if (id.startsWith("pa_")) assertTrue(item.get("parent").getAsString().contains("reactor_bindings/" + id));
        }
        assertEquals("moonstation14:block/power_collection/generation_singularity_generator/icon",
                json("assets/moonstation14/models/block/reactor_bindings/singularity_generator.json").get("parent").getAsString());
        assertEquals("moonstation14:block/reactor_bindings/field_generator/default",
                json("assets/moonstation14/models/block/reactor_bindings/field_generator.json").get("parent").getAsString());
        assertEquals("moonstation14:block/power_collection/generation_singularity_emitter/beam",
                json("assets/moonstation14/models/block/reactor_bindings/emitter_on.json").get("parent").getAsString());
    }

    @Test void r8ImportManifestAndLiveFieldGeneratorBindingsAreApprovedAndComplete() throws Exception {
        var item=json("assets/moonstation14/models/item/field_generator.json");
        assertEquals("moonstation14:block/reactor_bindings/field_generator/default",item.get("parent").getAsString());
        var provenance=json("assets/moonstation14/provenance/reactor_r8/generation_singularity_containment/import-manifest.json");
        assertEquals("APPROVED",provenance.getAsJsonObject("owner_approval").get("status").getAsString());
        assertEquals("2026-10-12",provenance.getAsJsonObject("owner_approval").get("recorded").getAsString());
        assertFalse(provenance.get("external_candidate_acceptance").getAsBoolean());
        assertEquals(8,provenance.getAsJsonArray("imported").size());
        for(var entry:provenance.getAsJsonArray("imported")) {
            String path=entry.getAsJsonObject().get("path").getAsString();
            assertTrue(path.startsWith("textures/block/reactor_r8/generation_singularity_containment/"));
            try(var stream=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/"+path)) {
                assertNotNull(stream,path);
                assertEquals(entry.getAsJsonObject().get("sha256").getAsString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())),path);
            }
        }
        assertNotNull(getClass().getClassLoader().getResource("assets/moonstation14/provenance/reactor_r8/generation_singularity_containment/ATTRIBUTION.md"));
        for(String tier:List.of("field0_lobes","a1","a2","a3")) {
            var model=json("assets/moonstation14/models/block/reactor_bindings/field_generator/"+tier+".json");
            assertTrue(model.getAsJsonObject("textures").get("atlas").getAsString().contains("block/reactor_r8/generation_singularity_containment/"));
        }
    }

    @Test void blockstatesCoverHorizontalPaAndEveryEmitterAxis() throws Exception {
        for (String id : List.of("pa_control_box", "pa_end_cap", "pa_fuel_chamber", "pa_power_box",
                "pa_emitter_port", "pa_emitter_fore", "pa_emitter_starboard")) {
            JsonObject variants = json("assets/moonstation14/blockstates/" + id + ".json").getAsJsonObject("variants");
            for (String facing : List.of("north", "east", "south", "west"))
                assertTrue(variants.has("facing=" + facing + ",visual=completed"), id + " " + facing);
        }
        JsonObject variants = json("assets/moonstation14/blockstates/emitter.json").getAsJsonObject("variants");
        for (String facing : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject off = variants.getAsJsonObject("facing=" + facing + ",visual=default");
            assertNotNull(off, facing);
            assertTrue(off.has("model"));
            if (facing.equals("up")) assertEquals(90,off.get("x").getAsInt(),"north muzzle rotates to +Y");
            if (facing.equals("down")) assertEquals(270,off.get("x").getAsInt(),"north muzzle rotates to -Y");
            if (facing.equals("east")) assertEquals(90,off.get("y").getAsInt());
            if (facing.equals("south")) assertEquals(180,off.get("y").getAsInt());
            if (facing.equals("west")) assertEquals(270,off.get("y").getAsInt());
        }
    }

    @Test void fieldIndicatorsAreIndependentMultipartLayers() throws Exception {
        JsonObject state=json("assets/moonstation14/blockstates/field_generator.json");
        var parts=state.getAsJsonArray("multipart");
        assertEquals(11,parts.size()); // shell + three power + three field + three zero/inactive physical overlays + connected light
        assertEquals("moonstation14:block/reactor_bindings/field_generator/p1",
                parts.get(2).getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        assertEquals("moonstation14:block/reactor_bindings/field_generator/a3",
                parts.get(8).getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        assertEquals("moonstation14:block/reactor_bindings/field_generator/active",
                parts.get(10).getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        // Integration-only split preserves the named accepted elements and their source UVs.
        for (String layer : List.of("base", "p1", "p3", "p6", "a1", "a2", "a3", "active")) {
            JsonObject model=json("assets/moonstation14/models/block/reactor_bindings/field_generator/"+layer+".json");
            assertTrue(model.has("elements"),layer);
            assertTrue(model.has("textures"),layer);
        }
    }
}
