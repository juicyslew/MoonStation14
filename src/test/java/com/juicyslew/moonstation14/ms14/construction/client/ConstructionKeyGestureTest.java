package com.juicyslew.moonstation14.ms14.construction.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionKeyGestureTest {
    @Test void tapOpensBrowserOnReleaseWithoutPaletteOrScrollCapture(){
        var gesture=new ConstructionKeyGesture();
        assertFalse(gesture.update(true,true).openBrowser());
        var released=gesture.update(false,true);
        assertTrue(released.openBrowser());assertFalse(released.showPalette());assertFalse(released.cancelScroll());
    }
    @Test void holdOpensOnlyCurrentFamilyPaletteAndCapturesScrollOnlyThere(){
        var gesture=new ConstructionKeyGesture();
        for(int i=0;i<9;i++)assertFalse(gesture.update(true,true).showPalette());
        var held=gesture.update(true,true);assertTrue(held.showPalette());assertTrue(held.cancelScroll());
        assertFalse(gesture.update(false,true).openBrowser());
    }
    @Test void holdWithoutFamilyFallsBackToBrowserOnRelease(){
        var gesture=new ConstructionKeyGesture();
        for(int i=0;i<12;i++)assertFalse(gesture.update(true,false).showPalette());
        assertTrue(gesture.update(false,false).openBrowser());
    }
    @Test void browserCloseCommandConsumesPressUntilPhysicalRelease() {
        var gesture=new ConstructionKeyGesture();
        int[] closes={0}; Runnable close=()->closes[0]++;
        assertTrue(gesture.closeBrowserOnPress(true,false,true,71,-1,close));
        assertFalse(gesture.closeBrowserOnPress(true,false,true,71,-1,close));
        assertEquals(1,closes[0]);
        for(int i=0;i<14;i++) {
            var frame=gesture.update(true,true);
            assertFalse(frame.openBrowser()); assertFalse(frame.showPalette());
            assertFalse(gesture.update(false,true).openBrowser());
        }
        assertTrue(gesture.pressConsumed());
        assertFalse(gesture.consumeRelease(72,-1));
        assertTrue(gesture.pressConsumed());
        // Screen-handled releases still go through the shared physical-release owner.
        assertTrue(gesture.consumeRelease(71,-1));
        assertFalse(gesture.pressConsumed());
        assertFalse(gesture.update(true,false).openBrowser());
        assertTrue(gesture.update(false,false).openBrowser());
        assertFalse(gesture.closeBrowserOnPress(false,false,true,71,-1,close));
        assertEquals(1,closes[0]);
    }
    @Test void typingAndUnmatchedBindingsCannotCloseBrowser() {
        var gesture=new ConstructionKeyGesture(); int[] closes={0}; Runnable close=()->closes[0]++;
        assertFalse(gesture.closeBrowserOnPress(true,true,true,71,1,close));
        assertFalse(gesture.pressConsumed());
        assertFalse(gesture.closeBrowserOnPress(true,false,false,74,2,close));
        assertFalse(gesture.pressConsumed()); assertEquals(0,closes[0]);
    }
    @Test void contextResetClearsConsumedPhysicalPress() {
        var gesture=new ConstructionKeyGesture(); gesture.closeBrowserOnPress(true,false,true,71,12,()->{});
        gesture.resetContext(); assertFalse(gesture.pressConsumed());
    }
}
