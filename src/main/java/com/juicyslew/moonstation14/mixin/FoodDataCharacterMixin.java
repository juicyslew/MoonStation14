package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Character players use custom hunger; vanilla food-driven survival effects are inactive for them. */
@Mixin(FoodData.class)
public abstract class FoodDataCharacterMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moonstation14$disableVanillaFoodTick(Player player, CallbackInfo ci) {
        if (player.level() instanceof ServerLevel
                && (HungerSystem.isEligible(player) || ActiveCharacterPolicy.isCarrier(player))) {
            ci.cancel();
        }
    }
}
