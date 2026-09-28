package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict in-memory JSON codec; deliberately performs no filesystem or world operations. */
public final class LifecycleProfileCodec {
    private LifecycleProfileCodec() { }

    public static String encode(SavedLifecycleProfile value) {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", value.schemaVersion());
        o.addProperty("accountId", value.accountId().toString());
        o.addProperty("profileKey", value.profileKey());
        o.addProperty("mindId", value.mindId().toString());
        o.addProperty("bodyId", value.bodyId().toString());
        o.addProperty("dimension", value.dimension());
        JsonObject p = new JsonObject(); p.addProperty("x", value.location().x());
        p.addProperty("y", value.location().y()); p.addProperty("z", value.location().z()); o.add("location", p);
        JsonObject appearance = new JsonObject();
        value.appearance().forEach(appearance::addProperty); o.add("appearance", appearance);
        o.addProperty("connectionEpoch", value.connectionEpoch().toString());
        o.addProperty("connectionGeneration", value.connectionGeneration());
        o.addProperty("state", value.state().name()); o.addProperty("revision", value.revision());
        if (value.offlineSinceMillis() == null) o.add("offlineSinceMillis", null);
        else o.addProperty("offlineSinceMillis", value.offlineSinceMillis());
        return o.toString();
    }

    public static SavedLifecycleProfile decode(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            JsonElement root = readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing data");
            if (!root.isJsonObject()) throw new IllegalArgumentException("profile must be an object");
            JsonObject o = root.getAsJsonObject();
            requireKeys(o, Set.of("schemaVersion", "accountId", "profileKey", "mindId", "bodyId", "dimension",
                    "location", "appearance", "connectionEpoch", "connectionGeneration", "state", "revision", "offlineSinceMillis"));
            requireKeys(o.getAsJsonObject("location"), Set.of("x", "y", "z"));
            return new SavedLifecycleProfile(integer(o, "schemaVersion"), uuid(o, "accountId"), text(o, "profileKey"),
                    uuid(o, "mindId"), uuid(o, "bodyId"), text(o, "dimension"),
                    new SavedLifecycleProfile.Location(number(o.getAsJsonObject("location"), "x"),
                            number(o.getAsJsonObject("location"), "y"), number(o.getAsJsonObject("location"), "z")),
                    stringMap(o.getAsJsonObject("appearance")), uuid(o, "connectionEpoch"),
                    longValue(o, "connectionGeneration"), state(o), longValue(o, "revision"), nullableLong(o, "offlineSinceMillis"));
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new IllegalArgumentException("invalid or truncated lifecycle profile", e);
        }
    }

    /** Reject duplicate ownership keys across a file/batch rather than selecting an arbitrary winner. */
    public static List<SavedLifecycleProfile> decodeBatch(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json)); reader.setLenient(false);
            JsonElement root = readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !root.isJsonArray()) throw new IllegalArgumentException("batch must be one JSON array");
            List<SavedLifecycleProfile> profiles = new ArrayList<>();
            Set<UUID> accounts = new HashSet<>(), minds = new HashSet<>(), bodies = new HashSet<>();
            Set<ProfileKey> keys = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray()) {
                SavedLifecycleProfile profile = decode(element.toString());
                if (!accounts.add(profile.accountId()) || !keys.add(new ProfileKey(profile.accountId(), profile.profileKey()))
                        || !minds.add(profile.mindId()) || !bodies.add(profile.bodyId()))
                    throw new IllegalArgumentException("duplicate account/profile/Mind/body ownership");
                profiles.add(profile);
            }
            return List.copyOf(profiles);
        } catch (IOException e) { throw new IllegalArgumentException("invalid or truncated lifecycle batch", e); }
    }

    public static String encodeStore(LifecycleStoreEnvelope envelope) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", envelope.schemaVersion());
        root.addProperty("storeRevision", envelope.storeRevision());
        root.addProperty("epochToken", envelope.epochToken().toString());
        root.addProperty("generationCounter", envelope.generationCounter());
        JsonArray profiles = new JsonArray();
        envelope.profiles().forEach(p -> profiles.add(com.google.gson.JsonParser.parseString(encode(p))));
        root.add("profiles", profiles);
        return root.toString();
    }

    public static LifecycleStoreEnvelope decodeStore(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json)); reader.setLenient(false);
            JsonElement parsed = readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !parsed.isJsonObject())
                throw new IllegalArgumentException("store must be one JSON object");
            JsonObject root = parsed.getAsJsonObject();
            requireKeys(root, Set.of("schemaVersion", "storeRevision", "epochToken", "generationCounter", "profiles"));
            if (!root.get("profiles").isJsonArray()) throw new IllegalArgumentException("profiles must be an array");
            return new LifecycleStoreEnvelope(integer(root, "schemaVersion"), longValue(root, "storeRevision"),
                    uuid(root, "epochToken"), longValue(root, "generationCounter"),
                    decodeBatch(root.get("profiles").toString()));
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new IllegalArgumentException("invalid or truncated lifecycle store", e);
        }
    }

    static void validateStoreProfiles(UUID epoch, long counter, List<SavedLifecycleProfile> profiles) {
        Set<UUID> accounts = new HashSet<>(), minds = new HashSet<>(), bodies = new HashSet<>();
        Set<ProfileKey> keys = new HashSet<>();
        for (SavedLifecycleProfile profile : profiles) {
            if (!epoch.equals(profile.connectionEpoch()) || profile.connectionGeneration() > counter)
                throw new IllegalArgumentException("profile generation does not belong to durable store epoch/counter");
            if (!accounts.add(profile.accountId()) || !keys.add(new ProfileKey(profile.accountId(), profile.profileKey()))
                    || !minds.add(profile.mindId()) || !bodies.add(profile.bodyId()))
                throw new IllegalArgumentException("duplicate account/profile/Mind/body ownership");
        }
    }

    private record ProfileKey(UUID accountId, String profileKey) { }

    /** Reads JSON recursively while detecting duplicate object member names before Gson can overwrite them. */
    private static JsonElement readValue(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject(); Set<String> names = new HashSet<>(); r.beginObject();
                while (r.hasNext()) { String name = r.nextName(); if (!names.add(name)) throw new IllegalArgumentException("duplicate JSON key: " + name); object.add(name, readValue(r)); }
                r.endObject(); yield object;
            }
            case BEGIN_ARRAY -> { JsonArray array = new JsonArray(); r.beginArray(); while (r.hasNext()) array.add(readValue(r)); r.endArray(); yield array; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield com.google.gson.JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("unexpected JSON token: " + r.peek());
        };
    }

    private static String text(JsonObject o, String key) { JsonElement e = required(o, key); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be text"); return e.getAsString(); }
    private static UUID uuid(JsonObject o, String key) { return UUID.fromString(text(o, key)); }
    private static int integer(JsonObject o, String key) { return Math.toIntExact(longValue(o, key)); }
    private static long longValue(JsonObject o, String key) { JsonElement e = required(o, key); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric"); return new BigDecimal(e.getAsString()).longValueExact(); }
    private static double number(JsonObject o, String key) { JsonElement e = required(o, key); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric"); return e.getAsDouble(); }
    private static SavedLifecycleProfile.State state(JsonObject o) { return SavedLifecycleProfile.State.valueOf(text(o, "state")); }
    private static Long nullableLong(JsonObject o, String key) { JsonElement e = required(o, key); if (e.isJsonNull()) return null; if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be null or numeric"); return new BigDecimal(e.getAsString()).longValueExact(); }
    private static JsonElement required(JsonObject o, String key) { JsonElement value = o.get(key); if (value == null) throw new IllegalArgumentException("missing " + key); return value; }
    private static void requireKeys(JsonObject object, Set<String> expected) {
        if (object == null || !object.keySet().equals(expected)) throw new IllegalArgumentException("missing or unknown object fields");
    }
    private static Map<String, String> stringMap(JsonObject o) { java.util.TreeMap<String, String> result = new java.util.TreeMap<>(); for (var e : o.entrySet()) { if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString()) throw new IllegalArgumentException("appearance values must be text"); result.put(e.getKey(), e.getValue().getAsString()); } return result; }
}
