package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnedViewRendererAnchorTest {
    private static Path projectFile(String relativePath) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) return candidate;
            directory = directory.getParent();
        }
        throw new IllegalStateException("Could not find project file " + relativePath);
    }

    @Test
    void rendererGridAndSectionChangeCoordinatesUseOnlyCommittedOwnedCamera() throws IOException {
        String mixin = Files.readString(projectFile("src/main/java/com/juicyslew/moonstation14/mixin/client/LevelRendererOwnedViewAnchorMixin.java"));
        assertTrue(mixin.contains("ownedCharacterForPresentation()"));
        assertTrue(mixin.contains("ownedGhostForPresentation()"));
        assertTrue(mixin.contains("body == camera"));
        assertTrue(mixin.contains("camera.getPosition().x"));
        assertTrue(mixin.contains("camera.getPosition().y"));
        assertTrue(mixin.contains("camera.getPosition().z"));
        assertTrue(mixin.contains("player.getX()"));
        assertTrue(mixin.contains("player.getY()"));
        assertTrue(mixin.contains("player.getZ()"));
        assertTrue(mixin.contains("setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"));
        assertTrue(mixin.contains("Lnet/minecraft/client/player/LocalPlayer;getX()D"));
        assertTrue(mixin.contains("Lnet/minecraft/client/player/LocalPlayer;getY()D"));
        assertTrue(mixin.contains("Lnet/minecraft/client/player/LocalPlayer;getZ()D"));
        assertTrue(mixin.contains("@Inject(method = \"allChanged()V\", at = @At(\"RETURN\"))"));
        assertTrue(mixin.contains("moonstation14$hasCommittedOwnedView()"));
        assertTrue(mixin.contains("lastCameraSectionX = Integer.MIN_VALUE"));
        assertTrue(mixin.contains("lastCameraSectionY = Integer.MIN_VALUE"));
        assertTrue(mixin.contains("lastCameraSectionZ = Integer.MIN_VALUE"));

        var config = JsonParser.parseString(Files.readString(projectFile("src/main/resources/moonstation14.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").toString().contains("client.LevelRendererOwnedViewAnchorMixin"));
    }

    @Test
    void rendererDiagnosticsUseEffectiveCameraPositionRatherThanEntityOrigin() throws IOException {
        String diagnostics = Files.readString(projectFile("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ControlledChunkDiagnostics.java"));
        assertTrue(diagnostics.contains("observeRender(Minecraft minecraft, Camera renderCamera)"));
        assertTrue(diagnostics.contains("renderCamera.getPosition()"));
        assertTrue(diagnostics.contains("rendererPosition={}"));
    }

    @Test
    void activeRenderCameraCrossesSectionsWhileCarrierStaysPutAndVanillaFallsBack() {
        assertEquals(40.0, RendererViewAnchorPolicy.coordinate(true, 40.0, 24.0));
        assertEquals(-8.0, RendererViewAnchorPolicy.coordinate(true, -8.0, 24.0));
        assertEquals(24.0, RendererViewAnchorPolicy.coordinate(false, 40.0, 24.0));
    }
}
