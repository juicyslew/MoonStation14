package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Character players do not receive vanilla's separate Peaceful nutrition effects. */
@Mixin(Player.class)
public abstract class PlayerPeacefulNutritionMixin {
    @WrapOperation(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean moonstation14$disablePeacefulNutrition(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                            Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        if (key == GameRules.RULE_NATURAL_REGENERATION
                && player.level() instanceof ServerLevel
                && (HungerSystem.isEligible(player) || ActiveCharacterPolicy.isCarrier(player))) {
            return false;
        }
        return original.call(rules, key);
    }
}
