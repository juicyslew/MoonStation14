package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GhostRoleMenuPresentationTest {
    private static GhostRolePayloads.Entry entry(String id,String role,String name){return new GhostRolePayloads.Entry(UUID.fromString(id),role,name,true,true);}
    private static final GhostRolePayloads.Entry MOUSE_A=entry("00000000-0000-0000-0000-000000000001","mouse","Mouse");
    private static final GhostRolePayloads.Entry MOUSE_B=entry("00000000-0000-0000-0000-000000000002","mouse","Mouse");
    private static final GhostRolePayloads.Entry HUMAN=entry("00000000-0000-0000-0000-000000000003","human","Human");
    private static final GhostRolePayloads.Entry PIG=entry("00000000-0000-0000-0000-000000000004","pig","Pig");

    @Test void groupsByTypedRoleIdAndKeepsEntitiesDistinctAndSorted() {
        var entries=List.of(MOUSE_B,HUMAN,PIG,MOUSE_A);
        assertEquals(List.of("human","mouse","pig"),GhostRoleMenuPresentation.groups(entries));
        assertEquals(List.of(MOUSE_A,MOUSE_B),GhostRoleMenuPresentation.members(entries,"mouse"));
        assertEquals(List.of(HUMAN),GhostRoleMenuPresentation.members(entries,"human"));
        assertEquals(List.of(PIG),GhostRoleMenuPresentation.members(entries,"pig"));
    }
    @Test void collapsedAndExpandedHeightReserveFooterOnlyForSelection() {
        var entries=List.of(MOUSE_A,MOUSE_B,HUMAN,PIG);Set<String> collapsed=Set.of();
        assertEquals(3,GhostRoleMenuPresentation.rowCount(entries,collapsed));
        int noSelection=GhostRoleMenuPresentation.desiredHeight(entries,collapsed,false,false,70);
        assertEquals(13+4+3*12+4,noSelection);
        assertEquals(13+4+3*12+26+4,GhostRoleMenuPresentation.desiredHeight(entries,collapsed,true,false,70));
        assertEquals(13+4+3*12+12+26+4,GhostRoleMenuPresentation.desiredHeight(entries,collapsed,true,true,70));
        assertEquals(13+4+5*12+4,GhostRoleMenuPresentation.desiredHeight(entries,Set.of("mouse"),false,false,70));
    }
    @Test void uuidSelectionSurvivesSnapshotReorderAndClearsWhenTargetDisappears() {
        String selected=MOUSE_B.target().toString();
        assertSame(MOUSE_B,GhostRoleMenuPresentation.selected(List.of(PIG,MOUSE_B,MOUSE_A),selected));
        assertSame(MOUSE_B,GhostRoleMenuPresentation.selected(List.of(MOUSE_B),selected));
        assertNull(GhostRoleMenuPresentation.selected(List.of(MOUSE_A),selected));
        assertNull(GhostRoleMenuPresentation.selected(List.of(MOUSE_A),null));
    }
    @Test void windowWidthPolicyIsCompactAndViewportBounded() {
        assertEquals(64,GhostRoleMenuPresentation.width(640));
        assertEquals(110,GhostRoleMenuPresentation.width(1920));
        assertEquals(64,GhostRoleMenuPresentation.width(70),"the one-tenth policy is capped by the 70px viewport");
        assertEquals(26,GhostRoleMenuPresentation.actionHeight(70));
        assertEquals(15,GhostRoleMenuPresentation.actionHeight(100));
    }
    @Test void flattenedRowsKeepScrolledRenderingAndHitTestingAlignedAcrossExpandedGroups() {
        var entries=List.of(MOUSE_A,MOUSE_B,HUMAN,PIG);var expanded=Set.of("mouse");
        var rows=GhostRoleMenuPresentation.visibleRows(entries,expanded);
        assertEquals(List.of(MOUSE_A,MOUSE_B),rows.stream().filter(r->!r.header()).map(GhostRoleMenuPresentation.Row::entry).toList());
        assertSame(MOUSE_B,GhostRoleMenuPresentation.rowAt(entries,expanded,3,0).entry());
        assertEquals(2,GhostRoleMenuPresentation.maxScroll(entries,expanded,3));
        assertEquals(4,GhostRoleMenuPresentation.maxScroll(entries,expanded,1));
        assertEquals(0,GhostRoleMenuPresentation.maxScroll(entries,Set.of(),3));
        assertNull(GhostRoleMenuPresentation.rowAt(entries,expanded,99,0));
        assertSame(MOUSE_B,GhostRoleMenuPresentation.rowAtPixel(entries,expanded,3,0,12).entry());
        assertNull(GhostRoleMenuPresentation.rowAtPixel(entries,expanded,3,9,11),"a partial trailing row cannot hit an unrendered row");
        assertNull(GhostRoleMenuPresentation.rowAtPixel(entries,expanded,3,-1,11));
    }
    @Test void scaledCloseBoundsAreAtTheActualWindowTopRightCorner() {
        int x=5,y=5,width=64;
        var close=GhostRoleMenuPresentation.closeBounds(x,y,width);
        assertEquals(x+width-2,close.right());
        assertEquals(y+3,close.top());
        assertTrue(close.left()>x+width/2,"close control must not drift toward the middle of the window");
        var dragged=GhostRoleMenuPresentation.closeBounds(100,90,width);
        assertEquals(153,dragged.left());
        assertEquals(93,dragged.top());
    }
    @Test void paintedRowBoundsAreTheOnlyClickableAreaIncludingHorizontalEdges() {
        var entries=List.of(MOUSE_A,MOUSE_B,HUMAN,PIG);var expanded=Set.of("mouse");
        var bounds=GhostRoleMenuPresentation.rowBounds(10,20,64,24,1);
        assertEquals(new GhostRoleMenuPresentation.RowBounds(13,32,71,44),bounds);
        assertSame(MOUSE_A,GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,13,20).entry());
        assertSame(MOUSE_A,GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,70.9,31.9).entry());
        assertSame(MOUSE_B,GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,13,32).entry());
        assertSame(MOUSE_B,GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,70.9,43.9).entry());
        assertNull(GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,12.9,35));
        assertNull(GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,71,35));
        assertNull(GhostRoleMenuPresentation.rowAtPoint(entries,expanded,2,10,20,64,24,20,44),"partial clipped row is not clickable");
    }
}
