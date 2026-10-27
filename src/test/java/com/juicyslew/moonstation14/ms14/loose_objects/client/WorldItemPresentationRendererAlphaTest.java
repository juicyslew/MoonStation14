package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldItemPresentationRendererAlphaTest {
    @Test void alphaClampsAndRoundsToByte() {
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(Float.NEGATIVE_INFINITY));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(Float.NaN));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(-.1f));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(0f));
        assertEquals(1, WorldItemPresentationRenderer.alphaByte(.5f / 255f));
        assertEquals(128, WorldItemPresentationRenderer.alphaByte(.5f));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(1f));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(Float.POSITIVE_INFINITY));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(2f));
    }
}
