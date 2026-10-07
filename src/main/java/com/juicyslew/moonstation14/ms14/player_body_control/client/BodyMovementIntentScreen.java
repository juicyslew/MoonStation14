package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;

/** Marker for a screen that intentionally forwards committed-body movement input while open. */
public interface BodyMovementIntentScreen {
    PopupMovementIntent.State movementIntent();
}
