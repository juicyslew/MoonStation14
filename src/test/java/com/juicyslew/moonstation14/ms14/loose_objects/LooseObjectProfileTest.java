package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LooseObjectProfileTest {
    @Test void loadsJsonProfilesAndResolvesInheritance() throws Exception {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        Path directory = projectPath("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", file.getFileName().toString().replaceFirst("\\.json$", "")), json);
            }
        }
        var resolved = PrototypeResolver.resolve(raw);
        LooseObjectProfile generic = decode(resolved.get(ModLooseObjectProfiles.GENERIC_ID));
        assertNull(generic.slippery(), "absent slippery composition remains disabled");
        LooseObjectProfile jug = decode(resolved.get(ModLooseObjectProfiles.JUG_ID));
        LooseObjectProfile grenade = decode(resolved.get(ModLooseObjectProfiles.GRENADE_ID));
        LooseObjectProfile blockModel = decode(resolved.get(LooseObjectProfile.BLOCK_MODEL_ID));
        LooseObjectProfile canister = decode(resolved.get(LooseObjectProfile.GAS_CANISTER_ID));
        LooseObjectProfile bananaPeel = decode(resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "trash_banana_peel")));
        LooseObjectProfile mimanaPeel = decode(resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "trash_mimana_peel")));
        assertEquals(.75f, generic.throwSpeed());
        assertEquals(generic.throwSpeed(), jug.throwSpeed());
        assertEquals(generic.throwSpeed(), grenade.throwSpeed());
        double horizontalTravel = 0;
        double horizontalSpeed = generic.throwSpeed();
        for (int tick = 0; tick < 4; tick++) {
            horizontalTravel += horizontalSpeed;
            horizontalSpeed *= .995 * .995; // airborne damping used by LooseItemEntity
        }
        assertTrue(horizontalTravel > .55 * 4, "direct horizontal air travel exceeds the old-speed baseline");
        assertEquals(8f, generic.throwRange());
        assertEquals(.1f, generic.minimumThrowRange());
        assertEquals(10, generic.throwCooldown());
        assertEquals("flat_sprite", generic.renderKind());
        assertEquals(.65f, generic.physicalWidth());
        assertEquals(.08f, generic.physicalHeight());
        assertEquals(.65f, generic.visualWidth());
        assertEquals(.65f, generic.visualHeight());
        assertEquals(1f, generic.modelScale());
        assertEquals(.8f, grenade.modelScale());
        assertEquals(.25f, grenade.modelFloorOffset());
        assertTrue(jug.breakOnImpact());
        assertEquals(.5f, jug.physicalWidth());
        assertEquals(.75f, jug.physicalHeight());
        assertEquals(generic.friction(), grenade.friction());
        assertEquals(.45f, grenade.physicalWidth());
        assertEquals("grenade", grenade.renderKind());
        assertEquals("block_model", blockModel.renderKind());
        assertEquals("block_model", bananaPeel.renderKind());
        assertEquals("block_model", mimanaPeel.renderKind());
        assertEquals(1.2f, bananaPeel.modelScale());
        assertEquals(.6f, bananaPeel.modelFloorOffset());
        assertEquals(bananaPeel.visual(), mimanaPeel.visual());
        assertEquals(.4f, bananaPeel.physicalWidth());
        assertEquals(bananaPeel.physicalWidth(), mimanaPeel.physicalWidth());
        assertEquals(bananaPeel.stepTrigger(), mimanaPeel.stepTrigger());
        assertEquals("moonstation14:ss14/effects/slip", bananaPeel.slippery().soundEvent().toString());
        assertEquals(bananaPeel.slippery().soundEvent(), mimanaPeel.slippery().soundEvent());
        assertEquals(0d, bananaPeel.slippery().soundVolumeDb());
        assertEquals(-100d, mimanaPeel.slippery().soundVolumeDb());
        assertEquals(.65f, blockModel.physicalHeight());
        assertEquals(.65f * 0 + .325f, blockModel.modelFloorOffset());
        assertEquals(.5f, canister.physicalWidth());
        assertEquals(1f, canister.physicalHeight());
        assertEquals(190f, canister.mass());
        assertEquals(blockModel.modelFloorOffset(), -.5f * blockModel.modelScale() + blockModel.modelFloorOffset() + .5f * blockModel.modelScale());
        assertNull(resolved.get(ResourceLocation.withDefaultNamespace("absent")));
        var catalog = new PrototypeCatalog<>(resolved.asMap().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> decode(entry.getValue()))));
        assertEquals(generic, ModLooseObjectProfiles.require(catalog, null));
        assertThrows(IllegalArgumentException.class, () -> ModLooseObjectProfiles.require(catalog,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "unknown")));
    }


    @Test void slipperyCompositionSurvivesPrototypeInheritanceAndFlatCodecRoundTrip() {
        JsonObject parent = JsonParser.parseString("""
                {"id":"moonstation14:slip_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},
                  "step_trigger":{"required_speed":4.25},"slippery":{"stun_seconds":0}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:slip_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:slip_parent","slippery":{"stun_seconds":1.25}}
                """).getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:slip_parent"), parent);
        raw.put(ResourceLocation.parse("moonstation14:slip_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        LooseObjectProfile profile = decode(resolved.get(ResourceLocation.parse("moonstation14:slip_child")));
        assertEquals(4.25d, profile.stepTrigger().requiredSpeed());
        assertEquals(1.25d, profile.slippery().stunSeconds());
        var encoded = LooseObjectProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("slippery"));
        assertFalse(encoded.getAsJsonObject().has("base"));
        assertEquals(profile, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void stepDamageCompositionDefaultsInheritsAndValidatesCanonicalDamageKeys() {
        JsonObject parent = JsonParser.parseString("""
                {"id":"moonstation14:step_damage_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,
                 "sprite_offset_x":0,"sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},
                 "step_trigger":{"required_speed":1},"preventable_step_trigger":true,
                 "step_damage":{"damage":{"piercing":4.5}}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:step_damage_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:step_damage_parent","step_damage":{"damage":{"piercing":4.5},"delete_source":true}}
                """).getAsJsonObject();
        JsonObject defaults = parent.deepCopy();
        defaults.addProperty("id", "moonstation14:step_damage_defaults");
        defaults.remove("step_damage");
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:step_damage_parent"), parent);
        raw.put(ResourceLocation.parse("moonstation14:step_damage_child"), child);
        raw.put(ResourceLocation.parse("moonstation14:step_damage_defaults"), defaults);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        LooseObjectProfile parentProfile = decode(resolved.get(ResourceLocation.parse("moonstation14:step_damage_parent")));
        LooseObjectProfile childProfile = decode(resolved.get(ResourceLocation.parse("moonstation14:step_damage_child")));
        assertEquals(Map.of("piercing", 4.5f), parentProfile.stepDamage().damage());
        assertFalse(parentProfile.stepDamage().deleteSource());
        assertEquals(Map.of("piercing", 4.5f), childProfile.stepDamage().damage());
        assertTrue(childProfile.stepDamage().deleteSource());
        assertEquals(parentProfile.stepTrigger(), childProfile.stepTrigger());
        assertTrue(childProfile.preventableStepTrigger());
        assertNull(decode(resolved.get(ResourceLocation.parse("moonstation14:step_damage_defaults"))).stepDamage());
        var encoded = LooseObjectProfile.CODEC.encodeStart(JsonOps.INSTANCE, childProfile).getOrThrow();
        assertEquals(childProfile, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        JsonObject invalid = parent.deepCopy();
        invalid.getAsJsonObject("step_damage").getAsJsonObject("damage").addProperty("not_real", 1f);
        assertThrows(IllegalArgumentException.class, () -> decode(invalid));
    }

    @Test void partialRootStepTriggerOverridePreservesInheritedFields() {
        JsonObject parent = JsonParser.parseString("""
                {"id":"moonstation14:trigger_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},
                 "step_trigger":{"intersection_ratio":0.2,"active":false,"required_speed":1,"ignore_weightless":false}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("{\"id\":\"moonstation14:trigger_child\",\"type\":\"moonstation14:loose_object_profile\",\"parent\":\"moonstation14:trigger_parent\",\"step_trigger\":{\"required_speed\":6.5}}").getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:trigger_parent"), parent);
        raw.put(ResourceLocation.parse("moonstation14:trigger_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var trigger = decode(resolved.get(ResourceLocation.parse("moonstation14:trigger_child"))).stepTrigger();
        assertEquals(.2d, trigger.intersectionRatio());
        assertFalse(trigger.active());
        assertEquals(6.5d, trigger.requiredSpeed());
    }

    @Test void preventableStepTriggerDefaultsAndInheritsWithChildOverride() {
        JsonObject parent = JsonParser.parseString("""
                {"id":"moonstation14:protection_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"preventable_step_trigger":true}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:protection_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:protection_parent","preventable_step_trigger":false}
                """).getAsJsonObject();
        JsonObject defaults = parent.deepCopy();
        defaults.addProperty("id", "moonstation14:protection_default");
        defaults.remove("preventable_step_trigger");
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:protection_parent"), parent);
        raw.put(ResourceLocation.parse("moonstation14:protection_child"), child);
        raw.put(ResourceLocation.parse("moonstation14:protection_default"), defaults);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        assertTrue(decode(resolved.get(ResourceLocation.parse("moonstation14:protection_parent"))).preventableStepTrigger());
        assertTrue(decode(resolved.get(ResourceLocation.parse("moonstation14:protection_child"))).preventableStepTrigger() == false);
        assertFalse(decode(resolved.get(ResourceLocation.parse("moonstation14:protection_default"))).preventableStepTrigger());
    }

    @Test void destructionOutputsDefaultAndChildArraysReplaceParentArrays() {
        JsonObject base = JsonParser.parseString("""
                {"id":"moonstation14:destroy_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"item_damage":{"allowed_groups":[],
                 "destruction_outputs":[{"output":"minecraft:stone","count":2}]}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:destroy_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:destroy_parent","item_damage":{"destruction_outputs":[{"output":"minecraft:dirt"}]}}
                """).getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:destroy_parent"), base);
        raw.put(ResourceLocation.parse("moonstation14:destroy_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var parent = decode(resolved.get(ResourceLocation.parse("moonstation14:destroy_parent")));
        var inherited = decode(resolved.get(ResourceLocation.parse("moonstation14:destroy_child")));
        assertEquals(2, parent.itemDamage().destructionOutputs().get(0).count());
        assertEquals(ResourceLocation.withDefaultNamespace("dirt"), inherited.itemDamage().destructionOutputs().get(0).output());
        assertEquals(1, inherited.itemDamage().destructionOutputs().size());
    }

    @Test void destructionSoundDbDefaultsInheritsOverridesAndValidatesGain() {
        JsonObject base = JsonParser.parseString("""
                {"id":"moonstation14:sound_parent","type":"moonstation14:loose_object_profile","mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,"geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,"throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,"physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,"sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"item_damage":{"allowed_groups":[]}}
                """).getAsJsonObject();
        assertEquals(0f, decode(base).itemDamage().destructionSoundVolumeDb());
        JsonObject override = base.deepCopy();
        override.getAsJsonObject("item_damage").addProperty("destruction_sound_volume_db", -4f);
        assertEquals((float) Math.pow(10d, -4d / 20d), decode(override).itemDamage().destructionSoundGain(), 0.000001f);
        JsonObject child = JsonParser.parseString("{\"id\":\"moonstation14:sound_child\",\"type\":\"moonstation14:loose_object_profile\",\"parent\":\"moonstation14:sound_parent\",\"item_damage\":{\"destruction_sound_volume_db\":-2}}").getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:sound_parent"), override);
        raw.put(ResourceLocation.parse("moonstation14:sound_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        assertEquals(-2f, decode(resolved.get(ResourceLocation.parse("moonstation14:sound_child"))).itemDamage().destructionSoundVolumeDb());
        for (String value : new String[]{"NaN", "Infinity", "10000"}) {
            JsonObject invalid = base.deepCopy();
            invalid.getAsJsonObject("item_damage").addProperty("destruction_sound_volume_db", Float.parseFloat(value));
            assertThrows(RuntimeException.class, () -> LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, invalid).getOrThrow(), value);
        }
    }

    @Test void itemDamageAllowedTypesCodecAndMergeSemantics() {
        JsonObject base = JsonParser.parseString("""
                {"id":"moonstation14:type_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"item_damage":{"allowed_groups":[],
                 "allowed_types":["heat","shock"]}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:type_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:type_parent","item_damage":{"allowed_types":["cold"]}}
                """).getAsJsonObject();
        JsonObject absent = base.deepCopy();
        absent.getAsJsonObject("item_damage").remove("allowed_types");
        var absentProfile = decode(absent);
        assertTrue(absentProfile.itemDamage().allowedTypes().isEmpty());
        JsonObject unknown = base.deepCopy();
        unknown.getAsJsonObject("item_damage").getAsJsonArray("allowed_types").add("not_a_damage_type");
        assertThrows(IllegalArgumentException.class, () -> LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, unknown).getOrThrow());
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:type_parent"), base);
        raw.put(ResourceLocation.parse("moonstation14:type_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        assertEquals(java.util.Set.of("heat", "shock"), decode(resolved.get(ResourceLocation.parse("moonstation14:type_parent"))).itemDamage().allowedTypes());
        assertEquals(java.util.Set.of("cold"), decode(resolved.get(ResourceLocation.parse("moonstation14:type_child"))).itemDamage().allowedTypes());
    }

    @Test void destructionExplosionChildOverridePreservesParentPayloadFields() {
        JsonObject base = JsonParser.parseString("""
                {"id":"moonstation14:explosion_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"item_damage":{"allowed_groups":[],
                 "destruction_explosion":{"explosion_type":"moonstation14:default","total_intensity":4.0,
                 "intensity_slope":1.5,"max_intensity":9.0}}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:explosion_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:explosion_parent","item_damage":{"destruction_explosion":{"total_intensity":7.0}}}
                """).getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:explosion_parent"), base);
        raw.put(ResourceLocation.parse("moonstation14:explosion_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var mergedPayload = resolved.get(ResourceLocation.parse("moonstation14:explosion_child"))
                .getAsJsonObject("item_damage").getAsJsonObject("destruction_explosion");
        assertEquals("moonstation14:default", mergedPayload.get("explosion_type").getAsString());
        assertEquals(7.0, mergedPayload.get("total_intensity").getAsDouble());
        assertEquals(1.5, mergedPayload.get("intensity_slope").getAsDouble());
        assertEquals(9.0, mergedPayload.get("max_intensity").getAsDouble());
    }

    @Test void destructionOutputCodecRejectsStructurallyInvalidCount() {
        JsonObject raw = JsonParser.parseString("""
                {"id":"moonstation14:bad_output","mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},"item_damage":{"allowed_groups":[],
                 "destruction_outputs":[{"output":"moonstation14:eggshells","count":0}]}}
                """).getAsJsonObject();
        assertTrue(LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, raw).error().isPresent());
    }

    private static LooseObjectProfile decode(JsonObject json) {
        return LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static Path projectPath(String path) throws java.io.IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new java.io.IOException("Could not locate project resource path " + path);
    }
}
