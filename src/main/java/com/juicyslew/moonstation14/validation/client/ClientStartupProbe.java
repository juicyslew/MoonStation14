package com.juicyslew.moonstation14.validation.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Opt-in bounded smoke marker; inactive during ordinary gameplay. */
public final class ClientStartupProbe {
    private static boolean completed;
    private ClientStartupProbe() { }

    public static void tick(ClientTickEvent.Post event) {
        if (completed) return;
        String output = System.getProperty("ms14.clientStartupProbe");
        if (output == null || output.isBlank()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) || minecraft.getOverlay() != null) return;
        completed = true;
        try {
            Path path = Path.of(output);
            Files.createDirectories(path.getParent());
            String source = Path.of(ClientStartupProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ClientStartupProbe.class.getResourceAsStream("ClientStartupProbe.class").readAllBytes()));
            Files.writeString(path, "{\"testId\":\"client-startup-smoke\",\"status\":\"title_screen_ready\",\"screen\":\"TitleScreen\",\"overlay\":null,\"probeClassSha256\":\"" + hash + "\",\"sourceLocation\":\"" + source.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n");
            minecraft.stop();
        } catch (Exception exception) {
            throw new RuntimeException("Could not write client startup probe marker", exception);
        }
    }
}
