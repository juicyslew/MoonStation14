package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.equipment.*;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess;
import com.juicyslew.moonstation14.ms14.hands.live.BodyStripTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Authenticated viewer state and timed cross-body stripping actions. */
public final class StripService {
    private static final double TARGET_DRIFT=.3;
    private static final String KIND="body-stripping";
    private record View(UUID target,net.minecraft.resources.ResourceLocation dimension,long epoch,long sequence,long targetRevision,boolean available) { }
    private static final Map<UUID,View> VIEWS=new HashMap<>();
    private StripService() { }

    public static void request(ServerPlayer actor,StripViewRequest request) {
        HandActorAuthority.Snapshot auth=HandActorAuthority.resolve(actor).orElse(null);
        if(auth==null||auth.epoch()!=request.epoch()) { sendUnavailable(actor,request,null); return; }
        View current=VIEWS.get(actor.getUUID());
        if(request.operation()==StripViewRequest.Operation.CLOSE) {
            if(current==null||current.epoch()!=request.epoch()||!current.target().equals(request.target())||request.sequence()<=current.sequence())return;
            VIEWS.remove(actor.getUUID()); sendClosed(actor,request,auth); return;
        }
        if(request.operation()==StripViewRequest.Operation.QUERY) {
            if(current==null) { sendClosed(actor,request,auth); return; }
            if(current.epoch()!=request.epoch()||!current.target().equals(request.target())||request.sequence()<=current.sequence())return;
            refresh(actor,auth,current,request.sequence()); return;
        }
        if(current!=null&&current.epoch()==request.epoch()&&request.sequence()<=current.sequence())return;
        LivingEntity target=resolve(auth.body(),request.target());
        if(target==null||target==auth.body()||!CharacterControlSystem.canAct(auth.body())
                ||!hasHand(auth.body())||!StripCapability.resolve(auth.body(),target).actorCanOpen()
                ||!StripCapability.resolve(auth.body(),target).targetCanBeStripped()
                ||!BodyInteractionAccess.inRange(auth.body(),target,StripInteractionRange.BLOCKS)) { sendUnavailable(actor,request,target); return; }
        LiveHands targetHands=target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(targetHands==null||!targetHands.compatible(target)) { sendUnavailable(actor,request,target); return; }
        VIEWS.put(actor.getUUID(),new View(target.getUUID(),target.level().dimension().location(),auth.epoch(),request.sequence(),targetHands.revision(),true));
        PacketDistributor.sendToPlayer(actor,view(actor,auth,target,request.sequence(),StripViewSnapshot.Status.AVAILABLE,true));
    }

    private static void refresh(ServerPlayer actor,HandActorAuthority.Snapshot auth,View current,long sequence) {
        LivingEntity target=resolve(auth.body(),current.target());
        LiveHands hands=target==null?null:target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        boolean available=eligibleView(auth.body(),target,hands);
        long revision=hands!=null&&hands.compatible(target)?hands.revision():current.targetRevision();
        View next=new View(current.target(),current.dimension(),current.epoch(),sequence,revision,available);
        if(target==null) VIEWS.remove(actor.getUUID()); else VIEWS.put(actor.getUUID(),next);
        if(available)PacketDistributor.sendToPlayer(actor,view(actor,auth,target,sequence,StripViewSnapshot.Status.AVAILABLE,true));
        else sendUnavailable(actor,new StripViewRequest(StripViewRequest.Operation.QUERY,auth.epoch(),sequence,current.target(),revision),target,revision);
    }

    private static boolean eligibleView(LivingEntity actor,LivingEntity target,LiveHands hands) {
        return target!=null&&!target.isRemoved()&&hands!=null&&hands.compatible(target)
                &&CharacterControlSystem.canAct(actor)&&hasHand(actor)
                &&StripCapability.resolve(actor,target).actorCanOpen()
                &&StripCapability.resolve(actor,target).targetCanBeStripped()
                &&BodyInteractionAccess.inRange(actor,target,StripInteractionRange.BLOCKS);
    }

    public static void action(ServerPlayer actor,StripSlotAction request) {
        HandActorAuthority.Snapshot auth=HandActorAuthority.resolve(actor).orElse(null);
        LiveHands own=auth==null?null:auth.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        View view=VIEWS.get(actor.getUUID());
        if(auth==null||auth.epoch()!=request.epoch()||own==null||!own.compatible(auth.body())
                ||own.revision()!=request.actorRevision()||view==null||!view.target().equals(request.target())
                ||view.epoch()!=request.epoch()||!view.dimension().equals(auth.body().level().dimension().location())||request.sequence()<=view.sequence()
                ||view.targetRevision()!=request.targetRevision()||!view.available()) return;
        LivingEntity target=resolve(auth.body(),request.target());
        StripCapability.Resolved capability=target==null?null:StripCapability.resolve(auth.body(),target);
        if(target==null||!CharacterControlSystem.canAct(auth.body())||!hasHand(auth.body())||capability==null
                ||!capability.actorCanOpen()||!capability.targetCanBeStripped()||!BodyInteractionAccess.inRange(auth.body(),target,StripInteractionRange.BLOCKS)) return;
        LiveHands targetHands=target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(targetHands==null||!targetHands.compatible(target)||targetHands.revision()!=request.targetRevision())return;
        startOwned(auth.body(),actor,target,request,()->HandActorAuthority.revalidate(actor,auth));
    }

    /** Shared production/native fixture path; admission proof is supplied by the caller. */
    static boolean startOwned(LivingEntity actorBody,ServerPlayer owner,LivingEntity target,StripSlotAction request,
                              BooleanSupplier authorityValid) {
        if(actorBody==null||target==null||request==null||authorityValid==null||actorBody==target)return false;
        LiveHands own=actorBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(own==null||!own.compatible(actorBody)||own.revision()!=request.actorRevision()
                ||!authorityValid.getAsBoolean()||!CharacterControlSystem.canAct(actorBody)
                ||!StripCapability.resolve(actorBody,target).actorCanOpen()
                ||!StripCapability.resolve(actorBody,target).targetCanBeStripped()
                ||!BodyInteractionAccess.inRange(actorBody,target,StripInteractionRange.BLOCKS))return false;
        LiveHands targetHands=target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(targetHands==null||!targetHands.compatible(target)||targetHands.revision()!=request.targetRevision())return false;
        StripCapability.Resolved capability=StripCapability.resolve(actorBody,target);
        String sourceToken; boolean insertion;
        ItemStack source=ItemStack.EMPTY; String sourceHand=null;
        if(request.kind()==StripSlotRef.Kind.HAND) {
            if(!capability.targetHandsCanBeStripped()||!targetHands.handIds().contains(request.slot()))return false;
            sourceToken=targetHands.token(request.slot()).orElse(null); insertion=sourceToken==null;
            if(!insertion) source=targetHands.stackCopy(request.slot()).orElse(ItemStack.EMPTY);
        } else {
            if(!capability.targetInventoryCanBeStripped()||targetHands.equipmentSnapshot(target).isEmpty())return false;
            var slot=targetHands.equipmentSnapshot(target).orElseThrow().slots().stream().filter(s->s.id().equals(request.slot())).findFirst().orElse(null);
            if(slot==null)return false;
            sourceToken=slot.token().orElse(null); insertion=sourceToken==null;
            if(!insertion) source=slot.stack().orElse(ItemStack.EMPTY);
        }
        if(insertion) {
            sourceHand=own.activeHand(); sourceToken=own.token(sourceHand).orElse(null);
            source=own.stackCopy(sourceHand).orElse(ItemStack.EMPTY);
            if(sourceToken==null||source.isEmpty())return false;
        }
        String stableToken=sourceToken, active=sourceHand; ItemStack captured=source.copy();
        boolean insert=insertion; double initialDistance=actorBody.distanceTo(target);
        double base=baseDuration(target,request.kind(),request.slot());
        double delay=clothingDelay(actorBody.level(),captured);
        int duration=duration(base,delay,target);
        HandActorAuthority.Snapshot auth=owner==null?null:HandActorAuthority.resolve(owner).orElse(null);
        if(owner!=null&&(auth==null||auth.body()!=actorBody||auth.epoch()!=request.epoch()))return false;
        BooleanSupplier environment=()->validEnvironment(actorBody,target,request,initialDistance,authorityValid);
        BooleanSupplier valid=()->validAction(actorBody,target,request,insert,stableToken,active,initialDistance,authorityValid);
        if(!environment.getAsBoolean()||!BodyStripTransfer.canTransferOwned(actorBody,target,new StripSlotRef(request.kind(),request.slot()),insert,stableToken,authorityValid))return false;
        TimedInteractionEngine.StartResult result=TimedInteractionSystem.start(actorBody,owner,request.sequence(),KIND,stableToken,
                duration,true,true,1.0,valid,()->complete(actorBody,target,request,insert,stableToken,initialDistance,authorityValid,valid));
        if(result==TimedInteractionEngine.StartResult.STARTED||result==TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED) {
            if(owner!=null) {
                View current=VIEWS.get(owner.getUUID());
                if(current!=null&&current.target().equals(target.getUUID()))VIEWS.put(owner.getUUID(),new View(current.target(),current.dimension(),current.epoch(),request.sequence(),current.targetRevision(),current.available()));
                if(auth!=null)PacketDistributor.sendToPlayer(owner,view(owner,auth,target,request.sequence(),StripViewSnapshot.Status.AVAILABLE,true));
            }
            return true;
        }
        return false;
    }

    private static TimedInteractionSystem.Completion complete(LivingEntity actorBody,LivingEntity target,
            StripSlotAction request,boolean insert,String token,double initialDistance,BooleanSupplier authority,BooleanSupplier valid) {
        if(!valid.getAsBoolean())return TimedInteractionSystem.Completion.STOP;
        BooleanSupplier guard=()->validEnvironment(actorBody,target,request,initialDistance,authority);
        // The environment guard survives legitimate source removal by insertion; the transfer
        // itself separately rechecks exact live candidates immediately before attachment writes.
        var result=BodyStripTransfer.transferOwned(actorBody,target,new StripSlotRef(request.kind(),request.slot()),insert,token,guard);
        return switch(result) { case SUCCESS->TimedInteractionSystem.Completion.STOP; case DENIED->TimedInteractionSystem.Completion.STOP; case RECOVERY_REQUIRED->TimedInteractionSystem.Completion.RECOVERY_REQUIRED; };
    }

    private static boolean validEnvironment(LivingEntity actorBody,LivingEntity target,StripSlotAction req,
            double initialDistance,BooleanSupplier authority) {
        if(!authority.getAsBoolean()||target.isRemoved()||target.level()!=actorBody.level()
                ||resolve(actorBody,target.getUUID())!=target||!actorBody.isAddedToLevel()
                ||!CharacterControlSystem.canAct(actorBody)||!hasHand(actorBody)
                ||!StripCapability.resolve(actorBody,target).actorCanOpen()
                ||Math.abs(actorBody.distanceTo(target)-initialDistance)>TARGET_DRIFT
                ||!BodyInteractionAccess.inRange(actorBody,target,StripInteractionRange.BLOCKS))return false;
        StripCapability.Resolved c=StripCapability.resolve(actorBody,target);
        return req.kind()==StripSlotRef.Kind.HAND?c.targetHandsCanBeStripped():c.targetInventoryCanBeStripped();
    }

    private static boolean validAction(LivingEntity actorBody,LivingEntity target,StripSlotAction req,
            boolean insertion,String token,String active,double initialDistance,BooleanSupplier authority) {
        if(!validEnvironment(actorBody,target,req,initialDistance,authority))return false;
        LiveHands own=actorBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands theirs=target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(own==null||theirs==null||!own.compatible(actorBody)||!theirs.compatible(target))return false;
        if(insertion) {
            if(!active.equals(own.activeHand())||own.token(active).filter(token::equals).isEmpty()
                    ||own.stackCopy(active).orElse(ItemStack.EMPTY).isEmpty())return false;
        } else if(tokenAt(theirs,target,req.kind(),req.slot()).filter(token::equals).isEmpty())return false;
        return BodyStripTransfer.canTransferOwned(actorBody,target,new StripSlotRef(req.kind(),req.slot()),insertion,token,authority);
    }

    private static Optional<String> tokenAt(LiveHands state,LivingEntity body,StripSlotRef.Kind kind,String slot) {
        if(kind==StripSlotRef.Kind.HAND)return state.handIds().contains(slot)?state.token(slot):Optional.empty();
        return state.equipmentSnapshot(body).stream().flatMap(s->s.slots().stream()).filter(s->s.id().equals(slot)).findFirst().flatMap(LiveHands.EquipmentSlot::token);
    }
    private static double baseDuration(LivingEntity target,StripSlotRef.Kind kind,String slot) {
        if(kind==StripSlotRef.Kind.HAND)return StripCapability.resolve(target,target).targetPolicy().map(p->p.handStripDelay()).orElse(4d);
        var component=StripCapability.resolve(target,target).targetEquipment().orElse(null); if(component==null)return 4;
        var template=component.template().map(PrototypeRuntime.serverInventoryTemplates()::get).orElse(null);
        return template==null?4:template.slot(slot).map(InventoryTemplateData.Slot::stripTime).orElse(4d);
    }
    private static int duration(double base,double delay,LivingEntity target) {
        MobStateAttachment state=target.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        MobStateComponent.State s=state==null?MobStateData.DEFAULT.state():state.data().state();
        return StripTimingPolicy.durationTicks(base,delay,s);
    }
    private static double clothingDelay(net.minecraft.world.level.Level level,ItemStack item) {
        if(item==null||item.isEmpty())return 0;
        var id=BuiltInRegistries.ITEM.getKey(item.getItem()); var data=ModEquipmentItems.catalog(level).get(id);
        return data==null?0:data.clothing().map(EquipmentItemData.Clothing::stripDelay).orElse(0d);
    }
    private static boolean hasHand(LivingEntity body) { LiveHands h=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());return h!=null&&h.compatible(body)&&!h.handIds().isEmpty(); }
    private static LivingEntity resolve(LivingEntity actor,UUID id) { if(id==null||!(actor.level() instanceof ServerLevel level))return null;var e=level.getEntity(id);return e instanceof LivingEntity l?l:null; }

    private static StripViewSnapshot view(ServerPlayer player,HandActorAuthority.Snapshot auth,LivingEntity target,long seq,StripViewSnapshot.Status status,boolean actionable) {
        LiveHands state=target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(state==null||!state.compatible(target))return new StripViewSnapshot(auth.epoch(),seq,target.getUUID(),target.level().dimension().location(),target.getId(),0,null,List.of(),StripViewSnapshot.Status.UNAVAILABLE,false);
        StripCapability.Resolved cap=StripCapability.resolve(auth.body(),target); List<StripViewSnapshot.Slot> slots=new ArrayList<>();
        if(cap.targetHandsCanBeStripped()) for(String id:state.handIds()) add(slots,StripSlotRef.Kind.HAND,id,state.token(id).isPresent(),false,state.stackCopy(id).orElse(ItemStack.EMPTY));
        String templateId=null;
        if(cap.targetInventoryCanBeStripped()) {
            var ec=cap.targetEquipment().orElseThrow(); templateId=ec.template().map(Object::toString).orElse(null);
            var template=ec.template().map(PrototypeRuntime.serverInventoryTemplates()::get).orElse(null);
            for(String id:state.equipmentSnapshot(target).orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList()) {
                var meta=template==null?null:template.slot(id).orElse(null); boolean hidden=meta!=null&&meta.stripHidden();
                ItemStack stack=state.equipmentStack(id).orElse(ItemStack.EMPTY); add(slots,StripSlotRef.Kind.EQUIPMENT,id,!stack.isEmpty(),hidden,stack);
            }
        }
        boolean canAct=actionable&&status==StripViewSnapshot.Status.AVAILABLE&&cap.actorCanOpen()&&CharacterControlSystem.canAct(auth.body())&&hasHand(auth.body());
        return new StripViewSnapshot(auth.epoch(),seq,target.getUUID(),target.level().dimension().location(),target.getId(),state.revision(),templateId,slots,status,canAct);
    }
    private static void add(List<StripViewSnapshot.Slot> out,StripSlotRef.Kind kind,String id,boolean occupied,boolean hidden,ItemStack stack) {
        ItemPresentation item=occupied&&!hidden&&!stack.isEmpty()?ItemPresentation.fromStack(stack):null;out.add(new StripViewSnapshot.Slot(kind,id,occupied,hidden,item));
    }
    private static void sendUnavailable(ServerPlayer p,StripViewRequest r,LivingEntity t) {
        LiveHands state=t==null?null:t.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        sendUnavailable(p,r,t,state==null?0:state.revision());
    }
    private static void sendUnavailable(ServerPlayer p,StripViewRequest r,LivingEntity t,long revision) {
        HandActorAuthority.Snapshot a=HandActorAuthority.resolve(p).orElse(null);if(a==null)return;
        PacketDistributor.sendToPlayer(p,new StripViewSnapshot(r.epoch(),r.sequence(),r.target(),p.level().dimension().location(),t==null?0:t.getId(),Math.max(0,revision),null,List.of(),StripViewSnapshot.Status.UNAVAILABLE,false));
    }
    private static void sendClosed(ServerPlayer p,StripViewRequest r,HandActorAuthority.Snapshot a) { PacketDistributor.sendToPlayer(p,new StripViewSnapshot(a.epoch(),r.sequence(),r.target(),p.level().dimension().location(),0,0,null,List.of(),StripViewSnapshot.Status.CLOSED,false)); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer player))return;
        var server=player.getServer();
        if(server==null||!server.isSameThread())return;
        VIEWS.remove(player.getUUID());
    }
    public static void entityLeave(EntityLeaveLevelEvent e) {
        if(!(e.getLevel() instanceof ServerLevel level))return;
        var server=level.getServer();
        if(server==null||!server.isSameThread())return;
        UUID target=e.getEntity().getUUID();
        for(var entry:List.copyOf(VIEWS.entrySet())) if(entry.getValue().target().equals(target)) {
            VIEWS.remove(entry.getKey());
            ServerPlayer viewer=server.getPlayerList().getPlayer(entry.getKey());
            if(viewer!=null)PacketDistributor.sendToPlayer(viewer,new StripViewSnapshot(entry.getValue().epoch(),entry.getValue().sequence(),target,
                    viewer.level().dimension().location(),0,entry.getValue().targetRevision(),null,List.of(),StripViewSnapshot.Status.UNAVAILABLE,false));
        }
    }
    public static void stopped(ServerStoppedEvent e) { if(e.getServer().isSameThread())VIEWS.clear(); }
    public static void tick(ServerTickEvent.Post event) {
        if(!event.getServer().isSameThread())return;
        for(var entry:List.copyOf(VIEWS.entrySet())) {
            ServerPlayer player=event.getServer().getPlayerList().getPlayer(entry.getKey()); View v=entry.getValue();
            if(player==null) { VIEWS.remove(entry.getKey()); continue; }
            var auth=HandActorAuthority.resolve(player).orElse(null);
            if(auth==null||auth.epoch()!=v.epoch()||!auth.body().level().dimension().location().equals(v.dimension())) { VIEWS.remove(entry.getKey()); continue; }
            LivingEntity target=resolve(auth.body(),v.target());
            if(target==null||!StripInteractionRange.contains(auth.body(),target)) {
                VIEWS.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player,new StripViewSnapshot(v.epoch(),v.sequence(),v.target(),player.level().dimension().location(),
                        target==null?0:target.getId(),v.targetRevision(),null,List.of(),StripViewSnapshot.Status.CLOSED,false));
                continue;
            }
            LiveHands targetHands=target==null?null:target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            boolean available=eligibleView(auth.body(),target,targetHands);
            long rev=targetHands!=null&&targetHands.compatible(target)?targetHands.revision():v.targetRevision();
            if(available!=v.available()||rev!=v.targetRevision()) {
                View next=new View(v.target(),v.dimension(),v.epoch(),v.sequence(),rev,available);VIEWS.put(entry.getKey(),next);
                if(available) PacketDistributor.sendToPlayer(player,view(player,auth,target,v.sequence(),StripViewSnapshot.Status.AVAILABLE,true));
                else PacketDistributor.sendToPlayer(player,new StripViewSnapshot(v.epoch(),v.sequence(),v.target(),player.level().dimension().location(),target==null?0:target.getId(),rev,null,List.of(),StripViewSnapshot.Status.UNAVAILABLE,false));
            }
        }
    }
}

