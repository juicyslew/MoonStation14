package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

@EventBusSubscriber(modid=MoonStation14.MOD_ID, value=Dist.CLIENT)
public final class DisarmFlightRenderer {
    private DisarmFlightRenderer() { }
    public static List<Vec3> geometry(Vec3 origin, Vec3 velocity, Vec3 camera) {
        return geometry(origin, velocity, camera, 2.0);
    }
    public static List<Vec3> geometry(Vec3 origin, Vec3 velocity, Vec3 camera, double maxDistance) {
        if (origin == null || velocity == null || camera == null || velocity.lengthSqr() < 1e-8) return List.of();
        double length = Math.min(2.0, Math.max(0, maxDistance));
        if (length <= 0) return List.of();
        Vec3 end = origin.add(velocity.normalize().scale(length));
        Vec3 axis = end.subtract(origin).normalize();
        Vec3 side = axis.cross(camera.subtract(origin));
        if (side.lengthSqr() < 1e-8) side = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0));
        side = side.normalize().scale(Math.min(.075, length * .25));
        Vec3 tip = end;
        Vec3 base = end.subtract(axis.scale(Math.min(.3, length * .5)));
        Vec3 wing = side.scale(2.5);
        return List.of(origin.add(side),origin.subtract(side),base.subtract(side),base.add(side),
                base.add(wing), tip, base.subtract(wing));
    }
    static net.minecraft.world.level.ClipContext terrainClipContext(Vec3 origin, Vec3 end) {
        return new net.minecraft.world.level.ClipContext(origin, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, CollisionContext.empty());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance(); var level=mc.level;
        if(level==null){DisarmFlightVisuals.reconcileWorld(null);return;}
        DisarmFlightVisuals.reconcileWorld(level.dimension().location());
        var camera=event.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        var consumer=buffers.getBuffer(RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/white_pixel.png")));
        var matrix=event.getPoseStack().last().pose();
        for(var flight:DisarmFlightVisuals.sample(System.nanoTime())) {
            if(flight.origin().distanceToSqr(camera)>64*64)continue;
            Vec3 initialVelocity=flight.velocity();
            double nominalLength=flight.actor() ? 1.35 : 2.0;
            Vec3 end=flight.origin().add(initialVelocity.normalize().scale(nominalLength));
            var hit=level.clip(terrainClipContext(flight.origin(),end));
            double clipDistance=hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS ? flight.origin().distanceTo(hit.getLocation()) : nominalLength;
            var q=geometry(flight.origin(),initialVelocity,camera,clipDistance); if(q.size()<4)continue;
            Vec3 normal=q.get(1).subtract(q.get(0)).cross(q.get(2).subtract(q.get(0))).normalize();
            // Draw the shaft quad and two arrowhead wings as thin quads through the same consumer.
            for(int[] face : new int[][]{{0,1,2,3},{2,4,5,5},{3,6,5,5}})
                for(int i=0;i<face.length;i++){int index=face[i];Vec3 p=q.get(index).subtract(camera);consumer.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor(255,190,35,250).setUv(i==0||i==3?0:1,i<2?0:1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal((float)normal.x,(float)normal.y,(float)normal.z);}
        }
        buffers.endBatch();
    }
}
