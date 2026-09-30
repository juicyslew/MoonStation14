package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns prototype types and their last successfully published catalogs.
 * Instances deliberately have no static state, so server and client managers
 * can coexist without sharing data.
 */
public final class PrototypeManager {
    private final Map<ResourceLocation, PrototypeType<?>> registeredTypes = new LinkedHashMap<>();
    private volatile Map<ResourceLocation, PrototypeCatalog<?>> publishedCatalogs = Map.of();
    private PreparedReload stagedReload;

    public synchronized <T> void register(PrototypeType<T> type) {
        Objects.requireNonNull(type, "type");
        PrototypeType<?> existingType = registeredTypes.get(type.typeId());
        if (existingType != null) {
            throw new IllegalArgumentException("Prototype type ID is already registered: " + type.typeId());
        }
        for (PrototypeType<?> existing : registeredTypes.values()) {
            if (existing.resourceDirectory().equals(type.resourceDirectory())) {
                throw new IllegalArgumentException("Prototype resource directory is already registered: "
                        + type.resourceDirectory() + " (type " + existing.typeId() + ")");
            }
        }
        // A registration changes the complete snapshot shape, so a candidate
        // prepared before it must not be allowed to commit afterward.
        discardStagedReload();
        registeredTypes.put(type.typeId(), type);
        Map<ResourceLocation, PrototypeCatalog<?>> next = new LinkedHashMap<>(publishedCatalogs);
        next.put(type.typeId(), new PrototypeCatalog<>(Map.of()));
        publishedCatalogs = immutableCatalogMap(next);
    }

    public synchronized Set<PrototypeType<?>> registeredTypes() {
        return Set.copyOf(registeredTypes.values());
    }

    /** Atomically clears published values while retaining all type registrations. */
    public synchronized void clearPublishedCatalogs() {
        discardStagedReload();
        Map<ResourceLocation, PrototypeCatalog<?>> empty = new LinkedHashMap<>();
        for (PrototypeType<?> type : registeredTypes.values()) {
            empty.put(type.typeId(), new PrototypeCatalog<>(Map.of()));
        }
        publishedCatalogs = immutableCatalogMap(empty);
    }

    public <T> PrototypeCatalog<T> snapshot(PrototypeType<T> type) {
        PrototypeType<T> registered = requireRegistered(type);
        PrototypeCatalog<?> catalog = publishedCatalogs.get(registered.typeId());
        if (catalog == null) {
            return new PrototypeCatalog<>(Map.of());
        }
        return castCatalog(catalog);
    }

    /** Returns an immutable snapshot of every registered type's catalog. */
    public Map<ResourceLocation, PrototypeCatalog<?>> snapshot() {
        return publishedCatalogs;
    }

    /**
     * Encodes one coherent, detached snapshot of every registered catalog.
     * The returned maps preserve registration and catalog insertion order.
     */
    public synchronized Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encodePublishedCatalogs() {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = new LinkedHashMap<>();
        Map<ResourceLocation, PrototypeCatalog<?>> snapshot = publishedCatalogs;
        for (PrototypeType<?> type : registeredTypes.values()) {
            PrototypeCatalog<?> catalog = snapshot.get(type.typeId());
            encoded.put(type.typeId(), encodeCatalog(type, catalog));
        }
        return Collections.unmodifiableMap(encoded);
    }

    /**
     * Decodes and validates a complete authoritative snapshot before
     * publishing any catalog. Inheritance and raw JSON catalog validation are
     * deliberately not performed for this already-resolved wire data.
     */
    public synchronized void publishEncodedCatalogs(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> encodedByType) {
        Objects.requireNonNull(encodedByType, "encodedByType");
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> owned = copyEncodedCatalogs(encodedByType);
        requireCompleteTypeSet(owned);

        Map<ResourceLocation, PrototypeCatalog<?>> prepared = new LinkedHashMap<>();
        for (PrototypeType<?> type : registeredTypes.values()) {
            prepared.put(type.typeId(), loadEncoded(type, owned.get(type.typeId())));
        }
        com.juicyslew.moonstation14.ms14.character.ModCharacters.validateOrganReferences(prepared);
        discardStagedReload();
        publishedCatalogs = immutableCatalogMap(prepared);
    }

    public <T> void reload(PrototypeType<T> type, Map<ResourceLocation, JsonObject> raw) {
        Objects.requireNonNull(raw, "raw");
        reload(Map.of(type, raw));
    }

    /**
     * Decodes and validates all supplied type catalogs before publishing any
     * of them. Catalogs not included in the set retain their last snapshot.
     */
    public synchronized void reload(
            Map<PrototypeType<?>, ? extends Map<ResourceLocation, JsonObject>> rawByType) {
        PreparedReload prepared = stage(rawByType);
        commit(prepared);
    }

    /**
     * Resolves, decodes, validates, and re-encodes a candidate without changing
     * the published catalogs. The returned token is owned by this manager and
     * can be committed at a lifecycle point where the aggregate reload is known
     * to have succeeded.
     */
    public synchronized PreparedReload stage(
            Map<PrototypeType<?>, ? extends Map<ResourceLocation, JsonObject>> rawByType) {
        return stage(rawByType, null);
    }

    /** As {@link #stage(Map)}, with validation of the complete encoded candidate. */
    public synchronized PreparedReload stage(
            Map<PrototypeType<?>, ? extends Map<ResourceLocation, JsonObject>> rawByType,
            PrototypeCandidateValidator candidateValidator) {
        Objects.requireNonNull(rawByType, "rawByType");
        // A new reload attempt invalidates an older candidate, including when
        // this attempt fails while resolving or encoding.
        discardStagedReload();

        Map<ResourceLocation, PrototypeCatalog<?>> candidate = new LinkedHashMap<>(publishedCatalogs);
        Set<ResourceLocation> preparedIds = new java.util.HashSet<>();
        for (Map.Entry<PrototypeType<?>, ? extends Map<ResourceLocation, JsonObject>> entry : rawByType.entrySet()) {
            PrototypeType<?> type = Objects.requireNonNull(entry.getKey(), "prototype type");
            Map<ResourceLocation, JsonObject> raw = Objects.requireNonNull(entry.getValue(), "raw prototypes");
            if (!preparedIds.add(type.typeId())) {
                throw new IllegalArgumentException("Prototype type ID is prepared more than once: " + type.typeId());
            }
            requireRegistered(type);
            candidate.put(type.typeId(), load(type, raw));
        }

        Map<ResourceLocation, PrototypeCatalog<?>> immutableCandidate = immutableCatalogMap(candidate);
        com.juicyslew.moonstation14.ms14.character.ModCharacters.validateOrganReferences(immutableCandidate);
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = encodeCandidate(immutableCandidate);
        if (candidateValidator != null) {
            // Do not let a validator mutate the token's detached encoded data.
            candidateValidator.validate(immutableEncodedCatalogs(encoded));
        }

        PreparedReload prepared = new PreparedReload(this, immutableCandidate);
        stagedReload = prepared;
        return prepared;
    }

    /** Commits a token, returning false for a stale, foreign, or already-used token. */
    public synchronized boolean commit(PreparedReload prepared) {
        if (prepared == null || prepared.owner != this || prepared != stagedReload || prepared.used) {
            return false;
        }
        if (!prepared.catalogs.keySet().equals(registeredTypes.keySet())) {
            // This can only happen if the registration set changed outside the
            // normal reload lifecycle. Treat the token as stale rather than
            // publishing an incomplete snapshot.
            prepared.used = true;
            stagedReload = null;
            return false;
        }
        prepared.used = true;
        stagedReload = null;
        publishedCatalogs = prepared.catalogs;
        return true;
    }

    /** Commits the current candidate, if one exists. Intended for lifecycle events. */
    public synchronized boolean commitStagedReload() {
        return stagedReload != null && commit(stagedReload);
    }

    /** Discards the current candidate without changing published catalogs. */
    public synchronized void discardStagedReload() {
        if (stagedReload != null) {
            stagedReload.used = true;
            stagedReload = null;
        }
    }

    public synchronized boolean hasStagedReload() {
        return stagedReload != null;
    }

    /** Opaque manager-owned transaction token returned by {@link #stage(Map)}. */
    public static final class PreparedReload {
        private final PrototypeManager owner;
        private final Map<ResourceLocation, PrototypeCatalog<?>> catalogs;
        private boolean used;

        private PreparedReload(PrototypeManager owner, Map<ResourceLocation, PrototypeCatalog<?>> catalogs) {
            this.owner = owner;
            this.catalogs = catalogs;
        }
    }

    /**
     * Publishes an already decoded, authoritative snapshot for one registered
     * prototype type. No JSON resolution or decoding is performed.
     */
    public synchronized <T> void publishDecoded(PrototypeType<T> type, Map<ResourceLocation, T> entries) {
        Objects.requireNonNull(entries, "entries");
        PrototypeType<T> registered = requireRegistered(type);
        PrototypeCatalog<T> catalog = copyDecoded(entries);
        registered.validator().ifPresent(validator -> validate(registered, catalog, validator));

        discardStagedReload();
        Map<ResourceLocation, PrototypeCatalog<?>> next = new LinkedHashMap<>(publishedCatalogs);
        next.put(registered.typeId(), catalog);
        publishedCatalogs = immutableCatalogMap(next);
    }

    private <T> PrototypeCatalog<T> load(PrototypeType<T> type, Map<ResourceLocation, JsonObject> raw) {
        Map<ResourceLocation, JsonObject> owned = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : raw.entrySet()) {
            owned.put(Objects.requireNonNull(entry.getKey(), "prototype id"),
                    Objects.requireNonNull(entry.getValue(), "prototype JSON").deepCopy());
        }
        PrototypeCatalog<JsonObject> ownedRaw = new PrototypeCatalog<>(owned);
        PrototypeCatalog<JsonObject> resolved = PrototypeResolver.resolve(ownedRaw.asMap(),
                type.mergeStrategy().orElse(null));
        type.jsonValidator().ifPresent(validator -> validateJson(type, ownedRaw, resolved, validator));
        Map<ResourceLocation, T> decoded = new LinkedHashMap<>();
        for (ResourceLocation id : resolved.keys()) {
            JsonObject json = resolved.get(id);
            decoded.put(id, decode(type, id, json));
        }
        PrototypeCatalog<T> catalog = new PrototypeCatalog<>(decoded);
        type.validator().ifPresent(validator -> validate(type, catalog, validator));
        return catalog;
    }

    private <T> PrototypeCatalog<T> loadEncoded(PrototypeType<T> type,
                                                  Map<ResourceLocation, JsonObject> encoded) {
        Map<ResourceLocation, T> decoded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : encoded.entrySet()) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey(), "prototype id");
            JsonObject json = Objects.requireNonNull(entry.getValue(), "prototype JSON");
            decoded.put(id, decode(type, id, json));
        }
        PrototypeCatalog<T> catalog = new PrototypeCatalog<>(decoded);
        type.validator().ifPresent(validator -> validate(type, catalog, validator));
        return catalog;
    }

    private <T> Map<ResourceLocation, JsonObject> encodeCatalog(PrototypeType<T> type,
                                                                  PrototypeCatalog<?> catalog) {
        Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();
        if (catalog == null) {
            return Collections.unmodifiableMap(encoded);
        }
        PrototypeCatalog<T> typedCatalog = castCatalog(catalog);
        for (Map.Entry<ResourceLocation, T> entry : typedCatalog.asMap().entrySet()) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey(), "prototype id");
            encoded.put(id, encode(type, id, entry.getValue()));
        }
        return Collections.unmodifiableMap(encoded);
    }

    private Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encodeCandidate(
            Map<ResourceLocation, PrototypeCatalog<?>> candidate) {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = new LinkedHashMap<>();
        for (PrototypeType<?> type : registeredTypes.values()) {
            encoded.put(type.typeId(), encodeCatalog(type, candidate.get(type.typeId())));
        }
        return immutableEncodedCatalogs(encoded);
    }

    private Map<ResourceLocation, Map<ResourceLocation, JsonObject>> immutableEncodedCatalogs(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> encoded) {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> copied = copyEncodedCatalogs(encoded);
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> immutable = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Map<ResourceLocation, JsonObject>> typeEntry : copied.entrySet()) {
            immutable.put(typeEntry.getKey(), Collections.unmodifiableMap(typeEntry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private <T> JsonObject encode(PrototypeType<T> type, ResourceLocation id, T value) {
        String path = resourcePath(type, id);
        try {
            DataResult<JsonElement> result = type.codec().encodeStart(JsonOps.INSTANCE, value);
            if (result.error().isPresent()) {
                throw new PrototypeLoadException(type.typeId(), id, path, result.error().get().message());
            }
            JsonElement encoded = result.result().orElseThrow(() -> new PrototypeLoadException(
                    type.typeId(), id, path, "codec returned no value"));
            if (!encoded.isJsonObject()) {
                throw new PrototypeLoadException(type.typeId(), id, path,
                        "codec encoded a non-object JSON value; expected a JSON object");
            }
            return encoded.getAsJsonObject().deepCopy();
        } catch (PrototypeLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PrototypeLoadException(type.typeId(), id, path,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    exception);
        }
    }

    private Map<ResourceLocation, Map<ResourceLocation, JsonObject>> copyEncodedCatalogs(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> encodedByType) {
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> owned = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> typeEntry
                : encodedByType.entrySet()) {
            Map.Entry<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> nonNullTypeEntry =
                    Objects.requireNonNull(typeEntry, "encoded type entry");
            ResourceLocation typeId = Objects.requireNonNull(nonNullTypeEntry.getKey(), "type id");
            Map<ResourceLocation, JsonObject> encoded =
                    Objects.requireNonNull(nonNullTypeEntry.getValue(), "encoded catalog");
            Map<ResourceLocation, JsonObject> ownedCatalog = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : encoded.entrySet()) {
                Map.Entry<ResourceLocation, JsonObject> nonNullEntry =
                        Objects.requireNonNull(entry, "prototype entry");
                ResourceLocation id = Objects.requireNonNull(nonNullEntry.getKey(), "prototype id");
                JsonObject json = Objects.requireNonNull(nonNullEntry.getValue(), "prototype JSON");
                ownedCatalog.put(id, json.deepCopy());
            }
            owned.put(typeId, ownedCatalog);
        }
        return owned;
    }

    private void requireCompleteTypeSet(Map<ResourceLocation, ?> encodedByType) {
        for (ResourceLocation typeId : encodedByType.keySet()) {
            if (!registeredTypes.containsKey(typeId)) {
                throw new IllegalArgumentException("Encoded prototype type is not registered: " + typeId);
            }
        }
        for (ResourceLocation typeId : registeredTypes.keySet()) {
            if (!encodedByType.containsKey(typeId)) {
                throw new IllegalArgumentException("Encoded prototype type is missing: " + typeId);
            }
        }
    }

    private <T> PrototypeCatalog<T> copyDecoded(Map<ResourceLocation, T> entries) {
        Map<ResourceLocation, T> owned = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, T> entry : entries.entrySet()) {
            Map.Entry<ResourceLocation, T> nonNullEntry = Objects.requireNonNull(entry, "prototype entry");
            owned.put(Objects.requireNonNull(nonNullEntry.getKey(), "prototype id"),
                    Objects.requireNonNull(nonNullEntry.getValue(), "decoded prototype"));
        }
        return new PrototypeCatalog<>(owned);
    }

    private void validateJson(PrototypeType<?> type, PrototypeCatalog<JsonObject> ownedRaw,
                              PrototypeCatalog<JsonObject> resolved,
                              PrototypeJsonCatalogValidator validator) {
        try {
            validator.validate(ownedRaw, resolved);
        } catch (RuntimeException exception) {
            String diagnostic = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new PrototypeLoadException(type.typeId(), null, catalogPath(type),
                    "JSON catalog validation failed: " + diagnostic, exception);
        }
    }

    private <T> T decode(PrototypeType<T> type, ResourceLocation id, JsonObject json) {
        String path = resourcePath(type, id);
        try {
            DataResult<T> result = type.codec().parse(JsonOps.INSTANCE, json);
            if (result.error().isPresent()) {
                String diagnostic = result.error().get().message();
                throw new PrototypeLoadException(type.typeId(), id, path, diagnostic);
            }
            return result.result().orElseThrow(() -> new PrototypeLoadException(type.typeId(), id, path,
                    "codec returned no value"));
        } catch (PrototypeLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PrototypeLoadException(type.typeId(), id, path,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    exception);
        }
    }

    private <T> void validate(PrototypeType<T> type, PrototypeCatalog<T> catalog,
                              PrototypeCatalogValidator<T> validator) {
        try {
            validator.validate(catalog);
        } catch (RuntimeException exception) {
            String diagnostic = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new PrototypeLoadException(type.typeId(), null, catalogPath(type),
                    "catalog validation failed: " + diagnostic, exception);
        }
    }

    private <T> PrototypeType<T> requireRegistered(PrototypeType<T> type) {
        Objects.requireNonNull(type, "type");
        PrototypeType<?> registered = registeredTypes.get(type.typeId());
        if (registered == null) {
            throw new IllegalArgumentException("Prototype type is not registered: " + type.typeId());
        }
        if (registered != type) {
            throw new IllegalArgumentException("Prototype type descriptor is not the registered instance: "
                    + type.typeId());
        }
        return type;
    }

    private static String resourcePath(PrototypeType<?> type, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + type.resourceDirectory() + "/" + id.getPath() + ".json";
    }

    private static String catalogPath(PrototypeType<?> type) {
        return "data/*/" + type.resourceDirectory() + "/*.json";
    }

    private static Map<ResourceLocation, PrototypeCatalog<?>> immutableCatalogMap(
            Map<ResourceLocation, PrototypeCatalog<?>> catalogs) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(catalogs));
    }

    @SuppressWarnings("unchecked")
    private static <T> PrototypeCatalog<T> castCatalog(PrototypeCatalog<?> catalog) {
        return (PrototypeCatalog<T>) catalog;
    }
}
