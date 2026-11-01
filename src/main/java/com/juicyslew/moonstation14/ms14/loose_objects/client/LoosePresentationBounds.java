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
        double[] minX={Double.POSITIVE_INFINITY},minY={Double.POSITIVE_INFINITY},minZ={Double.POSITIVE_INFINITY};
        double[] maxX={Double.NEGATIVE_INFINITY},maxY={Double.NEGATIVE_INFINITY},maxZ={Double.NEGATIVE_INFINITY};
        forEachTransformedModelVertex(model, matrix, v -> {
            minX[0]=Math.min(minX[0],v.x); maxX[0]=Math.max(maxX[0],v.x);
            minY[0]=Math.min(minY[0],v.y); maxY[0]=Math.max(maxY[0],v.y);
            minZ[0]=Math.min(minZ[0],v.z); maxZ[0]=Math.max(maxZ[0],v.z);
        });
        if (!Double.isFinite(minX[0])) return null;
        return new AABB(minX[0]+position.x,minY[0]+position.y,minZ[0]+position.z,
                maxX[0]+position.x,maxY[0]+position.y,maxZ[0]+position.z);
    }

    /** Projection support of flat-sprite vertices after caller pose, profile pose, and NONE transform.
     * Caller pose must contain weapon rotation only (no camera/view transform or world translation).
     * A null result means no model vertices were available. Axis must be finite and nonzero.
     */
    public static ProjectionExtent flatSpriteProjection(net.minecraft.client.resources.model.BakedModel model,
                                                        LooseObjectProfile profile, float appearanceScale,
                                                        org.joml.Matrix4f callerPose, org.joml.Vector3f axis) {
        if (axis == null || !Float.isFinite(axis.x) || !Float.isFinite(axis.y) || !Float.isFinite(axis.z)
                || axis.lengthSquared() == 0f) return null;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(callerPose);
        LooseItemRenderer.applyCameraFacingFlatPose(pose, profile, appearanceScale);
        model = model.applyTransform(net.minecraft.world.item.ItemDisplayContext.NONE, pose, false);
        pose.translate(-.5, -.5, -.5);
        float[] range={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
        Vector3f[] maximumPoint={null};
        Vector3f unit=new Vector3f(axis).normalize();
        forEachTransformedModelVertex(model, pose.last().pose(), v -> {
            float projection=v.dot(unit); range[0]=Math.min(range[0],projection);
            if (projection > range[1]) { range[1]=projection; maximumPoint[0]=new Vector3f(v); }
        });
        return Float.isFinite(range[0]) ? new ProjectionExtent(range[0],range[1],new Vec3(maximumPoint[0].x,maximumPoint[0].y,maximumPoint[0].z)) : null;
    }
    /** maxPoint is an immutable snapshot of an actual transformed vertex attaining max. */
    public record ProjectionExtent(float min, float max, Vec3 maxPoint) { }

    private static void forEachTransformedModelVertex(net.minecraft.client.resources.model.BakedModel model,
                                                       org.joml.Matrix4f matrix, java.util.function.Consumer<Vector3f> consumer) {
        var random=net.minecraft.util.RandomSource.create(0);
        var directions=new java.util.ArrayList<net.minecraft.core.Direction>(); directions.add(null);
        java.util.Collections.addAll(directions,net.minecraft.core.Direction.values());
        for(var direction:directions) { random.setSeed(0); for(var quad:model.getQuads(null,direction,random)) {
            var vertices=quad.getVertices(); for(int i=0;i<4;i++) { int at=i*(com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK.getVertexSize()/4);
                consumer.accept(matrix.transformPosition(new Vector3f(Float.intBitsToFloat(vertices[at]),Float.intBitsToFloat(vertices[at+1]),Float.intBitsToFloat(vertices[at+2]))));
            }
        }}
    }
}
