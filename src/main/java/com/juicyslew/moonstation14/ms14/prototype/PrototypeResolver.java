package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves raw JSON prototypes without decoding them into domain records.
 * Resolution is presence-aware: an explicitly supplied value, including an
 * empty object or array, is different from an absent value.
 */
public final class PrototypeResolver {
    private static final String ID = "id";
    private static final String PARENT = "parent";
    private static final String ABSTRACT = "abstract";

    private PrototypeResolver() {
    }

    public static PrototypeCatalog<JsonObject> resolve(Map<ResourceLocation, JsonObject> raw) {
        return resolve(raw, null);
    }

    public static PrototypeCatalog<JsonObject> resolve(Map<ResourceLocation, JsonObject> raw,
                                                       PrototypeMergeStrategy strategy) {
        Objects.requireNonNull(raw, "raw");
        Map<ResourceLocation, JsonObject> ownedRaw = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : raw.entrySet()) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey(), "prototype id");
            JsonObject object = Objects.requireNonNull(entry.getValue(), "prototype " + id);
            ownedRaw.put(id, object.deepCopy());
        }

        Resolver resolver = new Resolver(ownedRaw, strategy);
        Map<ResourceLocation, JsonObject> concrete = new LinkedHashMap<>();
        for (ResourceLocation id : ownedRaw.keySet()) {
            JsonObject resolved = resolver.resolve(id, new ArrayList<>());
            if (!resolver.metadata(id).abstractPrototype()) {
                concrete.put(id, resolved.deepCopy());
            }
        }
        return new PrototypeCatalog<>(concrete);
    }

    private static final class Resolver {
        private final Map<ResourceLocation, JsonObject> raw;
        private final PrototypeMergeStrategy strategy;
        private final Map<ResourceLocation, Metadata> metadataCache = new HashMap<>();
        private final Map<ResourceLocation, JsonObject> resolvedCache = new HashMap<>();

        private Resolver(Map<ResourceLocation, JsonObject> raw, PrototypeMergeStrategy strategy) {
            this.raw = raw;
            this.strategy = strategy;
        }

        private JsonObject resolve(ResourceLocation id, List<ResourceLocation> dependencyPath) {
            JsonObject cached = resolvedCache.get(id);
            if (cached != null) {
                return cached.deepCopy();
            }

            int cycleStart = dependencyPath.indexOf(id);
            if (cycleStart >= 0) {
                List<ResourceLocation> cycle = new ArrayList<>(dependencyPath.subList(cycleStart, dependencyPath.size()));
                cycle.add(id);
                throw failure("Inheritance cycle detected: " + formatPath(cycle));
            }

            JsonObject local = raw.get(id);
            if (local == null) {
                throw failure("Missing prototype '" + id + "' (dependency path: " + formatPathWith(id, dependencyPath) + ")");
            }
            Metadata metadata = metadata(id);
            List<ResourceLocation> nextPath = new ArrayList<>(dependencyPath);
            nextPath.add(id);

            List<JsonObject> parents = new ArrayList<>();
            for (ResourceLocation parentId : metadata.parents()) {
                if (!raw.containsKey(parentId)) {
                    throw failure("Missing parent '" + parentId + "' for '" + id
                            + "' (dependency path: " + formatPathWith(parentId, nextPath) + ")");
                }
                parents.add(resolve(parentId, nextPath));
            }

            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : local.entrySet()) {
                if (!PARENT.equals(entry.getKey()) && !ABSTRACT.equals(entry.getKey())) {
                    result.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }

            Set<String> handledFields = new HashSet<>();
            if (strategy != null) {
                Set<String> candidateFields = new HashSet<>();
                candidateFields.addAll(local.keySet());
                for (JsonObject parent : parents) {
                    candidateFields.addAll(parent.keySet());
                }
                for (String fieldName : candidateFields) {
                    if (strategy.handles(fieldName)) {
                        handledFields.add(fieldName);
                        JsonElement merged = strategy.merge(fieldName, id, local, parents);
                        if (merged != null) {
                            result.add(fieldName, merged.deepCopy());
                        } else {
                            result.remove(fieldName);
                        }
                    }
                }
            }

            // Child fields are already present. Parents are visited in order,
            // so the first parent wins when multiple parents provide a field.
            for (JsonObject parent : parents) {
                for (Map.Entry<String, JsonElement> entry : parent.entrySet()) {
                    String fieldName = entry.getKey();
                    if (ID.equals(fieldName) || PARENT.equals(fieldName) || ABSTRACT.equals(fieldName)
                            || handledFields.contains(fieldName)) {
                        continue;
                    }
                    if (!local.has(fieldName) && !result.has(fieldName)) {
                        result.add(fieldName, entry.getValue().deepCopy());
                    }
                }
            }

            resolvedCache.put(id, result.deepCopy());
            return result;
        }

        private Metadata metadata(ResourceLocation id) {
            Metadata cached = metadataCache.get(id);
            if (cached != null) {
                return cached;
            }
            JsonObject object = raw.get(id);
            if (object == null) {
                throw failure("Missing prototype '" + id + "'");
            }

            List<ResourceLocation> parents = parseParents(id, object);
            boolean abstractPrototype = false;
            if (object.has(ABSTRACT)) {
                JsonElement value = object.get(ABSTRACT);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                    throw failure("Prototype '" + id + "' has malformed 'abstract': expected a boolean");
                }
                abstractPrototype = value.getAsBoolean();
            }
            Metadata metadata = new Metadata(List.copyOf(parents), abstractPrototype);
            metadataCache.put(id, metadata);
            return metadata;
        }

        private List<ResourceLocation> parseParents(ResourceLocation childId, JsonObject object) {
            if (!object.has(PARENT)) {
                return List.of();
            }
            JsonElement value = object.get(PARENT);
            List<String> parentNames = new ArrayList<>();
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                parentNames.add(value.getAsString());
            } else if (value != null && value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                if (array.isEmpty()) {
                    throw failure("Prototype '" + childId + "' has an empty 'parent' array");
                }
                for (JsonElement item : array) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        throw failure("Prototype '" + childId + "' has malformed 'parent' array: expected only strings");
                    }
                    parentNames.add(item.getAsString());
                }
            } else {
                throw failure("Prototype '" + childId + "' has malformed 'parent': expected a string or ordered array of strings");
            }

            Set<ResourceLocation> seen = new HashSet<>();
            List<ResourceLocation> parents = new ArrayList<>();
            for (String parentName : parentNames) {
                if (parentName.isBlank()) {
                    throw failure("Prototype '" + childId + "' has an empty parent id");
                }
                ResourceLocation parentId = parseId(parentName, childId);
                if (!seen.add(parentId)) {
                    throw failure("Prototype '" + childId + "' declares duplicate parent '" + parentId + "'");
                }
                parents.add(parentId);
            }
            return parents;
        }

        private ResourceLocation parseId(String value, ResourceLocation childId) {
            try {
                int separator = value.indexOf(':');
                if (separator < 0) {
                    return ResourceLocation.fromNamespaceAndPath(childId.getNamespace(), value);
                }
                if (separator == 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
                    throw new IllegalArgumentException("invalid namespace/path separator");
                }
                return ResourceLocation.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
            } catch (RuntimeException exception) {
                throw failure("Prototype '" + childId + "' has invalid parent id '" + value + "'", exception);
            }
        }

        private PrototypeResolutionException failure(String message) {
            return new PrototypeResolutionException(message);
        }

        private PrototypeResolutionException failure(String message, Throwable cause) {
            return new PrototypeResolutionException(message, cause);
        }

        private String formatPathWith(ResourceLocation last, List<ResourceLocation> path) {
            List<ResourceLocation> full = new ArrayList<>(path);
            full.add(last);
            return formatPath(full);
        }

        private String formatPath(List<ResourceLocation> path) {
            return String.join(" -> ", path.stream().map(ResourceLocation::toString).toList());
        }

        private record Metadata(List<ResourceLocation> parents, boolean abstractPrototype) {
        }
    }
}
