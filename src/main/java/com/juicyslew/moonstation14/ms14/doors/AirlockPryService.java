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

/** Authenticated hand/tool pry admission and completion reuse the shared timed-interaction runtime. */
public final class AirlockPryService {
    private static final String KIND="airlock-pry";
    private AirlockPryService() { }

    public static int durationTicks(com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData data,boolean emptyHand,double speed){
        if(data==null||!Double.isFinite(speed)||speed<=0)return 0;
        return DoorPrySupport.durationTicks(data.door().pryTimeSeconds(),1.0,emptyHand?data.pryUnpowered().pryModifier():speed);
    }

    /** Returns true when this is an airlock target, including a denied attempt. */
    public static boolean start(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission,ItemStack held) {
        if(actor==null||request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        BlockPos target=admission.hit().pos(); if(!level.hasChunkAt(target)||!level.getWorldBorder().isWithinBounds(target))return false;
        var blockState=level.getBlockState(target); if(!(blockState.getBlock() instanceof AirlockBlock))return false;
        if(blockState.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER)target=target.below();
        if(!(level.getBlockEntity(target) instanceof AirlockBlockEntity airlock))return true;
        LiveHands hands=admission.hands(); ItemStack admitted=hands.stackCopy(request.handId()).orElse(null);
        boolean empty=held==null||held.isEmpty();
        if(empty ? admitted!=null&&!admitted.isEmpty() : !ToolQualities.has(held,ToolProfile.Quality.PRYING))return true;
        if(admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body)
            airlock.electrifiedContact(body,AirlockElectrificationSystem.Contact.USING);
        var initialSupply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!initialSupply.known())return true;
        if(!airlock.state().receiverPowerDisabled())airlock.setPowered(initialSupply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts());
        AirlockState snapshot=airlock.state(); double speed=empty?1.0:ToolQualities.prySpeed(held);
        if(!Double.isFinite(speed)||speed<=0||!snapshot.allowsPry(false)||!DoorPrySupport.stablePhase(snapshot.phase()))return true;
        HandActorAuthority.Snapshot authority=admission.authority();
        UUID identity=airlock.identity(); long operationRevision=snapshot.revision(); BlockPos exact=target.immutable();
        UUID frameId=admission.hit().frameId();
        String hand=request.handId(), token=request.expectedToken(); long handsRevision=request.expectedRevision();
        int duration=durationTicks(airlock.prototype(),empty,speed);if(duration==0)return true;
        ItemStack captured=empty?null:held.copy();
        java.util.function.BooleanSupplier check=()->valid(actor,authority,hands,hand,token,handsRevision,captured,level,exact,identity,operationRevision,frameId);
        TimedInteractionSystem.start(authority.body(),actor,request.sequence(),KIND,identity.toString(),duration,true,true,
                check,()->{
                    if(!check.getAsBoolean()||!(level.getBlockEntity(exact) instanceof AirlockBlockEntity current))return TimedInteractionSystem.Completion.STOP;
                    return current.pry()?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
                });
        return true;
    }

    private static boolean valid(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handRevision,ItemStack held,ServerLevel level,BlockPos pos,UUID identity,long revision,UUID frameId) {
        LiveHands currentHands=authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if(!HandActorAuthority.revalidate(actor,authority)||authority.body().level()!=level||currentHands==null
                ||currentHands!=hands&&!currentHands.continuesAcrossPassiveTankDebit(authority.body(),hands)
                ||!hands.compatible(authority.body())||!level.hasChunkAt(pos))return false;
        boolean handValid=held==null?DoorPrySupport.matchesEmptyHandSnapshot(hands,hand,token,handRevision)
                :DoorPrySupport.matchesHeldHandSnapshot(hands,hand,token,held,handRevision)&&ToolQualities.has(held,ToolProfile.Quality.PRYING);
        if(!handValid)return false;
        if(!(level.getBlockEntity(pos) instanceof AirlockBlockEntity airlock)||!airlock.identity().equals(identity)
                ||airlock.state().revision()!=revision||!airlock.state().allowsPry(false)||!DoorPrySupport.stablePhase(airlock.state().phase()))return false;
        var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!supply.known()||supply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts())return false;
        var point=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentTargetPoint(level,pos);
        if(point==null||!java.util.Objects.equals(point.frameId(),frameId))return false;
        return AirlockToolLineOfSight.canReachRoot(authority.body(),level,pos,airlock,identity);
    }
}
