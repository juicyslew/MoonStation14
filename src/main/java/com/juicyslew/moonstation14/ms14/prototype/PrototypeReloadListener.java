package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strictly reads prototype JSON, then stages a complete candidate for lifecycle commit. */
public final class PrototypeReloadListener
        extends SimplePreparableReloadListener<Map<PrototypeType<?>, Map<ResourceLocation, JsonObject>>> {
    private final PrototypeManager manager;
    private final PrototypeCandidateValidator candidateValidator;

    public PrototypeReloadListener(PrototypeManager manager) {
        this(manager, null);
    }

    public PrototypeReloadListener(PrototypeManager manager, PrototypeCandidateValidator candidateValidator) {
        this.manager = manager;
        this.candidateValidator = candidateValidator;
    }

    @Override
    protected Map<PrototypeType<?>, Map<ResourceLocation, JsonObject>> prepare(
        ResourceManager resourceManager, ProfilerFiller profiler) {
        // If a previous aggregate reload failed after this listener applied,
        // its candidate must not become eligible for a later login sync.
        manager.discardStagedReload();
        List<PrototypeType<?>> types = new ArrayList<>(manager.registeredTypes());
        types.sort(Comparator.comparing(type -> type.typeId().toString()));

        Map<PrototypeType<?>, Map<ResourceLocation, JsonObject>> rawByType = new LinkedHashMap<>();
        for (PrototypeType<?> type : types) {
            FileToIdConverter converter = FileToIdConverter.json(type.resourceDirectory());
            Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : converter.listMatchingResources(resourceManager).entrySet()) {
                ResourceLocation file = entry.getKey();
                ResourceLocation id = toId(type, converter, file);
                if (raw.containsKey(id)) {
                    throw failure(type, id, resourcePath(file), "duplicate prototype ID", null);
                }
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed == null || !parsed.isJsonObject()) {
                        throw failure(type, id, resourcePath(file), "expected a JSON object", null);
                    }
                    raw.put(id, parsed.getAsJsonObject());
                } catch (PrototypeLoadException exception) {
                    throw exception;
                } catch (IOException | RuntimeException exception) {
                    String diagnostic = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    throw failure(type, id, resourcePath(file), "could not read or parse JSON: " + diagnostic,
                            exception);
                }
            }
            rawByType.put(type, raw);
        }
        return rawByType;
    }

    @Override
    protected void apply(Map<PrototypeType<?>, Map<ResourceLocation, JsonObject>> rawByType,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        manager.stage(rawByType, encodedCatalogs -> {
            ModCharacters.validateReagentReferences(encodedCatalogs);
            if (candidateValidator != null) candidateValidator.validate(encodedCatalogs);
        });
    }

    private static ResourceLocation toId(PrototypeType<?> type, FileToIdConverter converter,
                                         ResourceLocation file) {
        try {
            return converter.fileToId(file);
        } catch (RuntimeException exception) {
            throw failure(type, null, resourcePath(file), "could not derive prototype ID: "
                    + diagnostic(exception), exception);
        }
    }

    private static PrototypeLoadException failure(PrototypeType<?> type, ResourceLocation id,
                                                  String path, String diagnostic, Throwable cause) {
        return new PrototypeLoadException(type.typeId(), id, path, diagnostic, cause);
    }

    private static String resourcePath(ResourceLocation file) {
        return "data/" + file.getNamespace() + "/" + file.getPath();
    }

    private static String diagnostic(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
