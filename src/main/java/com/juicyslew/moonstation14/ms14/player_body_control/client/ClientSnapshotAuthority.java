package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Diagnostic threshold for genuine authority corrections; placement is restored by the temporal consumer. */
final class ClientSnapshotAuthority {
    private ClientSnapshotAuthority() { }

    static boolean shouldReportCorrection(net.minecraft.world.phys.Vec3 before,
                                          net.minecraft.world.phys.Vec3 after, boolean barrier) {
        return barrier || before.distanceTo(after) >= .35;
    }
}
