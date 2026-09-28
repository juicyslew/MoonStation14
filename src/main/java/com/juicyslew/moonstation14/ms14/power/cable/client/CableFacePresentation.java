package com.juicyslew.moonstation14.ms14.power.cable.client;

import net.minecraft.core.Direction;

/** Pure tile concealment rule: a finished station tile masks only cables on its top face. */
public final class CableFacePresentation {
    private CableFacePresentation() { }
    public static boolean isVisible(Direction face, boolean stationFloor, boolean steelTileFinish) {
        return !(stationFloor && steelTileFinish && face == Direction.UP);
    }
}
