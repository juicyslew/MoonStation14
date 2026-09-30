package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Runtime-audited compatibility guard; the same fail-closed prerequisite is also called directly by resolve. */
@Mixin(value = HandActorAuthority.class, remap = false)
public abstract class HandActorParkedCarrierGuardMixin {
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private static void moonstation14$denyDirtyCarrierHandAction(ServerPlayer actor,
            CallbackInfoReturnable<Optional<HandActorAuthority.Snapshot>> callback) {
        if (!CarrierHandInventoryGate.allows(actor)) callback.setReturnValue(Optional.empty());
    }
}
