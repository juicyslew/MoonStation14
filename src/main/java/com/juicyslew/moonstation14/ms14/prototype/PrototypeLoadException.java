package com.juicyslew.moonstation14.ms14.prototype;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Thrown when a resolved prototype cannot be decoded or validated. */
public final class PrototypeLoadException extends IllegalArgumentException {
    private final ResourceLocation typeId;
    private final ResourceLocation prototypeId;
    private final String resourcePath;
    private final String diagnostic;

    public PrototypeLoadException(ResourceLocation typeId, ResourceLocation prototypeId,
                                  String resourcePath, String diagnostic) {
        this(typeId, prototypeId, resourcePath, diagnostic, null);
    }

    public PrototypeLoadException(ResourceLocation typeId, ResourceLocation prototypeId,
                                  String resourcePath, String diagnostic, Throwable cause) {
        super(formatMessage(typeId, prototypeId, resourcePath, diagnostic), cause);
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.prototypeId = prototypeId;
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    /** May be {@code null} for a catalog-wide validation failure. */
    public ResourceLocation prototypeId() {
        return prototypeId;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public String diagnostic() {
        return diagnostic;
    }

    private static String formatMessage(ResourceLocation typeId, ResourceLocation prototypeId,
                                        String resourcePath, String diagnostic) {
        return "Could not load prototype type '" + typeId + "'"
                + (prototypeId == null ? "" : " prototype '" + prototypeId + "'")
                + " from " + resourcePath + ": " + diagnostic;
    }
}
