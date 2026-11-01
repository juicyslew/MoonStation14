package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Authenticated world activation dispatch for exact airlock tiles. */
public final class AirlockInteraction {
    private AirlockInteraction() { }
    public static boolean activate(net.minecraft.server.level.ServerPlayer actor,BodyBlockUseRequest request, BodyHandRequestService.BlockUseAdmission admission) {
        if(request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        BlockPos pos=admission.hit().pos(); if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var state=level.getBlockState(pos); if(!(state.getBlock() instanceof AirlockBlock))return false;
        if(state.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER)pos=pos.below();
        if(level.getBlockEntity(pos) instanceof AirlockBlockEntity airlock
                && admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body) {
            var hands=admission.hands();
            var stack=hands.stackCopy(request.handId()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            airlock.electrifiedContact(body,stack.isEmpty()?AirlockElectrificationSystem.Contact.TOUCH:AirlockElectrificationSystem.Contact.USING);
            boolean activated=airlock.activate();
            if(!activated && stack.isEmpty())
                AirlockPryService.start(actor,request,admission,null);
        }
        return true;
    }

    /** Firelock E shares admitted world activation; only an authenticated empty hand gets hand pry fallback. */
    public static boolean activateFirelock(net.minecraft.server.level.ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission) {
        if(actor==null||request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        FirelockTargets.Target target=FirelockTargets.atHit(level,admission.hit().pos());if(target==null)return false;
        var held=admission.hands().stackCopy(request.handId()).orElse(null);
        if(held!=null&&!held.isEmpty())return true;
        if(target.controller().toggle(target.host(),false))return true;
        FirelockPryService.start(actor,request,admission);
        return true;
    }
}
