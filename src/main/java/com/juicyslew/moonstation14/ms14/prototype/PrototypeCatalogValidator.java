package com.juicyslew.moonstation14.ms14.prototype;

/** Validates a completed typed prototype catalog. */
@FunctionalInterface
public interface PrototypeCatalogValidator<T> {
    void validate(PrototypeCatalog<T> catalog);
}
