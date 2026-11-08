package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionSequenceGate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.Map;
import java.util.WeakHashMap;

/** Authenticated body-owned request adapter to the existing gun fire system. */
public final class GunShotRequestService {
    private static final Map<ServerPlayer,BodyActionSequenceGate> GATES=new WeakHashMap<>();
    private GunShotRequestService(){}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)GATES.remove(p);}
    public static GunShotReply handle(ServerPlayer player,GunShotRequest request){
        if(request==null)return reply(null,0,0,GunFireSystem.Status.REJECTED,false,null,0,0);
        var authority=BodyActionAuthority.resolve(player).orElse(null);
        if(authority==null)return reply(null,request.epoch(),request.sequence(),GunFireSystem.Status.REJECTED,false,null,0,0);
        var body=authority.body();long epoch=authority.identity().epoch();
        var gate=GATES.computeIfAbsent(player,p->new BodyActionSequenceGate());var admitted=gate.admit(epoch,request.epoch(),request.sequence(),player.level().getGameTime());
        if(admitted!=BodyActionSequenceGate.Result.ADMITTED)return reply(body.getUUID(),epoch,request.sequence(),GunFireSystem.Status.REJECTED,false,null,0,0);
        var selected=GunWeaponResolver.resolve(body).orElse(null);
        if(!BodyActionAuthority.revalidate(player,authority))return reply(body.getUUID(),epoch,request.sequence(),GunFireSystem.Status.REJECTED,false,null,0,0);
        if(selected==null||selected.handRevision()!=request.expectedHandRevision()||!selected.activeHand().equals(request.activeHand())||!selected.itemToken().equals(request.expectedItemToken()))
            return actualReply(player,body,epoch,request.sequence(),GunFireSystem.Status.REJECTED,false);
        var result=com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.batch(()->GunFireSystem.fire(player,authority,selected,request.nominatedAimPoint()));
        boolean consumed=consumedSource(result.status());
        if(!BodyActionAuthority.revalidate(player,authority))return reply(body.getUUID(),epoch,request.sequence(),GunFireSystem.Status.REJECTED,consumed,null,0,0);
        return actualReply(player,body,epoch,request.sequence(),result.status(),consumed);
    }
    private static GunShotReply actualReply(ServerPlayer player,net.minecraft.world.entity.LivingEntity body,long epoch,long seq,GunFireSystem.Status status,boolean consumed){
        long now=player.level().getGameTime();var fresh=GunWeaponResolver.resolve(body).orElse(null);
        long remaining=fresh==null?0:GunWeaponResolver.readOwned(body,fresh).map(owned->owned.stack().getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL).nextFireTick()).map(deadline->MeleeAttackRequestService.remainingTicks(deadline,now)).orElse(0L);
        return reply(body.getUUID(),epoch,seq,status,consumed,fresh,remaining,now);
    }
    private static boolean consumedSource(GunFireSystem.Status status){return status==GunFireSystem.Status.FIRED||status==GunFireSystem.Status.EMPTY||status==GunFireSystem.Status.CONSUMED_CANCELLED||status==GunFireSystem.Status.CONSUMED_PUBLICATION_FAILED;}
    private static GunShotReply reply(java.util.UUID id,long epoch,long seq,GunFireSystem.Status status,boolean consumed,GunWeaponResolver.Selection s,long remaining,long now){return new GunShotReply(id,epoch,seq,status,consumed,s==null?0:s.handRevision(),s==null?null:s.itemToken(),remaining);}
}
