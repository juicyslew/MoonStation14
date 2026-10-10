package com.juicyslew.moonstation14.ms14.stripping;

import java.util.Objects;

/** Stable slot address in a viewer-specific stripping projection. */
public record StripSlotRef(Kind kind, String id) {
    public enum Kind { HAND, EQUIPMENT }
    public StripSlotRef {
        Objects.requireNonNull(kind);
        if (id == null || id.isBlank() || id.length() > 64) throw new IllegalArgumentException("Invalid strip slot id");
    }
}
