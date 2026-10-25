package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LooseSlipPrototypeManagerTest {
    @Test void managerReloadAndAggregateExchangeRetainInheritedSlipComposition() {
        PrototypeManager server = new PrototypeManager();
        PrototypeManager client = new PrototypeManager();
        server.register(ModLooseObjectProfiles.PROFILE_TYPE);
        client.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var parent = json("""
                {"id":"moonstation14:slip_parent","type":"moonstation14:loose_object_profile",
                 "mass":1,"friction":0.5,"restitution":0,"throw_speed":0.75,"throw_spin":0,
                 "geometry":"cube","render_kind":"flat_sprite","break_on_impact":false,"break_threshold":0,
                 "throw_range":8,"minimum_throw_range":0.1,"throw_cooldown":10,"physical_width":0.65,
                 "physical_height":0.08,"visual":{"sprite_scale_x":0.65,"sprite_scale_y":0.65,"sprite_offset_x":0,
                 "sprite_offset_y":0,"model_scale":1,"model_floor_offset":0},
                 "step_trigger":{"active":true,"intersection_ratio":0.2,"required_speed":4.25,"ignore_weightless":false},
                 "slippery":{"stun_seconds":2,"knockdown_seconds":1.5}}
                """);
        var child = json("""
                {"id":"moonstation14:slip_child","type":"moonstation14:loose_object_profile",
                 "parent":"moonstation14:slip_parent",
                  "step_trigger":{"active":false,"intersection_ratio":0,"required_speed":0,"ignore_weightless":true},
                  "slippery":{"stun_seconds":0,"super_slippery":true}}
                """);
        ResourceLocation parentId = id("slip_parent"), childId = id("slip_child");
        server.reload(ModLooseObjectProfiles.PROFILE_TYPE, Map.of(parentId, parent, childId, child));
        LooseObjectProfile resolved = server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(childId);
        assertEquals(false, resolved.stepTrigger().active());
        assertEquals(0d, resolved.stepTrigger().intersectionRatio());
        assertEquals(0d, resolved.stepTrigger().requiredSpeed());
        assertEquals(true, resolved.stepTrigger().ignoreWeightless());
        assertEquals(0d, resolved.slippery().stunSeconds());
        assertEquals(1.5d, resolved.slippery().knockdownSeconds());
        assertEquals(true, resolved.slippery().superSlippery());

        var aggregate = server.encodePublishedCatalogs();
        client.publishEncodedCatalogs(aggregate);
        assertEquals(resolved, client.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(childId));
    }

    private static JsonObject json(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
}
