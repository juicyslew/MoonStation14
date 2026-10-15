package com.juicyslew.moonstation14.ms14.construction.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.domain.CraftQueue;
import com.juicyslew.moonstation14.ms14.construction.domain.CraftWorkSchedule;
import com.juicyslew.moonstation14.ms14.construction.inventory.BodyCraftInventoryService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import java.util.*;

/** Body-authoritative craft queue. Registration is intentionally exposed for central payload registration. */
public final class ConstructionCraftNetworking {
    private ConstructionCraftNetworking() { }
    private static final class Active {
        final ServerPlayer actor; final UUID body; final long epoch,sequence; final CraftQueue queue; boolean busy;
        Active(ServerPlayer actor,UUID body,long epoch,long sequence,CraftQueue queue){this.actor=actor;this.body=body;this.epoch=epoch;this.sequence=sequence;this.queue=queue;}
        ServerPlayer actor(){return actor;} UUID body(){return body;} long epoch(){return epoch;} long sequence(){return sequence;} CraftQueue queue(){return queue;}
    }
    private static final Map<UUID,Active> QUEUES=new HashMap<>();
    private static final Map<UUID,Long> SEQUENCES=new HashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        NeoForge.EVENT_BUS.addListener(ConstructionCraftNetworking::tick);
        NeoForge.EVENT_BUS.addListener(ConstructionCraftNetworking::stopped);
        NeoForge.EVENT_BUS.addListener(ConstructionCraftNetworking::logout);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ConstructionCraftRequest.TYPE,ConstructionCraftRequest.STREAM_CODEC,(r,c)->{
                    if(!(c.player() instanceof ServerPlayer p)||p.connection==null||p.getServer()==null||p.getServer().getPlayerList().getPlayer(p.getUUID())!=p)return;
                    handle(p,r);
                }).playToClient(ConstructionCraftState.TYPE,ConstructionCraftState.STREAM_CODEC,(s,c)->{if(FMLEnvironment.dist==Dist.CLIENT)ConstructionCraftState.deliverClient(s);});
    }
    private static void handle(ServerPlayer actor,ConstructionCraftRequest request) {
        var auth=HandActorAuthority.resolve(actor).orElse(null);
        if(auth==null||auth.epoch()!=request.epoch()) { send(actor,request,null,0,"No current body authority"); return; }
        UUID id=auth.body().getUUID();
        long previous=SEQUENCES.getOrDefault(actor.getUUID(),0L);
        if(request.sequence()<=previous){send(actor,request,auth.body().getUUID(),0,"Stale request");return;}
        SEQUENCES.put(actor.getUUID(),request.sequence());
        var hands=auth.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(hands==null||!hands.compatible(auth.body())||hands.revision()!=request.handsRevision()){send(actor,request,id,0,"Hands changed; refresh and retry");return;}
        if(request.cancel()) { var old=QUEUES.remove(id);if(old!=null)TimedInteractionSystem.cancelDuplicate(auth.body(),"construction_craft",old.queue().recipe());send(actor,request,id,0,"Queue cancelled");return; }
        var catalog=PrototypeRuntime.serverConstructionCatalog();var recipeId=ResourceLocation.tryParse(request.recipe());
        var recipe=recipeId==null?null:catalog.recipe(recipeId);
        if(recipe==null||recipe.outputKind()!=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData.OutputKind.ITEM){send(actor,request,id,0,"Recipe unavailable");return;}
        try { QUEUES.put(id,new Active(actor,id,auth.epoch(),request.sequence(),new CraftQueue(request.recipe(),request.quantity())));send(actor,request,id,request.quantity(),"Queued"); }
        catch(IllegalArgumentException invalid){send(actor,request,id,0,"Invalid quantity");}
    }
    private static void tick(ServerTickEvent.Post event) {
        for(var entry:List.copyOf(QUEUES.entrySet())) {
            Active active=entry.getValue(); ServerPlayer actor=active.actor();
            if(actor.isRemoved()||actor.getServer()!=event.getServer()||event.getServer().getPlayerList().getPlayer(actor.getUUID())!=actor){QUEUES.remove(entry.getKey());continue;}
            var authority=HandActorAuthority.resolve(actor).orElse(null);
            if(authority==null||authority.epoch()!=active.epoch()||!authority.body().getUUID().equals(active.body())){QUEUES.remove(entry.getKey());send(actor,active.sequence(),active.epoch(),active.body(),active.queue().recipe(),0,"Body authority changed; queue stopped");continue;}
            if(!active.queue().active()){QUEUES.remove(entry.getKey());continue;}
            var catalog=PrototypeRuntime.serverConstructionCatalog();var rid=ResourceLocation.tryParse(active.queue().recipe());var recipe=rid==null?null:catalog.recipe(rid);
            try {
                if(recipe==null||recipe.outputKind()!=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData.OutputKind.ITEM)throw new IllegalStateException("Recipe unavailable");
                var requirements=catalog.startingRequirements(rid); if(requirements.stream().anyMatch(s->!(s instanceof ConstructionGraph.MaterialStep)))throw new IllegalStateException("Recipe requires unsupported tools");
                if(active.busy)continue;
                int ticks=CraftWorkSchedule.durationTicks(requirements);
                if(ticks>0) {
                    active.busy=true;
                    var started=TimedInteractionSystem.start(authority.body(),actor,active.sequence(),"construction_craft",active.queue().recipe(),ticks,true,true,
                            ()->timedValid(active),()->{active.busy=false;return completeOne(active)?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.STOP;});
                    if(started!=com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED){active.busy=false;stop(active,"Unable to start crafting work");}
                    continue;
                }
                completeOne(active);
            } catch(RuntimeException failure){stop(active,failure.getMessage()==null?"Craft stopped":failure.getMessage());}
        }
    }
    private static boolean timedValid(Active active){
        if(QUEUES.get(active.body())!=active)return false;
        var authority=HandActorAuthority.resolve(active.actor()).orElse(null);
        if(authority==null||authority.epoch()!=active.epoch()||!authority.body().getUUID().equals(active.body())){stop(active,"Body authority changed; queue stopped");return false;}
        try {
            var catalog=PrototypeRuntime.serverConstructionCatalog();var id=ResourceLocation.tryParse(active.queue().recipe());var recipe=id==null?null:catalog.recipe(id);
            if(recipe==null){stop(active,"Recipe unavailable; queue stopped");return false;}
            var costs=costs(catalog.startingRequirements(id));var live=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if(live==null||!live.compatible(authority.body())){stop(active,"Body inventory unavailable; queue stopped");return false;}
            for(var e:costs.entrySet())if(!live.hasAdequateBodyMaterial(s->s.is(material(e.getKey())),e.getValue())
                    && !BodyCraftInventoryService.hasNearbyMaterial(authority.body(),s->s.is(material(e.getKey())),e.getValue())){stop(active,"Required material unavailable; queue stopped");return false;}
            return true;
        }catch(RuntimeException failure){stop(active,"Craft interrupted");return false;}
    }
    private static boolean completeOne(Active active){
        if(QUEUES.get(active.body())!=active)return false;
        try {
                var actor=active.actor(); var authority=HandActorAuthority.resolve(actor).orElse(null);
                if(authority==null||authority.epoch()!=active.epoch()||!authority.body().getUUID().equals(active.body()))throw new IllegalStateException("Body authority changed; queue stopped");
                var catalog=PrototypeRuntime.serverConstructionCatalog();var rid=ResourceLocation.tryParse(active.queue().recipe());var recipe=rid==null?null:catalog.recipe(rid);
                if(recipe==null||recipe.outputKind()!=com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData.OutputKind.ITEM)throw new IllegalStateException("Recipe unavailable");
                var requirements=catalog.startingRequirements(rid);var costs=costs(requirements);
                if(costs.size()!=1)throw new IllegalStateException("Recipe has unsupported material combination");
                var cost=costs.entrySet().iterator().next(); Item material=material(cost.getKey());
                var graphId=ResourceLocation.tryParse(recipe.graph().contains(":")?recipe.graph():"moonstation14:"+recipe.graph());
                String entity=Objects.requireNonNull(catalog.graph(graphId).nodes().get(recipe.targetNode()).entity()).orElseThrow();
                ResourceLocation itemId=ResourceLocation.tryParse(entity); if(itemId==null||!BuiltInRegistries.ITEM.containsKey(itemId))throw new IllegalStateException("Output item is not registered");
                int count=catalog.outputStackCount(rid);var out=new ItemStack(BuiltInRegistries.ITEM.get(itemId),count);
                LiveHands live=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                if(live==null||!live.compatible(authority.body()))throw new IllegalStateException("Body inventory unavailable");
                var result=BodyCraftInventoryService.craftBody(actor,live.revision(),s->s.is(material),cost.getValue(),out);
                if(result!=BodyCraftInventoryService.Result.SUCCESS)throw new IllegalStateException(result==BodyCraftInventoryService.Result.CAPACITY?"No room for output":result==BodyCraftInventoryService.Result.MATERIAL_MISSING?"Required material unavailable":"Craft interrupted");
                active.queue().completedOne(); int remaining=active.queue().remaining();
                send(actor,active.sequence(),active.epoch(),active.body(),active.queue().recipe(),remaining,remaining==0?"Craft complete":"Crafted one; queue continuing");
                if(remaining==0)QUEUES.remove(active.body());
                return true;
        } catch(RuntimeException failure){stop(active,failure.getMessage()==null?"Craft stopped":failure.getMessage());return false;}
    }
    private static Map<ConstructionGraph.MaterialId,Integer> costs(List<ConstructionGraph.Step> requirements){
        if(requirements.stream().anyMatch(s->!(s instanceof ConstructionGraph.MaterialStep)))throw new IllegalStateException("Recipe requires unsupported tools");
        Map<ConstructionGraph.MaterialId,Integer> costs=new EnumMap<>(ConstructionGraph.MaterialId.class);
        for(var step:requirements){var m=(ConstructionGraph.MaterialStep)step;costs.merge(m.material(),m.amount(),Math::addExact);}return costs;
    }
    private static void stop(Active active,String message){active.queue().stop();active.busy=false;QUEUES.remove(active.body());send(active.actor(),active.sequence(),active.epoch(),active.body(),active.queue().recipe(),0,message);}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p){var entries=QUEUES.values().stream().filter(a->a.actor()==p).toList();for(var a:entries){QUEUES.remove(a.body());if(p.level() instanceof net.minecraft.server.level.ServerLevel level&&level.getEntity(a.body()) instanceof net.minecraft.world.entity.LivingEntity body)TimedInteractionSystem.cancelDuplicate(body,"construction_craft",a.queue().recipe());}SEQUENCES.remove(p.getUUID());}}
    private static Item material(ConstructionGraph.MaterialId id){return switch(id){case Steel->ModItems.STEEL.get();case Plastic->ModItems.PLASTIC.get();};}
    private static void send(ServerPlayer p,ConstructionCraftRequest r,UUID body,int n,String message){PacketDistributor.sendToPlayer(p,new ConstructionCraftState(r.epoch(),r.sequence(),body,r.recipe(),n,message));}
    private static void send(ServerPlayer p,long seq,long epoch,UUID body,String recipe,int n,String message){PacketDistributor.sendToPlayer(p,new ConstructionCraftState(epoch,seq,body,recipe,n,message));}
    private static void stopped(ServerStoppedEvent event){QUEUES.clear();SEQUENCES.clear();}
}

