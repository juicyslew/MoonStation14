package com.juicyslew.moonstation14.ms14.ui.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MachineInteractionModelsTest {
    @Test void filteredNavigationKeepsStableIdsAndBoundedVisibleRows() {
        var list = new FilteredListModel(3, 2);
        var a = new FilteredListModel.Entry("a", "Alpha");
        var b = new FilteredListModel.Entry("b", "Beta");
        var c = new FilteredListModel.Entry("c", "Gamma");
        list.snapshot(1, List.of(a, b, c));
        assertTrue(list.select("b"));
        list.snapshot(1, List.of(c)); // stale
        assertEquals(3, list.matchCount());
        list.snapshot(2, List.of(c, b, a));
        assertEquals("b", list.selectedId());
        list.filter("a");
        assertEquals("b", list.selectedId());
        assertEquals(2, list.visibleCount());
        list.move(1);
        assertEquals("a", list.selectedId());
        list.scroll(Integer.MAX_VALUE);
        assertEquals(1, list.first());
        assertEquals("a", list.visibleAt(1).id());
        list.filter("zzz");
        assertNull(list.selectedId());
        assertEquals(0, list.visibleCount());
        assertThrows(IllegalArgumentException.class, () -> list.snapshot(3, List.of(a, a)));
        assertThrows(IllegalArgumentException.class, () -> list.snapshot(3, List.of(a, b, c, a)));
        assertThrows(IllegalArgumentException.class, () -> new FilteredListModel(513, 1));
    }

    @Test void tabsSelectLocallyAndFallbackWhenRemoved() {
        var tabs = new OptionModel(2);
        var one = new OptionModel.Option("one", "One");
        var two = new OptionModel.Option("two", "Two");
        tabs.snapshot(1, List.of(one, two));
        assertEquals("one", tabs.selectedId());
        tabs.step(1);
        assertEquals("two", tabs.selectedId());
        assertFalse(tabs.select("missing"));
        tabs.snapshot(2, List.of(one));
        assertEquals("one", tabs.selectedId());
        assertThrows(IllegalArgumentException.class, () -> tabs.snapshot(3, List.of(one, two, one)));
    }

    @Test void numericDraftValidatesAndReconcilesOnlyMatchingAcknowledgements() {
        var field = DraftFieldModel.number(6, BigDecimal.ZERO, BigDecimal.TEN);
        field.snapshot(1, "2");
        field.edit("NaN");
        assertEquals(DraftFieldModel.State.INVALID, field.state());
        assertNull(field.submit());
        field.edit("11");
        assertFalse(field.valid());
        field.edit("4");
        var first = field.submit();
        assertEquals(1, first.baseRevision());
        assertEquals(DraftFieldModel.State.PENDING, field.state());
        field.edit("5"); // no second in-flight edit
        assertEquals("4", field.draft());
        field.snapshot(2, "3"); // unrelated newer snapshot is not an acknowledgement
        assertEquals("4", field.draft());
        assertFalse(field.resolve(first.requestId() + 1, 2, "3", true));
        assertFalse(field.resolve(first.requestId(), 2, "other", true));
        assertTrue(field.resolve(first.requestId(), 2, "3", false));
        assertEquals(DraftFieldModel.State.REJECTED, field.state());
        assertEquals("3", field.draft());
        field.edit("6");
        var second = field.submit();
        assertTrue(field.resolve(second.requestId(), 3, "5", true));
        assertEquals("5", field.authority()); // server may clamp; never assume submitted value
        assertEquals(DraftFieldModel.State.CLEAN, field.state());
        field.snapshot(2, "0");
        assertEquals("5", field.draft());
        field.edit("9");
        field.discard();
        assertEquals("5", field.draft());
    }

    @Test void textDraftIsBoundedAndKeepsUnsavedLocalEditsAcrossSnapshots() {
        var text = DraftFieldModel.text(3);
        text.snapshot(1, "abc");
        text.edit("long text");
        assertEquals("lon", text.draft());
        text.snapshot(2, "xyz");
        assertEquals("lon", text.draft());
        assertThrows(IllegalArgumentException.class, () -> text.snapshot(3, "long"));
    }

    @Test void readOnlySlotsRequireExplicitOwnerAndNeverMutateSnapshot() {
        var grid = new SlotGridModel(2);
        grid.snapshot(1, List.of(new SlotGridModel.Slot("in", "Iron", 2),
                new SlotGridModel.Slot("out", "Output", 0)));
        var intents = new ArrayList<SlotGridModel.DragIntent>();
        assertFalse(grid.drag("in", "out"));
        grid.dragOwner(intents::add);
        assertFalse(grid.drag("in", "unknown"));
        assertFalse(grid.drag("in", "in"));
        assertTrue(grid.drag("in", "out"));
        assertEquals(new SlotGridModel.DragIntent("in", "out", 1), intents.getFirst());
        assertEquals(2, grid.at(0).count());
        grid.dragOwner(null);
        assertFalse(grid.drag("in", "out"));
        assertThrows(IllegalArgumentException.class, () -> grid.snapshot(2,
                List.of(new SlotGridModel.Slot("x", "", 0), new SlotGridModel.Slot("x", "", 0))));
    }

    @Test void queueCommandsAreValidatedIntentsNotSpeculativeOrder() {
        var queue = new QueueIntentModel(2);
        var a = new QueueIntentModel.Entry("a", "First");
        var b = new QueueIntentModel.Entry("b", "Second");
        var preset = new QueueIntentModel.Entry("p", "Recipe");
        assertNull(queue.enqueue("p"));
        queue.snapshot(4, List.of(a, b), List.of(preset));
        assertEquals(new QueueIntentModel.Intent(QueueIntentModel.Action.MOVE_BEFORE, "b", "a", 4),
                queue.moveBefore("b", "a"));
        assertNull(queue.moveBefore("b", "b"));
        assertNull(queue.remove("missing"));
        assertEquals(QueueIntentModel.Action.ENQUEUE, queue.enqueue("p").action());
        assertEquals(QueueIntentModel.Action.APPLY_PRESET, queue.applyPreset("p").action());
        assertEquals("a", queue.queuedAt(0).id());
        queue.snapshot(3, List.of(b), List.of());
        assertEquals(2, queue.queueSize());
        queue.snapshot(5, List.of(b, a), List.of(preset));
        assertEquals("b", queue.queuedAt(0).id());
    }

    @Test void viewportClampsControlsAndRejectsUnboundedOrNonfiniteData() {
        var view = new BoundedViewportModel(2, 100, 60);
        var origin = new BoundedViewportModel.Point("origin", 0, 0);
        var far = new BoundedViewportModel.Point("far", 100, 60);
        view.snapshot(1, List.of(origin, far));
        view.view(1000, -50, 100);
        assertEquals(100, view.centerX());
        assertEquals(0, view.centerY());
        assertEquals(16, view.zoom());
        assertFalse(view.visible(origin));
        view.view(Double.NaN, 0, 1);
        assertEquals(16, view.zoom());
        assertThrows(IllegalArgumentException.class, () -> new BoundedViewportModel.Point("bad", Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> view.snapshot(2,
                List.of(new BoundedViewportModel.Point("outside", 101, 0))));
        assertEquals(1, view.revision());
        assertThrows(IllegalArgumentException.class, () -> view.snapshot(2, List.of(origin, far, origin)));
    }
}
