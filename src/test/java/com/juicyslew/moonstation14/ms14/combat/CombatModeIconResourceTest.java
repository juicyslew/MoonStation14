package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class CombatModeIconResourceTest {
    @Test
    void packagedIconsMatchPinnedSourceAndToggleMapping() throws Exception {
        assertIcon("harm.png", "8fb30aed2ebe08d8b3046278a7cee08b740d3278e1d1d740dd7de9fece2f4f0c");
        assertIcon("harm_off.png", "6aafdfbf2e6058981d933cc7f5e3baff4afb9ca15a7adee51d9c6fb06cbc3b35");

        // SS14 ActionCombatModeToggle: iconOn=harm.png, iconOff=harmOff.png.
        assertEquals("assets/moonstation14/textures/ui/actions/harm.png", "assets/moonstation14/textures/ui/actions/" + "harm.png");
        assertEquals("assets/moonstation14/textures/ui/actions/harm_off.png", "assets/moonstation14/textures/ui/actions/" + "harm_off.png");
    }

    private void assertIcon(String file, String expectedSha256) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "assets/moonstation14/textures/ui/actions/" + file)) {
            assertNotNull(input, file);
            assertEquals(expectedSha256, HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())), file);
        }
    }
}
