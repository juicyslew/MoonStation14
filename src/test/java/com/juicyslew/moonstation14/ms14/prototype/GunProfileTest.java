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
}
