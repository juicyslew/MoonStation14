package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;
import org.joml.Vector3f;
import dev.ryanhcode.sable.companion.math.Pose3d;

/** Captures the same authoritative entry only after stock Sable accepted its snapshot call. */
@Mixin(value = ClientboundSableSnapshotDualPacket.class, remap = false)
public abstract class SableAuthoritativeSnapshotCaptureMixin {
    @Shadow @Final private int interpolationTick;

    @WrapOperation(method = "handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/network/client/ClientSableInterpolationState;receiveSnapshot(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;ILdev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V"))
    private void moonstation$captureAcceptedSnapshot(ClientSableInterpolationState interpolation,
            ClientSubLevel frame, int tick, dev.ryanhcode.sable.companion.math.Pose3dc pose,
            PacketReceiveMode mode, Operation<Void> original, Level level,
            PacketReceiveMode packetMode, @Local(name = "entry") ClientboundSableSnapshotDualPacket.Entry entry) {
        Pose3d capturedPose = new Pose3d(entry.pose());
        Vector3f capturedLinear = new Vector3f(entry.linearVelocity());
        Vector3f capturedAngular = new Vector3f(entry.angularVelocity());
        original.call(interpolation, frame, tick, pose, mode);
        ClientFrameTimelineStore.capture(level, frame, interpolationTick, capturedPose, capturedLinear, capturedAngular);
    }
}
