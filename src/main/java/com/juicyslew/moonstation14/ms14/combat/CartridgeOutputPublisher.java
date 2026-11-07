package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Single post-commit owner for live rounds and persistent spent cartridge outputs. */
public final class CartridgeOutputPublisher {
    private CartridgeOutputPublisher() { }
    public static BodyHandItemTransfer.Result publish(ServerLevel level, ServerPlayer actor, LivingEntity body,
            Vec3 position, Vec3 aim, boolean leftHand, List<ItemStack> outputs, BooleanSupplier sourceStillValid) {
        if(level==null||actor==null||body==null||position==null||aim==null||outputs==null||sourceStillValid==null)
            return BodyHandItemTransfer.Result.DENIED;
        var partition=CartridgeOutputPlan.partition(outputs,ModCartridgeProfiles.serverCatalog().asMap());
        var worldOutputs = new java.util.ArrayList<ItemStack>(partition.liveOutputs());
        worldOutputs.addAll(partition.casings());
        if(!worldOutputs.isEmpty()) {
            var result=FreshWorldOutputBatch.publishAfterSourceCommitAtPosition(level,position,worldOutputs,sourceStillValid);
            if(result!=BodyHandItemTransfer.Result.SUCCESS)return result;
        } else if(!sourceStillValid.getAsBoolean()) return BodyHandItemTransfer.Result.DENIED;
        return BodyHandItemTransfer.Result.SUCCESS;
    }
    public static CartridgeOutputPlan plan(List<ItemStack> outputs,Map<net.minecraft.resources.ResourceLocation,CartridgeProfile> profiles){
        return CartridgeOutputPlan.partition(outputs,profiles);
    }
}
