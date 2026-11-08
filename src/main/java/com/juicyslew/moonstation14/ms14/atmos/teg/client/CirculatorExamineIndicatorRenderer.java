package com.juicyslew.moonstation14.ms14.atmos.teg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Small depth-tested port arrows shown only after an accepted examine. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class CirculatorExamineIndicatorRenderer {
    private CirculatorExamineIndicatorRenderer() { }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { CirculatorExamineIndicator.clear(); return; }
        var old = CirculatorExamineIndicator.peek();
        if (old == null) return;
        var block = mc.level.getBlockState(old.position());
        Direction facing = block.hasProperty(AtmosMachineBlock.FACING) ? block.getValue(AtmosMachineBlock.FACING) : null;
        var indicator = CirculatorExamineIndicator.current(mc.level.dimension().location(), mc.level.getGameTime(),
                block.is(ModBlocks.TEG_CIRCULATOR.get()), facing);
        if (indicator == null) return;
        var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack(); pose.pushPose();
        try {
            pose.translate(indicator.position().getX()+.5-camera.x, indicator.position().getY()+.92-camera.y,
                    indicator.position().getZ()+.5-camera.z);
            var buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            var plan = CirculatorExamineRenderPlan.forFacing(indicator.facing());
            arrow(pose, buffer, plan.inlet(), .25f, .2f);
            arrow(pose, buffer, plan.outlet(), .25f, .2f);
            mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        } finally { pose.popPose(); }
    }
    private static void arrow(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer out,
                              CirculatorExamineRenderPlan.Arrow arrow, float length, float z) {
        Direction d=arrow.forward(); float x=arrow.centerX(), centerZ=arrow.centerZ();
        float dx=d.getStepX()*.24f, dz=d.getStepZ()*.24f;
        var matrix=pose.last().pose();
        vertex(out,matrix,x,0,z+centerZ); vertex(out,matrix,x+dx,length,z+centerZ+dz);
        vertex(out,matrix,x+dx,length,z+centerZ+dz);
        vertex(out,matrix,x+dx-d.getStepX()*.08f,length-.08f,z+centerZ+dz-d.getStepZ()*.08f);
        vertex(out,matrix,x+dx,length,z+centerZ+dz);
        vertex(out,matrix,x+dx+d.getStepX()*.08f,length-.08f,z+centerZ+dz+d.getStepZ()*.08f);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out, org.joml.Matrix4f matrix,float x,float y,float z) {
        out.addVertex(matrix,x,y,z).setColor(60,220,255,255).setNormal(0,1,0);
    }
}
