package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.component.ModDataAttachments;
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

/** Airlock-only tool routing after the caller has admitted the exact active hand. */
public final class AirlockToolInteraction {
    private static final String KIND="airlock-panel";
    private AirlockToolInteraction() { }

    /** Returns true for every airlock target, including unsupported/denied tools. */
    public static boolean use(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission,ItemStack held) {
        if(request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        BlockPos pos=admission.hit().pos();if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var block=level.getBlockState(pos);if(!(block.getBlock() instanceof AirlockBlock))return false;
        if(block.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER)pos=pos.below();
        if(!(level.getBlockEntity(pos) instanceof AirlockBlockEntity door))return true;
        if(admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body)
            door.electrifiedContact(body,AirlockElectrificationSystem.Contact.USING);
        var data=door.prototype();if(data==null)return true;
        ToolProfile.Quality openingQuality=data.wiresPanel().openingToolQuality();
        if(ToolQualities.has(held,openingQuality))return startPanel(actor,request,admission,held,level,pos,door,data.wiresPanel().toggleDelaySeconds(),openingQuality);
        // Cutting and pulsing are recognized only at a visible panel; menu/wire actions belong to UI transport.
        return door.panelVisible()&&data.wiresPanelSecurity().wiresAccessible()
                &&(ToolQualities.has(held,ToolProfile.Quality.CUTTING)||ToolQualities.has(held,ToolProfile.Quality.PULSING));
    }

    private static boolean startPanel(ServerPlayer actor,BodyBlockUseRequest req,BodyHandRequestService.BlockUseAdmission admission,ItemStack held,ServerLevel level,BlockPos pos,AirlockBlockEntity door,double seconds,ToolProfile.Quality openingQuality) {
        var authority=admission.authority();LiveHands hands=admission.hands();UUID identity=door.identity();long revision=door.state().revision();boolean panel=door.panelOpen();
        String hand=req.handId(),token=req.expectedToken();long handRevision=req.expectedRevision();BlockPos exact=pos.immutable();
        int ticks=panelDurationTicks(seconds);
        java.util.function.BooleanSupplier valid=()->valid(actor,authority,hands,hand,token,handRevision,held,level,exact,identity,revision,panel,openingQuality);
        TimedInteractionSystem.start(authority.body(),actor,req.sequence(),KIND,identity.toString(),ticks,true,true,valid::getAsBoolean,()->{
            if(!valid.getAsBoolean()||!(level.getBlockEntity(exact) instanceof AirlockBlockEntity current))return TimedInteractionSystem.Completion.STOP;
            return current.togglePanel(identity,revision,panel)?TimedInteractionSystem.Completion.STOP:TimedInteractionSystem.Completion.DENIED;
        });
        return true;
    }
    public static int panelDurationTicks(double resolvedSeconds){if(!Double.isFinite(resolvedSeconds)||resolvedSeconds<=0)throw new IllegalArgumentException("invalid resolved panel delay");return (int)Math.max(1,Math.ceil(resolvedSeconds*20));}

    private static boolean valid(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands hands,String hand,String token,long handRevision,ItemStack held,ServerLevel level,BlockPos pos,UUID identity,long revision,boolean panel,ToolProfile.Quality openingQuality) {
        if(!HandActorAuthority.revalidate(actor,authority)||authority.body().level()!=level
                ||authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=hands||!hands.compatible(authority.body())
                ||hands.revision()!=handRevision||!hands.token(hand).filter(token::equals).isPresent()
                ||!hands.stackCopy(hand).filter(s->ItemStack.isSameItemSameComponents(s,held)).isPresent()
                ||!ToolQualities.has(held,openingQuality)||!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof AirlockBlockEntity door)||!door.identity().equals(identity)
                ||door.state().revision()!=revision||door.panelOpen()!=panel||door.prototype()==null)return false;
        return AirlockToolLineOfSight.canReachRoot(authority.body(),level,pos,door,identity);
    }
}

