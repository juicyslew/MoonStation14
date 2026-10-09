package com.juicyslew.moonstation14.ms14.puddle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import java.util.Set;

/** Resolved supported Puddle composition: metadata, solution capacity and examine configuration. */
public record PuddleData(ExamineMetadata metadata, SolutionContainer solutionContainer,
                         ExaminableSolution examinableSolution) {
    public static final float DEFAULT_CAPACITY = 1000f;
    public static final String DEFAULT_SOLUTION_NAME = "puddle";
    public static final boolean DEFAULT_EXACT_VOLUME = false;

    public record SolutionContainer(float maxVolume) {
        public static final Codec<SolutionContainer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("maxVolume", DEFAULT_CAPACITY).forGetter(SolutionContainer::maxVolume)
        ).apply(i, SolutionContainer::new));
        public SolutionContainer {
            if (!Float.isFinite(maxVolume) || maxVolume <= 0) throw new IllegalArgumentException("invalid puddle solution capacity");
        }
    }

    public record ExaminableSolution(String solutionName, boolean exactVolume) {
        public static final Codec<ExaminableSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("solutionName", DEFAULT_SOLUTION_NAME).forGetter(ExaminableSolution::solutionName),
                Codec.BOOL.optionalFieldOf("exactVolume", DEFAULT_EXACT_VOLUME).forGetter(ExaminableSolution::exactVolume)
        ).apply(i, ExaminableSolution::new));
        public ExaminableSolution {
            if (solutionName == null || solutionName.isBlank() || solutionName.length() > 128)
                throw new IllegalArgumentException("invalid puddle logical solution name");
        }
    }

    private static final Codec<PuddleData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            ExamineMetadata.CODEC.optionalFieldOf("metadata", ExamineMetadata.EMPTY).forGetter(PuddleData::metadata),
            SolutionContainer.CODEC.optionalFieldOf("solutionContainer", new SolutionContainer(DEFAULT_CAPACITY)).forGetter(PuddleData::solutionContainer),
            ExaminableSolution.CODEC.optionalFieldOf("examinableSolution", new ExaminableSolution(DEFAULT_SOLUTION_NAME, DEFAULT_EXACT_VOLUME)).forGetter(PuddleData::examinableSolution)
    ).apply(i, PuddleData::new));

    public static final Codec<PuddleData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<PuddleData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement e ? e : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) throw new IllegalArgumentException("puddle prototype must be an object");
                JsonObject root = json.getAsJsonObject();
                fields(root, Set.of("id", "parent", "abstract", "metadata", "solutionContainer", "examinableSolution"));
                if (root.has("metadata")) fieldsObject(root.get("metadata"), Set.of("name", "description"), "metadata");
                if (root.has("solutionContainer")) fieldsObject(root.get("solutionContainer"), Set.of("maxVolume"), "solutionContainer");
                if (root.has("examinableSolution")) fieldsObject(root.get("examinableSolution"), Set.of("solutionName", "exactVolume"), "examinableSolution");
                return STRUCTURAL.decode(ops, input);
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid puddle prototype: " + e.getMessage()); }
        }
    });

    public PuddleData {
        if (metadata == null || solutionContainer == null || examinableSolution == null)
            throw new IllegalArgumentException("missing puddle composition");
    }
    private static void fieldsObject(JsonElement element, Set<String> allowed, String name) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        fields(element.getAsJsonObject(), allowed);
    }
    private static void fields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key) || object.get(key).isJsonNull())
            throw new IllegalArgumentException("unknown or null puddle field: " + key);
    }
}
