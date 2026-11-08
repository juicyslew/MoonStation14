package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SingularityVisualCatalogLifecycleTest {
    @Test void normalResourceReloadSurvivesClientLoginClearAndFullCatalogSync(@TempDir Path temp) throws Exception {
        PrototypeManager server = new PrototypeManager(), client = new PrototypeManager();
        server.register(ModSingularityVisuals.TYPE); client.register(ModSingularityVisuals.TYPE);
        Path pack = temp.resolve("server-data");
        for (int level = 1; level <= 6; level++) {
            String name = "level_" + level + ".json";
            Path target = pack.resolve("data/moonstation14/singularity_visual/" + name);
            Files.createDirectories(target.getParent());
            try (var in = getClass().getClassLoader().getResourceAsStream(
                    "data/moonstation14/singularity_visual/" + name)) {
                assertNotNull(in, name);
                Files.copy(in, target);
            }
        }
        PackResources packResources = new PathPackResources(new PackLocationInfo("singularity-test",
                Component.literal("test"), PackSource.DEFAULT, Optional.empty()), pack);
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(packResources))) {
            PrototypeReloadListener listener = new PrototypeReloadListener(server);
            var prepare = PrototypeReloadListener.class.getDeclaredMethod("prepare",
                    net.minecraft.server.packs.resources.ResourceManager.class,
                    net.minecraft.util.profiling.ProfilerFiller.class);
            var apply = PrototypeReloadListener.class.getDeclaredMethod("apply", Map.class,
                    net.minecraft.server.packs.resources.ResourceManager.class,
                    net.minecraft.util.profiling.ProfilerFiller.class);
            prepare.setAccessible(true);
            apply.setAccessible(true);
            apply.invoke(listener, prepare.invoke(listener, resources, InactiveProfiler.INSTANCE), resources,
                    InactiveProfiler.INSTANCE);
            assertTrue(server.commitStagedReload());
        }
        assertEquals(6, server.snapshot(ModSingularityVisuals.TYPE).asMap().size());
        client.clearPublishedCatalogs(); // login lifecycle
        assertTrue(client.snapshot(ModSingularityVisuals.TYPE).asMap().isEmpty());
        client.publishEncodedCatalogs(server.encodePublishedCatalogs());
        var resolved = client.snapshot(ModSingularityVisuals.TYPE);
        assertEquals(6, resolved.asMap().size());
        for (int level = 1; level <= 6; level++) {
            var profile = resolved.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "moonstation14", "level_" + level));
            assertNotNull(profile);
            assertTrue(profile.frameWidthPixels() > 0);
            assertTrue(profile.spriteHalfExtent() > 0);
        }
    }

}
