package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentPlaceholderGeometryTest {
    @Test void allSupportedGarmentsHaveTheirExpectedArticulatedPlanAnchors() {
        assertEquals(5, EquipmentRenderPlan.anchors("jumpsuit", EquipmentItemData.AppearanceRegion.TORSO).size());
        assertEquals(3, EquipmentRenderPlan.anchors("outerClothing", EquipmentItemData.AppearanceRegion.TORSO).size());
        for (String slot : new String[]{"head", "eyes", "mask", "ears", "neck", "gloves", "shoes", "back", "belt", "id", "suitstorage"})
            assertFalse(EquipmentRenderPlan.anchors(slot, switch (slot) {
                case "head" -> EquipmentItemData.AppearanceRegion.HEAD;
                case "eyes" -> EquipmentItemData.AppearanceRegion.EYES;
                case "mask" -> EquipmentItemData.AppearanceRegion.FACE;
                case "ears" -> EquipmentItemData.AppearanceRegion.EARS;
                case "neck" -> EquipmentItemData.AppearanceRegion.NECK;
                case "gloves" -> EquipmentItemData.AppearanceRegion.HANDS;
                case "shoes" -> EquipmentItemData.AppearanceRegion.FEET;
                default -> EquipmentItemData.AppearanceRegion.TORSO;
            }).isEmpty());
        assertTrue(EquipmentRenderPlan.anchors("pocket1", EquipmentItemData.AppearanceRegion.TORSO).isEmpty());
    }

    @Test void regionControlsGarmentGeometryAndPackedTintRemainsResolvedPlanData() {
        assertTrue(EquipmentPlaceholderGeometry.regionFits("mask", EquipmentItemData.AppearanceRegion.FACE));
        assertFalse(EquipmentPlaceholderGeometry.regionFits("mask", EquipmentItemData.AppearanceRegion.TORSO));
        var garment = new EquipmentRenderPlan.Garment("head", net.minecraft.resources.ResourceLocation.parse("moonstation14:cap"),
                EquipmentItemData.AppearanceRegion.HEAD, 0x7FAABBCC, EquipmentRenderPlan.anchors("head", EquipmentItemData.AppearanceRegion.HEAD), 40);
        assertEquals(0x7FAABBCC, garment.color());
    }

    @Test void slimArmsAreNarrowerAndBakedPiecesAreReused() {
        assertEquals(3, EquipmentPlaceholderGeometry.armWidth(true));
        assertEquals(4, EquipmentPlaceholderGeometry.armWidth(false));
        assertEquals(-1, EquipmentPlaceholderGeometry.armMinX(false, EquipmentRenderPlan.Anchor.LEFT_ARM));
        assertEquals(-3, EquipmentPlaceholderGeometry.armMinX(false, EquipmentRenderPlan.Anchor.RIGHT_ARM));
        assertEquals(-1, EquipmentPlaceholderGeometry.armMinX(true, EquipmentRenderPlan.Anchor.LEFT_ARM));
        assertEquals(-2, EquipmentPlaceholderGeometry.armMinX(true, EquipmentRenderPlan.Anchor.RIGHT_ARM));
        int before = EquipmentPlaceholderGeometry.cachedPieceCount();
        var first = EquipmentPlaceholderGeometry.cachedPieceForTest("jumpsuit", EquipmentRenderPlan.Anchor.LEFT_ARM,
                true, EquipmentItemData.AppearanceRegion.TORSO);
        int afterBake = EquipmentPlaceholderGeometry.cachedPieceCount();
        var second = EquipmentPlaceholderGeometry.cachedPieceForTest("jumpsuit", EquipmentRenderPlan.Anchor.LEFT_ARM,
                true, EquipmentItemData.AppearanceRegion.TORSO);
        assertSame(first, second);
        assertEquals(afterBake, EquipmentPlaceholderGeometry.cachedPieceCount());
        assertEquals(before + 1, afterBake);
    }

    @Test void pantsEndInsideBootCuffAndShoesExtendPastVanillaLegBottom() {
        // Both leg anchors use this same local PlayerModel leg geometry (4x12x4).
        for (var anchor : new EquipmentRenderPlan.Anchor[]{EquipmentRenderPlan.Anchor.LEFT_LEG,
                EquipmentRenderPlan.Anchor.RIGHT_LEG}) {
            var pants = EquipmentPlaceholderGeometry.cachedPieceForTest("jumpsuit", anchor, false,
                    EquipmentItemData.AppearanceRegion.TORSO);
            var shoe = EquipmentPlaceholderGeometry.cachedPieceForTest("shoes", anchor, false,
                    EquipmentItemData.AppearanceRegion.FEET);
            assertNotNull(pants);
            assertNotNull(shoe);
        }
        assertEquals(9.5F, EquipmentPlaceholderGeometry.PANTS_BOTTOM);
        assertTrue(EquipmentPlaceholderGeometry.PANTS_BOTTOM < 12F);
        assertTrue(EquipmentPlaceholderGeometry.SHOES_BOTTOM > 12F);
        assertTrue(EquipmentPlaceholderGeometry.PANTS_BOTTOM > 9F); // shoe cuff begins at local y=9
    }
}
