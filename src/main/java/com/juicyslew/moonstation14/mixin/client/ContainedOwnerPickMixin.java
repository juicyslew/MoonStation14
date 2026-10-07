package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vanilla entity/block picking on the authoritative owner's block while the body is contained. */
@Mixin(GameRenderer.class)
public abstract class ContainedOwnerPickMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void moonstation14$pickContainedOwner(float partialTick, CallbackInfo ci) {
        if (!ContainedCharacterClientView.isContained()) return;
        var mc = Minecraft.getInstance();
        mc.crosshairPickEntity = null;
        var target = ContainedCharacterClientView.worldTarget();
        mc.hitResult = target.<net.minecraft.world.phys.HitResult>map(pos -> new BlockHitResult(
                Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, true))
                .orElseGet(() -> BlockHitResult.miss(Vec3.ZERO, Direction.UP, net.minecraft.core.BlockPos.ZERO));
    }
}
