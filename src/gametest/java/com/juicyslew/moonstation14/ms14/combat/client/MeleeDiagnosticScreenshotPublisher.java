package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

/** Shared asynchronous screenshot file pipeline for the opt-in melee diagnostics. */
final class MeleeDiagnosticScreenshotPublisher {
    record Result(Path path, String sha256, Instant requestedAt, Instant completedAt, String sourceName) { }

    private MeleeDiagnosticScreenshotPublisher() { }

    static void publish(Minecraft mc, Path destination, Consumer<Result> success, Consumer<Throwable> failure) {
        String destinationName = destination.getFileName().toString();
        String sourceName = "ms14-" + UUID.randomUUID() + ".png";
        Path screenshots = mc.gameDirectory.toPath().resolve("screenshots");
        Path source = screenshots.resolve(sourceName);
        Instant requestedAt = Instant.now();
        try {
            Files.createDirectories(screenshots);
            Files.createDirectories(destination.toAbsolutePath().getParent());
            if (Files.exists(source)) throw new IllegalStateException("unique screenshot path already exists: " + sourceName);
            Screenshot.grab(mc.gameDirectory, sourceName, mc.getMainRenderTarget(), result -> mc.execute(() -> {
                try {
                    if (result == null) throw new IllegalStateException("screenshot callback returned null");
                    if (!Files.isRegularFile(source) || Files.size(source) <= 0) throw new IllegalStateException("missing or empty screenshot " + sourceName);
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    if (!Files.isRegularFile(destination) || Files.size(destination) <= 0) throw new IllegalStateException("missing or empty destination screenshot " + destinationName);
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(destination)));
                    success.accept(new Result(destination, hash, requestedAt, Instant.now(), sourceName));
                } catch (Throwable t) { failure.accept(t); }
            }));
        } catch (Throwable t) { failure.accept(t); }
    }
}
