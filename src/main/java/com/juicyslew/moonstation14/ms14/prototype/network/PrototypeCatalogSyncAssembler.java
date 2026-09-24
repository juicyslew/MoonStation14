package com.juicyslew.moonstation14.ms14.prototype.network;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Stages chunks and publishes one complete cross-type catalog atomically. */
public final class PrototypeCatalogSyncAssembler {
    private final Consumer<Map<ResourceLocation, Map<ResourceLocation, JsonObject>>> publisher;
    private Staging staging;
    private UUID lastCompletedSyncId;
    private long lastCompletedRevision;
    private Map<Integer, PrototypeCatalogSyncPayload> lastCompletedChunks = Map.of();

    public PrototypeCatalogSyncAssembler(
            Consumer<Map<ResourceLocation, Map<ResourceLocation, JsonObject>>> publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public synchronized void accept(PrototypeCatalogSyncPayload payload) {
        Objects.requireNonNull(payload, "payload");
        // A newer incomplete snapshot must not be disturbed by late packets from an older one.
        if (staging != null && payload.revision() < staging.revision) {
            return;
        }
        if (lastCompletedSyncId != null) {
            if (payload.revision() < lastCompletedRevision) {
                return;
            }
            if (payload.revision() == lastCompletedRevision
                    && payload.syncId().equals(lastCompletedSyncId)) {
                PrototypeCatalogSyncPayload completed = lastCompletedChunks.get(payload.chunkIndex());
                if (completed != null && completed.equals(payload)) {
                    return;
                }
                fail("conflicting duplicate completed sync chunk");
            } else if (payload.revision() == lastCompletedRevision) {
                fail("different sync IDs share a completed revision");
            }
        }
        if (staging != null) {
            if (payload.revision() < staging.revision) {
                return;
            }
            if (payload.revision() > staging.revision) {
                staging = new Staging(payload);
            } else if (!payload.syncId().equals(staging.syncId)) {
                fail("different sync IDs share a revision");
            } else if (!staging.metadataMatches(payload)) {
                fail("conflicting sync metadata");
            }
        } else {
            staging = new Staging(payload);
        }

        PrototypeCatalogSyncPayload previous = staging.chunks.get(payload.chunkIndex());
        if (previous != null) {
            if (!previous.equals(payload)) {
                fail("conflicting duplicate sync chunk");
            }
            return;
        }
        staging.chunks.put(payload.chunkIndex(), payload);
        for (PrototypeCatalogSyncPayload.PrototypeEntry entry : payload.entries()) {
            if (!staging.typeIds.contains(entry.typeId())) {
                fail("entry references an unlisted prototype type");
            }
            EntryKey key = new EntryKey(entry.typeId(), entry.prototypeId());
            if (!staging.entries.add(key)) {
                fail("duplicate prototype entry across sync chunks");
            }
            staging.jsonBytes += entry.jsonBytes();
        }
        staging.entryCount += payload.entries().size();
        if (staging.entryCount > staging.totalEntryCount
                || staging.entryCount > PrototypeCatalogSyncPayload.MAX_TOTAL_ENTRIES
                || staging.jsonBytes > PrototypeCatalogSyncPayload.MAX_TOTAL_JSON_BYTES) {
            fail("staged prototype snapshot exceeds bounds");
        }

        if (staging.chunks.size() == staging.chunkCount) {
            finish(staging);
        }
    }

    public synchronized void clear() {
        staging = null;
        lastCompletedSyncId = null;
        lastCompletedRevision = 0;
        lastCompletedChunks = Map.of();
    }

    public synchronized boolean hasStagedSync() {
        return staging != null;
    }

    public synchronized int stagedChunkCount() {
        return staging == null ? 0 : staging.chunks.size();
    }

    private void finish(Staging complete) {
        if (complete.entryCount != complete.totalEntryCount || complete.jsonBytes != complete.totalJsonBytes) {
            fail("sync ended with incomplete entry totals");
        }
        for (int index = 0; index < complete.chunkCount; index++) {
            if (!complete.chunks.containsKey(index)) {
                fail("sync is missing a chunk");
            }
        }

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> assembled = new LinkedHashMap<>();
        for (ResourceLocation typeId : complete.typeIds) {
            assembled.put(typeId, new LinkedHashMap<>());
        }
        for (int index = 0; index < complete.chunkCount; index++) {
            for (PrototypeCatalogSyncPayload.PrototypeEntry entry : complete.chunks.get(index).entries()) {
                Map<ResourceLocation, JsonObject> catalog = assembled.get(entry.typeId());
                if (catalog == null) {
                    fail("entry references an unlisted prototype type");
                }
                catalog.put(entry.prototypeId(), entry.json());
            }
        }
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> immutable = immutableSnapshot(assembled);
        staging = null;
        try {
            publisher.accept(immutable);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("could not publish prototype catalog snapshot", exception);
        }
        lastCompletedSyncId = complete.syncId;
        lastCompletedRevision = complete.revision;
        lastCompletedChunks = Collections.unmodifiableMap(new LinkedHashMap<>(complete.chunks));
    }

    private void fail(String message) {
        staging = null;
        throw new IllegalArgumentException(message);
    }

    private static Map<ResourceLocation, Map<ResourceLocation, JsonObject>> immutableSnapshot(
            Map<ResourceLocation, Map<ResourceLocation, JsonObject>> source) {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> copied = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Map<ResourceLocation, JsonObject>> type : source.entrySet()) {
            Map<ResourceLocation, JsonObject> catalog = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : type.getValue().entrySet()) {
                catalog.put(entry.getKey(), entry.getValue().deepCopy());
            }
            copied.put(type.getKey(), Collections.unmodifiableMap(catalog));
        }
        return Collections.unmodifiableMap(copied);
    }

    private static final class Staging {
        private final UUID syncId;
        private final long revision;
        private final int chunkCount;
        private final int totalEntryCount;
        private final int totalJsonBytes;
        private final List<ResourceLocation> typeIds;
        private final Map<Integer, PrototypeCatalogSyncPayload> chunks = new LinkedHashMap<>();
        private final java.util.Set<EntryKey> entries = new java.util.HashSet<>();
        private int entryCount;
        private int jsonBytes;

        private Staging(PrototypeCatalogSyncPayload first) {
            syncId = first.syncId();
            revision = first.revision();
            chunkCount = first.chunkCount();
            totalEntryCount = first.totalEntryCount();
            totalJsonBytes = first.totalJsonBytes();
            typeIds = List.copyOf(first.typeIds());
        }

        private boolean metadataMatches(PrototypeCatalogSyncPayload payload) {
            return chunkCount == payload.chunkCount()
                    && totalEntryCount == payload.totalEntryCount()
                    && totalJsonBytes == payload.totalJsonBytes()
                    && typeIds.equals(payload.typeIds());
        }
    }

    private record EntryKey(ResourceLocation typeId, ResourceLocation prototypeId) {
    }
}
