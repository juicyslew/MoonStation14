package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BodyAppearanceClothingLayerTest {
    private static final ResourceLocation AUTHORED = ResourceLocation.parse("moonstation14:wearables/meshes/test.json");
    @AfterEach void clearMeshes() { AuthoredWearableMeshes.clearForTests(); }

    @Test void productionDispatchAcquiresFallbackConsumerAfterEachAuthoredTextureSwitch() {
        var mesh = AuthoredWearableMesh.parse(new java.io.StringReader("""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"body":{"cubes":[{"quads":[{"vertices":[[16,0,0],[0,0,0],[0,16,0],[16,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """));
        AuthoredWearableMeshes.applySnapshot(Map.of(AUTHORED, mesh));
        var garments = List.of(garment("head", Optional.of(new EquipmentItemData.WornModel(AUTHORED, PlayerCharacterBodyShape.WIDE))),
                garment("id", Optional.empty()), garment("head", Optional.of(new EquipmentItemData.WornModel(AUTHORED, PlayerCharacterBodyShape.WIDE))),
                garment("id", Optional.empty()));
        var source = new SwitchingBuffers();
        BodyAppearanceClothingLayer.renderGarments(garments, model(), PlayerCharacterBodyShape.WIDE, new PoseStack(), source, 0);
        assertEquals(4, source.bufferRequests);
        assertTrue(source.emittedVertices > 8);
    }

    @Test void allSlimFallbackGarmentsUseFreshConsumerAndEmitOnce() {
        var source = new SwitchingBuffers();
        BodyAppearanceClothingLayer.renderGarments(List.of(garment("id", Optional.empty()), garment("head", Optional.empty())),
                model(), PlayerCharacterBodyShape.SLIM, new PoseStack(), source, 0);
        assertEquals(1, source.bufferRequests); // shared type; live builder remains valid
        assertTrue(source.emittedVertices > 0);
    }

    private static EquipmentRenderPlan.Garment garment(String slot, Optional<EquipmentItemData.WornModel> worn) {
        var region = slot.equals("head") ? EquipmentItemData.AppearanceRegion.HEAD : EquipmentItemData.AppearanceRegion.TORSO;
        return new EquipmentRenderPlan.Garment(slot, ResourceLocation.parse("moonstation14:test/" + slot), region,
                0xffffffff, EquipmentRenderPlan.anchors(slot, region), 1, worn);
    }

    private static PlayerModel<?> model() {
        return new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
    }

    /** Shared-builder stub: changing RenderType invalidates the previous consumer like BufferSource. */
    private static final class SwitchingBuffers implements MultiBufferSource {
        private RenderType activeType;
        private Guarded active;
        int bufferRequests, emittedVertices;
        @Override public VertexConsumer getBuffer(RenderType type) {
            if (!type.equals(activeType)) {
                if (active != null) active.valid = false;
                activeType = type;
                active = new Guarded();
                bufferRequests++;
            }
            return active;
        }
        private final class Guarded extends AuthoredWearableRendererTest.Capture {
            boolean valid = true;
            private void check() { if (!valid) throw new IllegalStateException("Not building"); }
            @Override public VertexConsumer addVertex(float x, float y, float z) { check(); emittedVertices++; return super.addVertex(x,y,z); }
            @Override public VertexConsumer setColor(int r,int g,int b,int a) { check(); return super.setColor(r,g,b,a); }
            @Override public VertexConsumer setUv(float u,float v) { check(); return super.setUv(u,v); }
            @Override public VertexConsumer setNormal(float x,float y,float z) { check(); return super.setNormal(x,y,z); }
        }
    }
}
