package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWeaponProfileTest {
    @Test void inheritanceStagingAggregateEncodingAndPublishingPreserveHitComposition() {
        var server = new PrototypeManager();
        var client = new PrototypeManager();
        server.register(ModMeleeWeaponProfiles.TYPE);
        client.register(ModMeleeWeaponProfiles.TYPE);
        var parent = ResourceLocation.fromNamespaceAndPath("test", "parent");
        var child = ResourceLocation.fromNamespaceAndPath("test", "child");
        var base = JsonParser.parseString("{\"weapon\":{\"damage\":{\"blunt\":5},\"attack_rate\":2,\"range\":4,\"auto_attack\":true,\"arc\":\"moonstation14:weapon_arc_fist\",\"animation_offset\":2,\"wide_animation_rotation\":30,\"blunt_stamina_damage_factor\":0.5,\"angle_degrees\":30,\"wide_arc\":\"moonstation14:weapon_arc_slash\",\"alt_disarm\":false},\"damage_on_hit\":{\"blunt\":5},\"chemical_injector\":{\"solution_name\":\"food\",\"transfer_cents\":300,\"pierce_armor\":false}}").getAsJsonObject();
        var derived = new JsonObject(); derived.addProperty("parent", "test:parent");
        derived.add("weapon", JsonParser.parseString("{\"attack_rate\":3,\"blunt_stamina_damage_factor\":0}"));
        derived.add("chemical_injector", JsonParser.parseString("{\"transfer_cents\":500}"));
        var staged = server.stage(Map.of(ModMeleeWeaponProfiles.TYPE, Map.of(parent, base, child, derived)));
        assertNull(server.snapshot(ModMeleeWeaponProfiles.TYPE).get(child));
        server.commit(staged);
        var actual = server.snapshot(ModMeleeWeaponProfiles.TYPE).get(child).weapon();
        assertEquals(Map.of("blunt", 5f), actual.damage());
        assertEquals(3f, actual.attackRate()); assertEquals(4f, actual.range()); assertTrue(actual.autoAttack());
        assertFalse(actual.resistanceBypass()); assertEquals(1f, actual.clickDamageModifier());
        assertFalse(actual.mustBeEquippedToUse());
        assertEquals(0f, actual.bluntStaminaDamageFactor());
        assertEquals(30f, actual.angleDegrees()); assertEquals("moonstation14:weapon_arc_slash", actual.wideArc().toString()); assertFalse(actual.altDisarm());
        assertEquals("moonstation14:weapon_arc_fist", actual.arc().toString()); assertEquals(2f, actual.animationOffset()); assertEquals(30f, actual.wideAnimationRotation());
        var profile = server.snapshot(ModMeleeWeaponProfiles.TYPE).get(child);
        assertEquals(Map.of("blunt", 5f), profile.damageOnHit());
        assertEquals(new MeleeWeaponProfile.MeleeChemicalInjectorData("food", 500, false), profile.chemicalInjector().orElseThrow());
        var aggregate = server.encodePublishedCatalogs();
        var encoded = aggregate.get(ModMeleeWeaponProfiles.TYPE.typeId()).get(child);
        assertEquals(3f, encoded.getAsJsonObject("weapon").get("attack_rate").getAsFloat());
        assertEquals("moonstation14:weapon_arc_fist", encoded.getAsJsonObject("weapon").get("arc").getAsString());
        assertEquals(2f, encoded.getAsJsonObject("weapon").get("animation_offset").getAsFloat());
        assertEquals(30f, encoded.getAsJsonObject("weapon").get("wide_animation_rotation").getAsFloat());
        assertEquals(30f, encoded.getAsJsonObject("weapon").get("angle_degrees").getAsFloat());
        assertEquals("moonstation14:weapon_arc_slash", actual.wideArc().toString());
        assertFalse(encoded.getAsJsonObject("weapon").get("alt_disarm").getAsBoolean());
        assertEquals(0f, encoded.getAsJsonObject("weapon").get("blunt_stamina_damage_factor").getAsFloat());
        assertEquals(5f, encoded.getAsJsonObject("damage_on_hit").get("blunt").getAsFloat());
        client.publishEncodedCatalogs(aggregate);
        var roundTrip = client.snapshot(ModMeleeWeaponProfiles.TYPE).get(child);
        assertEquals(actual, roundTrip.weapon());
        assertEquals(profile, roundTrip);
        var defaults = MeleeWeaponProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"weapon\":{\"damage\":{\"blunt\":1}}}" )).getOrThrow();
        assertTrue(defaults.damageOnHit().isEmpty()); assertTrue(defaults.chemicalInjector().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponProfile(actual, Map.of("bad-key", 1f), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponProfile(actual, Map.of("blunt", 0f), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponProfile.MeleeChemicalInjectorData(" ", 100, true));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponProfile.MeleeChemicalInjectorData("food", 0, true));
    }

    @Test void audioCueFieldOverrideInheritsSiblingCueSettingsThroughCatalogPublication() {
        var server = new PrototypeManager(); var client = new PrototypeManager();
        server.register(ModMeleeWeaponProfiles.TYPE); client.register(ModMeleeWeaponProfiles.TYPE);
        var parent = ResourceLocation.fromNamespaceAndPath("test", "audio_parent");
        var child = ResourceLocation.fromNamespaceAndPath("test", "audio_child");
        var base = JsonParser.parseString("{\"weapon\":{\"damage\":{\"slash\":15},\"attack_rate\":2,\"audio\":{\"hit\":{\"event\":\"moonstation14:ss14/weapons/blade_slice\"},\"swing\":{\"event\":\"moonstation14:custom/swing\",\"volume_db\":-8,\"pitch_variation\":0.2}}},\"damage_on_hit\":{\"blunt\":10}}").getAsJsonObject();
        var patch = JsonParser.parseString("{\"parent\":\"test:audio_parent\",\"weapon\":{\"audio\":{\"swing\":{\"volume_db\":-12}}}}").getAsJsonObject();
        server.commit(server.stage(Map.of(ModMeleeWeaponProfiles.TYPE, Map.of(parent, base, child, patch))));
        var profile = server.snapshot(ModMeleeWeaponProfiles.TYPE).get(child);
        var audio = profile.weapon().audio();
        assertEquals("moonstation14:ss14/weapons/blade_slice", audio.hit().event().toString());
        assertEquals("moonstation14:custom/swing", audio.swing().event().toString());
        assertEquals(-12f, audio.swing().volumeDb()); assertEquals(.2f, audio.swing().pitchVariation());
        assertEquals(Map.of("slash", 15f), profile.weapon().damage()); assertEquals(2f, profile.weapon().attackRate());
        assertEquals(Map.of("blunt", 10f), profile.damageOnHit());
        var encoded = server.encodePublishedCatalogs();
        var childJson = encoded.get(ModMeleeWeaponProfiles.TYPE.typeId()).get(child);
        assertEquals("moonstation14:ss14/weapons/blade_slice", childJson.getAsJsonObject("weapon").getAsJsonObject("audio").getAsJsonObject("hit").get("event").getAsString());
        client.publishEncodedCatalogs(encoded);
        var roundTrip = client.snapshot(ModMeleeWeaponProfiles.TYPE).get(child);
        assertEquals(profile, roundTrip);
        assertNull(roundTrip.weapon().audio().noDamage());
        assertEquals("moonstation14:ss14/effects/weak_hit", MeleeSoundData.DEFAULT.noDamage().event().toString());
    }
}
