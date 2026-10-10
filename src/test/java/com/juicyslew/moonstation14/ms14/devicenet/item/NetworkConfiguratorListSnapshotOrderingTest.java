package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorListWindow;
import com.juicyslew.moonstation14.ms14.devicenet.item.client.ConfiguratorListLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NetworkConfiguratorListSnapshotOrderingTest {
    private static NetworkConfiguratorListSnapshot snapshot(UUID session, long request, long revision, String address) {
        return new NetworkConfiguratorListSnapshot(session, request, revision, 2, true, false,
                address, List.of(), List.of(), List.of(), List.of());
    }

    @Test void revisionOrderingAcceptsUnsolicitedUpdatesAndRejectsOldReplies() {
        var session = UUID.randomUUID();
        var window = new NetworkConfiguratorListWindow();
        window.accept(snapshot(session, 3, 5, "old"), 800, 600, ignored -> { });
        window.accept(snapshot(session, 0, 6, "new"), 800, 600, ignored -> { });
        assertEquals("new", window.snapshot().targetAddress());
        window.accept(snapshot(session, 4, 5, "late"), 800, 600, ignored -> { });
        assertEquals("new", window.snapshot().targetAddress());
    }

    @Test void staleQueryAtSameHandRevisionIsIgnoredButNewRequestIsAccepted() {
        var session = UUID.randomUUID();
        var window = new NetworkConfiguratorListWindow();
        window.accept(snapshot(session, 4, 5, "current"), 800, 600, ignored -> { });
        window.accept(snapshot(session, 3, 5, "stale"), 800, 600, ignored -> { });
        assertEquals("current", window.snapshot().targetAddress());
        window.accept(snapshot(session, 5, 5, "next"), 800, 600, ignored -> { });
        assertEquals("next", window.snapshot().targetAddress());
    }

    @Test void geometryClampsAndFitsTheEntirePaneAtEveryViewport(){
        var window=new NetworkConfiguratorListWindow();var sent=new java.util.ArrayList<NetworkConfiguratorListWindow.Command>();
        window.accept(snapshot(UUID.randomUUID(),1,1,""),800,600,sent::add);
        assertEquals((800-window.width())/2,window.x());assertEquals((600-window.height())/2,window.y());
        window.setBounds(700,500,800,600);assertEquals(800-window.width(),window.x());assertEquals(600-window.height(),window.y());
        window.resize(170,140);assertFalse(window.undersized());
        assertTrue(window.x()>=0&&window.y()>=0&&window.x()+window.width()<=170&&window.y()+window.height()<=140);
        assertTrue(6L*window.width()*window.height()<=170L*140L);
        double scale=window.displayScale();var done=ConfiguratorListLayout.DONE;
        assertTrue(window.click(window.x()+(done.x()+done.width()/2.0)*scale,window.y()+(done.y()+done.height()/2.0)*scale));
        assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListRequest.Operation.DONE,sent.get(0).operation());
    }
}
