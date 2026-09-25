package com.juicyslew.moonstation14.ms14.prototype;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerClassloadingTest {
    private static final Set<String> CLIENT_ONLY_SOURCES = Set.of(
            "com/juicyslew/moonstation14/MoonStation14Client.java",
            "com/juicyslew/moonstation14/CameraJitterHandler.java",
            "com/juicyslew/moonstation14/util/ModSpecialProperties.java",
            "com/juicyslew/moonstation14/ms14/eye/client/EyeDamageFogHandler.java",
            "com/juicyslew/moonstation14/mixin/client/MinecraftStunInputMixin.java",
            "com/juicyslew/moonstation14/mixin/client/LocalPlayerStunDropMixin.java",
            "com/juicyslew/moonstation14/ms14/movement/client/MovementClientController.java",
            "com/juicyslew/moonstation14/mixin/client/LocalPlayerMovementTickMixin.java",
            "com/juicyslew/moonstation14/mixin/client/LocalPlayerMovementTravelMixin.java",
            "com/juicyslew/moonstation14/mixin/client/LivingEntityMovementAnimationInvoker.java",
            "com/juicyslew/moonstation14/mixin/client/LivingEntityWishFacingMixin.java",
            "com/juicyslew/moonstation14/mixin/client/MouseHandlerSlidingLookMixin.java",
            "com/juicyslew/moonstation14/ms14/player_body_control/ghost/client/GhostMobHarnessRenderer.java",
            "com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"
    );
    private static final Pattern CLIENT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?(?:net\\.minecraft\\.client|"
                    + "net\\.neoforged\\.neoforge\\.client|com\\.mojang\\.blaze3d)\\.[^;]+;");
    private static final Pattern CLIENT_CLASS_LITERAL = Pattern.compile(
            "(?:net\\.minecraft\\.client|net\\.neoforged\\.neoforge\\.client|"
                    + "com\\.mojang\\.blaze3d)(?:\\.[A-Za-z_$][\\w$]*)+\\.class\\b");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");

    @Test
    void serverOwnedProductionSourcesDoNotImportOrReferenceClientClassLiterals() throws IOException {
        Path sourceRoot = findProductionSourceRoot();
        assertTrue(Files.isDirectory(sourceRoot), "Production Java source directory should exist");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relativePath = sourceRoot.relativize(source).toString().replace('\\', '/');
                if (CLIENT_ONLY_SOURCES.contains(relativePath)) {
                    continue;
                }

                String code = COMMENTS.matcher(Files.readString(source)).replaceAll("");
                findMatches(CLIENT_IMPORT, code).forEach(match -> violations.add(relativePath + ": " + match));
                findMatches(CLIENT_CLASS_LITERAL, code)
                        .forEach(match -> violations.add(relativePath + ": " + match));
            }
        }

        assertTrue(violations.isEmpty(), () -> "Server-owned production sources reference client-only classes:\n"
                + String.join("\n", violations));
    }

    @Test
    void ghostMobHarnessRendererIsExplicitlyClientOnly() throws IOException {
        Path source = findProductionSourceRoot().resolve(
                "com/juicyslew/moonstation14/ms14/player_body_control/ghost/client/GhostMobHarnessRenderer.java");
        assertTrue(Files.isRegularFile(source), "Ghost mob harness renderer source should exist");

        String code = COMMENTS.matcher(Files.readString(source)).replaceAll("");
        assertTrue(code.matches("(?s).*@EventBusSubscriber\\s*\\(\\s*modid\\s*=\\s*MoonStation14\\.MOD_ID\\s*,"
                        + "\\s*value\\s*=\\s*Dist\\.CLIENT\\s*\\).*"),
                "Ghost mob harness renderer must be explicitly subscribed on Dist.CLIENT");
    }

    @Test
    void ghostControlClientIsExplicitlyClientOnly() throws IOException {
        Path source = findProductionSourceRoot().resolve(
                "com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java");
        assertTrue(Files.isRegularFile(source), "Ghost control client source should exist");

        String code = COMMENTS.matcher(Files.readString(source)).replaceAll("");
        assertTrue(code.matches("(?s).*@EventBusSubscriber\\s*\\(\\s*modid\\s*=\\s*MoonStation14\\.MOD_ID\\s*,"
                        + "\\s*value\\s*=\\s*Dist\\.CLIENT\\s*\\).*"),
                "Ghost control client must be explicitly subscribed on Dist.CLIENT");
    }

    private static Path findProductionSourceRoot() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            Path candidate = directory.resolve(Path.of("src", "main", "java"));
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return Path.of("src", "main", "java").toAbsolutePath().normalize();
    }

    private static List<String> findMatches(Pattern pattern, String source) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            matches.add(matcher.group().trim());
        }
        return matches;
    }
}
