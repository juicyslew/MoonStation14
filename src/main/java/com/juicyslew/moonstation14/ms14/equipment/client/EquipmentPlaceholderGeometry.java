package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared cuboid garment renderer. Its inputs are the resolved render plan and humanoid bones only. */
public final class EquipmentPlaceholderGeometry {
    // PlayerModel legs are 4x12x4 (local y=0..12). Pants stop inside the
    // boot cuff; shoes extend past the vanilla foot bottom to avoid a shared
    // coplanar lower surface.
    static final float PANTS_BOTTOM = 9.5F;
    static final float SHOES_BOTTOM = 12.25F;
    public static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/equipment/white.png");
    private record Key(String slot, EquipmentRenderPlan.Anchor anchor, boolean slim, EquipmentItemData.AppearanceRegion region) { }
    private static final Map<Key, ModelPart> PIECES = new ConcurrentHashMap<>();
    private EquipmentPlaceholderGeometry() { }

    public static void render(EquipmentRenderPlan.Plan plan, PlayerModel<?> model, boolean slim, PoseStack pose,
                              MultiBufferSource buffers, int light, RenderType type) {
        VertexConsumer consumer = buffers.getBuffer(type);
        for (var garment : plan.garments()) render(garment, model, slim, pose, consumer, light);
    }

    /** Render one planned garment, optionally filtered to one articulated anchor (for first-person reuse). */
    public static void render(EquipmentRenderPlan.Garment garment, PlayerModel<?> model, PoseStack pose,
                              VertexConsumer consumer, int light) {
        render(garment, model, false, pose, consumer, light);
    }

    public static void render(EquipmentRenderPlan.Garment garment, PlayerModel<?> model, boolean slim, PoseStack pose,
                              VertexConsumer consumer, int light) {
        for (var anchor : garment.anchors()) renderAnchor(garment, anchor, model, slim, pose, consumer, light);
    }

    public static void render(EquipmentRenderPlan.Garment garment, EquipmentRenderPlan.Anchor selected,
                              PlayerModel<?> model, PoseStack pose, VertexConsumer consumer, int light) {
        render(garment, selected, model, false, pose, consumer, light);
    }

    public static void render(EquipmentRenderPlan.Garment garment, EquipmentRenderPlan.Anchor selected,
                              PlayerModel<?> model, boolean slim, PoseStack pose, VertexConsumer consumer, int light) {
        if (garment.anchors().contains(selected)) renderAnchor(garment, selected, model, slim, pose, consumer, light);
    }

    private static void renderAnchor(EquipmentRenderPlan.Garment g, EquipmentRenderPlan.Anchor anchor,
                                     PlayerModel<?> m, boolean slim, PoseStack pose, VertexConsumer consumer, int light) {
        if (!regionFits(g.slot(), g.region())) return;
        ModelPart bone = boneFor(m, anchor);
        pose.pushPose();
        try {
            bone.translateAndRotate(pose);
            piece(g.slot(), anchor, g.region(), slim).render(pose, consumer, light, 0, g.color());
        } finally { pose.popPose(); }
    }

    /** Canonical vanilla player bone shared by authored and placeholder geometry. */
    static ModelPart boneFor(PlayerModel<?> model, EquipmentRenderPlan.Anchor anchor) {
        return switch (anchor) {
            case HEAD, FACE -> model.head; case TORSO, BACK, WAIST, CHEST, HIP -> model.body;
            case LEFT_ARM -> model.leftArm; case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg; case RIGHT_LEG -> model.rightLeg;
        };
    }

    static int cachedPieceCount() { return PIECES.size(); }
    static int armWidth(boolean slim) { return slim ? 3 : 4; }
    static float armMinX(boolean slim, EquipmentRenderPlan.Anchor a) {
        return slim ? (a == EquipmentRenderPlan.Anchor.LEFT_ARM ? -1 : -2)
                : (a == EquipmentRenderPlan.Anchor.LEFT_ARM ? -1 : -3);
    }
    static ModelPart cachedPieceForTest(String slot, EquipmentRenderPlan.Anchor anchor, boolean slim,
                                       EquipmentItemData.AppearanceRegion region) { return piece(slot, anchor, region, slim); }
    static boolean regionFits(String slot, EquipmentItemData.AppearanceRegion region) {
        return EquipmentRenderPlan.regionFits(slot, region);
    }

    private static ModelPart piece(String slot, EquipmentRenderPlan.Anchor a, EquipmentItemData.AppearanceRegion region, boolean slim) {
        return PIECES.computeIfAbsent(new Key(slot, a, slim, region), key -> bake(key.slot(), key.anchor(), key.region(), key.slim()));
    }

    private static ModelPart bake(String slot, EquipmentRenderPlan.Anchor a, EquipmentItemData.AppearanceRegion region, boolean slim) {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
        CubeListBuilder cubes = CubeListBuilder.create();
        switch (slot) {
            case "head" -> { if (a == EquipmentRenderPlan.Anchor.HEAD) cubes.texOffs(0,0).addBox(-4.5F,-9.5F,-4.5F,9,2,9).texOffs(0,12).addBox(-5,-7.5F,-5.5F,10,1,3); }
            case "eyes" -> cubes.texOffs(0,0).addBox(-4,-5,-5.3F,8,1,1).addBox(-4,-4,-5.3F,1,2,1).addBox(3,-4,-5.3F,1,2,1);
            case "mask" -> cubes.texOffs(0,0).addBox(-4,-3,-5.1F,8,4,1);
            case "ears" -> cubes.texOffs(0,0).addBox(-5,-3,-1,2,3,2).addBox(3,-3,-1,2,3,2);
            case "neck" -> cubes.texOffs(0,0).addBox(-1.5F,-2,-2.4F,3,7,1);
            case "jumpsuit" -> {
                if (a == EquipmentRenderPlan.Anchor.TORSO) cubes.texOffs(0,0).addBox(-4,-1,-2.2F,8,12,4.4F);
                if (a == EquipmentRenderPlan.Anchor.LEFT_ARM || a == EquipmentRenderPlan.Anchor.RIGHT_ARM) {
                    int width = armWidth(slim);
                    float x = armMinX(slim, a);
                    cubes.texOffs(0,0).addBox(x-0.025F,-1,-2.225F,width+0.05F,7,4.45F);
                }
                if (a == EquipmentRenderPlan.Anchor.LEFT_LEG || a == EquipmentRenderPlan.Anchor.RIGHT_LEG) cubes.texOffs(0,0).addBox(-2.1F,0,-2.1F,4.2F,PANTS_BOTTOM,4.2F);
            }
            case "outerClothing" -> {
                if (a == EquipmentRenderPlan.Anchor.TORSO) cubes.texOffs(0,0).addBox(-4.4F,-1.3F,-2.5F,8.8F,12.3F,5);
                else { int width = armWidth(slim); float x = armMinX(slim, a);
                    cubes.texOffs(0,0).addBox(x-0.35F,-1.3F,-2.4F,width+0.7F,7.5F,4.8F); }
            }
            case "gloves" -> { int width = armWidth(slim); float x = armMinX(slim, a);
                cubes.texOffs(0,0).addBox(x-0.45F,5.25F,-2.6F,width+0.9F,4.8F,5.2F); }
            case "shoes" -> cubes.texOffs(0,0).addBox(-2.2F,9,-3,4.4F,SHOES_BOTTOM-9,6);
            case "back" -> cubes.texOffs(0,0).addBox(-4,0,2,8,9,3);
            case "belt" -> cubes.texOffs(0,0).addBox(-4.2F,8,-2.5F,8.4F,2,5);
            case "id" -> cubes.texOffs(0,0).addBox(-1.5F,1,-2.8F,3,4,1);
            case "suitstorage" -> cubes.texOffs(0,0).addBox(3,3,-2,3,4,3);
            default -> { }
        }
        root.addOrReplaceChild("garment", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
}

