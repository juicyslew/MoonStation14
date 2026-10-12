package com.juicyslew.moonstation14.ms14.devicenet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Loads a complete bundled prototype catalog into an isolated manager. */
public final class PrototypeResourceFixture {
    private PrototypeResourceFixture() { }

    public static <T> void loadCatalog(PrototypeManager manager, PrototypeType<T> type) {
        try {
            var url = Objects.requireNonNull(PrototypeResourceFixture.class.getClassLoader()
                    .getResource("data/moonstation14/" + type.resourceDirectory()));
            var values = new LinkedHashMap<ResourceLocation, JsonObject>();
            try (var files = Files.list(Path.of(url.toURI()))) {
                for (var file : files.filter(p -> p.toString().endsWith(".json")).toList())
                    values.put(ResourceLocation.fromNamespaceAndPath("moonstation14",
                            file.getFileName().toString().replaceFirst("\\.json$", "")),
                            JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
            manager.reload(type, values);
        } catch (Exception e) {
            throw new AssertionError("Unable to load prototype resource catalog " + type.resourceDirectory(), e);
        }
    }
}
