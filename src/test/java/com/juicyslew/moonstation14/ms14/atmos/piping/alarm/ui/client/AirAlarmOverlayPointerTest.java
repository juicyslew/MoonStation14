package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmOverlayPointerTest {
    @Test void titleDragClampsAndReleaseClearsCapture() {
        var overlay=new AirAlarmOverlay(null,UUID.randomUUID(),400,300,()->true,r->{},id->{});
        assertTrue(overlay.mouseClicked(80+100,75+10,0));
        assertTrue(overlay.ownsPointerCapture());
        assertTrue(overlay.mouseDragged(900,900));
        assertFalse(overlay.contains(900,900));
        assertTrue(overlay.mouseReleased(0));
        assertFalse(overlay.ownsPointerCapture());
    }
    @Test void unavailablePointerIgnoresClickAndCloseUsesSession() {
        UUID session=UUID.randomUUID();var closed=new AtomicReference<UUID>();
        var unavailable=new AirAlarmOverlay(null,session,400,300,()->false,r->{},closed::set);
        assertFalse(unavailable.mouseClicked(48+100,38+10,0));assertNull(closed.get());
        var close=AirAlarmPaintLayout.forContent(400,300,0,0).close();
        var bounds=com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(close.x(),close.y(),close.width(),close.height());
        var overlay=new AirAlarmOverlay(null,session,400,300,()->true,r->{},closed::set);
         assertTrue(overlay.mouseClicked(80+bounds.left()+1,75+bounds.top()+1,0));
        assertEquals(session,closed.get());
    }
}
