package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Validates the detached, encoded form of a candidate before it can be staged. */
@FunctionalInterface
public interface PrototypeCandidateValidator {
    void validate(Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encodedCatalogs);
}
