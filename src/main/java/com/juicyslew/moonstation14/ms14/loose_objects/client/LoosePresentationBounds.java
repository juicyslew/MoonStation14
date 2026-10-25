package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.botany.ProduceWorldAppearance;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Draw-only bounds of the actual NONE-context baked model for scaled flat produce. */
public final class LoosePresentationBounds {
    private LoosePresentationBounds() { }

    /** Returns null when this is not the supported flat-produce presentation path. */
    public static AABB flatProduce(LooseItemEntity entity, float partialTick) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return null;
        LooseObjectProfile profile;
        try { profile = LooseObjectProfile.resolveForStack(stack, true); }
        catch (IllegalArgumentException unavailable) { return null; }
        if (!"flat_sprite".equals(profile.renderKind())
                || stack.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null) return null;
        var model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), null, entity.getId());
        Vec3 pos = new Vec3(net.minecraft.util.Mth.lerp(partialTick,entity.xOld,entity.getX()),
                net.minecraft.util.Mth.lerp(partialTick,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partialTick,entity.zOld,entity.getZ()));
        return flatProduce(model, profile, ProduceWorldAppearance.scale(stack), entity.getRotationAngle(partialTick), pos,
                LooseItemRenderLayers.offset(entity));
    }

    /** Geometry path isolated for deterministic tests; mirrors ItemRenderer's NONE-context model setup. */
    static AABB flatProduce(net.minecraft.client.resources.model.BakedModel model, LooseObjectProfile profile,
                            float appearanceScale, float yawRadians, Vec3 position, double layerOffset) {
        PoseStack pose = new PoseStack();
        pose.translate(0, layerOffset, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(yawRadians));
        LooseItemRenderer.applyFlatPose(pose, profile, appearanceScale);
        model = model.applyTransform(net.minecraft.world.item.ItemDisplayContext.NONE, pose, false);
        pose.translate(-.5, -.5, -.5);
        var matrix = pose.last().pose();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        var random = net.minecraft.util.RandomSource.create(0);
        var directions = new java.util.ArrayList<net.minecraft.core.Direction>();
        directions.add(null);
        java.util.Collections.addAll(directions, net.minecraft.core.Direction.values());
        for (var direction : directions) {
            random.setSeed(0);
            for (var quad : model.getQuads(null, direction, random)) {
                var vertices = quad.getVertices();
                for (int i=0;i<4;i++) {
                    int at=i*(com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK.getVertexSize()/4);
                    Vector3f v=matrix.transformPosition(new Vector3f(Float.intBitsToFloat(vertices[at]),Float.intBitsToFloat(vertices[at+1]),Float.intBitsToFloat(vertices[at+2])));
                    minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);
                }
            }
        }
        if (!Double.isFinite(minX)) return null;
        return new AABB(minX+position.x,minY+position.y,minZ+position.z,
                maxX+position.x,maxY+position.y,maxZ+position.z);
    }
}
