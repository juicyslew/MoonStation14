package com.juicyslew.moonstation14.ms14.equipment.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredWearableMeshesTest {
    private static AuthoredWearableMesh mesh(String x) {
        return AuthoredWearableMesh.parse(new StringReader("""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"body":{"cubes":[{"quads":[{"vertices":[[%s,0,0],[0,0,0],[0,16,0],[16,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """.formatted(x)));
    }

    @AfterEach void clear() { AuthoredWearableMeshes.clearForTests(); }

    @Test void reloadSnapshotReplacesRatherThanMergesAndIsImmutable() {
        var oldId=ResourceLocation.parse("moonstation14:wearables/meshes/old.json");
        var newId=ResourceLocation.parse("moonstation14:wearables/meshes/new.json");
        var oldMesh=mesh("16"); var newMesh=mesh("32");
        AuthoredWearableMeshes.applySnapshot(Map.of(oldId,oldMesh));
        var prepared=new java.util.HashMap<>(Map.of(newId,newMesh));
        AuthoredWearableMeshes.applySnapshot(prepared);
        prepared.clear();
        assertTrue(AuthoredWearableMeshes.get(oldId).isEmpty());
        assertSame(newMesh,AuthoredWearableMeshes.get(newId).orElseThrow());
    }
}
