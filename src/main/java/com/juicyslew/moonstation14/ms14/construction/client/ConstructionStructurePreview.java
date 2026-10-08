package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.chat.Component;

/** Read-only preview of the resolved initial physical construction stage. */
@EventBusSubscriber(modid=MoonStation14.MOD_ID,value=Dist.CLIENT)
public final class ConstructionStructurePreview {
    public enum Readiness { WRONG_MATERIAL, INSUFFICIENT, READY }
    public enum Target { UNAVAILABLE, BLOCKED, AVAILABLE }
    public record Status(Readiness material, Target target, int held, int required) { }
    private ConstructionStructurePreview() { }
    public enum Provider { BLOCK, DISPOSAL, UNSUPPORTED }
    public static Provider provider(String entity) {
        return provider(entity,PrototypeRuntime.clientConstructionEntities(),PrototypeRuntime.clientDisposals());
    }
    public static Provider provider(String entity,com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> blocks,
                             com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData> disposalEntities) {
        if(entity==null)return Provider.UNSUPPORTED;
        var id=ModConstruction.entityId(entity);
        var disposal=disposalEntities.get(id);
        if(disposal!=null)try { if(disposal.tube()!=null&&disposal.geometry()!=null)return Provider.DISPOSAL; } catch(IllegalStateException ignored) { }
        return blocks.get(id)!=null?Provider.BLOCK:Provider.UNSUPPORTED;
    }

    /** True when this class owns the shared plan HUD row (disposal retains its legacy palette HUD). */
    public static boolean rendersGeneralPlanHud() {
        ResourceLocation id=DisposalConstructionClient.sharedRecipe(); if(id==null)return false;
        var recipe=PrototypeRuntime.clientConstructionCatalog().recipe(id);
        return recipe!=null&&recipe.outputKind()==ConstructionRecipeData.OutputKind.STRUCTURE
                &&provider(PrototypeRuntime.clientConstructionCatalog().initialStage(id).map(s->s.entity()).orElse(null))==Provider.BLOCK;
    }

    public static Status status(String material, int required, ItemStack held, boolean targetLoaded, boolean eligible) {
        Readiness r = readiness(material, required, held);
        Target t = !targetLoaded || (r == Readiness.INSUFFICIENT) ? Target.UNAVAILABLE : eligible ? Target.AVAILABLE : Target.BLOCKED;
        return new Status(r, t, held == null ? 0 : held.getCount(), required);
    }
    public static Readiness readiness(String material, int required, ItemStack held) {
        ResourceLocation id=ResourceLocation.tryParse(material == null ? "" : material);
        if (held==null || held.isEmpty() || id==null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                || !held.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))) return Readiness.WRONG_MATERIAL;
        return held.getCount()<required ? Readiness.INSUFFICIENT : Readiness.READY;
    }
    public static BlockHitResult hit(Mob body) {
        var eye=body.getEyePosition(); var result=body.level().clip(new ClipContext(eye,eye.add(body.getLookAngle().scale(5)),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        return result instanceof BlockHitResult b && b.getType()==HitResult.Type.BLOCK ? b : null;
    }
    /** Preview preflight follows the server's existing air-only foundation rule. */
    public static boolean foundationEligible(boolean loaded,boolean inWorldBorder,boolean air,boolean bodyCollides) {
        return loaded&&inWorldBorder&&air&&!bodyCollides;
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); Mob body=GhostControlClient.ownedCharacterForHud();
        if(!ConstructionPreviewView.allowed(mc.level!=null,mc.options.hideGui,body!=null,body!=null&&body.level()==mc.level,GhostControlClient.committedCharacterEpoch()))return;
        ResourceLocation recipeId=DisposalConstructionClient.sharedRecipe(); if(recipeId==null)return;
        var catalog=PrototypeRuntime.clientConstructionCatalog(); var recipe=catalog.recipe(recipeId);
        if(recipe==null||recipe.outputKind()!=ConstructionRecipeData.OutputKind.STRUCTURE)return;
        var stage=catalog.initialStage(recipeId).orElse(null); if(stage==null)return;
        if(provider(stage.entity())!=Provider.BLOCK)return;
        var cost=stage.steps().stream().filter(ConstructionGraph.MaterialStep.class::isInstance).map(ConstructionGraph.MaterialStep.class::cast).findFirst().orElse(null);
        if(cost==null)return;
        ItemStack held=BodyHandClient.activeHeldStackFor(body.getUUID(),GhostControlClient.committedCharacterEpoch()).orElse(ItemStack.EMPTY);
        Readiness materialReadiness=readiness("moonstation14:"+cost.material().name().toLowerCase(),cost.amount(),held);
        if(materialReadiness==Readiness.WRONG_MATERIAL)return;
        BlockHitResult hit=hit(body); if(hit==null)return; BlockPos target=hit.getBlockPos().relative(hit.getDirection());
        if(!mc.level.hasChunkAt(target))return;
        var entityData=PrototypeRuntime.clientConstructionEntities().get(ModConstruction.entityId(stage.entity())); if(entityData==null)return;
        ResourceLocation blockId=ResourceLocation.tryParse(entityData.block()); if(blockId==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(blockId))return;
        var state=mc.level.getBlockState(target); var targetState=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
        boolean bodyCollides=com.juicyslew.moonstation14.ms14.atmos.piping.BodyPlacementCollision.blockIntersects(mc.level,body,target,targetState);
        boolean valid=foundationEligible(mc.level.hasChunkAt(target),mc.level.getWorldBorder().isWithinBounds(target),state.isAir(),bodyCollides);
        var status=status("moonstation14:"+cost.material().name().toLowerCase(),cost.amount(),held,true,valid);
        var pose=event.getPoseStack(); if(pose==null)return;
        var camera=event.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        var dispatcher=mc.getBlockRenderer(); var block=targetState;
        pose.pushPose(); pose.translate(target.getX()-camera.x,target.getY()-camera.y,target.getZ()-camera.z);
        int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,target);
        // Reuse the actual block model/atlas, but submit it through the translucent model pass.
        var model=dispatcher.getBlockModel(block);
        var kind=status.target()==Target.BLOCKED?ConstructionPreviewTint.Kind.BLOCKED:status.target()==Target.AVAILABLE?ConstructionPreviewTint.Kind.READY:ConstructionPreviewTint.Kind.INSUFFICIENT;
        var vertex=ConstructionPreviewTint.apply(buffers.getBuffer(RenderType.translucent()),kind);
        dispatcher.getModelRenderer().renderModel(pose.last(),vertex,block,model,
                ConstructionPreviewTint.rgba(kind).red(),ConstructionPreviewTint.rgba(kind).green(),ConstructionPreviewTint.rgba(kind).blue(),light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose(); buffers.endBatch();
    }

}
