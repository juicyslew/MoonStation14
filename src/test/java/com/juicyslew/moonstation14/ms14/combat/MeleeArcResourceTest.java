package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class MeleeArcResourceTest {
    @Test
    void packagedImagesHaveExpectedDimensionsAndSourceHashes() throws Exception {
        assertImage("fist.png", "bf94a132e542bf7c31093ba80a7fc9aaf737a99acdae7ab288726b267a6a6a33");
        assertImage("spear.png", "7fd170b19ae26470b92ed7ebf09f8d664846c050a99290f4727506c39c11ae1c");
    }

    private void assertImage(String name, String expectedHash) throws Exception {
        String path = "assets/moonstation14/textures/effects/melee/" + name;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            byte[] image = input.readAllBytes();
            assertEquals(expectedHash, HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(image)), path);
            assertEquals(32, readInt(image, 16), "PNG width: " + path);
            assertEquals(32, readInt(image, 20), "PNG height: " + path);
        }
    }

    private int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
    }
}
