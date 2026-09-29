package com.juicyslew.moonstation14.ms14.ui.client.navigation;

import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSearchListLabelsTest {
    private static final List<FilteredListModel.Entry> ENTRIES = List.of(
            new FilteredListModel.Entry("oxygen", "Oxygen"),
            new FilteredListModel.Entry("nitrogen", "Nitrogen"),
            new FilteredListModel.Entry("carbon", "Carbon dioxide"));

    @Test void snapshotPopulatesLabelsBeforeFirstDrawAndFilteringDoesNotRebuildThem() {
        var model = new FilteredListModel(16, 2);
        var labels = new MachineSearchList.LabelCache(model);
        assertTrue(labels.snapshot(0, ENTRIES));
        assertEquals("Oxygen", labels.get(model.visibleAt(0).id()).getString());
        var oxygen = labels.get("oxygen");

        model.filter("dioxide");
        assertEquals("Carbon dioxide", labels.get(model.visibleAt(0).id()).getString());
        model.filter("");
        model.scroll(1);
        assertEquals("Nitrogen", labels.get(model.visibleAt(0).id()).getString());
        assertSame(oxygen, labels.get("oxygen"));
        assertFalse(labels.snapshot(0, List.of(new FilteredListModel.Entry("other", "Other"))));
        assertSame(oxygen, labels.get("oxygen"));

        assertTrue(labels.snapshot(1, List.of(new FilteredListModel.Entry("other", "Other"))));
        assertNull(labels.get("oxygen"));
        assertEquals("Other", labels.get(model.visibleAt(0).id()).getString());
    }

    @Test void freshListCacheCanSnapshotAtEachViewportSize() {
        for (int rows : List.of(1, 2, 3)) {
            var model = new FilteredListModel(16, rows);
            var labels = new MachineSearchList.LabelCache(model);
            assertTrue(labels.snapshot(0, ENTRIES));
            model.filter("nit"); // The gallery reapplies the query after building its resized list.
            assertEquals("Nitrogen", labels.get(model.visibleAt(0).id()).getString());
        }
    }
}
