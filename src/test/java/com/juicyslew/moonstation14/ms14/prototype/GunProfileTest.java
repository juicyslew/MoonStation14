package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunProfileTest {
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath("moonstation14", s); }
    @Test void resolvesSupportedRevolverInheritanceInIsolatedCatalog() {
        var manager = new PrototypeManager(); manager.register(ModGunProfiles.TYPE);
        var resources = BotanyCatalogCandidateTest.resources("gun_profile", "base_weapon_revolver", "weapon_revolver_python", "base_toy_gun", "revolver_cap_gun", "revolver_cap_gun_fake");
        final java.util.Map<ResourceLocation,GunProfile> resolved = new java.util.LinkedHashMap<>();
        manager.stage(java.util.Map.of(ModGunProfiles.TYPE, resources), catalogs -> catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key, raw) -> resolved.put(key, GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        var python = resolved.get(id("weapon_revolver_python"));
        assertFalse(python.pacifismAllowed());
        assertEquals(2f, python.gun().fireRate()); assertEquals(40f, python.gun().projectileSpeed());
        assertEquals(GunProfile.SpreadData.SOURCE_DEFAULTS, python.gun().spread());
        assertEquals(.25f, python.gun().burstCooldownSeconds());
        assertEquals(GunProfile.GunData.SOURCE_EMPTY_SOUND, python.gun().emptySound().orElseThrow());
        assertTrue(python.gun().useKey()); assertEquals("SEMI_AUTO", python.gun().firingMode()); assertEquals(.66f, python.gun().useDelaySeconds().orElseThrow());
        assertEquals(2.25f, python.gun().gunshotSound().orElseThrow().volumeDb()); assertEquals(6, python.revolverAmmoProvider().orElseThrow().capacity());
        assertEquals(GunProfile.RevolverAmmoProviderData.SOURCE_INSERT_SOUND, python.revolverAmmoProvider().orElseThrow().insertSound().orElseThrow());
        assertTrue(python.ammoCounter());
        var capGun = resolved.get(id("revolver_cap_gun"));
        var fakeGun = resolved.get(id("revolver_cap_gun_fake"));
        assertTrue(capGun.pacifismAllowed()); assertTrue(fakeGun.pacifismAllowed());
        assertTrue(capGun.ammoCounter()); assertTrue(fakeGun.ammoCounter());
        assertEquals("moonstation14:cartridge_magnum_ap", fakeGun.revolverAmmoProvider().orElseThrow().fillCartridgePrototype().toString());
        var fake = fakeGun.revolverAmmoProvider().orElseThrow();
        assertEquals(id("cartridge_magnum_ap"), fake.fillCartridgePrototype()); assertTrue(fake.acceptedCartridgeTags().contains(id("magnum"))); assertTrue(fake.acceptedCartridgeTags().contains(id("cap")));
        var explicitFalse = new GunProfile(new GunProfile.GunData(2,40,false,"SEMI_AUTO",java.util.Optional.empty(),java.util.Optional.of(0f)), java.util.Optional.empty());
        assertFalse(explicitFalse.gun().useKey()); assertEquals(0f, explicitFalse.gun().useDelaySeconds().orElseThrow());
        assertFalse(explicitFalse.ammoCounter());
        var roundtrip = GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, GunProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, python).getOrThrow()).getOrThrow();
        assertEquals(python, roundtrip);
        assertTrue(GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                GunProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, fakeGun).getOrThrow()).getOrThrow().pacifismAllowed());
        var customized = new GunProfile.GunData(2, 40, true, "SEMI_AUTO", java.util.Optional.empty(), java.util.Optional.empty(),
                new GunProfile.SpreadData(.25f, 3f, .75f, 2f));
        assertEquals(customized, GunProfile.GunData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                GunProfile.GunData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, customized).getOrThrow()).getOrThrow());
    }
    @Test void supportsSelectedFullAndSemiModesAndDistinctWieldComposition() {
        var full=new GunProfile.GunData(5,40,true,"FULL_AUTO",java.util.Optional.empty(),java.util.Optional.empty(),GunProfile.SpreadData.SOURCE_DEFAULTS,.25f,java.util.Optional.empty(),1f,java.util.Set.of("FULL_AUTO","SEMI_AUTO"));
        assertEquals(full, GunProfile.GunData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,GunProfile.GunData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,full).getOrThrow()).getOrThrow());
        assertThrows(IllegalArgumentException.class,()->new GunProfile.GunData(5,40,true,"FULL_AUTO",java.util.Optional.empty(),java.util.Optional.empty()));
        var wield=new GunProfile.WieldBonusData(-20,-30,0,0);
        var profile=new GunProfile(new GunProfile.GunData(1,40,true,"SEMI_AUTO",java.util.Optional.empty(),java.util.Optional.empty()),java.util.Optional.empty(),false,false,java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.of(wield),java.util.Optional.of(new GunProfile.WieldableData(false)),true,.6f,java.util.Optional.empty(),java.util.Optional.empty());
        assertTrue(profile.requiresWield()); assertEquals(wield,profile.wieldBonus().orElseThrow()); assertEquals(.6f,profile.gunSpreadModifier());
        assertEquals(profile,GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,GunProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow()).getOrThrow());
    }
    @Test void inheritedGunProfilesResolveModeOverrideWhitelistAndWieldSeparately() {
        var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();
        raw.put(id("weapon_base"),com.google.gson.JsonParser.parseString("{\"id\":\"weapon_base\",\"gun\":{\"fire_rate\":5,\"projectile_speed\":40,\"use_key\":true,\"firing_mode\":\"FULL_AUTO\",\"available_modes\":[\"FULL_AUTO\",\"SEMI_AUTO\"]},\"wield_bonus\":{\"minimum_degrees\":-20,\"maximum_degrees\":-30},\"magazine_chamber_provider\":{\"magazine_tags\":[\"moonstation14:magazine_pistol\",\"moonstation14:magazine_pistol_highcap\"],\"cartridge_tags\":[\"moonstation14:pistol\"],\"starting_magazine\":\"moonstation14:magazine_pistol\"}}").getAsJsonObject());
        raw.put(id("mk58"),com.google.gson.JsonParser.parseString("{\"id\":\"mk58\",\"parent\":\"weapon_base\",\"gun\":{\"firing_mode\":\"SEMI_AUTO\",\"available_modes\":[\"SEMI_AUTO\"]},\"magazine_chamber_provider\":{\"magazine_tags\":[\"moonstation14:magazine_pistol\",\"moonstation14:magazine_pistol_highcap\"]}}").getAsJsonObject());
        raw.put(id("lecter"),com.google.gson.JsonParser.parseString("{\"id\":\"lecter\",\"parent\":\"weapon_base\"}").getAsJsonObject());
        var manager=new PrototypeManager();manager.register(ModGunProfiles.TYPE);var resolved=new java.util.HashMap<ResourceLocation,GunProfile>();
        manager.stage(java.util.Map.of(ModGunProfiles.TYPE,raw),catalogs->catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key,value)->resolved.put(key,GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow())));
        assertEquals(java.util.Set.of("SEMI_AUTO"),resolved.get(id("mk58")).gun().availableModes());
        assertEquals(java.util.Set.of("FULL_AUTO","SEMI_AUTO"),resolved.get(id("lecter")).gun().availableModes());
        assertEquals("FULL_AUTO",resolved.get(id("lecter")).gun().firingMode());
        assertEquals(java.util.Set.of(ResourceLocation.parse("moonstation14:magazine_pistol"),ResourceLocation.parse("moonstation14:magazine_pistol_highcap")),resolved.get(id("mk58")).magazineChamberProvider().orElseThrow().magazineTags());
        assertTrue(resolved.get(id("lecter")).wieldBonus().isPresent()); assertFalse(resolved.get(id("lecter")).requiresWield());
    }
    @Test void optionalGunManipulationCuesUseDefaultsAndMergeChildOverride() {
        var cueDefaults = new java.util.ArrayList<PositionalSoundCue>();
        cueDefaults.add(new GunProfile.BallisticProviderData(1,id("tag"),id("fill"),true).cycleSound().orElseThrow());
        cueDefaults.add(new GunProfile.CellSlotData(id("slot"),java.util.Set.of(id("tag")),java.util.Optional.empty()).insertSound().orElseThrow());
        cueDefaults.add(new GunProfile.WieldableData(true).wieldSound().orElseThrow());
        cueDefaults.add(new GunProfile.GunData(1,40,true,"SEMI_AUTO",java.util.Optional.empty(),java.util.Optional.empty()).modeSound().orElseThrow());
        for (var cue : cueDefaults) assertFalse(cue.event().getPath().contains(".."));
        assertEquals("ss14/weapons/guns/cock/smg_cock",cueDefaults.get(0).event().getPath());
        assertEquals("ss14/weapons/guns/magin/revolver_magin",cueDefaults.get(1).event().getPath());
        assertEquals("ss14/effects/thudswoosh",cueDefaults.get(2).event().getPath());
        var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();
        raw.put(id("parent"),com.google.gson.JsonParser.parseString("{\"id\":\"parent\",\"gun\":{\"fire_rate\":5,\"projectile_speed\":40,\"use_key\":true,\"firing_mode\":\"SEMI_AUTO\"},\"cell_slot\":{\"slot_id\":\"cell\",\"accepted_tags\":[\"moonstation14:cell\"]}}").getAsJsonObject());
        raw.put(id("child"),com.google.gson.JsonParser.parseString("{\"id\":\"child\",\"parent\":\"parent\",\"gun\":{\"mode_sound\":{\"event\":\"moonstation14:custom_selector\",\"volume_db\":0,\"pitch_variation\":0}},\"cell_slot\":{\"insert_sound\":{\"event\":\"moonstation14:custom_insert\",\"volume_db\":0,\"pitch_variation\":0}}}").getAsJsonObject());
        var manager=new PrototypeManager();manager.register(ModGunProfiles.TYPE);var resolved=new java.util.HashMap<ResourceLocation,GunProfile>();
        manager.stage(java.util.Map.of(ModGunProfiles.TYPE,raw),catalogs->catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key,value)->resolved.put(key,GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow())));
        var parent=resolved.get(id("parent"));var child=resolved.get(id("child"));
        assertEquals("ss14/weapons/guns/misc/selector",parent.gun().modeSound().orElseThrow().event().getPath());
        assertEquals("custom_selector",child.gun().modeSound().orElseThrow().event().getPath());
        assertEquals("custom_insert",child.cellSlot().orElseThrow().insertSound().orElseThrow().event().getPath());
        assertEquals(parent.cellSlot().orElseThrow().ejectSound(),child.cellSlot().orElseThrow().ejectSound());
    }
    @Test void chamberInsertCueHasIndependentDefaultAndChildOverride() {
        var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();
        raw.put(id("base"),com.google.gson.JsonParser.parseString("{\"id\":\"base\",\"gun\":{\"fire_rate\":1,\"projectile_speed\":1,\"use_key\":false,\"firing_mode\":\"SEMI_AUTO\"},\"magazine_chamber_provider\":{\"magazine_tags\":[\"moonstation14:mag\"],\"cartridge_tags\":[\"moonstation14:round\"]}}").getAsJsonObject());
        raw.put(id("child"),com.google.gson.JsonParser.parseString("{\"id\":\"child\",\"parent\":\"base\",\"magazine_chamber_provider\":{\"chamber_insert_sound\":{\"event\":\"moonstation14:custom_chamber\",\"volume_db\":0,\"pitch_variation\":0}}}").getAsJsonObject());
        var manager=new PrototypeManager();manager.register(ModGunProfiles.TYPE);var found=new java.util.HashMap<ResourceLocation,GunProfile>();
        manager.stage(java.util.Map.of(ModGunProfiles.TYPE,raw),catalogs->catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key,value)->found.put(key,GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow())));
        var base=found.get(id("base")).magazineChamberProvider().orElseThrow();var child=found.get(id("child")).magazineChamberProvider().orElseThrow();
        assertEquals("ss14/weapons/guns/magin/revolver_magin",base.chamberInsertSound().orElseThrow().event().getPath());
        assertEquals("custom_chamber",child.chamberInsertSound().orElseThrow().event().getPath());
        assertEquals(child,GunProfile.MagazineChamberProviderData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,GunProfile.MagazineChamberProviderData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,child).getOrThrow()).getOrThrow());
    }
}
