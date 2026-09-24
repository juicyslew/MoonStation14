package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;

/** Validates owned raw and resolved JSON prototype catalogs before decoding. */
@FunctionalInterface
public interface PrototypeJsonCatalogValidator {
    void validate(PrototypeCatalog<JsonObject> ownedRaw, PrototypeCatalog<JsonObject> resolved);
}
