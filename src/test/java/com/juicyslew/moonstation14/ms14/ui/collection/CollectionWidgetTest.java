package com.juicyslew.moonstation14.ms14.ui.collection;

import com.juicyslew.moonstation14.ms14.ui.client.collection.QueueWidget;
import com.juicyslew.moonstation14.ms14.ui.client.collection.SampleViewportWidget;
import com.juicyslew.moonstation14.ms14.ui.client.collection.SlotGridWidget;
import com.juicyslew.moonstation14.ms14.ui.model.BoundedViewportModel;
import com.juicyslew.moonstation14.ms14.ui.model.QueueIntentModel;
import com.juicyslew.moonstation14.ms14.ui.model.SlotGridModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionWidgetTest {
    @Test
    void slotDragRequiresBothOptInsAndNeverChangesSnapshot() {
        SlotGridModel model = new SlotGridModel(4);
        model.snapshot(1, List.of(new SlotGridModel.Slot("a", "Iron", 2), new SlotGridModel.Slot("b", "Glass", 1)));
        SlotGridWidget widget = new SlotGridWidget(0, 0, 56, 28, model);
        List<SlotGridModel.DragIntent> sent = new ArrayList<>();
        assertTrue(widget.mouseClicked(5, 5, 0));
        assertFalse(widget.mouseReleased(33, 5, 0));
        widget.setDragEnabled(true);
        widget.mouseClicked(5, 5, 0);
        widget.mouseReleased(33, 5, 0);
        assertEquals(List.of(), sent);
        model.dragOwner(sent::add);
        widget.mouseClicked(5, 5, 0);
        widget.mouseReleased(33, 5, 0);
        assertEquals(List.of(new SlotGridModel.DragIntent("a", "b", 1)), sent);
        assertEquals("a", model.at(0).id());
        widget.mouseClicked(5, 5, 0);
        model.snapshot(2, List.of(model.at(0), model.at(1)));
        widget.mouseReleased(33, 5, 0);
        assertEquals(1, sent.size());
    }

    @Test
    void queueReorderEmitsOnlyOwnerIntentAndCancelsOnRevisionChange() {
        QueueIntentModel model = new QueueIntentModel(4);
        model.snapshot(1, List.of(new QueueIntentModel.Entry("a", "A"), new QueueIntentModel.Entry("b", "B")),
                List.of(new QueueIntentModel.Entry("p", "Preset")));
        QueueWidget widget = new QueueWidget(0, 0, 100, 60, model);
        List<QueueIntentModel.Intent> sent = new ArrayList<>();
        widget.mouseClicked(5, 25, 0);
        widget.mouseReleased(5, 45, 0);
        assertTrue(sent.isEmpty());
        widget.setIntentOwner(sent::add);
        widget.mouseClicked(5, 25, 0);
        widget.mouseReleased(5, 45, 0);
        assertEquals(List.of(model.moveBefore("a", "b")), sent);
        assertEquals("a", model.queuedAt(0).id());
        widget.mouseClicked(5, 25, 0);
        model.snapshot(2, List.of(model.queuedAt(1), model.queuedAt(0)), List.of(model.presetAt(0)));
        widget.mouseReleased(5, 45, 0);
        assertEquals(1, sent.size());
    }

    @Test
    void viewportPanAndZoomDoNotChangeSamples() {
        BoundedViewportModel model = new BoundedViewportModel(2, 100, 100);
        model.snapshot(1, List.of(new BoundedViewportModel.Point("one", 30, 40)));
        SampleViewportWidget widget = new SampleViewportWidget(0, 0, 100, 100, model, 100, 100);
        assertTrue(widget.mouseScrolled(50, 50, 0, 1));
        assertEquals(1.25, model.zoom());
        widget.mouseClicked(50, 50, 0);
        assertTrue(widget.mouseDragged(60, 50, 0, 10, 0));
        assertEquals(42, model.centerX());
        assertEquals(1, model.size());
        assertEquals(30, model.at(0).x());
    }
}
