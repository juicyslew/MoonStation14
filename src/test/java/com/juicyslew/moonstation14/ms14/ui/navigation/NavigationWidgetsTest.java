package com.juicyslew.moonstation14.ms14.ui.navigation;

import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.NavigationHit;
import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.model.OptionModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure interaction examples for the tab strip, list viewport and EditBox submit wrapper. */
class NavigationWidgetsTest {
    @Test void searchListViewportRejectsOversizedPages() {
        assertThrows(IllegalArgumentException.class, () -> MachineSearchList.viewportHeight(0));
        assertThrows(IllegalArgumentException.class, () -> MachineSearchList.viewportHeight(25));
        assertThrows(IllegalArgumentException.class, () -> MachineSearchList.viewportHeight(512));
        assertThrows(IllegalArgumentException.class, () -> MachineSearchList.viewportHeight(Integer.MAX_VALUE));
        assertEquals(24 * 18, MachineSearchList.viewportHeight(MachineSearchList.MAX_VISIBLE_ROWS));
    }

    @Test void searchListRejectsMismatchedPageSizeEvenWithFewEntries() {
        var empty = new FilteredListModel(4, 2);
        assertEquals(2, empty.pageSize());
        assertThrows(IllegalArgumentException.class, () -> new MachineSearchList(0, 22, 80, 1,
                Component.literal("Search"), empty, id -> {}));

        var single = new FilteredListModel(4, 1);
        single.snapshot(1, List.of(new FilteredListModel.Entry("a", "Alpha")));
        assertThrows(IllegalArgumentException.class, () -> new MachineSearchList(0, 22, 80, 2,
                Component.literal("Search"), single, id -> {}));
    }

    @Test void searchListViewportBoundsVisibleRowsAndHitExtentAtModelCap() {
        int pageSize = MachineSearchList.MAX_VISIBLE_ROWS;
        int height = MachineSearchList.viewportHeight(pageSize);
        var list = new FilteredListModel(512, pageSize);
        list.snapshot(1, IntStream.range(0, 512)
                .mapToObj(i -> new FilteredListModel.Entry("id-" + i, "Entry " + i)).toList());
        assertEquals(512, list.matchCount());
        assertEquals(pageSize, list.visibleCount());
        assertEquals(pageSize - 1, NavigationHit.row(10, 20 + height - 1, 0, 20, 80, 18,
                Math.min(pageSize, list.visibleCount())));
        assertEquals(-1, NavigationHit.row(10, 20 + height, 0, 20, 80, 18,
                Math.min(pageSize, list.visibleCount())));
        list.scroll(512);
        assertEquals(pageSize, list.visibleCount());
        assertEquals("id-511", list.visibleAt(pageSize - 1).id());
        list.filter("Entry 511");
        assertEquals(1, list.visibleCount());
        assertEquals(-1, NavigationHit.row(10, 20 + 18, 0, 20, 80, 18,
                Math.min(pageSize, list.visibleCount())));
    }

    @Test void tabHitSelectsStableIdRatherThanPositionAfterReorder() {
        var tabs = new OptionModel(3);
        tabs.snapshot(1, List.of(new OptionModel.Option("alpha", "A"), new OptionModel.Option("beta", "B")));
        int index = NavigationHit.tab(69, 12, 10, 10, 100, 20, tabs.size());
        assertEquals(1, index);
        assertTrue(tabs.select(tabs.at(index).id()));
        tabs.snapshot(2, List.of(new OptionModel.Option("beta", "B"), new OptionModel.Option("alpha", "A")));
        assertEquals("beta", tabs.selectedId());
        assertEquals(-1, NavigationHit.tab(110, 12, 10, 10, 100, 20, tabs.size()));
    }

    @Test void listHitOnlyTargetsVisibleRowsAndFilteringKeepsStableId() {
        var list = new FilteredListModel(4, 2);
        list.snapshot(1, List.of(new FilteredListModel.Entry("a", "Alpha"),
                new FilteredListModel.Entry("b", "Beta"), new FilteredListModel.Entry("c", "Gamma")));
        list.scroll(1);
        int row = NavigationHit.row(10, 37, 0, 20, 80, 18, list.visibleCount());
        assertEquals(0, row);
        assertTrue(list.select(list.visibleAt(row).id()));
        assertEquals("b", list.selectedId());
        assertEquals(-1, NavigationHit.row(10, 56, 0, 20, 80, 18, list.visibleCount()));
        list.filter("be");
        assertEquals("b", list.selectedId());
        assertEquals(1, list.visibleCount());
    }

    @Test void draftSubmitOnlyWhenValidChangedAndNotPendingUntilMatchingAck() {
        var field = DraftFieldModel.number(4, BigDecimal.ZERO, BigDecimal.TEN);
        field.snapshot(1, "2");
        assertFalse(canSubmit(field));
        field.edit("11");
        assertFalse(canSubmit(field));
        field.edit("4");
        assertTrue(canSubmit(field));
        var intent = field.submit();
        assertFalse(canSubmit(field));
        field.snapshot(2, "3");
        assertFalse(field.resolve(intent.requestId() + 1, 2, "3", true));
        assertFalse(canSubmit(field));
        assertTrue(field.resolve(intent.requestId(), 2, "3", false));
        assertFalse(canSubmit(field));
        field.edit("5");
        assertTrue(canSubmit(field));
    }

    private static boolean canSubmit(DraftFieldModel field) {
        return NavigationHit.canSubmit(field.valid(), field.pending() != null, field.revision(),
                field.draft(), field.authority());
    }
}
