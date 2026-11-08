package com.juicyslew.moonstation14.ms14.equipment.client;

import com.google.gson.JsonParseException;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Client reload-owned snapshot of authored wearable geometry. */
public final class AuthoredWearableMeshes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthoredWearableMeshes.class);
    private static volatile Map<ResourceLocation,AuthoredWearableMesh> cache = Map.of();
    private AuthoredWearableMeshes() { }
    public static Optional<AuthoredWearableMesh> get(ResourceLocation id) { return Optional.ofNullable(cache.get(id)); }
    public static void register(RegisterClientReloadListenersEvent event) { event.registerReloadListener(new Listener()); }
    static void applySnapshot(Map<ResourceLocation,AuthoredWearableMesh> prepared) { cache=Map.copyOf(prepared); }
    static void clearForTests() { cache=Map.of(); }

    private static final class Listener extends SimplePreparableReloadListener<Map<ResourceLocation,AuthoredWearableMesh>> {
        @Override protected Map<ResourceLocation,AuthoredWearableMesh> prepare(ResourceManager manager, net.minecraft.util.profiling.ProfilerFiller profiler) {
            var parsed = new HashMap<ResourceLocation,AuthoredWearableMesh>();
            for (var entry : manager.listResources("wearables/meshes", path -> path.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation id = entry.getKey();
                try (var input=entry.getValue().open(); var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)) {
                    parsed.put(id,AuthoredWearableMesh.parse(reader));
                } catch (Exception ex) { LOGGER.error("Skipping invalid authored wearable mesh {}",id,ex); }
            }
            return Map.copyOf(parsed);
        }
        @Override protected void apply(Map<ResourceLocation,AuthoredWearableMesh> prepared, ResourceManager manager, net.minecraft.util.profiling.ProfilerFiller profiler) { applySnapshot(prepared); }
    }
}

