package com.juicyslew.moonstation14.ms14.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeReloadListenerTest {
    private static final Codec<Value> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("value").forGetter(Value::value)
    ).apply(instance, Value::new));

    @Test
    void derivesNamespaceAndNestedIdsAndUsesHigherPriorityReplacement(@TempDir Path temp) throws IOException {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<Value> type = new PrototypeType<>(
                id("value"), "moonstation14/reagent", CODEC);
        manager.register(type);

        Path low = writePack(temp.resolve("low"), "{\"value\":1}");
        Path high = writePack(temp.resolve("high"), "{\"value\":2}");
        try (MultiPackResourceManager resources = resources(low, high)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(manager);
            var prepared = listener.prepare(resources, InactiveProfiler.INSTANCE);
            listener.apply(prepared, resources, InactiveProfiler.INSTANCE);
            assertTrue(manager.hasStagedReload());
            assertTrue(manager.snapshot(type).asMap().isEmpty());
            assertTrue(manager.commitStagedReload());
        }

        assertEquals(new Value(2), manager.snapshot(type).get(id("acme:medicine/antidote")));

        Path empty = temp.resolve("empty");
        Files.createDirectories(empty);
        try (MultiPackResourceManager resources = resources(empty)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(manager);
            var prepared = listener.prepare(resources, InactiveProfiler.INSTANCE);
            listener.apply(prepared, resources, InactiveProfiler.INSTANCE);
            assertTrue(manager.hasStagedReload());
            assertTrue(manager.commitStagedReload());
        }
        assertTrue(manager.snapshot(type).asMap().isEmpty());
    }

    @Test
    void malformedAndNonObjectFilesAbortPrepareAndRetainPublishedSnapshot(@TempDir Path temp) throws IOException {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<Value> type = new PrototypeType<>(
                id("value"), "moonstation14/reagent", CODEC);
        manager.register(type);
        Path good = writePack(temp.resolve("good"), "{\"value\":7}");
        try (MultiPackResourceManager resources = resources(good)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(manager);
            var prepared = listener.prepare(resources, InactiveProfiler.INSTANCE);
            listener.apply(prepared, resources, InactiveProfiler.INSTANCE);
            assertTrue(manager.hasStagedReload());
            assertTrue(manager.commitStagedReload());
        }

        Path malformed = writePack(temp.resolve("malformed"), "not json");
        try (MultiPackResourceManager resources = resources(malformed)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(manager);
            PrototypeLoadException exception = assertThrows(PrototypeLoadException.class,
                    () -> listener.prepare(resources, InactiveProfiler.INSTANCE));
            assertTrue(exception.getMessage().contains("value"));
            assertTrue(exception.getMessage().contains("data/acme/moonstation14/reagent/medicine/antidote.json"));
        }
        assertEquals(new Value(7), manager.snapshot(type).get(id("acme:medicine/antidote")));

        Path nonObject = writePack(temp.resolve("non-object"), "[]");
        try (MultiPackResourceManager resources = resources(nonObject)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(manager);
            assertThrows(PrototypeLoadException.class,
                    () -> listener.prepare(resources, InactiveProfiler.INSTANCE));
        }
        assertEquals(new Value(7), manager.snapshot(type).get(id("acme:medicine/antidote")));
    }

    private static Path writePack(Path root, String json) throws IOException {
        Path file = root.resolve("data/acme/moonstation14/reagent/medicine/antidote.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
        return root;
    }

    private static MultiPackResourceManager resources(Path... packs) {
        List<PackResources> resources = java.util.Arrays.stream(packs)
                .map(path -> new PathPackResources(
                        new PackLocationInfo(path.getFileName().toString(), Component.literal("test"),
                                PackSource.DEFAULT, Optional.empty()), path))
                .map(pack -> (PackResources) pack)
                .toList();
        return new MultiPackResourceManager(PackType.SERVER_DATA, resources);
    }

    private static ResourceLocation id(String path) {
        int separator = path.indexOf(':');
        return separator < 0
                ? ResourceLocation.fromNamespaceAndPath("moonstation14", path)
                : ResourceLocation.fromNamespaceAndPath(path.substring(0, separator), path.substring(separator + 1));
    }

    private record Value(int value) {
    }
}
