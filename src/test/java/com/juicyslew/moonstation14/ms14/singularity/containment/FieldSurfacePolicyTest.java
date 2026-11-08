package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.ms14.singularity.containment.client.FieldFaceRenderer;
import com.juicyslew.moonstation14.ms14.singularity.containment.client.FieldPalette;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldSurfacePolicyTest {
    @Test void paletteKeepsMagentaSubstrateAndUntintedSubtleOverlay() {
        assertTrue(FieldPalette.SUBSTRATE_RED > FieldPalette.SUBSTRATE_GREEN);
        assertTrue(FieldPalette.SUBSTRATE_BLUE > FieldPalette.SUBSTRATE_GREEN);
        assertEquals(1f, FieldPalette.OVERLAY_RED);
        assertEquals(1f, FieldPalette.OVERLAY_GREEN);
        assertEquals(1f, FieldPalette.OVERLAY_BLUE);
        assertTrue(FieldPalette.SUBSTRATE_ALPHA > 0 && FieldPalette.SUBSTRATE_ALPHA <= .18f);
    }

    @Test void everyAxisCellCornerUsesGeneratorCenterCoordinates() {
        for (Direction.Axis axis : Direction.Axis.values()) {
            int a = axis.ordinal(), u = (a + 1) % 3, v = (a + 2) % 3;
            var face = new FieldFaceSnapshotPayload.Face("test", axis, 2, 4, 5, 7, 8);
            var cell = FieldFaceRenderer.cells(face).getFirst();
            double[] origin = cell.origin();
            assertEquals(FieldGeometry.center(2), origin[a]);
            assertEquals(FieldGeometry.center(4), origin[u]);
            assertEquals(FieldGeometry.center(7), origin[v]);
            for (int du : new int[]{0, 1}) for (int dv : new int[]{0, 1}) {
                double[] corner = origin.clone(); corner[u] += du; corner[v] += dv;
                assertEquals(FieldGeometry.center(4 + du), corner[u]);
                assertEquals(FieldGeometry.center(7 + dv), corner[v]);
            }
        }
    }
}
