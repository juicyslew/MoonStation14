package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SeedItemProfileTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void optionalSeedIdentityRoundTripsWithoutLosingComponentPresence() {
        var noSeed = new SeedItemProfile(null);
        var encoded = SeedItemProfile.CODEC.encodeStart(JsonOps.INSTANCE, noSeed).getOrThrow();
        assertTrue(encoded.isJsonObject());
        assertNull(SeedItemProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().seedId());
        var seeded = new SeedItemProfile(id("wheat"));
        assertEquals(seeded, SeedItemProfile.CODEC.parse(JsonOps.INSTANCE,
                SeedItemProfile.CODEC.encodeStart(JsonOps.INSTANCE, seeded).getOrThrow()).getOrThrow());
    }

    @Test void actualItemProfilesResolveInheritanceAndPreserveUnboundComponentPresence() {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(id("base"), object("{\"abstract\":true,\"seed_id\":\"moonstation14:wheat\"}"));
        raw.put(id("inherited"), object("{\"parent\":\"moonstation14:base\"}"));
        raw.put(id("carried_only"), object("{}"));
        raw.putAll(BotanyCatalogCandidateTest.mainResources("seed_item_profile",
                "aloe_seeds", "poppy_seeds", "potato_seeds", "tomato_seeds", "wheat_seeds"));
        var seedsRaw = BotanyTestSeedFixtures.mainCatalog();
        var manager = new PrototypeManager();
        manager.register(ModSeedItemProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var decoded = new AtomicReference<Map<ResourceLocation, SeedItemProfile>>();
        var seedsDecoded = new AtomicReference<Map<ResourceLocation, SeedProfile>>();
        manager.stage(Map.of(ModSeedItemProfiles.TYPE, raw, ModSeedProfiles.TYPE, seedsRaw), catalogs -> {
            var values = new LinkedHashMap<ResourceLocation, SeedItemProfile>();
            catalogs.get(ModSeedItemProfiles.TYPE.typeId()).forEach((key, json) -> values.put(key,
                    SeedItemProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
            decoded.set(Map.copyOf(values));
            var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, json) -> seeds.put(key,
                    SeedProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
            seedsDecoded.set(Map.copyOf(seeds));
        });
        assertSame(before, manager.snapshot());
        assertEquals(7, decoded.get().size());
        assertFalse(decoded.get().containsKey(id("base")), "abstract prototype is not a concrete item");
        assertEquals(id("wheat"), decoded.get().get(id("inherited")).seedId());
        assertNull(decoded.get().get(id("carried_only")).seedId());
        assertEquals(72, seedsDecoded.get().size());
        for (String seed : new String[]{"wheat", "tomato", "potato", "poppy", "aloe"})
            assertNotNull(seedsDecoded.get().get(id(seed)), "missing baseline seed " + seed);
        assertNotNull(seedsDecoded.get().get(id("defaults")), "reserved defaults template must resolve");
        assertEquals(SetHolder.of("aloe_seeds", "poppy_seeds", "potato_seeds", "tomato_seeds", "wheat_seeds"),
                decoded.get().keySet().stream().filter(key -> key.getPath().endsWith("_seeds"))
                        .map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet()));
        decoded.get().forEach((key, profile) -> {
            if (key.getPath().endsWith("_seeds")) {
                var seedId = id(key.getPath().replace("_seeds", ""));
                assertEquals(seedId, profile.seedId());
                assertEquals(key, seedsDecoded.get().get(seedId).packet());
            }
        });
    }

    private static JsonObject object(String json) { return com.google.gson.JsonParser.parseString(json).getAsJsonObject(); }
    private static final class SetHolder {
        static java.util.Set<String> of(String... values) { return java.util.Set.of(values); }
    }
}
