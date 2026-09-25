package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.movement.client.MovementClientController;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityWishFacingMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void moonstation14$renderWithWishFacing(LivingEntity entity, float entityYaw, float partialTick,
                                                     PoseStack poseStack, MultiBufferSource buffer,
                                                     int packedLight, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof Player player) || minecraft.player != player
                || minecraft.options.getCameraType().isFirstPerson()
                || player.isPassenger() || player.isCreative() || player.isSpectator()
                || player.getAbilities().flying || player.isFallFlying() || player.isSwimming()
                || player.isInWater() || player.onClimbable()) {
            original.call(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        var facing = MovementClientController.owns(minecraft.player)
                ? MovementClientController.visualFacing(minecraft.player) : java.util.Optional.<MovementClientController.VisualFacing>empty();
        if (facing.isEmpty()) {
            original.call(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        float bodyYaw = player.yBodyRot;
        float previousBodyYaw = player.yBodyRotO;
        MovementClientController.VisualFacing angles = facing.get();
        player.yBodyRotO = angles.previousYaw();
        player.yBodyRot = angles.currentYaw();
        try {
            original.call(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            player.yBodyRotO = previousBodyYaw;
            player.yBodyRot = bodyYaw;
        }
    }
}
