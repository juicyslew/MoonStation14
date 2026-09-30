package com.juicyslew.moonstation14.ms14.chat.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalChatVisualPolicyTest {
    @Test
    void onlyAcceptedSystemChatIsMirroredNeverActionBarOrPlayerChat() {
        var system = LocalChatVisualPolicy.Source.ACCEPTED_SYSTEM_CHAT;
        assertTrue(LocalChatVisualPolicy.shouldMirrorNotice(system, false, true));
        assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(system, true, true));
        assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(system, false, false));
        assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(null, false, true));
        for (var source : new LocalChatVisualPolicy.Source[] {
                LocalChatVisualPolicy.Source.GENERIC_CHAT_INSERTION,
                LocalChatVisualPolicy.Source.PLAYER_CHAT,
                LocalChatVisualPolicy.Source.DISGUISED_CHAT
        }) {
            assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(source, false, true));
        }
    }

    @Test
    void forgedSystemLookingPlayerMessagesHaveNoAdmissionPath() throws IOException {
        // GuiMessageTag.system() and null signatures also occur on disguised/unsigned player chat.
        assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(LocalChatVisualPolicy.Source.DISGUISED_CHAT, false, true));
        assertFalse(LocalChatVisualPolicy.shouldMirrorNotice(LocalChatVisualPolicy.Source.PLAYER_CHAT, false, true));
        String mixin = Files.readString(projectFile("src/main/java/com/juicyslew/moonstation14/mixin/client/ChatListenerSystemNoticeMixin.java"));
        assertTrue(mixin.contains("handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"));
        assertFalse(mixin.contains("GuiMessageTag"));
        assertFalse(mixin.contains("MessageSignature"));
        assertFalse(mixin.contains("getGameProfile"));
        assertFalse(mixin.contains("@Inject(method = \"addMessage"));
    }

    @Test
    void vanillaHistoryHiddenOnlyWhenFeatureActiveInWorld() {
        assertTrue(LocalChatVisualPolicy.shouldHideVanillaHistory(true, true));
        assertFalse(LocalChatVisualPolicy.shouldHideVanillaHistory(false, true));
        assertFalse(LocalChatVisualPolicy.shouldHideVanillaHistory(true, false));
        assertFalse(LocalChatVisualPolicy.shouldHideVanillaHistory(false, false));
    }

    @Test
    void hiddenHistoryHasNoClickOrHoverLookupButFallbackRetainsVanilla() throws IOException {
        String mixin = Files.readString(projectFile("src/main/java/com/juicyslew/moonstation14/mixin/client/ChatComponentUnifiedViewMixin.java"));
        for (String lookup : new String[] {
                "getClickedComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;",
                "handleChatQueueClicked(DD)Z",
                "getMessageTagAt(DD)Lnet/minecraft/client/GuiMessageTag;"
        }) {
            int declaration = mixin.indexOf("@Inject(method = \"" + lookup + "\"");
            assertTrue(declaration >= 0, lookup);
            String injection = mixin.substring(declaration, mixin.indexOf("\n    }", declaration));
            assertTrue(injection.contains("at = @At(\"HEAD\"), cancellable = true, require = 1"), lookup);
            assertTrue(injection.contains("if (moonstation14$hideVanillaHistory()) callback.setReturnValue("), lookup);
            assertTrue(injection.contains(lookup.startsWith("handleChatQueueClicked")
                    ? "callback.setReturnValue(false)" : "callback.setReturnValue(null)"), lookup);
        }
        assertTrue(mixin.contains("LocalChatVisualPolicy.shouldHideVanillaHistory(LocalSpeechReviewOverlay.replacesVanillaChat(),"));
        assertTrue(mixin.contains("minecraft != null && minecraft.level != null && minecraft.player != null"));
    }

    @Test
    void vanillaScrollRoutesOnlyUnderUnifiedWorldGuardAndUsesSignedDirection() throws IOException {
        assertEquals(1, LocalChatVisualPolicy.scrollDirection(10));
        assertEquals(1, LocalChatVisualPolicy.scrollDirection(1));
        assertEquals(-1, LocalChatVisualPolicy.scrollDirection(-10));
        assertEquals(-1, LocalChatVisualPolicy.scrollDirection(-1));
        assertEquals(0, LocalChatVisualPolicy.scrollDirection(0));
        String mixin = Files.readString(projectFile("src/main/java/com/juicyslew/moonstation14/mixin/client/ChatComponentUnifiedViewMixin.java"));
        int declaration = mixin.indexOf("@Inject(method = \"scrollChat(I)V\"");
        assertTrue(declaration >= 0);
        String injection = mixin.substring(declaration, mixin.indexOf("\n    }", declaration));
        assertTrue(injection.contains("at = @At(\"HEAD\"), cancellable = true, require = 1"));
        assertTrue(injection.contains("if (!moonstation14$hideVanillaHistory()) return;"));
        assertTrue(injection.contains("LocalSpeechReviewOverlay.scroll(LocalChatVisualPolicy.scrollDirection(amount), LocalSpeechReviewOverlay.historySize());"));
        assertTrue(injection.contains("callback.cancel();"));
    }

    @Test
    void bothMixinsAreRegisteredClientOnly() throws IOException {
        var root = JsonParser.parseString(Files.readString(projectFile("src/main/resources/moonstation14.mixins.json"))).getAsJsonObject();
        for (String name : new String[] {"client.ChatComponentUnifiedViewMixin", "client.ChatListenerSystemNoticeMixin"}) {
            assertTrue(root.getAsJsonArray("client").asList().stream().anyMatch(value -> name.equals(value.getAsString())));
            assertFalse(root.getAsJsonArray("mixins").asList().stream().anyMatch(value -> name.equals(value.getAsString())));
        }
    }

    @Test
    void everyRegisteredMixinHasCompiledClassBytesOnRuntimeClasspath() throws IOException {
        var loader = getClass().getClassLoader();
        try (InputStream config = loader.getResourceAsStream("moonstation14.mixins.json")) {
            assertNotNull(config, "Mixin config missing from runtime resources");
            var root = JsonParser.parseReader(new java.io.InputStreamReader(config, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String packagePath = root.get("package").getAsString().replace('.', '/');
            for (String side : new String[] {"mixins", "client"}) {
                for (var entry : root.getAsJsonArray(side)) {
                    String path = packagePath + "/" + entry.getAsString().replace('.', '/') + ".class";
                    try (InputStream compiled = loader.getResourceAsStream(path)) {
                        assertNotNull(compiled, "Configured " + side + " mixin missing: " + path);
                        assertArrayEquals(new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe},
                                compiled.readNBytes(4), "Invalid mixin class bytes: " + path);
                    }
                }
            }
        }
    }

    private static Path projectFile(String relative) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            directory = directory.getParent();
        }
        throw new AssertionError("Missing project file: " + relative);
    }
}
