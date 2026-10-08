package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceRequest;
import com.juicyslew.moonstation14.ms14.construction.network.ConstructionGuidanceState;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Dwell-triggered guidance request and compact, nonmodal HUD presentation. */
@EventBusSubscriber(modid=MoonStation14.MOD_ID,value=Dist.CLIENT)
public final class ConstructionGuidanceClient {
    private static final ConstructionGuidanceDwell DWELL=new ConstructionGuidanceDwell();
    private static ConstructionGuidanceDwell.Target aimed;
    private static ConstructionGuidanceState visible;
    private static long sequence,lastRequest;
    private static Minecraft bound;
    private ConstructionGuidanceClient(){ }
    static { ConstructionGuidanceState.setClientHandler(ConstructionGuidanceClient::receive); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!ConstructionGuidanceAdmission.allowed(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen,
                mc.screen!=null,mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen,
                com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.textFocused())){clear();return;}
        LivingEntity body=body(mc); long epoch=epoch(mc);
        if(body==null||epoch<=0){clear();return;}
        if(bound!=mc){clear();bound=mc;}
        BlockHitResult hit=body.pick(6,1f,false) instanceof BlockHitResult b&&b.getType()==HitResult.Type.BLOCK?b:null;
        ConstructionGuidanceDwell.Target next=hit!=null&&candidate(hit.getBlockPos())
                ?new ConstructionGuidanceDwell.Target(mc.level.dimension(),hit.getBlockPos(),body.getUUID(),epoch):null;
        aimed=next;
        if(next==null){visible=null;DWELL.update(null,System.nanoTime());return;}
        if(visible!=null&&!next.equals(targetOf(visible))){visible=null;lastRequest=0;}
        long now=System.nanoTime();
        boolean refresh=visible!=null&&now-lastRequest>=1_000_000_000L;
        if(refresh)DWELL.rearm();
        if(DWELL.update(next,now)||refresh){
            if(sequence==Long.MAX_VALUE){sequence=0;}
            UUID expected=visible!=null&&next.equals(targetOf(visible))?visible.host():null;
            PacketDistributor.sendToServer(new ConstructionGuidanceRequest(epoch,++sequence,hit.getBlockPos(),hit.getDirection(),expected));
            lastRequest=now;
        }
    }

    private static void receive(ConstructionGuidanceState state){
        Minecraft mc=Minecraft.getInstance();
        if(aimed==null||state.sequence()!=sequence||state.epoch()!=aimed.bodyEpoch()
                ||!state.body().equals(aimed.body())||!state.target().equals(aimed.position())
                ||!mc.level.dimension().equals(aimed.dimension()))return;
        visible=state.goal().isEmpty()||state.requirement().isEmpty()||state.host()==null?null:state;
    }
    private static ConstructionGuidanceDwell.Target targetOf(ConstructionGuidanceState s){
        return aimed!=null&&aimed.position().equals(s.target())?aimed:null;
    }
    private static boolean candidate(net.minecraft.core.BlockPos pos){
        var mc=Minecraft.getInstance();if(mc.level==null)return false;Block b=mc.level.getBlockState(pos).getBlock();
        var entities=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientConstructionEntities();
        return entities.asMap().values().stream().anyMatch(data->{var id=net.minecraft.resources.ResourceLocation.tryParse(data.block());return id!=null&&net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)&&net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id)==b;});
    }
    private static LivingEntity body(Minecraft mc){
        var owned=GhostControlClient.ownedCharacterForHud();
        if(owned!=null&&owned.level()==mc.level)return owned;
        LocalPlayer player=mc.player;
        return player!=null&&!player.isSpectator()?player:null;
    }
    private static long epoch(Minecraft mc){long ghost=GhostControlClient.committedCharacterEpoch();return ghost>0?ghost:1;}
    private static void clear(){DWELL.reset();aimed=null;visible=null;lastRequest=0;bound=null;}

    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(visible==null||aimed==null||mc.player==null||mc.options.hideGui||com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient.paletteOpen())return;
        if(!aimed.position().equals(visible.target())||!aimed.body().equals(visible.body()))return;
        int y=com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient.sharedRecipe()==null
                ?ConstructionHudRenderer.TOP_OFFSET:ConstructionHudRenderer.lastPlanBottom()+4;
        ConstructionHudRenderer.drawGuidanceAt(event.getGuiGraphics(),mc,
                net.minecraft.network.chat.Component.literal(visible.goal()),
                y,
                ConstructionHudRenderer.guidanceRows(new ConstructionHudRenderer.Guidance(visible.goal(),visible.requirement())).get(0));
    }
}
