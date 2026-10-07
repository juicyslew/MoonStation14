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
        if(!(level.getBlockEntity(target) instanceof AirlockBlockEntity airlock))return true;
        if(!ToolQualities.has(held,ToolProfile.Quality.PRYING))return true;
        var initialSupply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!initialSupply.known())return true;
        if(!airlock.state().receiverPowerDisabled())airlock.setPowered(initialSupply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts());
        AirlockState snapshot=airlock.state(); double speed=ToolQualities.prySpeed(held);
        if(!Double.isFinite(speed)||speed<=0||!snapshot.allowsPry(false))return true;
        HandActorAuthority.Snapshot authority=admission.authority(); LiveHands hands=admission.hands();
        UUID identity=airlock.identity(); long operationRevision=snapshot.revision(); BlockPos exact=target.immutable();
        String hand=request.handId(), token=request.expectedToken(); long handsRevision=request.expectedRevision();
        double prySeconds=5.0/speed; int duration=(int)Math.max(1,Math.ceil(prySeconds*20));
        BooleanSupplierWithLive check=()->valid(actor,authority,hands,hand,token,handsRevision,held,level,exact,identity,operationRevision);
        TimedInteractionSystem.start(authority.body(),actor,request.sequence(),KIND,identity.toString(),duration,true,true,
                check::get,()->{
                    if(!check.get()||!(level.getBlockEntity(exact) instanceof AirlockBlockEntity current))return TimedInteractionSystem.Completion.STOP;
                    return current.pry()?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
                });
        return true;
    }
    private static boolean valid(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handRevision,ItemStack held,ServerLevel level,BlockPos pos,UUID identity,long revision) {
        if(!HandActorAuthority.revalidate(actor,authority)||authority.body().level()!=level
                ||authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())!=hands
                ||!hands.compatible(authority.body())||hands.revision()!=handRevision
                ||!hands.token(hand).filter(token::equals).isPresent()
                ||!hands.stackCopy(hand).filter(stack->ItemStack.isSameItemSameComponents(stack,held)).isPresent()
                ||!ToolQualities.has(held,ToolProfile.Quality.PRYING)||!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof AirlockBlockEntity airlock)||!airlock.identity().equals(identity)
                ||airlock.state().revision()!=revision||!airlock.state().allowsPry(false))return false;
        var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,airlock);
        if(!supply.known()||supply.deliveredWatts()==airlock.prototype().apcPowerReceiver().requestedWatts())return false;
        return AirlockToolLineOfSight.canReachRoot(authority.body(),level,pos,airlock,identity);
    }
    @FunctionalInterface private interface BooleanSupplierWithLive { boolean get(); }
}
