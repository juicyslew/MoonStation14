package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MeleeRosterCompositionTest {
    private static final String ROOT = "data/moonstation14/moonstation14/";

    @Test
    void shippedProfilesResolveInheritedDefaultsAndRoundTripPublication() throws IOException {
        var server = new PrototypeManager();
        var client = new PrototypeManager();
        server.register(ModMeleeWeaponProfiles.TYPE);
        client.register(ModMeleeWeaponProfiles.TYPE);
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        for (String name : new String[]{"base_melee", "crowbar", "kitchen_knife"})
            resources.put(id(name), load("melee_weapon_profile/" + name + ".json"));
        server.reload(ModMeleeWeaponProfiles.TYPE, resources);
        var profiles = server.snapshot(ModMeleeWeaponProfiles.TYPE);
        assertFalse(profiles.contains(id("base_melee")), "abstract prototypes must not be published in the catalog");
        assertTrue(load("melee_weapon_profile/base_melee.json").get("abstract").getAsBoolean());
        var crowbar = profiles.get(id("crowbar"));
        assertEquals(Map.of("blunt", 8f), crowbar.weapon().damage());
        assertEquals(1f, crowbar.weapon().attackRate()); assertEquals(1.5f, crowbar.weapon().range());
         assertEquals(120f, crowbar.weapon().angleDegrees()); assertEquals(.6f, crowbar.weapon().wideOriginHeightRatio()); assertEquals(.5f, crowbar.weapon().bluntStaminaDamageFactor());
        assertTrue(crowbar.weapon().resetOnHandSelected()); assertFalse(crowbar.weapon().autoAttack());
        assertEquals("moonstation14:weapon_arc_thrust", crowbar.weapon().arc().toString());
        assertEquals("moonstation14:weapon_arc_slash", crowbar.weapon().wideArc().toString());
        assertEquals(-135f, crowbar.weapon().wideAnimationRotation());
        assertEquals("moonstation14:ss14/effects/metal_thud", crowbar.weapon().audio().hit().event().toString());
        assertEquals("moonstation14:ss14/weapons/punch_miss", crowbar.weapon().audio().swing().event().toString());
        assertEquals("moonstation14:ss14/effects/weak_hit", crowbar.weapon().audio().noDamage().event().toString());

        var knife = profiles.get(id("kitchen_knife"));
        assertEquals(Map.of("slash", 10f), knife.weapon().damage());
        assertEquals("moonstation14:ss14/weapons/blade_slice", knife.weapon().audio().hit().event().toString());
        assertEquals(crowbar.weapon().attackRate(), knife.weapon().attackRate());
        assertEquals(crowbar.weapon().range(), knife.weapon().range());
        assertEquals(crowbar.weapon().arc(), knife.weapon().arc());
        assertEquals(crowbar.weapon().wideArc(), knife.weapon().wideArc());
        assertEquals(120f, knife.weapon().angleDegrees()); assertEquals(.6f, knife.weapon().wideOriginHeightRatio());
        assertEquals(crowbar.weapon().audio().swing(), knife.weapon().audio().swing());
        assertEquals(crowbar.weapon().audio().noDamage(), knife.weapon().audio().noDamage());
        assertTrue(crowbar.damageOnHit().isEmpty()); assertTrue(knife.damageOnHit().isEmpty());

        JsonObject override = JsonParser.parseString("""
                {"parent":"base_melee","weapon":{"wide_origin_height_ratio":0.5}}
                """).getAsJsonObject();
        resources.put(id("height_override_test"), override);
        server.reload(ModMeleeWeaponProfiles.TYPE, resources);
        var overridden = server.snapshot(ModMeleeWeaponProfiles.TYPE).get(id("height_override_test")).weapon();
        assertEquals(.5f, overridden.wideOriginHeightRatio());
        assertEquals(120f, overridden.angleDegrees());
        assertEquals(ResourceLocation.parse("moonstation14:weapon_arc_thrust"), overridden.arc());

        client.publishEncodedCatalogs(server.encodePublishedCatalogs());
        var currentServer = server.snapshot(ModMeleeWeaponProfiles.TYPE);
        assertEquals(currentServer.asMap(), client.snapshot(ModMeleeWeaponProfiles.TYPE).asMap());
        var clientOverride = client.snapshot(ModMeleeWeaponProfiles.TYPE).get(id("height_override_test")).weapon();
        assertEquals(.5f, clientOverride.wideOriginHeightRatio());
        assertEquals(120f, clientOverride.angleDegrees());
    }

    @Test
    void shippedHumanWeaponComposesAndResolverSelectsOnlyRealHeldProfiles() throws IOException {
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.commit(manager.stage(Map.of(ModCharacters.CHARACTER_TYPE, Map.of(id("human"), load("character/human.json")))));
        var human = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(id("human"));
        var fists = human.component(MeleeWeaponComponent.class).orElseThrow().weapon();
        assertTrue(human.component(CombatModeComponent.class).orElseThrow().canDisarm().orElse(false),
                "canonical human composition must resolve the upstream species disarm default");
        assertEquals(Map.of("blunt", 5f), fists.damage());
        assertEquals(1f, fists.attackRate()); assertEquals(1.5f, fists.range());
          assertEquals(120f, fists.angleDegrees()); assertEquals(.6f, fists.wideOriginHeightRatio());
        assertEquals("moonstation14:weapon_arc_fist", fists.arc().toString());
        assertEquals("moonstation14:weapon_arc_slash", fists.wideArc().toString());
        assertTrue(fists.altDisarm()); assertTrue(fists.resetOnHandSelected());
        assertEquals("moonstation14:ss14/weapons/punch_miss", fists.audio().swing().event().toString());
        assertEquals("moonstation14:ss14/weapons/punch", fists.audio().hit().event().toString());
        assertEquals("moonstation14:ss14/effects/weak_hit", fists.audio().noDamage().event().toString());

        var profiles = new PrototypeManager(); profiles.register(ModMeleeWeaponProfiles.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : new String[]{"base_melee", "crowbar", "kitchen_knife"})
            raw.put(id(name), load("melee_weapon_profile/" + name + ".json"));
        profiles.commit(profiles.stage(Map.of(ModMeleeWeaponProfiles.TYPE, raw)));
        var catalog = profiles.snapshot(ModMeleeWeaponProfiles.TYPE);
        var crowbarSelection = select(catalog.get(id("crowbar")), true, fists);
        assertEquals(MeleeWeaponResolver.Kind.HELD, crowbarSelection.kind());
        assertEquals(Map.of("blunt", 8f), crowbarSelection.weapon().damage());
        var knifeSelection = select(catalog.get(id("kitchen_knife")), true, fists);
        assertEquals(MeleeWeaponResolver.Kind.HELD, knifeSelection.kind());
        assertEquals(Map.of("slash", 10f), knifeSelection.weapon().damage());
        var unarmed = select(null, false, fists);
        assertEquals(MeleeWeaponResolver.Kind.UNARMED, unarmed.kind());
        assertSame(fists, unarmed.weapon());
        var canDisarm = human.component(CombatModeComponent.class).orElseThrow().canDisarm().orElse(null);
        assertEquals(MeleeAttackRequest.Mode.DISARM,
                MeleeSecondaryGesture.mode(unarmed.kind(), fists.altDisarm(), canDisarm));
        assertTrue(MeleeSecondaryGesture.allowsDisarm(unarmed, canDisarm));
        assertTrue(MeleeWeaponResolver.choose(UUID.randomUUID(), 1, "right", true, Optional.of("other"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(fists)).isEmpty());
    }

    private static MeleeWeaponResolver.Selection select(MeleeWeaponProfile profile, boolean occupied, MeleeWeaponData fists) {
        return MeleeWeaponResolver.choose(UUID.randomUUID(), 1, "right", occupied,
                occupied ? Optional.of("weapon") : Optional.empty(), Optional.empty(), Optional.ofNullable(profile),
                Optional.empty(), Optional.of(fists))
                .orElseThrow();
    }

    private static JsonObject load(String path) throws IOException {
        var url = MeleeRosterCompositionTest.class.getClassLoader().getResource(ROOT + path);
        if (url == null) throw new IOException("Missing classpath resource: " + ROOT + path);
        try (var reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
