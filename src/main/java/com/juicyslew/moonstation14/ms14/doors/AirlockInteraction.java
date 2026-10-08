package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Authenticated world activation dispatch for exact airlock tiles. */
public final class AirlockInteraction {
    private AirlockInteraction() { }
    public static boolean activate(BodyBlockUseRequest request, BodyHandRequestService.BlockUseAdmission admission) {
        if(request==null||admission==null||!(admission.authority().body().level() instanceof ServerLevel level))return false;
        BlockPos pos=admission.hit().pos(); if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var state=level.getBlockState(pos); if(!(state.getBlock() instanceof AirlockBlock))return false;
        if(state.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER)pos=pos.below();
        if(level.getBlockEntity(pos) instanceof AirlockBlockEntity airlock
                && admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body) {
            var hands=admission.hands();
            var stack=hands.stackCopy(request.handId()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            airlock.electrifiedContact(body,stack.isEmpty()?AirlockElectrificationSystem.Contact.TOUCH:AirlockElectrificationSystem.Contact.USING);
            airlock.activate();
        }
        return true;
    }
}
