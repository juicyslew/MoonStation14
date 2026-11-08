package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile;
import com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MeleeArcPrototypeActivationTest {
    private static final String[] FILES = {"weapon_arc_static.json", "weapon_arc_fist.json",
            "weapon_arc_claw.json",
            "weapon_arc_slash.json", "weapon_arc_thrust.json"};

    @Test void registeredDirectoryReloadResolvesAndPublishesTheProductionArcCatalog(@TempDir Path pack)
            throws Exception {
        assertEquals(5, FILES.length, "all five canonical raw arc resources must be present");
        PrototypeManager server = new PrototypeManager();
        server.register(ModMeleeArcProfiles.TYPE);
        Path registeredDirectory = pack.resolve("data/moonstation14/" + ModMeleeArcProfiles.TYPE.resourceDirectory());
        Files.createDirectories(registeredDirectory);
        for (String file : FILES) {
            String resource = "data/moonstation14/" + ModMeleeArcProfiles.TYPE.resourceDirectory() + "/" + file;
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, "missing canonical production resource " + resource);
                Files.copy(input, registeredDirectory.resolve(file));
            }
        }

        try (MultiPackResourceManager resources = resources(pack)) {
            PrototypeReloadListener listener = new PrototypeReloadListener(server);
            var prepared = listener.prepare(resources, InactiveProfiler.INSTANCE);
            listener.apply(prepared, resources, InactiveProfiler.INSTANCE);
            assertTrue(server.commitStagedReload());
        }

        var catalog = server.snapshot(ModMeleeArcProfiles.TYPE);
        try (var raw = Files.list(registeredDirectory)) {
            assertEquals(5, raw.filter(path -> path.toString().endsWith(".json")).count(),
                    "raw catalog contains static parent plus four concrete arcs");
        }
        assertEquals(4, catalog.asMap().size());
        var staticProfile = catalog.get(id("weapon_arc_static"));
        assertNull(staticProfile, "abstract resource base must not be published");
        for (String leaf : List.of("weapon_arc_fist", "weapon_arc_claw", "weapon_arc_slash", "weapon_arc_thrust")) {
            var profile = catalog.get(id(leaf));
            assertNotNull(profile, "missing resolved concrete profile " + leaf);
            assertEquals(id("textures/effects/melee/disarm_hand.png"), profile.disarmTexture());
            assertEquals(1, profile.disarmFrames());
            assertEquals("weapon_arc_fist".equals(leaf) || "weapon_arc_claw".equals(leaf) ? MeleeArcProfile.Animation.NONE
                    : "weapon_arc_slash".equals(leaf) ? MeleeArcProfile.Animation.SLASH
                    : MeleeArcProfile.Animation.THRUST, profile.animation());
            if (leaf.equals("weapon_arc_claw"))
            {
                assertEquals(id("textures/effects/melee/claw.png"), profile.texture(), "claw profile resolves to packaged upstream art");
                assertTrue(profile.strikeTextureGlyph(), "strike presentation must draw the resolved texture rather than a procedural trail");
                assertEquals(4, profile.textureFrames());
                assertEquals(2, profile.textureColumns());
                assertEquals(.399, profile.strike().durationSeconds());
                assertFalse(profile.fadeOut());
            }
            assertEquals(!leaf.equals("weapon_arc_claw"), profile.fadeOut());
            assertEquals(leaf.equals("weapon_arc_claw") ? .399 : 2.0, profile.lifetimeSeconds());
            assertNotNull(profile.strike(), "leaves inherit the base strike presentation");
            assertEquals(.4, profile.strike().extensionEndFraction());
            assertEquals(new MeleeArcProfile.WeaponPose(45.0, 20.0, 2.0), profile.weaponPose());
        }

        PrototypeManager client = new PrototypeManager();
        client.register(ModMeleeArcProfiles.TYPE);
        Map<ResourceLocation, Map<ResourceLocation, com.google.gson.JsonObject>> wire = server.encodePublishedCatalogs();
        assertEquals(4, wire.get(ModMeleeArcProfiles.TYPE.typeId()).size());
        assertEquals(java.util.Set.of(id("weapon_arc_fist"), id("weapon_arc_claw"), id("weapon_arc_slash"), id("weapon_arc_thrust")),
                wire.get(ModMeleeArcProfiles.TYPE.typeId()).keySet(), "encoded client catalog publishes every concrete arc ID");
        client.publishEncodedCatalogs(wire);
        assertEquals(catalog.asMap(), client.snapshot(ModMeleeArcProfiles.TYPE).asMap());
    }

    private static MultiPackResourceManager resources(Path pack) {
        PackResources resource = new PathPackResources(new PackLocationInfo("melee-arcs",
                Component.literal("test"), PackSource.DEFAULT, Optional.empty()), pack);
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(resource));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
