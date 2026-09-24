package com.juicyslew.moonstation14.ms14.prototype.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeCatalogSyncTest {
    private static final ResourceLocation TYPE_A = id("alpha/type");
    private static final ResourceLocation TYPE_B = id("beta/type");
    private static final Codec<NumberPrototype> NUMBER_CODEC = Codec.INT.xmap(NumberPrototype::new,
            NumberPrototype::value);

    @Test
    void payloadRoundTripsNestedJsonAndNestedIds() {
        JsonObject json = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("name", "é");
        json.add("nested", nested);
        PrototypeCatalogSyncPayload payload = new PrototypeCatalogSyncPayload(
                UUID.randomUUID(), 1, 0, 1, 1,
                json.toString().getBytes(StandardCharsets.UTF_8).length,
                List.of(TYPE_A), List.of(new PrototypeCatalogSyncPayload.PrototypeEntry(
                        TYPE_A, id("nested/path/value"), json)));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PrototypeCatalogSyncPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload.encodedByteSize(), buffer.writerIndex());
            PrototypeCatalogSyncPayload decoded = PrototypeCatalogSyncPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals("é", decoded.entries().get(0).json().getAsJsonObject("nested").get("name").getAsString());
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsMalformedShapesAndBounds() {
        byte[] nonObject = payloadBytes(buffer -> {
            writeHeader(buffer, 0, 1, 1, 1, 2);
            writeString(buffer, TYPE_A.toString());
            buffer.writeVarInt(1);
            writeString(buffer, TYPE_A.toString());
            writeString(buffer, "moonstation14/not-an-object");
            buffer.writeVarInt(2);
            buffer.writeCharSequence("[]", StandardCharsets.UTF_8);
        });
        byte[] malformedJson = payloadBytes(buffer -> {
            writeHeader(buffer, 0, 1, 1, 1, 1);
            writeString(buffer, TYPE_A.toString());
            buffer.writeVarInt(1);
            writeString(buffer, TYPE_A.toString());
            writeString(buffer, "moonstation14/malformed");
            buffer.writeVarInt(1);
            buffer.writeByte('{');
        });
        byte[] oversized = payloadBytes(buffer -> {
            writeHeader(buffer, 0, 1, 1, 1, PrototypeCatalogSyncPayload.MAX_JSON_BYTES);
            writeString(buffer, TYPE_A.toString());
            buffer.writeVarInt(1);
            writeString(buffer, TYPE_A.toString());
            writeString(buffer, "moonstation14/oversized");
            buffer.writeVarInt(PrototypeCatalogSyncPayload.MAX_JSON_BYTES + 1);
        });
        byte[] boundedCount = payloadBytes(buffer ->
                writeHeader(buffer, 0, PrototypeCatalogSyncPayload.MAX_CHUNK_COUNT + 1, 0, 0, 0));
        byte[] valid = encode(payload(UUID.randomUUID(), 1, 0, 1, 0, 0, List.of(TYPE_A), List.of()));
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 0x01;
        byte[] malformedVarInt = payloadBytes(buffer -> {
            writeHeader(buffer, 0, 1, 0, 0, 0);
            buffer.writeByte(0x80);
        });

        assertDecodeRejected(nonObject);
        assertDecodeRejected(malformedJson);
        assertDecodeRejected(oversized);
        assertDecodeRejected(boundedCount);
        assertDecodeRejected(truncated);
        assertDecodeRejected(trailing);
        assertDecodeRejected(malformedVarInt);
    }

    @Test
    void decoderRejectsWhitespacePaddedBodyOverWireBudget() {
        byte[] oversized = payloadBytes(buffer -> {
            writeHeader(buffer, 0, 1, 1, 1, PrototypeCatalogSyncPayload.MAX_JSON_BYTES);
            writeString(buffer, TYPE_A.toString());
            buffer.writeVarInt(1);
            writeString(buffer, TYPE_A.toString());
            writeString(buffer, "moonstation14/padded");
            buffer.writeVarInt(PrototypeCatalogSyncPayload.MAX_JSON_BYTES);
            byte[] paddedJson = new byte[PrototypeCatalogSyncPayload.MAX_JSON_BYTES];
            Arrays.fill(paddedJson, (byte) ' ');
            paddedJson[paddedJson.length - 2] = '{';
            paddedJson[paddedJson.length - 1] = '}';
            buffer.writeBytes(paddedJson);
        });

        assertTrue(oversized.length > PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES);
        assertDecodeRejected(oversized);
    }

    @Test
    void chunkingIsDeterministicAndIncludesEmptyTypes() {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();
        entries.put(id("z/nested"), object("z"));
        entries.put(id("a/nested"), object("a"));
        catalogs.put(TYPE_B, Map.of());
        catalogs.put(TYPE_A, entries);

        List<PrototypeCatalogSyncPayload> first = PrototypeCatalogChunker.chunk(UUID.randomUUID(), 1, catalogs);
        List<PrototypeCatalogSyncPayload> second = PrototypeCatalogChunker.chunk(first.get(0).syncId(), 1, catalogs);
        assertEquals(first, second);
        assertEquals(List.of(TYPE_A, TYPE_B), first.get(0).typeIds());
        assertTrue(first.stream().allMatch(chunk -> chunk.encodedByteSize()
                <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES));
        assertEquals(2, first.get(0).totalEntryCount());
    }

    @Test
    void chunksRemainWithinActualEncodedByteBudget() {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();
        for (int index = 0; index < 24; index++) {
            entries.put(id("entry-" + String.format("%02d", index)), object("x".repeat(30_000)));
        }
        catalogs.put(TYPE_A, entries);

        List<PrototypeCatalogSyncPayload> chunks = PrototypeCatalogChunker.chunk(UUID.randomUUID(), 2, catalogs);
        assertTrue(chunks.size() > 1);
        for (PrototypeCatalogSyncPayload chunk : chunks) {
            assertTrue(chunk.encodedByteSize() <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES);
            assertEquals(chunk.encodedByteSize(), encode(chunk).length);
        }
    }

    @Test
    void validationReservesMaximumRevisionWireWidth() {
        UUID syncId = new UUID(0L, 0L);
        int revisionOneBoundary = largestValueLengthThatFits(1L);
        int maximumRevisionBoundary = largestValueLengthThatFits(Long.MAX_VALUE);
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> boundaryCatalog =
                singleEntryCatalog(revisionOneBoundary);

        assertTrue(PrototypeCatalogChunker.chunk(syncId, 1L, boundaryCatalog).get(0).encodedByteSize()
                <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> PrototypeCatalogChunker.chunk(syncId, Long.MAX_VALUE, boundaryCatalog));
        assertThrows(IllegalArgumentException.class,
                () -> PrototypeCatalogNetworking.validateSyncableCatalogs(boundaryCatalog));

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> nearBoundaryCatalog =
                singleEntryCatalog(maximumRevisionBoundary);
        assertTrue(PrototypeCatalogChunker.chunk(syncId, Long.MAX_VALUE, nearBoundaryCatalog).get(0).encodedByteSize()
                <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES);
        PrototypeCatalogNetworking.validateSyncableCatalogs(nearBoundaryCatalog);
    }

    @Test
    void productionCatalogSyncRoundTripsOutOfOrderAndPublishesAtomically() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        assertEquals(411, raw.size());

        PrototypeManager server = new PrototypeManager();
        server.register(ModReagents.REAGENT_TYPE);
        server.reload(ModReagents.REAGENT_TYPE, raw);
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();
        assertEquals(406, exported.get(ModReagents.REAGENT_TYPE.typeId()).size());

        List<PrototypeCatalogSyncPayload> chunks = PrototypeCatalogChunker.chunk(
                UUID.randomUUID(), 7, exported);
        assertEquals(chunks, PrototypeCatalogChunker.chunk(chunks.get(0).syncId(), 7, exported));
        List<PrototypeCatalogSyncPayload> wireChunks = chunks.stream().map(chunk -> decode(encode(chunk))).toList();
        int totalJsonBytes = wireChunks.get(0).totalJsonBytes();
        int largestChunkBytes = wireChunks.stream().mapToInt(PrototypeCatalogSyncPayload::encodedByteSize).max().orElseThrow();
        System.out.printf("production prototype sync: chunks=%d totalJsonBytes=%d largestChunkBytes=%d%n",
                wireChunks.size(), totalJsonBytes, largestChunkBytes);
        assertTrue(wireChunks.stream().allMatch(chunk -> chunk.encodedByteSize()
                <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES));
        assertEquals(totalJsonBytes, wireChunks.stream().flatMap(chunk -> chunk.entries().stream())
                .mapToInt(PrototypeCatalogSyncPayload.PrototypeEntry::jsonBytes).sum());

        PrototypeManager client = new PrototypeManager();
        client.register(ModReagents.REAGENT_TYPE);
        AtomicInteger publications = new AtomicInteger();
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot -> {
            publications.incrementAndGet();
            client.publishEncodedCatalogs(snapshot);
        });
        List<PrototypeCatalogSyncPayload> reversed = new ArrayList<>(wireChunks);
        reversed.sort(Comparator.comparingInt(PrototypeCatalogSyncPayload::chunkIndex).reversed());
        for (int index = 0; index < reversed.size() - 1; index++) {
            assembler.accept(reversed.get(index));
            assertEquals(0, publications.get());
        }
        assembler.accept(reversed.get(reversed.size() - 1));

        assertEquals(1, publications.get());
        assertFalse(assembler.hasStagedSync());
        assertEquals(server.encodePublishedCatalogs(), client.encodePublishedCatalogs());
    }

    @Test
    void assemblerPublishesOnlyAtCompletionAndIgnoresIdenticalDuplicate() {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> published = new LinkedHashMap<>();
        int[] publicationCount = {0};
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot -> {
            publicationCount[0]++;
            published.clear();
            published.putAll(snapshot);
        });
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs = new LinkedHashMap<>();
        catalogs.put(TYPE_A, Map.of(id("one"), object("one"), id("two"), object("two")));
        catalogs.put(TYPE_B, Map.of());
        List<PrototypeCatalogSyncPayload> chunks = PrototypeCatalogChunker.chunk(UUID.randomUUID(), 1, catalogs);

        PrototypeCatalogSyncPayload first = chunks.get(0);
        PrototypeCatalogSyncPayload second = new PrototypeCatalogSyncPayload(
                first.syncId(), first.revision(), 1, 2, first.totalEntryCount(), first.totalJsonBytes(),
                first.typeIds(), List.of());
        PrototypeCatalogSyncPayload firstOfTwo = new PrototypeCatalogSyncPayload(
                first.syncId(), first.revision(), 0, 2, first.totalEntryCount(), first.totalJsonBytes(),
                first.typeIds(), first.entries());
        assembler.accept(second);
        assertEquals(0, publicationCount[0]);
        assembler.accept(firstOfTwo);
        assertEquals(1, publicationCount[0]);
        assembler.accept(firstOfTwo);
        assertEquals(1, publicationCount[0]);
        assertEquals(2, published.get(TYPE_A).size());
        assertTrue(published.get(TYPE_B).isEmpty());
        assertFalse(assembler.hasStagedSync());
    }

    @Test
    void assemblerHandlesDuplicatesConflictsMetadataAndRevisionOrdering() {
        List<PrototypeCatalogSyncPayload> completed = twoChunkSync(UUID.randomUUID(), 1, object("old"));
        List<PrototypeCatalogSyncPayload> newer = twoChunkSync(UUID.randomUUID(), 2, object("new"));
        List<JsonObject> publishedValues = new ArrayList<>();
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot ->
                publishedValues.add(snapshot.get(TYPE_A).get(id("value"))));

        assembler.accept(newer.get(1));
        assembler.accept(completed.get(0));
        assertEquals(1, assembler.stagedChunkCount());
        assembler.accept(newer.get(0));
        assertEquals(List.of(object("new")), publishedValues);

        assembler.accept(newer.get(0));
        assertEquals(1, publishedValues.size());
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
                twoChunkSync(newer.get(0).syncId(), 2, object("conflict")).get(0)));
        assertFalse(assembler.hasStagedSync());

        List<PrototypeCatalogSyncPayload> metadataBase = twoChunkSync(UUID.randomUUID(), 3, object("metadata"));
        assembler.accept(metadataBase.get(0));
        PrototypeCatalogSyncPayload metadataConflict = new PrototypeCatalogSyncPayload(
                metadataBase.get(0).syncId(), 3, 1, 2, metadataBase.get(0).totalEntryCount(),
                metadataBase.get(0).totalJsonBytes() + 1, metadataBase.get(0).typeIds(), List.of());
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(metadataConflict));
        assertFalse(assembler.hasStagedSync());
    }

    @Test
    void newerSyncReplacesOlderIncompleteStaging() {
        List<PrototypeCatalogSyncPayload> older = twoChunkSync(UUID.randomUUID(), 10, object("old"));
        List<PrototypeCatalogSyncPayload> newer = twoChunkSync(UUID.randomUUID(), 11, object("new"));
        List<JsonObject> publishedValues = new ArrayList<>();
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot ->
                publishedValues.add(snapshot.get(TYPE_A).get(id("value"))));

        assembler.accept(older.get(0));
        assembler.accept(newer.get(1));
        assembler.accept(newer.get(0));

        assertEquals(List.of(object("new")), publishedValues);
    }

    @Test
    void emptyTypesPublishAsEmptyCatalogs() {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs = new LinkedHashMap<>();
        catalogs.put(TYPE_A, Map.of());
        catalogs.put(TYPE_B, Map.of());
        PrototypeCatalogSyncPayload payload = PrototypeCatalogChunker.chunk(UUID.randomUUID(), 1, catalogs).get(0);
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> published = new LinkedHashMap<>();
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot -> published.putAll(snapshot));

        assembler.accept(decode(encode(payload)));

        assertEquals(List.of(TYPE_A, TYPE_B), new ArrayList<>(published.keySet()));
        assertTrue(published.values().stream().allMatch(Map::isEmpty));
    }

    @Test
    void importFailureRetainsPreviousClientSnapshotAndClearsStaging() {
        PrototypeType<NumberPrototype> type = new PrototypeType<>(id("number"), "number", NUMBER_CODEC);
        PrototypeManager client = new PrototypeManager();
        client.register(type);
        client.publishDecoded(type, Map.of(id("stable"), new NumberPrototype(7)));
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(client::publishEncodedCatalogs);
        JsonObject invalid = new JsonObject();
        invalid.addProperty("value", "not-a-number");
        PrototypeCatalogSyncPayload payload = new PrototypeCatalogSyncPayload(
                UUID.randomUUID(), 1, 0, 1, 1, invalid.toString().getBytes(StandardCharsets.UTF_8).length,
                List.of(type.typeId()), List.of(new PrototypeCatalogSyncPayload.PrototypeEntry(
                        type.typeId(), id("replacement"), invalid)));

        assertThrows(RuntimeException.class, () -> assembler.accept(decode(encode(payload))));
        assertFalse(assembler.hasStagedSync());
        assertEquals(new NumberPrototype(7), client.snapshot(type).get(id("stable")));
        assertFalse(client.snapshot(type).contains(id("replacement")));
    }

    @Test
    void publisherFailureClearsStaging() {
        PrototypeCatalogSyncPayload payload = new PrototypeCatalogSyncPayload(
                UUID.randomUUID(), 1, 0, 1, 0, 0, List.of(TYPE_A), List.of());
        PrototypeCatalogSyncAssembler assembler = new PrototypeCatalogSyncAssembler(snapshot -> {
            throw new IllegalStateException("publisher failed");
        });

        assertThrows(IllegalArgumentException.class, () -> assembler.accept(decode(encode(payload))));
        assertFalse(assembler.hasStagedSync());
    }

    private static JsonObject object(String value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    private static Map<ResourceLocation, Map<ResourceLocation, JsonObject>> singleEntryCatalog(int valueLength) {
        return Map.of(TYPE_A, Map.of(id("boundary"), object("x".repeat(valueLength))));
    }

    private static int largestValueLengthThatFits(long revision) {
        int low = 0;
        int high = PrototypeCatalogSyncPayload.MAX_JSON_BYTES;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            if (singleEntryChunkFits(revision, candidate)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private static boolean singleEntryChunkFits(long revision, int valueLength) {
        try {
            PrototypeCatalogChunker.chunk(new UUID(0L, 0L), revision, singleEntryCatalog(valueLength));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static List<PrototypeCatalogSyncPayload> twoChunkSync(UUID syncId, long revision, JsonObject value) {
        PrototypeCatalogSyncPayload.PrototypeEntry entry = new PrototypeCatalogSyncPayload.PrototypeEntry(
                TYPE_A, id("value"), value);
        return List.of(
                payload(syncId, revision, 0, 2, 1, entry.jsonBytes(), List.of(TYPE_A), List.of(entry)),
                payload(syncId, revision, 1, 2, 1, entry.jsonBytes(), List.of(TYPE_A), List.of()));
    }

    private static PrototypeCatalogSyncPayload payload(UUID syncId, long revision, int chunkIndex, int chunkCount,
                                                        int totalEntries, int totalJsonBytes,
                                                        List<ResourceLocation> types,
                                                        List<PrototypeCatalogSyncPayload.PrototypeEntry> entries) {
        return new PrototypeCatalogSyncPayload(syncId, revision, chunkIndex, chunkCount, totalEntries,
                totalJsonBytes, types, entries);
    }

    private static byte[] encode(PrototypeCatalogSyncPayload payload) {
        return payloadBytes(buffer -> PrototypeCatalogSyncPayload.STREAM_CODEC.encode(buffer, payload));
    }

    private static PrototypeCatalogSyncPayload decode(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
        try {
            return PrototypeCatalogSyncPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertDecodeRejected(byte[] bytes) {
        assertThrows(RuntimeException.class, () -> decode(bytes));
    }

    private interface BufferWriter {
        void write(RegistryFriendlyByteBuf buffer);
    }

    private static byte[] payloadBytes(BufferWriter writer) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            writer.write(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, int chunkIndex, int chunkCount,
                                    int typeCount, int totalEntries, int totalJsonBytes) {
        buffer.writeLong(0L);
        buffer.writeLong(1L);
        buffer.writeVarLong(1L);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarInt(typeCount);
        buffer.writeVarInt(totalEntries);
        buffer.writeVarInt(totalJsonBytes);
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeVarInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static Map<ResourceLocation, JsonObject> loadResources() throws IOException {
        String resourceRoot = "data/moonstation14/moonstation14/reagent";
        URL root = PrototypeCatalogSyncTest.class.getClassLoader().getResource(resourceRoot);
        if (root == null) {
            throw new IOException("Classpath resource directory not found: " + resourceRoot);
        }
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        if ("file".equals(root.getProtocol())) {
            readDirectory(raw, Path.of(URI.create(root.toString())));
        } else if ("jar".equals(root.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                jar.stream().filter(entry -> entry.getName().startsWith(resourceRoot + "/")
                                && entry.getName().endsWith(".json"))
                        .sorted(Comparator.comparing(JarEntry::getName))
                        .forEach(entry -> read(raw, entry.getName().substring(entry.getName().lastIndexOf('/') + 1),
                                jar, entry));
            }
        } else if ("union".equals(root.getProtocol())) {
            String unionPath = root.toString();
            int buildMarker = unionPath.indexOf("/build/");
            if (buildMarker < 0) {
                throw new IOException("Could not locate Gradle build directory in classpath URL: " + root);
            }
            String projectPath = URLDecoder.decode(unionPath.substring("union:".length(), buildMarker),
                    StandardCharsets.UTF_8);
            if (projectPath.startsWith("/") && projectPath.length() > 2 && projectPath.charAt(2) == ':') {
                projectPath = projectPath.substring(1);
            }
            readDirectory(raw, Path.of(projectPath).resolve("build/resources/main").resolve(resourceRoot));
        } else {
            throw new IOException("Unsupported classpath URL: " + root);
        }
        return raw;
    }

    private static void readDirectory(Map<ResourceLocation, JsonObject> raw, Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().forEach(path -> read(raw, path.getFileName().toString(), path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list classpath resource directory " + directory, exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            read(raw, filename, input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, JarFile jar, JarEntry entry) {
        try (InputStream input = jar.getInputStream(entry)) {
            read(raw, filename, input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + entry.getName(), exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, InputStream input) {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            String path = filename.substring(0, filename.length() - ".json".length());
            raw.put(id(path), JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + filename, exception);
        }
    }

    private record NumberPrototype(int value) {
    }
}
