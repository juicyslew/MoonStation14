package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LooseObjectNativeGeometryEnvelopeTest {
    private static final Set<String> ITEMS = Set.of("weapon_pistol_mk58", "weapon_rifle_lecter", "weapon_revolver_inspector",
            "weapon_shotgun_kammerer", "weapon_sniper_mosin", "weapon_laser_gun", "weapon_energy_shotgun",
            "weapon_laser_svalinn", "magazine_pistol", "magazine_rifle", "speedloader_magnum", "power_cell_small");

    @Test void reachableNativeObjGeometryFitsResolvedLooseCollisionEnvelope() throws Exception {
        Path root = projectRoot();
        Path modelDir = root.resolve("src/main/resources/assets/moonstation14/models/item");
        Map<ResourceLocation, JsonObject> profiles = new LinkedHashMap<>();
        Path profileDir = root.resolve("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.list(profileDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList())
                profiles.put(ResourceLocation.fromNamespaceAndPath("moonstation14", file.getFileName().toString().replaceFirst("\\.json$", "")), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        var resolved = PrototypeResolver.resolve(profiles, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        int checked = 0;
        for (String item : ITEMS) {
            var profile = LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14", item))).getOrThrow();
            assertEquals("block_model", profile.renderKind(), item);
            float expectedScale = item.equals("weapon_energy_shotgun") ? .7f : item.equals("weapon_laser_svalinn") ? .65f : 1f;
            assertEquals(expectedScale, profile.modelScale(), 1e-6f, item);
            assertEquals(profile, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,
                    LooseObjectProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow()).getOrThrow(), item);
            Set<Path> reachable = new java.util.LinkedHashSet<>();
            collectObjReferences(modelDir, modelDir.resolve(item + ".json"), reachable, new java.util.HashSet<>());
            assertFalse(reachable.isEmpty(), item + " has no reachable native OBJ geometry");
            double minX=Double.POSITIVE_INFINITY, maxX=Double.NEGATIVE_INFINITY, minY=Double.POSITIVE_INFINITY, maxY=Double.NEGATIVE_INFINITY, minZ=Double.POSITIVE_INFINITY, maxZ=Double.NEGATIVE_INFINITY;
            double rawMinX=Double.POSITIVE_INFINITY, rawMaxX=Double.NEGATIVE_INFINITY, rawMinY=Double.POSITIVE_INFINITY, rawMaxY=Double.NEGATIVE_INFINITY, rawMinZ=Double.POSITIVE_INFINITY, rawMaxZ=Double.NEGATIVE_INFINITY;
            for (Path obj : reachable) {
                for (String line : Files.readAllLines(obj)) if (line.startsWith("v ")) {
                    String[] xyz=line.trim().split("\\s+"); double x=Double.parseDouble(xyz[1]), y=Double.parseDouble(xyz[2]), z=Double.parseDouble(xyz[3]);
                    rawMinX=Math.min(rawMinX,x); rawMaxX=Math.max(rawMaxX,x); rawMinY=Math.min(rawMinY,y); rawMaxY=Math.max(rawMaxY,y); rawMinZ=Math.min(rawMinZ,z); rawMaxZ=Math.max(rawMaxZ,z);
                    var transformed = com.juicyslew.moonstation14.ms14.loose_objects.client.WorldItemPresentationRenderer
                            .transformBlockModelVertex(new org.joml.Vector3f((float)x, (float)y, (float)z), profile);
                    minX=Math.min(minX,transformed.x); maxX=Math.max(maxX,transformed.x);
                    minY=Math.min(minY,transformed.y); maxY=Math.max(maxY,transformed.y);
                    minZ=Math.min(minZ,transformed.z); maxZ=Math.max(maxZ,transformed.z);
                }
                checked++;
            }
            assertTrue(Double.isFinite(minY), "No vertices for " + item);
            double width=Math.max(maxX-minX,maxZ-minZ), height=maxY-minY;
            assertEquals(0d, minY, 1e-5, item+" floor");
            assertEquals(0d, (minX+maxX)/2, 1e-5, item+" centered X");
            assertEquals(0d, (minZ+maxZ)/2, 1e-5, item+" centered Z");
            assertEquals(width, profile.physicalWidth(), 1e-4, item+" widest resting envelope");
            assertEquals(height, profile.physicalHeight(), 1e-4, item+" resting height");
            double thinnestRawAxis = Math.min(rawMaxX-rawMinX, Math.min(rawMaxY-rawMinY, rawMaxZ-rawMinZ));
            assertEquals(thinnestRawAxis * profile.modelScale(), height, 1e-5,
                    item+" stable rest must put a minimum-extent axis vertical");
            if (item.equals("weapon_laser_svalinn")) {
                assertTrue(profile.modelScale() < 1f && profile.modelScale() > .5f);
                assertEquals(.89984375, profile.physicalWidth(), 1e-6);
                assertEquals(.1625, profile.physicalHeight(), 1e-6);
                assertEquals(.325, profile.modelFloorOffset(), 1e-6);
            }
        }
        assertTrue(checked >= ITEMS.size());
    }

    private static void collectObjReferences(Path modelDir, Path model, Set<Path> result, Set<Path> visited) throws Exception {
        model=model.toAbsolutePath().normalize(); if (!visited.add(model)) return;
        JsonObject json=JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        if (json.has("model")) {
            String ref=json.get("model").getAsString();
            if (ref.endsWith(".obj")) result.add(modelDir.getParent().getParent().resolve(ref.substring(ref.indexOf(':')+1)).normalize());
            else collectObjReferences(modelDir, modelPath(modelDir,ref), result, visited);
        }
        if (json.has("parent")) collectObjReferences(modelDir, modelPath(modelDir,json.get("parent").getAsString()),result,visited);
        if (json.has("overrides")) for (var entry:json.getAsJsonArray("overrides")) collectObjReferences(modelDir,modelPath(modelDir,entry.getAsJsonObject().get("model").getAsString()),result,visited);
    }
    private static Path modelPath(Path dir,String id) { String path=id.substring(id.indexOf(':')+1); return dir.getParent().resolve(path+".json"); }
    private static Path projectRoot() throws Exception { Path p=Path.of("").toAbsolutePath(); while(p!=null&&!Files.isDirectory(p.resolve("src/main/resources")))p=p.getParent(); if(p==null)throw new java.io.IOException("Project root not found"); return p; }
}
