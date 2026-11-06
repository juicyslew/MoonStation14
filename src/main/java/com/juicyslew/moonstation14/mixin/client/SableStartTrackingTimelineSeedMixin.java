package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Seeds the packet's exact SDK-clock pose pair only after stock tracking setup completed. */
@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
public abstract class SableStartTrackingTimelineSeedMixin {
    @WrapOperation(method = "handle(Lfoundry/veil/api/network/handler/PacketContext;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ClientSubLevel;updateRenderData()V"))
    private void moonstation$seedInitialTimeline(ClientSubLevel receiver, Operation<Void> original,
            @Local(name = "level") Level level, @Local(name = "subLevel") ClientSubLevel frame) {
        original.call(receiver);
        ClientboundStartTrackingSubLevelPacket packet = (ClientboundStartTrackingSubLevelPacket)(Object)this;
        if (frame == receiver && frame.getUniqueId().equals(packet.subLevelID()))
            ClientFrameTimelineStore.seed(level, frame, packet.gameTick(), packet.lastPose(), packet.pose());
    }
}
