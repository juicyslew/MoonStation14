package com.juicyslew.moonstation14.ms14.chat.presentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source contract: renderer classes must not be loaded by the headless test JVM. */
class ChatNameplateTest {
    private static final String RENDERERS = "com/juicyslew/moonstation14/ms14/player_body_control/";

    @Test
    void characterOnlyRendersOfflineStatusNotItsNameOrVanillaCustomName() throws IOException {
        String source = renderer("lifecycle/character/client/PlayerCharacterHarnessRenderer.java");
        String visibility = between(source, "protected boolean shouldShowName(", "protected void renderNameTag(");
        String tag = between(source, "protected void renderNameTag(", "@SubscribeEvent");

        assertTrue(Pattern.compile("return\\s+entity\\.hasOfflineBadge\\(\\)\\s*;").matcher(visibility).find());
        assertFalse(visibility.contains("super.shouldShowName"));
        assertFalse(tag.contains("super.renderNameTag(entity, displayName"),
                "Never pass the supplied name to the vanilla tag renderer");
        assertTrue(tag.contains("if (entity.hasOfflineBadge())"));
        assertTrue(tag.contains("super.renderNameTag(entity, Component.literal(\"Offline\")"));
        assertTrue(Pattern.compile("super\\.renderNameTag\\(").matcher(tag).results().count() == 1,
                "Offline should be the only overhead text");
        assertClientOnly(source);
    }

    @Test
    void ghostNeverRendersNamesEvenWhenVanillaCustomNameIsSet() throws IOException {
        String source = renderer("ghost/client/GhostMobHarnessRenderer.java");
        String visibility = between(source, "protected boolean shouldShowName(", "protected void renderNameTag(");
        String tag = between(source, "protected void renderNameTag(", "@SubscribeEvent");

        assertTrue(Pattern.compile("return\\s+false\\s*;").matcher(visibility).find());
        assertFalse(visibility.contains("super.shouldShowName"));
        assertFalse(tag.contains("super.renderNameTag"));
        assertClientOnly(source);
    }

    private static String renderer(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/java").resolve(RENDERERS + relativePath));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "Missing renderer method: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "Missing renderer method boundary: " + end);
        return source.substring(from, to);
    }

    private static void assertClientOnly(String source) {
        assertTrue(source.contains("@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)"));
    }
}
