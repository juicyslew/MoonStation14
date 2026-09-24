package com.juicyslew.moonstation14.ms14.prototype;

import com.mojang.serialization.Codec;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * The immutable description needed to load one family of prototypes.
 *
 * @param <T> decoded prototype value type
 */
public final class PrototypeType<T> {
    private final ResourceLocation typeId;
    private final String resourceDirectory;
    private final Codec<T> codec;
    private final Optional<PrototypeMergeStrategy> mergeStrategy;
    private final Optional<PrototypeJsonCatalogValidator> jsonValidator;
    private final Optional<PrototypeCatalogValidator<T>> validator;

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec) {
        this(typeId, resourceDirectory, codec, null, null, null);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                          PrototypeMergeStrategy mergeStrategy) {
        this(typeId, resourceDirectory, codec, mergeStrategy, null, null);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                          PrototypeCatalogValidator<T> validator) {
        this(typeId, resourceDirectory, codec, null, null, validator);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                         PrototypeJsonCatalogValidator jsonValidator) {
        this(typeId, resourceDirectory, codec, null, jsonValidator, null);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                          PrototypeMergeStrategy mergeStrategy,
                          PrototypeCatalogValidator<T> validator) {
        this(typeId, resourceDirectory, codec, mergeStrategy, null, validator);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                         PrototypeMergeStrategy mergeStrategy,
                         PrototypeJsonCatalogValidator jsonValidator) {
        this(typeId, resourceDirectory, codec, mergeStrategy, jsonValidator, null);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                         PrototypeJsonCatalogValidator jsonValidator,
                         PrototypeCatalogValidator<T> validator) {
        this(typeId, resourceDirectory, codec, null, jsonValidator, validator);
    }

    public PrototypeType(ResourceLocation typeId, String resourceDirectory, Codec<T> codec,
                          PrototypeMergeStrategy mergeStrategy,
                          PrototypeJsonCatalogValidator jsonValidator,
                          PrototypeCatalogValidator<T> validator) {
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.resourceDirectory = validateDirectory(resourceDirectory);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.mergeStrategy = Optional.ofNullable(mergeStrategy);
        this.jsonValidator = Optional.ofNullable(jsonValidator);
        this.validator = Optional.ofNullable(validator);
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public String resourceDirectory() {
        return resourceDirectory;
    }

    public Codec<T> codec() {
        return codec;
    }

    public Optional<PrototypeMergeStrategy> mergeStrategy() {
        return mergeStrategy;
    }

    public Optional<PrototypeJsonCatalogValidator> jsonValidator() {
        return jsonValidator;
    }

    public Optional<PrototypeCatalogValidator<T>> validator() {
        return validator;
    }

    private static String validateDirectory(String value) {
        Objects.requireNonNull(value, "resourceDirectory");
        String directory = value.strip();
        if (directory.isEmpty() || directory.startsWith("/") || directory.endsWith("/")
                || directory.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("resourceDirectory must be a relative non-empty path: " + value);
        }
        for (String segment : directory.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("resourceDirectory contains an invalid path segment: " + value);
            }
        }
        return directory;
    }
}
