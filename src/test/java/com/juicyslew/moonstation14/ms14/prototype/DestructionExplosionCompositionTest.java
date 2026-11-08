package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DestructionExplosionCompositionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void explosionPayloadIsOptionalAndDecodesCompletely() {
        assertTrue(ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).result().isEmpty());
        var data = new JsonObject();
        data.addProperty("explosion_type", "moonstation14:test_explosion");
        data.addProperty("total_intensity", 2.5);
        data.addProperty("intensity_slope", 1.25);
        data.addProperty("max_intensity", 7.0);
        assertTrue(ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, data).result().isPresent());
    }

    @Test void resolvedCandidateMayReferenceExplosionOnlyInSameCandidate() {
        var manager = new PrototypeManager();
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModExplosions.EXPLOSION_TYPE);
        var generic = BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic");
        var profile = generic.get(id("generic")).deepCopy();
        var damage = new JsonObject(); damage.add("allowed_groups", new com.google.gson.JsonArray());
        var explosion = new JsonObject(); explosion.addProperty("explosion_type", "moonstation14:new_custom");
        explosion.addProperty("total_intensity", 2.0); explosion.addProperty("intensity_slope", 1.0);
        explosion.addProperty("max_intensity", 5.0);
        damage.add("destruction_explosion", explosion); profile.add("item_damage", damage);
        var profiles = new LinkedHashMap<>(generic); profiles.put(id("candidate_test"), profile);
        var explosions = new LinkedHashMap<ResourceLocation, JsonObject>();
        var candidateExplosion = BotanyCatalogCandidateTest.mainResources("explosion", "default")
                .get(id("default")).deepCopy();
        explosions.put(id("new_custom"), candidateExplosion);
        assertDoesNotThrow(() -> manager.stage(Map.of(ModLooseObjectProfiles.PROFILE_TYPE, profiles,
                ModExplosions.EXPLOSION_TYPE, explosions), ignored -> {}));
        var before = manager.snapshot();
        explosions.clear();
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModLooseObjectProfiles.PROFILE_TYPE, profiles,
                ModExplosions.EXPLOSION_TYPE, explosions), ignored -> {}));
        assertSame(before, manager.snapshot());
    }

    @Test void encodedPublicationRejectsUnknownExplosionReferenceWithoutChangingSnapshot() {
        var manager = new PrototypeManager();
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModExplosions.EXPLOSION_TYPE);
        var generic = BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic");
        var profile = generic.get(id("generic")).deepCopy();
        var damage = new JsonObject(); damage.add("allowed_groups", new com.google.gson.JsonArray());
        var payload = new JsonObject(); payload.addProperty("explosion_type", "moonstation14:missing_explosion");
        payload.addProperty("total_intensity", 2.0); payload.addProperty("intensity_slope", 1.0);
        payload.addProperty("max_intensity", 5.0);
        damage.add("destruction_explosion", payload); profile.add("item_damage", damage);
        var profiles = new LinkedHashMap<>(generic); profiles.put(id("candidate_test"), profile);
        var candidate = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>();
        candidate.put(ModLooseObjectProfiles.PROFILE_TYPE.typeId(), generic);
        candidate.put(ModExplosions.EXPLOSION_TYPE.typeId(), BotanyCatalogCandidateTest.mainResources("explosion", "default"));
        manager.publishEncodedCatalogs(candidate);
        var before = manager.snapshot();
        candidate.put(ModLooseObjectProfiles.PROFILE_TYPE.typeId(), profiles);
        assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(candidate));
        assertSame(before, manager.snapshot());
    }

    @Test void encodedPublicationAcceptsReferenceToExplosionInSameCandidateOnly() {
        var manager = new PrototypeManager();
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModExplosions.EXPLOSION_TYPE);
        var generic = BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic");
        var profile = generic.get(id("generic")).deepCopy();
        var damage = new JsonObject(); damage.add("allowed_groups", new com.google.gson.JsonArray());
        var payload = new JsonObject(); payload.addProperty("explosion_type", "moonstation14:new_custom");
        payload.addProperty("total_intensity", 2.0); payload.addProperty("intensity_slope", 1.0);
        payload.addProperty("max_intensity", 5.0);
        damage.add("destruction_explosion", payload); profile.add("item_damage", damage);
        var profiles = new LinkedHashMap<>(generic); profiles.put(id("candidate_test"), profile);
        var explosions = new LinkedHashMap<ResourceLocation, JsonObject>();
        explosions.put(id("new_custom"), BotanyCatalogCandidateTest.mainResources("explosion", "default").get(id("default")));
        manager.publishEncodedCatalogs(Map.of(ModLooseObjectProfiles.PROFILE_TYPE.typeId(), profiles,
                ModExplosions.EXPLOSION_TYPE.typeId(), explosions));
        assertTrue(manager.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).contains(id("candidate_test")));
        assertTrue(manager.snapshot(ModExplosions.EXPLOSION_TYPE).contains(id("new_custom")));
    }
}
