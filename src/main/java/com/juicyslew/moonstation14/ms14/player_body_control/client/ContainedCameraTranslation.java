package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.world.phys.Vec3;

/** Pure translation policy shared with focused camera tests. */
public final class ContainedCameraTranslation {
    private ContainedCameraTranslation() { }
    public static Vec3 delta(Vec3 renderedParent, Vec3 bodyFeetOffset, Vec3 nativeBodyOrigin) {
        return renderedParent.add(bodyFeetOffset).subtract(nativeBodyOrigin);
    }
}
