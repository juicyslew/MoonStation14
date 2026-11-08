package com.juicyslew.moonstation14.ms14.singularity.emitter;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class EmitterFlyByProfileTest {
    @Test void emitterPrototypeResolvesBaseBulletDefaultsAndPerEmitterOverride() throws Exception {
        var manager = new PrototypeManager();
        manager.register(EmitterProfile.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        try (var input = getClass().getResourceAsStream(
                "/data/moonstation14/moonstation14/emitter/singularity_emitter.json")) {
            assertNotNull(input);
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
        }
        manager.reload(EmitterProfile.TYPE, raw);
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_emitter");
        var inherited = manager.snapshot(EmitterProfile.TYPE).get(id);
        assertEquals("moonstation14:projectile/fly_by", inherited.boltFlyBy().sound().toString());
        assertEquals(.1f, inherited.boltFlyBy().probability());
        assertEquals(1.5f, inherited.boltFlyBy().range());
        assertEquals(5, inherited.boltImpactLifetimeTicks()); // inherited BulletImpactEffect timed despawn: .25s at 20Hz

        var edited = JsonParser.parseString(raw.get(id).toString()).getAsJsonObject();
        edited.add("bolt_fly_by", JsonParser.parseString(
                "{\"sound\":\"moonstation14:projectile/fly_by\",\"probability\":0.35,\"range\":2.5}"));
        raw.put(id, edited);
        manager.reload(EmitterProfile.TYPE, raw);
        var override = manager.snapshot(EmitterProfile.TYPE).get(id).boltFlyBy();
        assertEquals(.35f, override.probability());
        assertEquals(2.5f, override.range());
        edited.addProperty("bolt_impact_lifetime_ticks", 9);
        raw.put(id, edited);
        manager.reload(EmitterProfile.TYPE, raw);
        assertEquals(9, manager.snapshot(EmitterProfile.TYPE).get(id).boltImpactLifetimeTicks());
    }
}
