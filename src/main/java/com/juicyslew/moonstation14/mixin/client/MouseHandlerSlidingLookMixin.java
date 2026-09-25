package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.movement.client.MovementClientController;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes local look only while a custom-owned, supported player is stunned. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerSlidingLookMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$freezeLookWhileStunned(double timeDelta, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || !MovementClientController.owns(player)
                || !supportedPlayer(minecraft, player)
                || !CharacterControlSystem.isClientActionBlocked(player)) return;

        callback.cancel();
    }

    private static boolean supportedPlayer(Minecraft minecraft, LocalPlayer player) {
        GameType gameType = minecraft.gameMode.getPlayerMode();
        return (gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE)
                && player.isAlive() && !player.isPassenger() && !player.isFallFlying() && !player.isSwimming()
                && !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying
                && !player.isSpectator();
    }
}
