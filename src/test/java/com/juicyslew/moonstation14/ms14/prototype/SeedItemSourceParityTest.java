package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedItemProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SeedItemSourceParityTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void packetLeafSeedDefaultsMatchResolvedItemProfiles() throws Exception {
        var seedRows = BotanyCatalogSourceParityTest.read("source-seed-rows.json").getAsJsonArray();
        var packets = BotanyCatalogSourceParityTest.read("packets/entity-rows.json").getAsJsonArray();
        var packetById = new LinkedHashMap<String, com.google.gson.JsonObject>();
        for (var element : packets) {
            var row = element.getAsJsonObject();
            assertNull(packetById.put(row.get("id").getAsString(), row));
        }
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.register(ModSeedItemProfiles.TYPE);
        var before = manager.snapshot();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs ->
                catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, json) -> seeds.put(key,
                        SeedProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())));
        assertSame(before, manager.snapshot());
        var names = seeds.entrySet().stream().filter(e -> !e.getKey().equals(id("defaults")))
                .map(e -> e.getValue().packet().getPath()).distinct().toList();
        assertEquals(70, names.size());
        var main = Set.of("wheat_seeds", "tomato_seeds", "potato_seeds", "poppy_seeds", "aloe_seeds");
        var itemJson = BotanyCatalogCandidateTest.resources("seed_item_profile", names.stream().filter(n -> !main.contains(n)).toArray(String[]::new));
        itemJson.putAll(BotanyCatalogCandidateTest.mainResources("seed_item_profile", main.toArray(String[]::new)));
        var decoded = new LinkedHashMap<ResourceLocation, SeedItemProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(), ModSeedItemProfiles.TYPE, itemJson), catalogs ->
                catalogs.get(ModSeedItemProfiles.TYPE.typeId()).forEach((key, json) -> decoded.put(key,
                        SeedItemProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())));
        assertSame(before, manager.snapshot());
        var sourceIds = new HashSet<String>();
        var aliases = new HashMap<String, String>();
        var sourcePacketIds = new HashSet<String>();
        var sourceByPacketId = new LinkedHashMap<String, com.google.gson.JsonObject>();
        for (var element : seedRows) {
            var source = element.getAsJsonObject(); var sourceId = source.get("id").getAsString();
            var key = BotanyCatalogSourceParityTest.snake(sourceId);
            var packetPrototype = source.get("packetPrototype").getAsString();
            var packet = packetById.get(packetPrototype);
            assertNotNull(packet, "missing source packet " + packetPrototype);
            var localPacket = seeds.get(id(key));
            assertNotNull(localPacket, "missing source seed " + key);
            var itemAlias = localPacket.packet().getPath();
            var previousAlias = aliases.putIfAbsent(packetPrototype, itemAlias);
            assertTrue(previousAlias == null || previousAlias.equals(itemAlias), "inconsistent alias for " + packetPrototype);
            sourceIds.add(sourceId);
            sourcePacketIds.add(packetPrototype);
            sourceByPacketId.putIfAbsent(packetPrototype, source);
        }
        var checks = new ArrayList<Executable>();
        for (var packetPrototype : sourcePacketIds) {
            var source = sourceByPacketId.get(packetPrototype);
            var sourceId = source.get("id").getAsString();
            var key = BotanyCatalogSourceParityTest.snake(sourceId);
            var itemAlias = aliases.get(packetPrototype);
            var packet = packetById.get(packetPrototype);
            var record = packet.getAsJsonObject("record");
            checks.add(() -> assertAll(sourceId + " -> " + key + " -> " + itemAlias,
                    () -> assertEquals("Resources/Prototypes/Entities/Objects/Specific/Hydroponics/seeds.yml", packet.get("sourcePath").getAsString()),
                    () -> assertEquals(packetPrototype, record.get("id").getAsString()),
                    () -> {
                        var components = record.getAsJsonArray("components").asList().stream().filter(c -> c.getAsJsonObject().get("type").getAsString().equals("Seed")).toList();
                        assertEquals(1, components.size());
                        var component = components.get(0).getAsJsonObject();
                         assertTrue(component.has("seedId") && component.get("seedId").isJsonPrimitive(), "missing required source seedId for " + packetPrototype);
                         var expected = id(BotanyCatalogSourceParityTest.snake(component.get("seedId").getAsString()));
                         assertEquals(expected, decoded.get(id(itemAlias)).seedId());
                     },
                      () -> {
                          var seedComponent = record.getAsJsonArray("components").asList().stream()
                                  .map(c -> c.getAsJsonObject())
                                  .filter(c -> c.get("type").getAsString().equals("Seed")).findFirst().orElseThrow();
                          assertTrue(!seedComponent.has("healthOverride") || seedComponent.get("healthOverride").isJsonNull(),
                                  "unexpected Seed healthOverride in " + packetPrototype);
                      }
              ));
        }
        assertEquals(71, sourceIds.size()); assertEquals(70, sourcePacketIds.size()); assertEquals(70, aliases.size()); assertEquals(70, decoded.size());
        assertEquals(70, checks.size()); assertEquals(70, new HashSet<>(aliases.values()).size());
        var concreteIds = BotanyCatalogSourceParityTest.read("packets/metadata.json").getAsJsonObject().getAsJsonArray("requestedLeafIds").asList().stream().map(e -> e.getAsString()).collect(java.util.stream.Collectors.toSet());
        assertEquals(sourcePacketIds, concreteIds);
        assertEquals(Set.of("BaseItem", "BasePaperLabelable", "BaseSyndicateContraband", "SeedBase"), new HashSet<>(BotanyCatalogSourceParityTest.read("packets/metadata.json").getAsJsonObject().getAsJsonArray("capturedEntityIds").asList().stream().map(e -> e.getAsString()).filter(s -> !sourcePacketIds.contains(s)).toList()));
        assertAll("packet item defaults", checks);
        assertEquals(id("pea"), decoded.get(id("pea_seeds")).seedId());
        assertEquals(id("pea_seeds"), seeds.get(id("world_pea")).packet());
    }

    @Test void canonicalPacketFootprintSizesMatchCapturedSeedBaseComposition() throws Exception {
        var rows = BotanyCatalogSourceParityTest.read("packets/entity-rows.json").getAsJsonArray();
        var byId = new HashMap<String, com.google.gson.JsonObject>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            byId.put(row.get("id").getAsString(), row);
        }
        var seedRows = BotanyCatalogSourceParityTest.read("source-seed-rows.json").getAsJsonArray();
        var packetIds = new LinkedHashMap<String, String>();
        for (var element : seedRows) {
            var source = element.getAsJsonObject();
            var packet = source.get("packetPrototype").getAsString();
            packetIds.putIfAbsent(packet, BotanyCatalogSourceParityTest.snake(packet));
        }
        var raw = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "tiny"));
        var mainPackets = Set.of("wheat_seeds", "tomato_seeds", "potato_seeds", "poppy_seeds", "aloe_seeds");
        int testFootprints = 0;
        for (String profile : packetIds.values()) {
            String path = mainPackets.contains(profile)
                    ? "data/moonstation14/moonstation14/item_footprint/" + profile + ".json"
                    : "botany-catalog-candidate/item_footprint/" + profile + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                raw.put(id(profile), com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                if (!mainPackets.contains(profile)) testFootprints++;
            }
        }
        var manager = new PrototypeManager();
        manager.register(ModItemFootprints.TYPE);
        var resolvedCatalog = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, ItemFootprintProfile>>();
        manager.stage(Map.of(ModItemFootprints.TYPE, raw), catalogs -> {
            var values = new LinkedHashMap<ResourceLocation, ItemFootprintProfile>();
            catalogs.get(ModItemFootprints.TYPE.typeId()).forEach((key, json) -> values.put(key,
                    ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow()));
            resolvedCatalog.set(values);
        });
        var catalog = resolvedCatalog.get();
        assertEquals(70, packetIds.size());
        assertEquals(65, testFootprints);
        var seedBaseItem = byId.get("SeedBase").getAsJsonObject("record").getAsJsonArray("components").asList().stream()
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(c -> c.get("type").getAsString().equals("Item")).findFirst().orElseThrow();
        assertEquals("Tiny", seedBaseItem.get("size").getAsString(), "captured inherited SeedBase Item size");
        int compared = 0;
        for (var entry : packetIds.entrySet()) {
            var source = byId.get(entry.getKey());
            assertNotNull(source, "missing captured source packet " + entry.getKey());
            var record = source.getAsJsonObject("record");
            var ownItem = record.getAsJsonArray("components").asList().stream()
                    .map(com.google.gson.JsonElement::getAsJsonObject)
                    .filter(c -> c.get("type").getAsString().equals("Item")).findFirst().orElse(null);
            String sourceSize = ownItem == null ? seedBaseItem.get("size").getAsString() : ownItem.get("size").getAsString();
            assertEquals("Tiny", sourceSize, entry.getKey() + " captured resolved Item.size");
            var resolved = catalog.get(id(entry.getValue()));
            assertNotNull(resolved, entry.getValue());
            assertEquals(ItemFootprintProfile.ItemSize.valueOf(sourceSize), resolved.itemSize().orElseThrow(), entry.getKey());
            assertEquals(1, resolved.cells().size(), entry.getKey() + " one-cell Minecraft grid adaptation");
            compared++;
        }
        assertEquals(70, compared);
    }


    @Test void packetSourceMetadataClosesOverAllRecords() throws Exception {
        var metadata = BotanyCatalogSourceParityTest.read("packets/metadata.json").getAsJsonObject();
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", metadata.get("sourceRevision").getAsString());
        var rows = BotanyCatalogSourceParityTest.read("packets/entity-rows.json").getAsJsonArray();
        var byId = new HashMap<String, com.google.gson.JsonObject>();
        for (var element : rows) { var row = element.getAsJsonObject(); assertNull(byId.put(row.get("id").getAsString(), row)); }
        assertEquals(74, byId.size()); assertEquals(74, metadata.getAsJsonArray("capturedEntityIds").size());
        assertEquals(new HashSet<>(metadata.getAsJsonArray("capturedEntityIds").asList().stream().map(e -> e.getAsString()).toList()), byId.keySet());
        assertEquals(70, metadata.get("capturedConcreteProductCount").getAsInt()); assertEquals(4, metadata.get("capturedAdditionalAncestorCount").getAsInt());
        for (var row : byId.values()) {
            assertTrue(row.get("sourcePath").getAsString().startsWith("Resources/Prototypes/"));
            var record = row.getAsJsonObject("record"); assertEquals(row.get("id").getAsString(), record.get("id").getAsString());
            var parent = record.get("parent");
            if (parent != null && parent.isJsonPrimitive()) assertTrue(byId.containsKey(parent.getAsString()), row.get("id").getAsString());
            else if (parent != null) parent.getAsJsonArray().forEach(p -> assertTrue(byId.containsKey(p.getAsString()), row.get("id").getAsString()));
        }
        assertTrue(metadata.get("sourceRecordFormat").getAsString().contains("True and False"));
        assertTrue(metadata.get("sourceRecordFormat").getAsString().contains("boolean scalar values remain booleans"));
    }
}

