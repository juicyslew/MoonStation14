package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionSequenceGate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;

/** Authenticated server-thread protocol adapter into the shared melee attack owner. */
public final class MeleeAttackRequestService {
    private static final Map<ServerPlayer,BodyActionSequenceGate> GATES=new WeakHashMap<>();
    private MeleeAttackRequestService(){}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)GATES.remove(p);}
    public static MeleeAttackReply handle(ServerPlayer player,MeleeAttackRequest request){
        if(request==null)return reply(null,0,0,MeleeAttackReply.Status.NO_AUTHORITY,false,null,0,null,0);
        var authority=BodyActionAuthority.resolve(player).orElse(null);
        if(authority==null)return reply(null,request.epoch(),request.sequence(),MeleeAttackReply.Status.NO_AUTHORITY,false,null,0,null,0);
        long epoch=authority.identity().epoch();var body=authority.body();
        var gate=GATES.computeIfAbsent(player,p->new BodyActionSequenceGate());var admitted=gate.admit(epoch,request.epoch(),request.sequence(),player.level().getGameTime());
        if(admitted!=BodyActionSequenceGate.Result.ADMITTED){var status=switch(admitted){case WRONG_EPOCH->MeleeAttackReply.Status.WRONG_EPOCH;case REPLAY->MeleeAttackReply.Status.REPLAY;case RATE_LIMITED->MeleeAttackReply.Status.RATE_LIMITED;default->throw new IllegalStateException();};return reply(body.getUUID(),epoch,request.sequence(),status,false,null,0,null,0);}
        var current=MeleeWeaponResolver.resolve(body).orElse(null);
        if(!BodyActionAuthority.revalidate(player,authority))return reply(body.getUUID(),epoch,request.sequence(),MeleeAttackReply.Status.NO_AUTHORITY,false,null,0,null,0);
        if(current==null||current.kind()!=request.expectedKind()||current.handRevision()!=request.expectedHandRevision()||!current.activeHand().equals(request.expectedActiveHand())||!current.itemToken().orElse("").equals(request.expectedItemToken()))return reply(body.getUUID(),epoch,request.sequence(),MeleeAttackReply.Status.STALE_WEAPON,false,current==null?null:current.kind(),current==null?0:current.handRevision(),current==null?null:current.itemToken().orElse(null),0);
          boolean wide=request.attackMode()==MeleeAttackRequest.Mode.WIDE;
          boolean disarm=request.attackMode()==MeleeAttackRequest.Mode.DISARM;
          var arcTarget=wide?request.wideTargets().stream().findFirst().orElse(null):request.targetUUID();
          var frozenArc=MeleeArcObserverPublisher.capture(body,current,arcTarget,wide);
          var result=com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.batch(() -> wide ? LiveMeleeAttackService.attackWide(player,authority,current,request.wideTargets()) : disarm ? LiveMeleeAttackService.disarm(player,authority,current,request.targetUUID()) : LiveMeleeAttackService.attack(player,authority,current,request.targetUUID()));
         if(result.status()==LiveMeleeAttackService.Status.HIT || result.status()==LiveMeleeAttackService.Status.MISS || result.status()==LiveMeleeAttackService.Status.HANDLED || result.status()==LiveMeleeAttackService.Status.DISARM_HANDLED)
               MeleeArcObserverPublisher.publish(player,body,request.targetUUID(),frozenArc,result.swingLeft());
         var status=switch(result.status()){case REJECTED->MeleeAttackReply.Status.REJECTED;case PRE_ATTEMPT_CANCELLED->MeleeAttackReply.Status.PRE_ATTEMPT_CANCELLED;case CONSUMED_CANCELLED->MeleeAttackReply.Status.CONSUMED_CANCELLED;case MISS->MeleeAttackReply.Status.MISS;case HIT->MeleeAttackReply.Status.HIT;case HANDLED->MeleeAttackReply.Status.HANDLED;case DISARM_FAILED->MeleeAttackReply.Status.DISARM_FAILED;case DISARM_HANDLED->MeleeAttackReply.Status.DISARM_HANDLED;};
         if (!BodyActionAuthority.revalidate(player, authority))
             return reply(body.getUUID(),epoch,request.sequence(),MeleeAttackReply.Status.NO_AUTHORITY,result.cooldownConsumed(),null,0,null,0);
         var selected=MeleeWeaponResolver.resolve(body).orElse(null);
         long remaining=selected==null?0:remainingTicks(MeleeCooldownSystem.nextAttackTick(body,selected).orElse(0),player.level().getGameTime());
         return reply(body.getUUID(),epoch,request.sequence(),status,result.cooldownConsumed(),selected==null?null:selected.kind(),selected==null?0:selected.handRevision(),selected==null?null:selected.itemToken().orElse(null),remaining);
    }
    private static MeleeAttackReply reply(java.util.UUID id,long epoch,long seq,MeleeAttackReply.Status status,boolean consumed,MeleeWeaponResolver.Kind kind,long rev,String token,long next){return new MeleeAttackReply(id,epoch,seq,status,consumed,kind,rev,token,next);}
    static long remainingTicks(long deadline,long now) { return deadline<=now?0:deadline-now<0?Long.MAX_VALUE:deadline-now; }
}
