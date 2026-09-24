package com.juicyslew.moonstation14.ms14.prototype.network;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Deterministically turns an encoded all-type catalog into bounded packets. */
public final class PrototypeCatalogChunker {
    private static final Comparator<ResourceLocation> ID_ORDER = Comparator.comparing(ResourceLocation::toString);

    private PrototypeCatalogChunker() {
    }

    public static List<PrototypeCatalogSyncPayload> chunk(UUID syncId, long revision,
                                                            Map<ResourceLocation,
                                                                    ? extends Map<ResourceLocation, JsonObject>> catalogs) {
        Objects.requireNonNull(syncId, "syncId");
        Objects.requireNonNull(catalogs, "catalogs");
        List<ResourceLocation> typeIds = new ArrayList<>(catalogs.keySet());
        for (ResourceLocation typeId : typeIds) {
            Objects.requireNonNull(typeId, "prototype type ID");
        }
        typeIds.sort(ID_ORDER);
        if (typeIds.size() > PrototypeCatalogSyncPayload.MAX_TYPE_COUNT) {
            throw new IllegalArgumentException("too many registered prototype types");
        }

        List<PrototypeCatalogSyncPayload.PrototypeEntry> allEntries = new ArrayList<>();
        for (ResourceLocation typeId : typeIds) {
            if (typeId == null) {
                throw new IllegalArgumentException("prototype type ID cannot be null");
            }
            Map<ResourceLocation, JsonObject> catalog = Objects.requireNonNull(catalogs.get(typeId), "catalog");
            List<ResourceLocation> prototypeIds = new ArrayList<>(catalog.keySet());
            prototypeIds.sort(ID_ORDER);
            for (ResourceLocation prototypeId : prototypeIds) {
                JsonObject json = Objects.requireNonNull(catalog.get(prototypeId), "prototype JSON");
                allEntries.add(new PrototypeCatalogSyncPayload.PrototypeEntry(typeId, prototypeId, json));
            }
        }
        if (allEntries.size() > PrototypeCatalogSyncPayload.MAX_TOTAL_ENTRIES) {
            throw new IllegalArgumentException("too many prototype entries");
        }
        long totalJsonBytesLong = 0;
        for (PrototypeCatalogSyncPayload.PrototypeEntry entry : allEntries) {
            totalJsonBytesLong = Math.addExact(totalJsonBytesLong, entry.jsonBytes());
        }
        if (totalJsonBytesLong > PrototypeCatalogSyncPayload.MAX_TOTAL_JSON_BYTES) {
            throw new IllegalArgumentException("prototype JSON snapshot is too large");
        }
        int totalJsonBytes = (int) totalJsonBytesLong;

        List<List<PrototypeCatalogSyncPayload.PrototypeEntry>> chunkEntries = new ArrayList<>();
        List<PrototypeCatalogSyncPayload.PrototypeEntry> current = new ArrayList<>();
        for (PrototypeCatalogSyncPayload.PrototypeEntry entry : allEntries) {
            List<PrototypeCatalogSyncPayload.PrototypeEntry> candidate = new ArrayList<>(current);
            candidate.add(entry);
            if (!fits(syncId, revision, PrototypeCatalogSyncPayload.MAX_CHUNK_COUNT,
                    allEntries.size(), totalJsonBytes, typeIds, candidate)) {
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("single prototype JSON cannot fit in a sync chunk");
                }
                chunkEntries.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(entry);
                if (!fits(syncId, revision, PrototypeCatalogSyncPayload.MAX_CHUNK_COUNT,
                        allEntries.size(), totalJsonBytes, typeIds, current)) {
                    throw new IllegalArgumentException("single prototype JSON cannot fit in a sync chunk");
                }
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty() || chunkEntries.isEmpty()) {
            chunkEntries.add(List.copyOf(current));
        }
        if (chunkEntries.size() > PrototypeCatalogSyncPayload.MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("too many prototype sync chunks");
        }

        int chunkCount = chunkEntries.size();
        List<PrototypeCatalogSyncPayload> result = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            PrototypeCatalogSyncPayload payload = new PrototypeCatalogSyncPayload(
                    syncId, revision, index, chunkCount, allEntries.size(), totalJsonBytes,
                    typeIds, chunkEntries.get(index));
            if (payload.encodedByteSize() > PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES) {
                throw new IllegalStateException("chunker produced an oversized sync chunk");
            }
            result.add(payload);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean fits(UUID syncId, long revision, int chunkCount, int totalEntries, int totalJsonBytes,
                                List<ResourceLocation> typeIds,
                                List<PrototypeCatalogSyncPayload.PrototypeEntry> entries) {
        try {
            return new PrototypeCatalogSyncPayload(syncId, revision, 0, chunkCount, totalEntries, totalJsonBytes,
                    typeIds, entries).encodedByteSize() <= PrototypeCatalogSyncPayload.MAX_ENCODED_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
