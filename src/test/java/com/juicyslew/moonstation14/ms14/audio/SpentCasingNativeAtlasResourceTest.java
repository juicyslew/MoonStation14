package com.juicyslew.moonstation14.ms14.audio;

import com.google.gson.JsonParser;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpentCasingNativeAtlasResourceTest {
    @Test void configuredCasingAndSpentSpritesExistInNativeBlockAtlasInputs() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !java.nio.file.Files.isDirectory(current.resolve("src/main/resources/assets")))
            current = current.getParent();
        assertNotNull(current, "production resources root");
        Path root = current.resolve("src/main/resources");
        List<PackResources> packs = List.of(new PathPackResources(
                new PackLocationInfo("production", net.minecraft.network.chat.Component.literal("production"),
                        PackSource.DEFAULT, Optional.empty()), root));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs)) {
            var suppliers = SpriteSourceList.load(manager, ResourceLocation.withDefaultNamespace("blocks")).list(manager);
            var ids = new java.util.HashSet<ResourceLocation>();
            SpriteResourceLoader capture = (id, resource, constructor) -> {
                ids.add(id);
                try (var input = resource.open()) { assertTrue(input.read() >= 0, "empty sprite PNG " + id); }
                catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                return null;
            };
            for (int i = 1; i < suppliers.size(); i++) suppliers.get(i).apply(capture);
            var expected = new java.util.LinkedHashSet<ResourceLocation>();
            String base = "moonstation14:ss14/weapons/objects/weapons/guns/ammunition/casings/";
            expected.add(ResourceLocation.parse(base + "ammo_casing.rsi/base-spent"));
            expected.add(ResourceLocation.parse("moonstation14:ss14/guns/cartridge_spent_body"));
            expected.add(ResourceLocation.parse("moonstation14:ss14/guns/shell_spent_body"));
            for (String name : List.of("cartridge_pistol", "cartridge_rifle", "cartridge_light_rifle")) {
                var profile = JsonParser.parseReader(new java.io.InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/cartridge_profile/" + name + ".json"),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                expected.add(ResourceLocation.parse(profile.getAsJsonObject("spent_presentation").get("sprite").getAsString()));
            }
            var missing = new java.util.LinkedHashSet<>(expected); missing.removeAll(ids);
            assertTrue(missing.isEmpty(), "native atlas IDs=" + ids + "; missing spent sprite IDs=" + missing);
            for (var id : expected) {
                assertTrue(manager.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png")).isPresent(),
                        "missing PNG for atlas sprite " + id);
            }
        }
    }
}
