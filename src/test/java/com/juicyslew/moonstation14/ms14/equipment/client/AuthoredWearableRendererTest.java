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

    @Test void selectsActualAuthoredSequenceMeshesAtPresentationBoundaries() {
        var base = ResourceLocation.parse("moonstation14:wearables/meshes/hats/hat_holy_watermelon.json");
        var frames = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new EquipmentItemData.WornModel.Frame(
                i == 0 ? base : ResourceLocation.parse("moonstation14:wearables/meshes/hats/hat_holy_watermelon__f" + i + ".json"), .5)).toList();
        var animated = new EquipmentItemData.WornModel(base, PlayerCharacterBodyShape.WIDE, frames);
        assertEquals(frames.get(0).mesh(), AuthoredWearableRenderer.selectedMesh(animated, 0));
        assertEquals(frames.get(0).mesh(), AuthoredWearableRenderer.selectedMesh(animated, .4999));
        assertEquals(frames.get(1).mesh(), AuthoredWearableRenderer.selectedMesh(animated, .5));
        assertEquals(frames.get(2).mesh(), AuthoredWearableRenderer.selectedMesh(animated, 1));
        assertEquals(frames.get(3).mesh(), AuthoredWearableRenderer.selectedMesh(animated, 1.5));
        assertEquals(frames.get(0).mesh(), AuthoredWearableRenderer.selectedMesh(animated, 2));
        assertEquals(base, AuthoredWearableRenderer.selectedMesh(new EquipmentItemData.WornModel(base, PlayerCharacterBodyShape.WIDE), 50));
    }

    @Test void selectedFrameMeshLookupEmitsThatMeshAndMissingSelectedFrameFallsBack() throws Exception {
        var base = ResourceLocation.parse("moonstation14:wearables/meshes/hats/hat_holy_watermelon.json");
        var frame = ResourceLocation.parse("moonstation14:wearables/meshes/hats/hat_holy_watermelon__f1.json");
        var worn = new EquipmentItemData.WornModel(base, PlayerCharacterBodyShape.WIDE, java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> new EquipmentItemData.WornModel.Frame(i == 0 ? base : ResourceLocation.parse(
                        "moonstation14:wearables/meshes/hats/hat_holy_watermelon__f" + i + ".json"), .5)).toList());
        var garment = new EquipmentRenderPlan.Garment("head", base, EquipmentItemData.AppearanceRegion.HEAD,
                -1, java.util.List.of(EquipmentRenderPlan.Anchor.HEAD), 0, Optional.of(worn));
        var loaded = java.util.Map.of(base, loadMesh(base), frame, loadMesh(frame));
        var selectedLookup = new java.util.ArrayList<ResourceLocation>();
        var emittedTexture = new java.util.ArrayList<ResourceLocation>();
        var capture = new Capture();
        var bone = new net.minecraft.client.model.geom.ModelPart(java.util.List.of(), java.util.Map.of());
        var rendered = AuthoredWearableRenderer.render(garment, .5, id -> {
            selectedLookup.add(id);
            return Optional.ofNullable(loaded.get(id));
        }, mesh -> { emittedTexture.add(mesh.texture()); return capture; }, ignored -> bone, new PoseStack(), 123);
        assertTrue(rendered);
        assertEquals(java.util.List.of(frame), selectedLookup);
        assertEquals(java.util.List.of(ResourceLocation.parse("moonstation14:textures/entity/equipment/hats/hat_holy_watermelon__f1.png")), emittedTexture);
        assertTrue(capture.x.size() > 0, "selected frame authored vertices are emitted");

        var missingFrameLookups = new java.util.ArrayList<ResourceLocation>();
        var missingFrameCapture = new Capture();
        var authored = AuthoredWearableRenderer.render(garment, .5, id -> {
            missingFrameLookups.add(id);
            return Optional.ofNullable(id.equals(base) ? loaded.get(base) : null);
        }, ignored -> missingFrameCapture, ignored -> bone, new PoseStack(), 123);
        assertFalse(authored, "missing selected frame requests placeholder fallback despite base mesh being loaded");
        assertEquals(java.util.List.of(frame), missingFrameLookups);
        assertTrue(missingFrameCapture.x.isEmpty());
    }

    private static AuthoredWearableMesh loadMesh(ResourceLocation id) throws Exception {
        var path = "/assets/" + id.getNamespace() + "/" + id.getPath();
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                AuthoredWearableRendererTest.class.getResourceAsStream(path)), java.nio.charset.StandardCharsets.UTF_8)) {
            return AuthoredWearableMesh.parse(reader);
        }
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

    @Test void clippingInterpolatesAtElbowAndPreservesUvs() {
        var q = new AuthoredWearableMesh.Quad(java.util.List.of(
                new AuthoredWearableMesh.Vertex(0,0,0,0,0), new AuthoredWearableMesh.Vertex(1,0,0,1,0),
                new AuthoredWearableMesh.Vertex(1,.5f,0,1,1), new AuthoredWearableMesh.Vertex(0,.5f,0,0,1)),
                new AuthoredWearableMesh.Normal(0,0,-1));
        var split=AuthoredWearableRenderer.clipAtElbow(q);
        assertEquals(4,split.upper().size()); assertEquals(4,split.lower().size());
        assertTrue(split.upper().stream().allMatch(v->v.y()<=.25f));
        assertTrue(split.lower().stream().allMatch(v->v.y()>=.25f));
        var crossings=java.util.stream.Stream.concat(split.upper().stream(),split.lower().stream()).filter(v->v.y()==.25f).toList();
        assertEquals(4,crossings.size());
        assertEquals(java.util.List.of(0f,0f,1f,1f),crossings.stream().map(AuthoredWearableMesh.Vertex::u).sorted().toList());
        assertTrue(crossings.stream().allMatch(v->Math.abs(v.v()-.5f)<1e-6f));
    }

    @Test void nativeArmRemainsUnchangedAndFirstPersonAllowlistFiltersTorsoBeforeClip() {
        var mesh=AuthoredWearableMesh.parse(new java.io.StringReader("""
          {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"left_arm":{"cubes":[{"quads":[{"vertices":[[0,0,0],[16,0,0],[16,8,0],[0,8,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]},"body":{"cubes":[{"quads":[{"vertices":[[1600,0,0],[1616,0,0],[1616,16,0],[1600,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
          """));
        var vanilla=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var capture=new Capture();
        AuthoredWearableRenderer.emit(mesh,a->vanilla,new PoseStack(),capture,1);
        assertEquals(8,capture.x.size(),"plain arm without forearm child uses untouched original quad emission");
        var child=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of()); child.setPos(0,4,0);
        var articulated=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),new java.util.HashMap<>(java.util.Map.of("forearm",child)));
        capture=new Capture();
        AuthoredWearableRenderer.emit(mesh,a->a==EquipmentRenderPlan.Anchor.LEFT_ARM?articulated:vanilla,new PoseStack(),capture,1,java.util.Set.of(EquipmentRenderPlan.Anchor.LEFT_ARM));
        assertEquals(8,capture.x.size(),"two clipped arm polygons emitted; torso is excluded");
        assertTrue(capture.x.stream().noneMatch(x->x>50),"far-away torso sentinel must not be emitted");
    }

    @Test void upperGeometryOffsetIsAppliedOnceAndLowerUsesForearmJointRebasedAtFourPixels() {
        var mesh=AuthoredWearableMesh.parse(new java.io.StringReader("""
          {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"left_arm":{"cubes":[{"quads":[{"vertices":[[0,0,0],[16,0,0],[16,8,0],[0,8,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
          """));
        var upper=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of()); upper.setPos(0,2,0);
        var lowerGeometry=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of()); lowerGeometry.yScale=8f/6f;
        var forearm=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),new java.util.HashMap<>(java.util.Map.of("lower_geometry",lowerGeometry))); forearm.setPos(0,6,0);
        var root=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),new java.util.HashMap<>(java.util.Map.of(
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD,upper,"forearm",forearm)));
        var capture=new Capture();
        AuthoredWearableRenderer.emit(mesh,a->root,new PoseStack(),capture,1);
        assertEquals(8,capture.y.size());
        var source=mesh.bones().get(EquipmentRenderPlan.Anchor.LEFT_ARM).getFirst().vertices();
        var clipped=AuthoredWearableRenderer.clipAtElbow(mesh.bones().get(EquipmentRenderPlan.Anchor.LEFT_ARM).getFirst());
        var expectedUpper=clipped.upper().stream().map(v->v.y()+.125f).toList();
        var expectedLower=clipped.lower().stream().map(v->.375f+(v.y()-.25f)*8f/6f).toList();
        assertEquals(expectedUpper,capture.y.subList(0,4));
        assertEquals(expectedLower,capture.y.subList(4,8));
        assertEquals(source.get(0).u(),capture.u.get(0),1e-6f);
        var neutralZ=new java.util.ArrayList<>(capture.z);
        capture=new Capture(); forearm.xRot=(float)(Math.PI/2);
        AuthoredWearableRenderer.emit(mesh,a->root,new PoseStack(),capture,1);
        boolean movedOffJoint=false;
        for(int i=0;i<clipped.lower().size();i++) {
            var vertex=clipped.lower().get(i);
            double localY=(vertex.y()-.25)*8/6, localZ=vertex.z();
            float expectedY=(float)(.375 + localY*Math.cos(Math.PI/2)-localZ*Math.sin(Math.PI/2));
            float expectedZ=(float)(localY*Math.sin(Math.PI/2)+localZ*Math.cos(Math.PI/2));
            assertEquals(expectedY,capture.y.get(4+i),1e-6f);
            assertEquals(expectedZ,capture.z.get(4+i),1e-6f);
            assertEquals(vertex.u(),capture.u.get(4+i),1e-6f);
            if(vertex.y()>.25f) movedOffJoint |= Math.abs(capture.z.get(4+i)-neutralZ.get(4+i))>1e-4f;
        }
        assertTrue(movedOffJoint,"vertices away from the elbow joint follow the bend");
    }

    static class Capture implements VertexConsumer {
        final java.util.ArrayList<Float> x=new java.util.ArrayList<>(),y=new java.util.ArrayList<>(),z=new java.util.ArrayList<>(),u=new java.util.ArrayList<>(),v=new java.util.ArrayList<>(),normalZ=new java.util.ArrayList<>();
        final java.util.ArrayList<int[]> colors=new java.util.ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){this.x.add(x);this.y.add(y);this.z.add(z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){colors.add(new int[]{r,g,b,a});return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u.add(u);this.v.add(v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){normalZ.add(z);return this;}
    }
}
