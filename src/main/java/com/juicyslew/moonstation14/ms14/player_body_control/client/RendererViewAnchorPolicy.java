package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Pure coordinate selection used by LevelRenderer's section-change and ViewArea anchors. */
public final class RendererViewAnchorPolicy {
    private RendererViewAnchorPolicy() { }

    public static double coordinate(boolean committedOwnedView, double renderCameraCoordinate, double carrierCoordinate) {
        return committedOwnedView ? renderCameraCoordinate : carrierCoordinate;
    }
}
