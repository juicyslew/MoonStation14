package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.Objects;
import java.util.List;

/** Named endpoint port, shared by source and sink declarations. */
public record DevicePort(String id, String name, String description, List<String> defaultLinks) {
    public DevicePort(String id, String name, String description) { this(id, name, description, List.of()); }
    public DevicePort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        defaultLinks=List.copyOf(Objects.requireNonNull(defaultLinks));
        if (id.isBlank()) throw new IllegalArgumentException("port id must be non-empty");
    }
}

