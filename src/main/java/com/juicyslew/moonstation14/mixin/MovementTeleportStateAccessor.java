package com.juicyslew.moonstation14.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to vanilla's current client teleport acknowledgement state. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface MovementTeleportStateAccessor {
    @Accessor("awaitingTeleport")
    int moonstation14$getAwaitingTeleport();

    @Accessor("awaitingPositionFromClient")
    Vec3 moonstation14$getAwaitingPositionFromClient();
}
