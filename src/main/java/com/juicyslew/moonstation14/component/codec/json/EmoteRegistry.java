package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;

/** Closed production allowlist for SS14-compatible reagent emotes. */
public final class EmoteRegistry {
    private static final Map<String, EmoteSpec> EMOTES = Map.of(
            "cough", new EmoteSpec("cough"),
            "crying", new EmoteSpec("crying"),
            "hew", new EmoteSpec("hew"),
            "honk", new EmoteSpec("honk"),
            "laugh", new EmoteSpec("laugh"),
            "scream", new EmoteSpec("scream"),
            "weh", new EmoteSpec("weh"),
            "whistle", new EmoteSpec("whistle"),
            "yawn", new EmoteSpec("yawn"));

    public static final Codec<String> CODEC = Codec.STRING.comapFlatMap(
            EmoteRegistry::validate, value -> value);

    private EmoteRegistry() { }

    public static Set<String> ids() { return Collections.unmodifiableSet(new TreeSet<>(EMOTES.keySet())); }
    public static Optional<EmoteSpec> find(String id) { return Optional.ofNullable(EMOTES.get(id)); }
    public static DataResult<String> validate(String id) {
        return find(id).isPresent() ? DataResult.success(id)
                : DataResult.error(() -> "Unknown emote id '" + id + "'; expected one of " + ids());
    }

    public record EmoteSpec(String sound) { }
}
