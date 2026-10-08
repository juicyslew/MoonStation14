package com.juicyslew.moonstation14.ms14.hands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LiveHandsWriteArchitectureTest {
    @Test void gameplayAttachmentWritesStayInsideGatewayOrExplicitBootstrap() throws Exception {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "main source tree must be available");
        try (var files = Files.walk(root.resolve(relative))) {
            var violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("LiveHandsCommitGateway.java") && !path.endsWith("LiveHands.java"))
                    .filter(path -> {
                        try { return Files.readString(path).contains("setData(ModDataAttachments.LIVE_HANDS.get(),"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }).toList();
            assertTrue(violations.isEmpty(), "direct live-hand writes: " + violations);
        }
    }

    @Test void constructionInventoryAndRefundPublishThroughSharedGatewayBatches() throws Exception {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/ms14");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "main source tree must be available");
        root = root.resolve(relative);
        String inventory = Files.readString(root.resolve("construction/inventory/BodyCraftInventoryService.java"));
        assertTrue(inventory.contains("if (!LiveHandsCommitGateway.commitOwned(body, expected, value))"));
        assertTrue(inventory.contains("throw new IllegalStateException(\"Live hands publication rejected\")"));
        assertTrue(inventory.contains("LiveHandsCommitGateway.batch(() -> craftHeldCommitted"));
        assertTrue(inventory.contains("LiveHandsCommitGateway.batch(() -> craftBodyCommitted"));
        assertTrue(Files.readString(root.resolve("construction/world/ConstructionWorldService.java"))
                .contains("LiveHandsCommitGateway.commitTrusted(body, hands,"));
    }
}
