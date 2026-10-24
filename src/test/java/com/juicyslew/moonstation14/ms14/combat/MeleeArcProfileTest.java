package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcProfileTest {
    @Test void resolvesCompositionAndPublishesConcreteProfiles() throws java.io.IOException {
        var server = new PrototypeManager(); var client = new PrototypeManager();
        server.register(ModMeleeArcProfiles.TYPE); client.register(ModMeleeArcProfiles.TYPE);
        var staticId = id("weapon_arc_static"); var fistId = id("weapon_arc_fist"); var thrustId = id("weapon_arc_thrust"); var slashId = id("weapon_arc_slash");
        var resources = Map.of(staticId, read("weapon_arc_static.json"), fistId, read("weapon_arc_fist.json"), thrustId, read("weapon_arc_thrust.json"), slashId, read("weapon_arc_slash.json"));
        var staged = server.stage(Map.of(ModMeleeArcProfiles.TYPE, resources));
        server.commit(staged);
        assertNull(server.snapshot(ModMeleeArcProfiles.TYPE).get(staticId));
        var fist = server.snapshot(ModMeleeArcProfiles.TYPE).get(fistId);
        var thrust = server.snapshot(ModMeleeArcProfiles.TYPE).get(thrustId);
        var slash = server.snapshot(ModMeleeArcProfiles.TYPE).get(slashId);
        assertEquals(id("textures/effects/melee/fist.png"), fist.texture());
        assertEquals(MeleeArcProfile.Animation.NONE, fist.animation());
        assertTrue(fist.fadeOut()); assertEquals(2.0, fist.lifetimeSeconds());
        assertEquals(id("textures/effects/melee/spear.png"), thrust.texture());
        assertEquals(MeleeArcProfile.Animation.THRUST, thrust.animation());
        assertTrue(thrust.fadeOut()); assertEquals(2.0, thrust.lifetimeSeconds());
        assertEquals(id("textures/effects/melee/spear.png"), slash.texture());
        assertEquals(MeleeArcProfile.Animation.SLASH, slash.animation()); assertTrue(slash.fadeOut()); assertEquals(2.0, slash.lifetimeSeconds());
        var aggregate = server.encodePublishedCatalogs();
        var published = aggregate.get(ModMeleeArcProfiles.TYPE.typeId());
        assertEquals(3, published.size()); assertFalse(published.containsKey(staticId));
        client.publishEncodedCatalogs(aggregate);
        assertEquals(fist, client.snapshot(ModMeleeArcProfiles.TYPE).get(fistId));
        assertEquals(thrust, client.snapshot(ModMeleeArcProfiles.TYPE).get(thrustId));
        assertEquals(slash, client.snapshot(ModMeleeArcProfiles.TYPE).get(slashId));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static com.google.gson.JsonObject read(String name) throws java.io.IOException {
        var path = "/data/moonstation14/prototypes/melee_arc_profile/" + name;
        try (var stream = MeleeArcProfileTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new java.io.IOException("missing prototype resource " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
