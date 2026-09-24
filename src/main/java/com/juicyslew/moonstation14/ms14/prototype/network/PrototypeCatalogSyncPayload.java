package com.juicyslew.moonstation14.ms14.prototype.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A bounded chunk of one authoritative, all-prototype-type snapshot. */
public final class PrototypeCatalogSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath("moonstation14", "prototype_catalog_sync");
    public static final Type<PrototypeCatalogSyncPayload> TYPE = new Type<>(PAYLOAD_ID);
    public static final int MAX_ENCODED_BYTES = 512 * 1024;
    public static final int MAX_CHUNK_COUNT = 4096;
    public static final int MAX_ENTRIES_PER_CHUNK = 8192;
    public static final int MAX_TYPE_COUNT = 256;
    public static final int MAX_TOTAL_ENTRIES = 100_000;
    public static final int MAX_TOTAL_JSON_BYTES = 32 * 1024 * 1024;
    public static final int MAX_JSON_BYTES = MAX_ENCODED_BYTES;
    public static final int MAX_ID_BYTES = 512;

    public static final StreamCodec<RegistryFriendlyByteBuf, PrototypeCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.of(PrototypeCatalogSyncPayload::write, PrototypeCatalogSyncPayload::read);

    private final UUID syncId;
    private final long revision;
    private final int chunkIndex;
    private final int chunkCount;
    private final int totalEntryCount;
    private final int totalJsonBytes;
    private final List<ResourceLocation> typeIds;
    private final List<PrototypeEntry> entries;

    public PrototypeCatalogSyncPayload(UUID syncId, long revision, int chunkIndex, int chunkCount,
                                       int totalEntryCount, int totalJsonBytes,
                                       List<ResourceLocation> typeIds, List<PrototypeEntry> entries) {
        this.syncId = Objects.requireNonNull(syncId, "syncId");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (chunkCount < 1 || chunkCount > MAX_CHUNK_COUNT || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("invalid chunk index/count");
        }
        if (totalEntryCount < 0 || totalEntryCount > MAX_TOTAL_ENTRIES
                || totalJsonBytes < 0 || totalJsonBytes > MAX_TOTAL_JSON_BYTES) {
            throw new IllegalArgumentException("invalid snapshot totals");
        }
        this.revision = revision;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalEntryCount = totalEntryCount;
        this.totalJsonBytes = totalJsonBytes;

        Objects.requireNonNull(typeIds, "typeIds");
        if (typeIds.size() > MAX_TYPE_COUNT) {
            throw new IllegalArgumentException("too many prototype types");
        }
        List<ResourceLocation> copiedTypes = new ArrayList<>(typeIds.size());
        ResourceLocation previous = null;
        for (ResourceLocation typeId : typeIds) {
            Objects.requireNonNull(typeId, "typeId");
            if (previous != null && previous.toString().compareTo(typeId.toString()) >= 0) {
                throw new IllegalArgumentException("type IDs must be unique and sorted");
            }
            if (utf8Length(typeId.toString()) > MAX_ID_BYTES) {
                throw new IllegalArgumentException("prototype type ID is too long");
            }
            copiedTypes.add(typeId);
            previous = typeId;
        }
        this.typeIds = Collections.unmodifiableList(copiedTypes);

        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_ENTRIES_PER_CHUNK || entries.size() > totalEntryCount) {
            throw new IllegalArgumentException("too many entries in chunk");
        }
        int chunkJsonBytes = 0;
        List<PrototypeEntry> copiedEntries = new ArrayList<>(entries.size());
        Set<String> entryKeys = new HashSet<>();
        for (PrototypeEntry entry : entries) {
            PrototypeEntry copied = Objects.requireNonNull(entry, "entry");
            if (!this.typeIds.contains(copied.typeId())) {
                throw new IllegalArgumentException("entry references an unlisted prototype type");
            }
            if (!entryKeys.add(copied.typeId() + "\u0000" + copied.prototypeId())) {
                throw new IllegalArgumentException("duplicate prototype entry in chunk");
            }
            chunkJsonBytes = Math.addExact(chunkJsonBytes, copied.jsonBytes());
            copiedEntries.add(copied);
        }
        if (chunkJsonBytes > totalJsonBytes) {
            throw new IllegalArgumentException("chunk JSON bytes exceed snapshot total");
        }
        this.entries = Collections.unmodifiableList(copiedEntries);
        if (encodedByteSize() > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("encoded prototype sync chunk exceeds byte budget");
        }
    }

    public UUID syncId() {
        return syncId;
    }

    public long revision() {
        return revision;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public int totalEntryCount() {
        return totalEntryCount;
    }

    public int totalJsonBytes() {
        return totalJsonBytes;
    }

    public List<ResourceLocation> typeIds() {
        return typeIds;
    }

    public List<PrototypeEntry> entries() {
        return entries;
    }

    /** Exact byte count written by {@link #STREAM_CODEC}, excluding the payload type ID. */
    public int encodedByteSize() {
        long size = 16L + varIntSize(revision) + varIntSize(chunkIndex) + varIntSize(chunkCount)
                + varIntSize(typeIds.size()) + varIntSize(totalEntryCount) + varIntSize(totalJsonBytes)
                + varIntSize(entries.size());
        for (ResourceLocation typeId : typeIds) {
            size += stringWireSize(typeId.toString());
        }
        for (PrototypeEntry entry : entries) {
            size += stringWireSize(entry.typeId().toString());
            size += stringWireSize(entry.prototypeId().toString());
            size += varIntSize(entry.jsonBytes()) + entry.jsonBytes();
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("encoded chunk size overflow");
        }
        return (int) size;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PrototypeCatalogSyncPayload payload)) {
            return false;
        }
        return syncId.equals(payload.syncId) && revision == payload.revision
                && chunkIndex == payload.chunkIndex && chunkCount == payload.chunkCount
                && totalEntryCount == payload.totalEntryCount && totalJsonBytes == payload.totalJsonBytes
                && typeIds.equals(payload.typeIds) && entries.equals(payload.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(syncId, revision, chunkIndex, chunkCount, totalEntryCount,
                totalJsonBytes, typeIds, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, PrototypeCatalogSyncPayload payload) {
        buffer.writeLong(payload.syncId.getMostSignificantBits());
        buffer.writeLong(payload.syncId.getLeastSignificantBits());
        buffer.writeVarLong(payload.revision);
        buffer.writeVarInt(payload.chunkIndex);
        buffer.writeVarInt(payload.chunkCount);
        buffer.writeVarInt(payload.typeIds.size());
        buffer.writeVarInt(payload.totalEntryCount);
        buffer.writeVarInt(payload.totalJsonBytes);
        for (ResourceLocation typeId : payload.typeIds) {
            writeString(buffer, typeId.toString(), MAX_ID_BYTES);
        }
        buffer.writeVarInt(payload.entries.size());
        for (PrototypeEntry entry : payload.entries) {
            writeString(buffer, entry.typeId().toString(), MAX_ID_BYTES);
            writeString(buffer, entry.prototypeId().toString(), MAX_ID_BYTES);
            byte[] json = entry.wireJsonBytes();
            buffer.writeVarInt(json.length);
            buffer.writeBytes(json);
        }
    }

    private static PrototypeCatalogSyncPayload read(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("prototype sync payload exceeds byte budget");
        }
        try {
            UUID syncId = new UUID(buffer.readLong(), buffer.readLong());
            long revision = readPositiveVarLong(buffer, "revision");
            int chunkIndex = readBoundedVarInt(buffer, MAX_CHUNK_COUNT, "chunk index");
            int chunkCount = readBoundedVarInt(buffer, MAX_CHUNK_COUNT, "chunk count");
            int typeCount = readBoundedVarInt(buffer, MAX_TYPE_COUNT, "type count");
            int totalEntries = readBoundedVarInt(buffer, MAX_TOTAL_ENTRIES, "total entry count");
            int totalJsonBytes = readBoundedVarInt(buffer, MAX_TOTAL_JSON_BYTES, "total JSON bytes");

            List<ResourceLocation> typeIds = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) {
                typeIds.add(readId(buffer, "type ID"));
            }
            int entryCount = readBoundedVarInt(buffer, MAX_ENTRIES_PER_CHUNK, "entry count");
            List<PrototypeEntry> entries = new ArrayList<>(entryCount);
            int decodedJsonBytes = 0;
            for (int i = 0; i < entryCount; i++) {
                ResourceLocation typeId = readId(buffer, "entry type ID");
                ResourceLocation prototypeId = readId(buffer, "prototype ID");
                int jsonBytes = readBoundedVarInt(buffer, MAX_JSON_BYTES, "JSON length");
                if (jsonBytes > totalJsonBytes - decodedJsonBytes) {
                    throw new IllegalArgumentException("JSON bytes exceed snapshot total");
                }
                byte[] json = readBytes(buffer, jsonBytes, "JSON");
                String jsonText = decodeUtf8(json);
                JsonElement parsed = JsonParser.parseString(jsonText);
                if (!parsed.isJsonObject()) {
                    throw new IllegalArgumentException("prototype JSON must be an object");
                }
                entries.add(new PrototypeEntry(typeId, prototypeId, parsed.getAsJsonObject()));
                decodedJsonBytes += jsonBytes;
            }
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("trailing bytes in prototype sync payload");
            }
            PrototypeCatalogSyncPayload payload = new PrototypeCatalogSyncPayload(
                    syncId, revision, chunkIndex, chunkCount, totalEntries, totalJsonBytes, typeIds, entries);
            if (payload.encodedByteSize() > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("encoded prototype sync chunk exceeds byte budget");
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed prototype sync payload", exception);
        }
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("string exceeds byte bound");
        }
        buffer.writeVarInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static ResourceLocation readId(RegistryFriendlyByteBuf buffer, String label) {
        String value = decodeUtf8(readBytes(buffer, readBoundedVarInt(buffer, MAX_ID_BYTES, label), label));
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid " + label + ": " + value, exception);
        }
    }

    private static byte[] readBytes(RegistryFriendlyByteBuf buffer, int length, String label) {
        if (length < 0 || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("truncated " + label);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static int readBoundedVarInt(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        int value = readCanonicalVarInt(buffer, label);
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("invalid " + label + ": " + value);
        }
        return value;
    }

    private static long readPositiveVarLong(RegistryFriendlyByteBuf buffer, String label) {
        long value = readCanonicalVarLong(buffer, label);
        if (value <= 0) {
            throw new IllegalArgumentException("invalid " + label + ": " + value);
        }
        return value;
    }

    private static int readCanonicalVarInt(RegistryFriendlyByteBuf buffer, String label) {
        int result = 0;
        for (int index = 0; index < 5; index++) {
            if (!buffer.isReadable()) {
                throw new IllegalArgumentException("truncated " + label);
            }
            int next = buffer.readUnsignedByte();
            if (index == 4 && (next & 0xF0) != 0) {
                throw new IllegalArgumentException("malformed " + label);
            }
            result |= (next & 0x7F) << (index * 7);
            if ((next & 0x80) == 0) {
                if (varIntSize(result) != index + 1) {
                    throw new IllegalArgumentException("non-canonical " + label);
                }
                return result;
            }
        }
        throw new IllegalArgumentException("malformed " + label);
    }

    private static long readCanonicalVarLong(RegistryFriendlyByteBuf buffer, String label) {
        long result = 0;
        for (int index = 0; index < 10; index++) {
            if (!buffer.isReadable()) {
                throw new IllegalArgumentException("truncated " + label);
            }
            int next = buffer.readUnsignedByte();
            if (index == 9 && (next & 0xFE) != 0) {
                throw new IllegalArgumentException("malformed " + label);
            }
            result |= (long) (next & 0x7F) << (index * 7);
            if ((next & 0x80) == 0) {
                if (varIntSize(result) != index + 1) {
                    throw new IllegalArgumentException("non-canonical " + label);
                }
                return result;
            }
        }
        throw new IllegalArgumentException("malformed " + label);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid UTF-8", exception);
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int stringWireSize(String value) {
        int bytes = utf8Length(value);
        return varIntSize(bytes) + bytes;
    }

    private static int varIntSize(long value) {
        if (value < 0) {
            return 10;
        }
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    public static final class PrototypeEntry {
        private final ResourceLocation typeId;
        private final ResourceLocation prototypeId;
        private final JsonObject json;
        private final int jsonBytes;
        private final byte[] wireJsonBytes;

        public PrototypeEntry(ResourceLocation typeId, ResourceLocation prototypeId, JsonObject json) {
            this.typeId = Objects.requireNonNull(typeId, "typeId");
            this.prototypeId = Objects.requireNonNull(prototypeId, "prototypeId");
            this.json = Objects.requireNonNull(json, "json").deepCopy();
            this.wireJsonBytes = this.json.toString().getBytes(StandardCharsets.UTF_8);
            this.jsonBytes = wireJsonBytes.length;
            if (jsonBytes > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("prototype JSON is too large");
            }
            if (utf8Length(typeId.toString()) > MAX_ID_BYTES || utf8Length(prototypeId.toString()) > MAX_ID_BYTES) {
                throw new IllegalArgumentException("prototype ID is too long");
            }
        }

        public ResourceLocation typeId() {
            return typeId;
        }

        public ResourceLocation prototypeId() {
            return prototypeId;
        }

        public JsonObject json() {
            return json.deepCopy();
        }

        public int jsonBytes() {
            return jsonBytes;
        }

        private byte[] wireJsonBytes() {
            return wireJsonBytes;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PrototypeEntry entry)) {
                return false;
            }
            return typeId.equals(entry.typeId) && prototypeId.equals(entry.prototypeId)
                    && json.equals(entry.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeId, prototypeId, json);
        }
    }
}
