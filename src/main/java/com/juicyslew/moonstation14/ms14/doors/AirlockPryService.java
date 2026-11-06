package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.UUID;

/** Crowbar pry admission and completion reuse the shared timed-interaction runtime. */
public final class AirlockPryService {
    private static final String KIND="airlock-pry";
    private AirlockPryService() { }
    /** Returns true when this is an airlock target, including a denied attempt. */
    public static boolean start(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission,ItemStack held) {
        if(request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        BlockPos target=admission.hit().pos(); if(!level.hasChunkAt(target)||!level.getWorldBorder().isWithinBounds(target))return false;
        var blockState=level.getBlockState(target); if(!(blockState.getBlock() instanceof AirlockBlock))return false;
        if(blockState.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER)target=target.below();
        if(!(level.getBlockEntity(target) instanceof AirlockBlockEntity airlock))return false;
        if(!ToolQualities.has(held,ToolProfile.Quality.PRYING))return false;
        if(admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body)
            airlock.electrifiedContact(body,AirlockElectrificationSystem.Contact.USING);
        var initialSupply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!initialSupply.known())return true;
        if(!airlock.state().receiverPowerDisabled())airlock.setPowered(initialSupply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts());
        AirlockState snapshot=airlock.state(); double speed=ToolQualities.prySpeed(held);
        if(!Double.isFinite(speed)||speed<=0||!snapshot.allowsPry(false))return true;
        HandActorAuthority.Snapshot authority=admission.authority(); LiveHands hands=admission.hands();
        UUID identity=airlock.identity(); long operationRevision=snapshot.revision(); BlockPos exact=target.immutable();
        UUID frameId=admission.hit().frameId();
        String hand=request.handId(), token=request.expectedToken(); long handsRevision=request.expectedRevision();
        double prySeconds=5.0/speed; int duration=(int)Math.max(1,Math.ceil(prySeconds*20));
        BooleanSupplierWithLive check=()->valid(actor,authority,hands,hand,token,handsRevision,held,level,exact,identity,operationRevision,frameId);
        TimedInteractionSystem.start(authority.body(),actor,request.sequence(),KIND,identity.toString(),duration,true,true,
                check::get,()->{
                    if(!check.get()||!(level.getBlockEntity(exact) instanceof AirlockBlockEntity current))return TimedInteractionSystem.Completion.STOP;
                    return current.pry()?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
                });
        return true;
    }
    private static boolean valid(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handRevision,ItemStack held,ServerLevel level,BlockPos pos,UUID identity,long revision,UUID frameId) {
        String failure = null;
        if(!HandActorAuthority.revalidate(actor,authority))failure="authority-revalidation";
        else if(authority.body().level()!=level)failure="body-level";
        else if(authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())!=hands)failure="hands-identity";
        else if(!hands.compatible(authority.body()))failure="hands-incompatible";
        else if(hands.revision()!=handRevision)failure="hands-revision";
        else if(!hands.token(hand).filter(token::equals).isPresent())failure="hand-token";
        else if(!hands.stackCopy(hand).filter(stack->ItemStack.isSameItemSameComponents(stack,held)).isPresent())failure="held-stack";
        else if(!ToolQualities.has(held,ToolProfile.Quality.PRYING))failure="tool-quality";
        else if(!level.hasChunkAt(pos))failure="target-unloaded";
        if(failure!=null)return logInvalid(actor,level,pos,identity,failure,null);
        if(!(level.getBlockEntity(pos) instanceof AirlockBlockEntity airlock))return logInvalid(actor,level,pos,identity,"root-missing",null);
        if(!airlock.identity().equals(identity))return logInvalid(actor,level,pos,identity,"root-identity",null);
        if(airlock.state().revision()!=revision)return logInvalid(actor,level,pos,identity,"door-revision",null);
        if(!airlock.state().allowsPry(false))return logInvalid(actor,level,pos,identity,"pry-state",null);
        var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!supply.known()) {
            String diagnostic = null;
            if (com.juicyslew.moonstation14.MoonStation14.LOGGER.isDebugEnabled())
                diagnostic = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(level, airlock);
            return logInvalid(actor,level,pos,identity,"power-unknown",supply,diagnostic);
        }
        if(supply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts())return logInvalid(actor,level,pos,identity,"fully-powered",supply);
        var point=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentTargetPoint(level,pos);
        if(point==null)return logInvalid(actor,level,pos,identity,"target-projection",supply);
        if(!java.util.Objects.equals(point.frameId(),frameId))return logInvalid(actor,level,pos,identity,"target-frame",supply);
        if(!AirlockToolLineOfSight.canReachRoot(authority.body(),level,pos,airlock,identity))return logInvalid(actor,level,pos,identity,"line-of-sight",supply);
        return true;
    }
    private static boolean logInvalid(ServerPlayer actor,ServerLevel level,BlockPos pos,UUID identity,String reason,
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation sample) {
        return logInvalid(actor,level,pos,identity,reason,sample,null);
    }
    private static boolean logInvalid(ServerPlayer actor,ServerLevel level,BlockPos pos,UUID identity,String reason,
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation sample, String diagnostic) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                "[airlock-pry-validity] actor={} root={} pos={} reason={} now={} powerSample={} powerDiagnostic={}",
                actor==null?null:actor.getUUID(),identity,pos,reason,level.getServer().overworld().getGameTime(),sample,diagnostic);
        return false;
    }
    @FunctionalInterface private interface BooleanSupplierWithLive { boolean get(); }
}
