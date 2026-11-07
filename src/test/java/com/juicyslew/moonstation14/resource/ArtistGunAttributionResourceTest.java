package com.juicyslew.moonstation14.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ArtistGunAttributionResourceTest {
    private static final String ROOT = "/assets/moonstation14/models/item/guns/";

    @Test void packagedGunAttributionsRetainGroupsSourcesAndLicenseTerms() throws Exception {
        assertAttribution("artist-magazine-attribution.md", "CC-BY-SA-3.0");
        assertAttribution("artist-energy-attribution.md", "c9df5ef5d675b0d1d226828bddf6b78c28502d91");
        assertAttribution("artist-manual-attribution.md", "c9df5ef5d675b0d1d226828bddf6b78c28502d91");
        assertAttribution("artist-manual-export-attribution.md", "c9df5ef5d675b0d1d226828bddf6b78c28502d91");
    }

    private static void assertAttribution(String filename, String group) throws IOException {
        String text;
        try (var stream = ArtistGunAttributionResourceTest.class.getResourceAsStream(ROOT + filename)) {
            assertNotNull(stream, ROOT + filename);
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(text.contains(group), filename);
        assertTrue(text.toLowerCase().contains("license"), filename);
        assertTrue(text.toLowerCase().contains("source"), filename);
    }
}
