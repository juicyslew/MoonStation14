package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Admitted firelock pry shared by standalone and attached firelock controllers. */
public final class FirelockPryService {
    private static final String KIND="firelock-pry";
    private FirelockPryService() { }
    public static int durationTicks(double basePryTimeSeconds,double targetModifier,double speedModifier){return DoorPrySupport.durationTicks(basePryTimeSeconds,targetModifier,speedModifier);}
    public static int durationTicks(com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData data,boolean locked,double speedModifier){return data==null?0:durationTicks(data.door().pryTimeSeconds(),locked?data.pryModifier():1.0,speedModifier);}
    static boolean strongPry(com.juicyslew.moonstation14.ms14.tools.ToolProfile profile){return profile!=null&&profile.prying()!=null;}

    public static boolean start(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission){
        if(actor==null||request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        FirelockTargets.Target target=FirelockTargets.atHit(level,admission.hit().pos());if(target==null)return false;
        LiveHands hands=admission.hands();ItemStack held=hands.stackCopy(request.handId()).orElse(null);
        if(held!=null&&!held.isEmpty())return startTool(actor,request,admission,held);
        var meter=PowerRuntime.firelockPowerForHost(level,target.host());if(!meter.known())return true;
        var data=target.host().prototype();if(data==null)return true;
        target.controller().setPaidPower(target.host(),meter.deliveredWatts()==data.apcPowerReceiver().requestedWatts());
        FirelockState snapshot=target.controller().state();if(snapshot==null||snapshot.pryVeto(false,false)==FirelockState.Pry.VETOED||(snapshot.phase()!=AirlockState.DoorPhase.CLOSED&&snapshot.phase()!=AirlockState.DoorPhase.OPEN))return true;
        HandActorAuthority.Snapshot authority=admission.authority();UUID id=target.identity();long revision=snapshot.revision();BlockPos root=target.root();String hand=request.handId(),token=request.expectedToken();long handsRevision=request.expectedRevision();
        int duration=durationTicks(data,snapshot.locked(),data.handPryTimeModifier());
        java.util.function.BooleanSupplier valid=()->validHand(actor,authority,hands,hand,token,handsRevision,level,root,id,revision);
        TimedInteractionSystem.start(authority.body(),actor,request.sequence(),KIND,id.toString(),duration,true,true,valid,()->{
            if(!valid.getAsBoolean())return TimedInteractionSystem.Completion.STOP;
            FirelockTargets.Target current=FirelockTargets.atRoot(level,root);return current!=null&&current.controller().completePry(current.host(),false,false)?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
        },java.util.Optional.of(root),()->{});
        return true;
    }

    public static boolean startTool(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission,ItemStack held){
        if(actor==null||request==null||admission==null||held==null||held.isEmpty()||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        FirelockTargets.Target target=FirelockTargets.atHit(level,admission.hit().pos());if(target==null)return false;
        var profile=com.juicyslew.moonstation14.ms14.tools.ToolQualities.profile(held);
        if(profile==null||!com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(held,com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.PRYING))return false;
        // SS14's PryingComponent path is a strong pry; derive this from the resolved
        // component composition rather than an item-specific capability flag.
        boolean strongPry=strongPry(profile);
        double speed=profile.prying()==null?0:profile.prying().speedModifier();if(!Double.isFinite(speed)||speed<=0)return true;
        var data=target.host().prototype();var meter=PowerRuntime.firelockPowerForHost(level,target.host());if(data==null||!meter.known())return true;
        target.controller().setPaidPower(target.host(),meter.deliveredWatts()==data.apcPowerReceiver().requestedWatts());
        FirelockState snapshot=target.controller().state();if(snapshot==null||snapshot.pryVeto(profile.poweredPry(),strongPry)==FirelockState.Pry.VETOED||(snapshot.phase()!=AirlockState.DoorPhase.CLOSED&&snapshot.phase()!=AirlockState.DoorPhase.OPEN))return true;
        var authority=admission.authority();LiveHands hands=admission.hands();String hand=request.handId(),token=request.expectedToken();long handRevision=request.expectedRevision();UUID identity=target.identity();long revision=snapshot.revision();BlockPos root=target.root();ItemStack captured=held.copy();
        int duration=durationTicks(data,snapshot.locked(),speed);
        java.util.function.BooleanSupplier valid=()->validTool(actor,authority,hands,hand,token,handRevision,captured,level,root,identity,revision,strongPry,profile.poweredPry());
        TimedInteractionSystem.start(authority.body(),actor,request.sequence(),KIND,identity.toString(),duration,true,true,valid,()->{
            if(!valid.getAsBoolean())return TimedInteractionSystem.Completion.STOP;FirelockTargets.Target current=FirelockTargets.atRoot(level,root);
            return current!=null&&current.controller().completePry(current.host(),strongPry,profile.poweredPry())?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
        },java.util.Optional.of(root),()->{});
        return true;
    }

    private static boolean validHand(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handsRevision,ServerLevel level,BlockPos root,UUID identity,long revision){
        if(!HandActorAuthority.revalidate(actor,authority)||authority.body().level()!=level||authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=hands||!hands.compatible(authority.body())||!DoorPrySupport.matchesEmptyHandSnapshot(hands,hand,token,handsRevision))return false;
        FirelockTargets.Target target=FirelockTargets.atRoot(level,root);if(target==null||!target.identity().equals(identity)||target.controller().state()==null||target.controller().state().revision()!=revision)return false;
        var data=target.host().prototype();var power=PowerRuntime.firelockPowerForHost(level,target.host());
        return data!=null&&power.known()&&power.deliveredWatts()!=data.apcPowerReceiver().requestedWatts()&&AirlockToolLineOfSight.firelockReach(authority.body(),level,root,target,identity);
    }
    private static boolean validTool(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handRevision,ItemStack captured,ServerLevel level,BlockPos root,UUID id,long revision,boolean strong,boolean poweredPry){
        if(!HandActorAuthority.revalidate(actor,authority)||authority.body().level()!=level||authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=hands||!hands.compatible(authority.body())||hands.revision()!=handRevision||!hand.equals(hands.activeHand())||!DoorPrySupport.matchesHeldHandSnapshot(hands,hand,token,captured,handRevision))return false;
        FirelockTargets.Target target=FirelockTargets.atRoot(level,root);if(target==null||!target.identity().equals(id)||target.controller().state()==null||target.controller().state().revision()!=revision||target.controller().state().pryVeto(poweredPry,strong)==FirelockState.Pry.VETOED)return false;
        var profile=com.juicyslew.moonstation14.ms14.tools.ToolQualities.profile(captured);if(profile==null||!com.juicyslew.moonstation14.ms14.tools.ToolQualities.has(captured,com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.PRYING))return false;
        var data=target.host().prototype();var meter=PowerRuntime.firelockPowerForHost(level,target.host());boolean actualPower=data!=null&&meter.known()&&meter.deliveredWatts()==data.apcPowerReceiver().requestedWatts();
        return data!=null&&meter.known()&&(!actualPower||poweredPry||strong)&&target.controller().state().powered()==actualPower&&AirlockToolLineOfSight.firelockReach(authority.body(),level,root,target,id);
    }
}
