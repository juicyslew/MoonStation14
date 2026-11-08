package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcProfileTest {
    @Test void resolvesCompositionAndPublishesConcreteProfiles() throws Exception {
        for (String name : new String[] {"weapon_arc_static.json", "weapon_arc_fist.json", "weapon_arc_claw.json",
                "weapon_arc_slash.json", "weapon_arc_thrust.json"}) {
            assertNotNull(MeleeArcProfileTest.class.getResource(
                    "/data/moonstation14/moonstation14/melee_arc_profile/" + name), name);
        }
        var server = new PrototypeManager(); var client = new PrototypeManager();
        server.register(ModMeleeArcProfiles.TYPE); client.register(ModMeleeArcProfiles.TYPE);
        var staticId = id("weapon_arc_static"); var fistId = id("weapon_arc_fist"); var clawId=id("weapon_arc_claw"); var thrustId = id("weapon_arc_thrust"); var slashId = id("weapon_arc_slash");
        var resources = Map.of(staticId, read("weapon_arc_static.json"), fistId, read("weapon_arc_fist.json"), clawId, read("weapon_arc_claw.json"), thrustId, read("weapon_arc_thrust.json"), slashId, read("weapon_arc_slash.json"));
        var staged = server.stage(Map.of(ModMeleeArcProfiles.TYPE, resources));
        server.commit(staged);
        assertNull(server.snapshot(ModMeleeArcProfiles.TYPE).get(staticId));
        var fist = server.snapshot(ModMeleeArcProfiles.TYPE).get(fistId);
        var thrust = server.snapshot(ModMeleeArcProfiles.TYPE).get(thrustId);
        var slash = server.snapshot(ModMeleeArcProfiles.TYPE).get(slashId);
        assertEquals(id("textures/effects/melee/fist.png"), fist.texture());
        assertEquals(id("textures/effects/melee/disarm_hand.png"), fist.disarmTexture());
        assertEquals(1, fist.disarmFrames());
        try (var hand = MeleeArcProfileTest.class.getResourceAsStream("/assets/moonstation14/textures/effects/melee/disarm_hand.png")) {
            assertNotNull(hand, "configured hand-only DISARM texture must exist");
            assertTrue(hand.read() >= 0);
        }
        assertEquals(MeleeArcProfile.Animation.NONE, fist.animation());
        assertTrue(fist.fadeOut()); assertEquals(2.0, fist.lifetimeSeconds());
        assertEquals(new MeleeStrikeProfile(.45, .4, .55, .1, .5), fist.strike());
        assertEquals(MeleeArcProfile.WeaponPose.DEFAULT, MeleeArcProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"texture\":\"moonstation14:textures/effects/melee/fist.png\"}")).result().orElseThrow().weaponPose());
        assertEquals(new MeleeArcProfile.WeaponPose(45.0, 20.0, 2.0), fist.weaponPose());
        assertEquals(id("textures/effects/melee/spear.png"), thrust.texture());
        assertEquals(MeleeArcProfile.Animation.THRUST, thrust.animation());
        assertTrue(thrust.fadeOut()); assertEquals(2.0, thrust.lifetimeSeconds());
        assertNotNull(thrust.strike());
        assertEquals(id("textures/effects/melee/spear.png"), slash.texture());
        assertEquals(MeleeArcProfile.Animation.SLASH, slash.animation()); assertTrue(slash.fadeOut()); assertEquals(2.0, slash.lifetimeSeconds());
        assertEquals(id("textures/effects/melee/claw.png"), server.snapshot(ModMeleeArcProfiles.TYPE).get(clawId).texture());
        try (var claw = MeleeArcProfileTest.class.getResourceAsStream("/assets/moonstation14/textures/effects/melee/claw.png")) {
            assertNotNull(claw);
            byte[] bytes = claw.readAllBytes();
            assertEquals("3cc77eae2e7fa93a685032f6ebaedcebe1ec5f25738909faaa4894584a833e5e",
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertNotNull(image);
            assertEquals(64, image.getWidth()); assertEquals(64, image.getHeight());
        }
        var aggregate = server.encodePublishedCatalogs();
        var published = aggregate.get(ModMeleeArcProfiles.TYPE.typeId());
        assertEquals(4, published.size()); assertFalse(published.containsKey(staticId));
        client.publishEncodedCatalogs(aggregate);
        assertEquals(fist, client.snapshot(ModMeleeArcProfiles.TYPE).get(fistId));
        assertEquals(thrust, client.snapshot(ModMeleeArcProfiles.TYPE).get(thrustId));
        assertEquals(slash, client.snapshot(ModMeleeArcProfiles.TYPE).get(slashId));
         assertEquals(new MeleeArcProfile.WeaponPose(45.0, 20.0, 2.0), client.snapshot(ModMeleeArcProfiles.TYPE).get(thrustId).weaponPose());
    }
    @Test void absentStrikeFieldRemainsOptional() {
        var decoded = MeleeArcProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"texture\":\"moonstation14:textures/effects/melee/fist.png\"}"));
        assertTrue(decoded.result().isPresent());
        assertNull(decoded.result().orElseThrow().strike());
        assertEquals(MeleeArcProfile.WeaponPose.DEFAULT, decoded.result().orElseThrow().weaponPose());
        assertEquals(3, decoded.result().orElseThrow().disarmFrames());
    }
    @Test void disarmFramesAreBoundedAndDecoded() {
        var decoded = MeleeArcProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"texture\":\"moonstation14:textures/effects/melee/fist.png\",\"disarm_frames\":1}"));
        assertEquals(1, decoded.result().orElseThrow().disarmFrames());
        assertTrue(MeleeArcProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"texture\":\"moonstation14:textures/effects/melee/fist.png\",\"disarm_frames\":0}")).result().isEmpty());
    }
    @Test void weaponPoseRejectsNonFiniteAndOutOfRangeAngles() {
        assertThrows(IllegalArgumentException.class, () -> new MeleeArcProfile.WeaponPose(Double.NaN, 20));
        assertThrows(IllegalArgumentException.class, () -> new MeleeArcProfile.WeaponPose(181, 20));
        assertThrows(IllegalArgumentException.class, () -> new MeleeArcProfile.WeaponPose(45, 46));
        assertTrue(MeleeArcProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"texture\":\"moonstation14:textures/effects/melee/fist.png\",\"weapon_pose\":{\"forward_degrees\":45,\"oblique_degrees\":20}}"))
                .result().isPresent());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static com.google.gson.JsonObject read(String name) throws java.io.IOException {
        var path = "/data/moonstation14/moonstation14/melee_arc_profile/" + name;
        try (var stream = MeleeArcProfileTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new java.io.IOException("missing prototype resource " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
