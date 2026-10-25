package com.juicyslew.moonstation14.ms14.mobs.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPoseBoundsTest {
    private static ModelPart baked(boolean cube) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition parent = root.addOrReplaceChild("parent", cube ? CubeListBuilder.create().texOffs(0, 0).addBox(0, 0, 0, 2, 4, 2) : CubeListBuilder.create(), net.minecraft.client.model.geom.PartPose.offset(1, 2, 3));
        if (cube) parent.addOrReplaceChild("child", CubeListBuilder.create().texOffs(0, 0).addBox(0, 0, 0, 2, 4, 2), net.minecraft.client.model.geom.PartPose.offset(0, 1, 0));
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    @Test void measuresTransformedCuboidCorners() {
        ModelPart root = baked(true);
        OptionalDouble value = AnimalPoseBounds.maxVisibleY(root);
        assertTrue(value.isPresent());
        assertEquals(7, value.getAsDouble(), .001);
        root.getChild("parent").yRot = (float) (Math.PI / 2);
        assertTrue(Double.isFinite(AnimalPoseBounds.maxVisibleY(root).orElseThrow()));
    }

    @Test void visibilityAndSkipDrawRespectRenderingSemantics() {
        ModelPart root = baked(true);
        root.getChild("parent").visible = false;
        assertTrue(AnimalPoseBounds.maxVisibleY(root).isEmpty());
        root.getChild("parent").visible = true;
        root.getChild("parent").skipDraw = true;
        assertTrue(AnimalPoseBounds.maxVisibleY(root).isPresent());
    }

    @Test void emptyModelReturnsEmpty() {
        assertTrue(AnimalPoseBounds.maxVisibleY(baked(false)).isEmpty());
    }

    @Test void pathFilterMeasuresOnlySelectedSubtree() {
        ModelPart root = baked(true);
        assertEquals(6, AnimalPoseBounds.maxVisibleY(root, path -> path.endsWith("/parent")).orElseThrow(), .001);
    }
}
