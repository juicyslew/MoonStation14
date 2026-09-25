package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Narrow client input bridge for the isolated ghost harness experiment. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostControlClient {
    private static long highestEpoch;
    private static long pendingEpoch;
    private static int ghostEntityId = -1;
    private static long activeEpoch;
    private static long sentSequence;
    private static boolean readySent;
    private static boolean committed;
    private static LocalPlayer owner;
    private static Level ownerLevel;

    static {
        // This class is discovered only on the physical client due to its Dist.CLIENT subscriber annotation.
        GhostControlNetworking.installClientHandler(GhostControlClient::onPayload);
    }

    private GhostControlClient() { }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(context.player() instanceof LocalPlayer player) || player != minecraft.player || minecraft.level == null)
            return;

        if (payload instanceof GhostControlPayloads.Begin begin) {
            if (begin.mindEpoch() <= highestEpoch) return;
            clearSession(minecraft, true);
            highestEpoch = begin.mindEpoch();
            pendingEpoch = begin.mindEpoch();
            ghostEntityId = begin.ghostEntityId();
            owner = player;
            ownerLevel = minecraft.level;
            tryReady(minecraft, player);
        } else if (payload instanceof GhostControlPayloads.Commit commit) {
            if (pendingEpoch == 0 || !readySent || commit.epoch() != pendingEpoch || owner != player
                    || ownerLevel != minecraft.level || !player.isSpectator()) return;
            Entity ghost = minecraft.level.getEntity(ghostEntityId);
            if (ghost instanceof GhostMobHarnessEntity && minecraft.getCameraEntity() == ghost) {
                activeEpoch = pendingEpoch;
                committed = true;
                sentSequence = 0;
            }
        } else if (payload instanceof GhostControlPayloads.Stop stop) {
            if (pendingEpoch == stop.epoch() && owner == player) clearSession(minecraft, true);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            if (owner != null) clearSession(minecraft, true);
            owner = null;
            ownerLevel = null;
            return;
        }
        if (owner != null && (owner != player || ownerLevel != minecraft.level
                || (readySent && !player.isSpectator()))) {
            clearSession(minecraft, true);
            owner = null;
            ownerLevel = null;
            return;
        }
        if (pendingEpoch == 0 || owner != player || ownerLevel != minecraft.level) return;
        if (!player.isSpectator()) return;
        tryReady(minecraft, player);
        if (!committed || activeEpoch != pendingEpoch || !readySent || player.input == null) return;

        Entity ghost = minecraft.level.getEntity(ghostEntityId);
        if (!(ghost instanceof GhostMobHarnessEntity) || minecraft.getCameraEntity() != ghost) return;
        if (sentSequence == Long.MAX_VALUE) {
            clearSession(minecraft, true);
            return;
        }

        double strafe = player.input.leftImpulse;
        double forward = player.input.forwardImpulse;
        double length = Math.hypot(strafe, forward);
        if (length > 1d) { strafe /= length; forward /= length; }
        short wishX = (short) Math.max(-1000, Math.min(1000, Math.round(strafe * 1000d)));
        short wishZ = (short) Math.max(-1000, Math.min(1000, Math.round(forward * 1000d)));
        byte verticalWish = (byte) ((player.input.jumping ? 1 : 0) - (player.input.shiftKeyDown ? 1 : 0));
        int buttons = player.isSprinting() ? GhostControlPayloads.BUTTON_SPRINT : 0;
        float yaw = normalizeYaw(player.getYRot());
        float pitch = Math.max(-90f, Math.min(90f, finiteOrZero(player.getXRot())));
        if (!Float.isFinite(yaw)) return;
        GhostControlNetworking.sendToServer(new GhostControlPayloads.Intent(activeEpoch, ++sentSequence,
                wishX, wishZ, verticalWish, buttons, yaw, pitch));
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft minecraft = Minecraft.getInstance();
        clearSession(minecraft, true);
        highestEpoch = 0;
    }

    private static void tryReady(Minecraft minecraft, LocalPlayer player) {
        if (readySent || pendingEpoch == 0 || owner != player || ownerLevel != minecraft.level || !player.isSpectator())
            return;
        if (!(minecraft.level.getEntity(ghostEntityId) instanceof GhostMobHarnessEntity)) return;
        readySent = true;
        GhostControlNetworking.sendToServer(new GhostControlPayloads.Ready(pendingEpoch));
    }

    private static void clearSession(Minecraft minecraft, boolean restoreCamera) {
        Entity ghost = ghostEntityId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(ghostEntityId);
        if (restoreCamera && ghost instanceof GhostMobHarnessEntity && minecraft.getCameraEntity() == ghost
                && minecraft.player != null) minecraft.setCameraEntity(minecraft.player);
        pendingEpoch = 0;
        ghostEntityId = -1;
        activeEpoch = 0;
        sentSequence = 0;
        readySent = false;
        committed = false;
        owner = null;
        ownerLevel = null;
    }

    private static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) return Float.NaN;
        float normalized = yaw % 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized < -180f) normalized += 360f;
        return normalized;
    }

    private static float finiteOrZero(float value) { return Float.isFinite(value) ? value : 0f; }
}
