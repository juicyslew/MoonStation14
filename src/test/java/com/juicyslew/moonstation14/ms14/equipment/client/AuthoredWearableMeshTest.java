package com.juicyslew.moonstation14.ms14.equipment.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredWearableMeshTest {
    private static final Path ASSETS=findAssets();

    private static Path findAssets() {
        Path current=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(current!=null) {
            Path candidate=current.resolve("src/main/resources/assets/moonstation14/wearables/meshes");
            if(Files.isDirectory(candidate)) return candidate;
            current=current.getParent();
        }
        return Path.of("src/main/resources/assets/moonstation14/wearables/meshes");
    }

    @Test void allInstalledMeshesParseAndRemainImmutable() throws Exception {
        try(var paths=Files.list(ASSETS)) {
            var files=paths.filter(p->p.toString().endsWith(".json")).toList();
            assertEquals(11,files.size());
            for(Path file:files) try(var reader=Files.newBufferedReader(file)) {
                var mesh=AuthoredWearableMesh.parse(reader);
                assertNotNull(mesh.texture()); assertFalse(mesh.bones().isEmpty());
                mesh.bones().values().forEach(quads->quads.forEach(q->{
                    assertEquals(4,q.vertices().size());
                    assertTrue(Float.isFinite(q.normal().x())&&Float.isFinite(q.normal().y())&&Float.isFinite(q.normal().z()));
                    q.vertices().forEach(v->{assertTrue(Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z())); assertTrue(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1);});
                }));
                assertThrows(UnsupportedOperationException.class,()->mesh.bones().clear());
            }
        }
    }

    @Test void scalesPositionsOnceAndKeepsTopLeftUv() {
        String json="""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"left_arm":{"canonical_pivot":[9,8,7],"cubes":[{"quads":[{"vertices":[[16,0,0],[0,0,0],[0,16,0],[16,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """;
        var mesh=AuthoredWearableMesh.parse(new StringReader(json)); var quad=mesh.bones().get(EquipmentRenderPlan.Anchor.LEFT_ARM).get(0);
        assertEquals(1,quad.vertices().get(0).x()); assertEquals(0,quad.vertices().get(0).u());
        assertEquals(ResourceLocation.parse("moonstation14:textures/test.png"),mesh.texture());
    }

    @Test void rejectsUnknownBonesInvalidWindingAndInvalidValues() {
        String base="{\"schema\":\"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)\",\"texture\":\"moonstation14:test\",\"anchors\":%s}";
        assertThrows(IllegalArgumentException.class,()->AuthoredWearableMesh.parse(new StringReader(base.formatted("{\"neck\":{}}"))));
        String malformed="{\"head\":{\"cubes\":[{\"quads\":[{\"vertices\":[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],\"normal\":[0,0,1],\"uv_top_left\":[[0,0],[1,0],[1,1],[0,1]],\"winding\":[0,0,2,3]}]}]}}";
        assertThrows(IllegalArgumentException.class,()->AuthoredWearableMesh.parse(new StringReader(base.formatted(malformed))));
    }
}
