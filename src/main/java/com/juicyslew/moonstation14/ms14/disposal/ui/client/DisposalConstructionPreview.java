package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.client.DisposalTubeVisualRenderer;
import com.juicyslew.moonstation14.ms14.disposal.domain.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.*;

/** Client-only construction preview; it never places blocks or sends construction authority. */
public final class DisposalConstructionPreview {
    public record PreviewPlan(UUID body, long epoch, String prototype, int rotationOffset) { }
    public record PlacementStatus(boolean aimed, boolean valid, String reason) { }
    public record ConfigurationPlan(UUID body,long epoch,BlockPos pos,String prototype,DisposalOrientation frame) { }
    private static Supplier<PreviewPlan> planProvider = () -> null;
    private static BiFunction<UUID, Long, Optional<ItemStack>> heldStackProvider = BodyHandClient::activeHeldStackFor;
    private static BiFunction<BlockPos, ResourceLocation, Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord>> tubeRecordProvider = (p,d) -> Optional.empty();
    private static Consumer<PlacementStatus> readiness = ignored -> { };
    private static Supplier<ConfigurationPlan> configurationPlanProvider = () -> null;
    private DisposalConstructionPreview() { }
    public static void setPlanProvider(Supplier<PreviewPlan> provider) { planProvider=Objects.requireNonNull(provider); }
    public static void setHeldStackProvider(BiFunction<UUID, Long, Optional<ItemStack>> provider) { heldStackProvider=Objects.requireNonNull(provider); }
    public static void setTubeRecordProvider(BiFunction<BlockPos, ResourceLocation, Optional<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord>> provider) { tubeRecordProvider=Objects.requireNonNull(provider); }
    public static void setReadiness(Consumer<PlacementStatus> callback) { readiness=Objects.requireNonNull(callback); }
    public static void setConfigurationPlanProvider(Supplier<ConfigurationPlan> provider) { configurationPlanProvider=Objects.requireNonNull(provider); }
    public static void register() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DisposalConstructionPreview::render); }

    /** Shared ray target calculation for HUD consumers. */
    public static BlockHitResult previewTarget(Mob body) {
        var eye=body.getEyePosition(); var hit=body.level().clip(new ClipContext(eye,eye.add(body.getLookAngle().scale(5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        return hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK ? block : null;
    }
    public static BlockPos target(BlockHitResult hit, boolean sealed) { return sealed ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection()); }
    public static boolean enoughMaterial(ItemStack stack, String material, int count) {
        var id=ResourceLocation.tryParse(material);
        return id!=null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                && stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)) && stack.getCount()>=count;
    }
    public static boolean interactive(net.minecraft.world.level.block.state.BlockState state) {
        var block=state.getBlock();
        return state.hasBlockEntity() || block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock || block instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); Mob body=GhostControlClient.ownedCharacterForHud();
        if(mc.screen instanceof DisposalBuildScreen){
            ConfigurationPlan config=configurationPlanProvider.get();
            if(config==null||mc.level==null||body==null||mc.getCameraEntity()!=body||!config.body().equals(body.getUUID())
                    ||config.epoch()!=GhostControlClient.committedCharacterEpoch()||!mc.level.hasChunkAt(config.pos()))return;
            var id=ResourceLocation.tryParse(config.prototype());var data=id==null?null:PrototypeRuntime.clientDisposals().get(id);
            if(data==null||data.tube()==null||data.geometry()==null)return;
            var boxes=data.geometry().boxes(data.tube(),config.frame());var pose=event.getPoseStack();if(pose==null)return;
            var camera=event.getCamera().getPosition();MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();var consumer=buffers.getBuffer(RenderType.debugFilledBox());
            DisposalTubeVisualRenderer.renderColoredGhostBoxes(consumer,pose,boxes,config.pos().getX()-camera.x,config.pos().getY()-camera.y,config.pos().getZ()-camera.z,40,220,255,105);buffers.endBatch();return;
        }
        PreviewPlan plan=planProvider.get();
        PlacementStatus status=new PlacementStatus(false,false,"no_target");
        try {
            if(plan==null||mc.level==null||mc.player==null||mc.options.hideGui||!mc.player.isSpectator()||body==null||mc.getCameraEntity()!=body
                    ||body.level()!=mc.level||!body.isAlive()||plan.epoch()<=0||!plan.body().equals(body.getUUID())||plan.epoch()!=GhostControlClient.committedCharacterEpoch())return;
            var held=heldStackProvider.apply(plan.body(),plan.epoch()).orElse(null);
            var data=PrototypeRuntime.clientDisposals().get(ResourceLocation.tryParse(plan.prototype()));
            if(data==null||data.tube()==null||data.geometry()==null||data.construction()==null||held==null||!materialMatches(held,data.construction().material()))return;
            boolean enough=enoughMaterial(held,data.construction().material(),data.construction().steelCount());
            var hit=previewTarget(body); if(hit==null)return;
            boolean sealed=mc.level.hasChunkAt(hit.getBlockPos())&&mc.level.getBlockState(hit.getBlockPos()).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                    &&com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility.defaults().eligible(mc.level,hit.getBlockPos())
                    &&data.tube().kind()==com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Kind.STRAIGHT;
            BlockPos target=target(hit,sealed); if(!mc.level.hasChunkAt(target))return;
            if(sealed&&tubeRecordProvider.apply(target,mc.level.dimension().location()).isPresent())return;
            var preferred=DisposalOrientation.placement(hit.getDirection(),net.minecraft.core.Direction.getNearest(body.getLookAngle().x,body.getLookAngle().y,body.getLookAngle().z));
            var neighbours=EnumSet.noneOf(net.minecraft.core.Direction.class);
            for(var d:net.minecraft.core.Direction.values()) { var p=target.relative(d); if(!mc.level.hasChunkAt(p))continue;
                var rec=tubeRecordProvider.apply(p,mc.level.dimension().location()).orElse(null); if(rec==null)continue;
                var nd=PrototypeRuntime.clientDisposals().get(ResourceLocation.tryParse(rec.prototypeId()));
                if(rec.anchored()&&nd!=null&&nd.tube()!=null&&DisposalTubeRouting.worldConnections(nd.tube(),rec.frame()).contains(DisposalDirection.fromMC(d.getOpposite()))) neighbours.add(d);
            }
            var candidates=DisposalOrientationCandidates.candidates(data.tube(),preferred,neighbours); var frame=candidates.get(Math.floorMod(plan.rotationOffset(),candidates.size()));
            var boxes=data.geometry().boxes(data.tube(),frame); var bodyBox=body.getBoundingBox(); boolean valid=target.getY()>=mc.level.getMinBuildHeight()&&target.getY()<mc.level.getMaxBuildHeight()&&(sealed||mc.level.getBlockState(target).isAir());
            for(var local:boxes){var world=local.move(target);if(world.intersects(bodyBox))valid=false;
                for(var entity:mc.level.getEntities(body,world.inflate(1.0e-7)))if(entity.getBoundingBox().intersects(world))valid=false;
                int x0=net.minecraft.util.Mth.floor(world.minX),x1=net.minecraft.util.Mth.floor(Math.nextDown(world.maxX)),y0=net.minecraft.util.Mth.floor(world.minY),y1=net.minecraft.util.Mth.floor(Math.nextDown(world.maxY)),z0=net.minecraft.util.Mth.floor(world.minZ),z1=net.minecraft.util.Mth.floor(Math.nextDown(world.maxZ));
                for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){BlockPos p=new BlockPos(x,y,z);if(!mc.level.hasChunkAt(p)){valid=false;continue;}if(p.equals(target)&&sealed)continue;var state=mc.level.getBlockState(p);if(interactive(state))valid=false;for(var c:state.getCollisionShape(mc.level,p,CollisionContext.empty()).toAabbs())if(c.move(p).intersects(world))valid=false;}}
            status=new PlacementStatus(true,valid&&enough,!enough?"insufficient_material":valid?"ready":"blocked");
            var pose=event.getPoseStack(); if(pose==null)return; var camera=event.getCamera().getPosition(); MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();var consumer=buffers.getBuffer(RenderType.debugFilledBox());
            int r=valid?(enough?40:135):255,g=valid?(enough?255:135):35,b=valid?(enough?80:35):35; DisposalTubeVisualRenderer.renderColoredGhostBoxes(consumer,pose,boxes,target.getX()-camera.x,target.getY()-camera.y,target.getZ()-camera.z,r,g,b,valid&&enough?220:150);buffers.endBatch();
        } finally { readiness.accept(status); }
    }
    static boolean materialMatches(ItemStack stack,String material){var id=ResourceLocation.tryParse(material);return id!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)&&stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));}
}
