package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Read-only, transition-deduplicated diagnostics for the committed local camera's chunk cache. */
public final class ControlledChunkDiagnostics {
    private static String lastState;
    private static int centerX;
    private static int centerZ;
    private static boolean hasCenter;

    private ControlledChunkDiagnostics() { }

    public static void observeCenter(int x, int z) {
        centerX = x;
        centerZ = z;
        hasCenter = true;
        observe(Minecraft.getInstance());
    }

    public static void observe(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) camera = minecraft.player;
        boolean committedView = camera != minecraft.player
                && (GhostControlClient.ownedCharacterForPresentation() == camera
                || GhostControlClient.isOwnedMedicalObserverGhost(camera)
                || GhostControlClient.ownedHarnessForTracker(camera) == camera);
        if (!committedView) {
            reset();
            return;
        }

        observe(minecraft, camera, camera.position());
    }

    /** Records the actual renderer camera position (which may be interpolated or third-person offset). */
    public static void observeRender(Minecraft minecraft, Camera renderCamera) {
        if (minecraft == null || renderCamera == null || minecraft.player == null || minecraft.level == null) return;
        Entity entity = renderCamera.getEntity();
        if (entity != GhostControlClient.ownedCharacterForPresentation()
                && !GhostControlClient.isOwnedMedicalObserverGhost(entity)) return;
        observe(minecraft, entity, renderCamera.getPosition());
    }

    private static void observe(Minecraft minecraft, Entity camera, net.minecraft.world.phys.Vec3 renderPosition) {
        ChunkPos cameraChunk = new ChunkPos(BlockPos.containing(renderPosition));
        ChunkPos carrierChunk = minecraft.player.chunkPosition();
        boolean cached = minecraft.level.getChunkSource()
                .getChunk(cameraChunk.x, cameraChunk.z, ChunkStatus.FULL, false) != null;
        String state = minecraft.player.getUUID() + ":" + camera.getUUID() + ":" + cameraChunk + ":"
                + renderPosition + ":"
                + carrierChunk + ":" + (hasCenter ? centerX + "," + centerZ : "unknown") + ":" + cached;
        if (state.equals(lastState)) return;
        lastState = state;
        MoonStation14.LOGGER.info("[controlled chunks] owner={} camera={} rendererPosition={} cacheCenter={} cameraChunk={} carrierChunk={} cameraChunkCached={}",
                minecraft.player.getUUID(), camera.getUUID(), renderPosition,
                hasCenter ? "(" + centerX + ", " + centerZ + ")" : "unknown", cameraChunk, carrierChunk, cached);
    }

    private static void reset() {
        lastState = null;
        hasCenter = false;
    }
}
