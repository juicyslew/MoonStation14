package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredWearableRendererTest {
    private static final Optional<EquipmentItemData.WornModel> WIDE = Optional.of(new EquipmentItemData.WornModel(
            ResourceLocation.parse("moonstation14:wearables/meshes/bag.json"), PlayerCharacterBodyShape.WIDE));

    @Test void requiresWideShapeMetadataAndLoadedMesh() {
        assertTrue(AuthoredWearableRenderer.canRender(WIDE, PlayerCharacterBodyShape.WIDE, true));
        assertFalse(AuthoredWearableRenderer.canRender(WIDE, PlayerCharacterBodyShape.SLIM, true));
        assertFalse(AuthoredWearableRenderer.canRender(WIDE, PlayerCharacterBodyShape.WIDE, false));
        assertFalse(AuthoredWearableRenderer.canRender(Optional.empty(), PlayerCharacterBodyShape.WIDE, true));
    }

    @Test void productionEmitterUsesBoneTransformAndWhiteAuthoredVertices() {
        var mesh=AuthoredWearableMesh.parse(new java.io.StringReader("""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"head":{"canonical_pivot":[99,88,77],"cubes":[{"quads":[{"vertices":[[16,0,0],[0,0,0],[0,16,0],[16,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """));
        var bone=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        bone.setPos(16,0,0);
        var capture=new Capture(); var pose=new PoseStack();
        AuthoredWearableRenderer.emit(mesh, ignored -> bone, pose,capture,123);
        assertEquals(4,capture.x.size());
        assertEquals(2f,capture.x.getFirst(),1e-6f,"one block mesh vertex plus one-block bone pivot");
        assertTrue(capture.colors.stream().allMatch(c->java.util.Arrays.equals(c,new int[]{255,255,255,255})));
        assertEquals(0f,capture.u.getFirst()); assertEquals(0f,capture.v.getFirst());
        assertEquals(-1f,capture.normalZ.getFirst());
        assertEquals(0f,pose.last().pose().m30(),1e-6f,"emitter must restore the caller pose");
    }

    private static final class Capture implements VertexConsumer {
        final java.util.ArrayList<Float> x=new java.util.ArrayList<>(),u=new java.util.ArrayList<>(),v=new java.util.ArrayList<>(),normalZ=new java.util.ArrayList<>();
        final java.util.ArrayList<int[]> colors=new java.util.ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){this.x.add(x);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){colors.add(new int[]{r,g,b,a});return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u.add(u);this.v.add(v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){normalZ.add(z);return this;}
    }
}
