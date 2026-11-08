package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** Narrow read-only view of lifecycle ownership for ghost-role discovery. */
public final class LifecycleRoleAvailability {
    private LifecycleRoleAvailability() { }

    /** True only when the active shared lifecycle runtime has no harness registration for this body. */
    public static boolean unclaimed(MinecraftServer server, Entity body) {
        if (server == null || body == null || !server.isSameThread() || body.getServer() != server) return false;
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        MobHarnessId id = new MobHarnessId(body.getUUID());
        return context.ownership().registeredHarness(id).isEmpty()
                && PlayerCharacterHarnessBinder.binding(body) == null;
    }
}
