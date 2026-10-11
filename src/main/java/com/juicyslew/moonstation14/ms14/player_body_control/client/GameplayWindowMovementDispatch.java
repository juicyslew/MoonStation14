package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.client.BodyMovementIntentScreen;
import com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Converts the production gameplay-window movement sample to the existing authoritative wire intent. */
@OnlyIn(Dist.CLIENT)
public final class GameplayWindowMovementDispatch {
    private GameplayWindowMovementDispatch() { }

    public static GhostControlPayloads.Intent dispatch(long epoch,long sequence,MobHarnessKind kind,
            net.minecraft.client.gui.screens.Screen screen,PopupMovementIntent.State ordinaryInput,float yaw,float pitch,
            Predicate<GhostControlPayloads.Intent> beforeDispatch,Consumer<GhostControlPayloads.Intent> sink) {
        PopupMovementIntent.State movement;
        boolean neutral = false;
        if (kind == MobHarnessKind.CHARACTER && screen instanceof GameplayWindow window) {
            // Keyboard-owning gameplay windows suppress movement, but still send neutral intent
            // so the server does not retain the previously sampled buttons until lease expiry.
            neutral = window.capturesKeyboard();
            movement = neutral ? PopupMovementIntent.State.IDLE
                    : screen instanceof BodyMovementIntentScreen source ? source.movementIntent() : ordinaryInput;
        } else movement = ordinaryInput;
        GhostControlPayloads.Intent intent=neutral
                ? new GhostControlPayloads.Intent(epoch, sequence, (short) 0, (short) 0, (byte) 0, 0, yaw, pitch)
                : intent(epoch,sequence,kind,movement,yaw,pitch);
        if(beforeDispatch.test(intent))sink.accept(intent);
        return intent;
    }

    private static GhostControlPayloads.Intent intent(long epoch,long sequence,MobHarnessKind kind,
            PopupMovementIntent.State movement,float yaw,float pitch) {
        Objects.requireNonNull(movement);
        double strafe=movement.left(),forward=movement.forward(),length=Math.hypot(strafe,forward);
        if(length>1d){strafe/=length;forward/=length;}
        short wishX=(short)Math.max(-1000,Math.min(1000,Math.round(strafe*1000d)));
        short wishZ=(short)Math.max(-1000,Math.min(1000,Math.round(forward*1000d)));
        byte vertical=(byte)((movement.jump()?1:0)-(movement.shift()?1:0));
        int buttons=GhostControlClient.intentButtons(kind,movement.jump(),movement.shift(),movement.sprint());
        return new GhostControlPayloads.Intent(epoch,sequence,wishX,wishZ,vertical,buttons,yaw,pitch);
    }

}
