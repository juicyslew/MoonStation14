package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ParticleAcceleratorParticlePrototypeTest {
    @Test void resolvesBaseBulletDefaultsAndParticleOverridesThroughRegisteredType() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModParticleAcceleratorParticles.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String leaf : new String[]{"base_bullet", "particle_accelerator_particle"}) {
            String path = "/data/moonstation14/moonstation14/pa_particle/" + leaf + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        manager.reload(ModParticleAcceleratorParticles.TYPE, raw);
        var resolved = manager.snapshot(ModParticleAcceleratorParticles.TYPE)
                .get(ResourceLocation.fromNamespaceAndPath("moonstation14", "particle_accelerator_particle"));
        assertNotNull(resolved);
        assertEquals(25f, resolved.radiationDamage());
        assertEquals(60, resolved.lifetimeTicks());
        assertEquals(.48, resolved.halfExtent());
        assertFalse(resolved.deleteOnCollide());
        assertEquals("moonstation14:textures/entity/pa_particle/particle", resolved.sprite());
        assertEquals("moonstation14:projectile/fly_by", resolved.flyBy().sound().toString());
        assertEquals(.10f, resolved.flyBy().probability());
        assertEquals(1.5f, resolved.flyBy().range());
        for (int strength = 0; strength <= 3; strength++) {
            String texture = "/assets/moonstation14/textures/entity/pa_particle/particle" + strength + ".png";
            try (var stream = getClass().getResourceAsStream(texture)) {
                assertNotNull(stream, texture);
            }
        }
    }

    @Test void inheritedFlyByProfileOverrideResolvesIntoParticleProfile() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModParticleAcceleratorParticles.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String leaf : new String[]{"base_bullet", "particle_accelerator_particle"}) {
            try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/pa_particle/" + leaf + ".json")) {
                assertNotNull(stream);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (leaf.equals("particle_accelerator_particle")) json.add("fly_by", JsonParser.parseString(
                        "{\"sound\":\"moonstation14:projectile/fly_by\",\"probability\":0.4,\"range\":2.25}"));
                raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        manager.reload(ModParticleAcceleratorParticles.TYPE, raw);
        var profile = manager.snapshot(ModParticleAcceleratorParticles.TYPE)
                .get(ResourceLocation.fromNamespaceAndPath("moonstation14", "particle_accelerator_particle"));
        assertEquals(.4f, profile.flyBy().probability());
        assertEquals(2.25f, profile.flyBy().range());
    }
}
