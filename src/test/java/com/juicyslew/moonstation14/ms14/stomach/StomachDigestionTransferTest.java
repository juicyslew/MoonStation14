package com.juicyslew.moonstation14.ms14.stomach;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StomachDigestionTransferTest {
    private static final ResourceKey<ReagentData> A = key("transfer_a");
    private static final ResourceKey<ReagentData> DIGESTED = key("transfer_digested");
    private static final ResourceKey<ReagentData> BLOODSTREAM = key("transfer_bloodstream");
    private static final ResourceKey<ReagentData> RESPIRATION = key("transfer_respiration");
    private static final ResourceKey<ReagentData> MISSING = key("transfer_missing");

    @Test
    void transfersQuarterAtHalfEfficacyAndSkipsDigestionOrMissingPrototype() {
        ReagentAttachment stomach = new ReagentAttachment(Map.of(A, 1f, DIGESTED, 1f,
                BLOODSTREAM, 1f, RESPIRATION, 1f, MISSING, 1f));
        ReagentAttachment body = new ReagentAttachment();

        StomachDigestionTransfer.transfer(stomach, body, Map.of(
                A.location(), prototype("transfer_a"),
                DIGESTED.location(), prototype("transfer_digested", MetabolismStage.DIGESTION),
                BLOODSTREAM.location(), prototype("transfer_bloodstream", MetabolismStage.BLOODSTREAM),
                RESPIRATION.location(), prototype("transfer_respiration", MetabolismStage.RESPIRATION)), 1000f);

        assertEquals(.75f, stomach.getMap().get(A), 0f);
        assertEquals(.12f, body.getMap().get(A), 0f);
        assertEquals(1f, stomach.getMap().get(DIGESTED), 0f);
        assertEquals(.75f, stomach.getMap().get(BLOODSTREAM), 0f);
        assertEquals(.75f, stomach.getMap().get(RESPIRATION), 0f);
        assertEquals(.12f, body.getMap().get(BLOODSTREAM), 0f);
        assertEquals(.12f, body.getMap().get(RESPIRATION), 0f);
        assertEquals(1f, stomach.getMap().get(MISSING), 0f);
        assertEquals(3, body.getMap().size());
    }

    @Test
    void fullBodyRetainsSourceAndNearFullBodyTransfersOnlyAdmittedProportion() {
        ReagentAttachment fullStomach = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment fullBody = new ReagentAttachment(Map.of(DIGESTED, 1000f));
        StomachDigestionTransfer.transfer(fullStomach, fullBody, Map.of(A.location(), prototype("transfer_a")), 1000f);
        assertEquals(1f, fullStomach.getMap().get(A), 0f);

        ReagentAttachment nearStomach = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment nearBody = new ReagentAttachment(Map.of(DIGESTED, 999.9f));
        StomachDigestionTransfer.transfer(nearStomach, nearBody, Map.of(A.location(), prototype("transfer_a")), 1000f);
        assertEquals(79L, nearStomach.snapshotUnits().get(A));
        assertEquals(10L, nearBody.snapshotUnits().get(A));
        assertEquals(100_000L, nearBody.totalUnits());
    }

    @Test
    void centsPrecisionHandlesZeroProductPartialCapacityAndMixedInputs() {
        ReagentAttachment oneCent = new ReagentAttachment(Map.of(A, .01f));
        ReagentAttachment emptyBody = new ReagentAttachment();
        StomachDigestionTransfer.transfer(oneCent, emptyBody, Map.of(A.location(), prototype("transfer_a")), 1000f);
        assertEquals(1L, oneCent.snapshotUnits().get(A), "zero-product source remains as wash/flush residue");
        assertTrue(emptyBody.isEmpty());

        ReagentAttachment limitedStomach = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment limitedBody = new ReagentAttachment(Map.of(DIGESTED, 999.99f));
        StomachDigestionTransfer.transfer(limitedStomach, limitedBody,
                Map.of(A.location(), prototype("transfer_a")), 1000f);
        assertEquals(97L, limitedStomach.snapshotUnits().get(A), "free 1c admits product 1c from maximum 3c source");
        assertEquals(1L, limitedBody.snapshotUnits().get(A));
        assertEquals(100_000L, limitedBody.totalUnits());

        ReagentAttachment mixedStomach = new ReagentAttachment(Map.of(A, .25f, RESPIRATION, .01f));
        ReagentAttachment mixedBody = new ReagentAttachment();
        StomachDigestionTransfer.transfer(mixedStomach, mixedBody, Map.of(
                A.location(), prototype("transfer_a"), RESPIRATION.location(), prototype("transfer_respiration")), 1000f);
        assertFalse(mixedStomach.snapshotUnits().containsKey(A));
        assertEquals(1L, mixedStomach.snapshotUnits().get(RESPIRATION));
        assertEquals(12L, mixedBody.snapshotUnits().get(A));
        assertFalse(mixedBody.snapshotUnits().containsKey(RESPIRATION));
        assertEquals(25L, ReagentUnits.fromFloat(.25f));
    }

    @Test
    void exactStomachCapacityCentsSurviveTransfer() {
        ReagentAttachment stomach = new ReagentAttachment(Map.of(A, 49.99f));
        StomachDigestionTransfer.transfer(stomach, new ReagentAttachment(),
                Map.of(A.location(), prototype("transfer_a")), 1000f);
        assertEquals(4_974L, stomach.snapshotUnits().get(A));
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    private static ReagentData prototype(String id, Object... stages) {
        java.util.HashMap<MetabolismStage, MetabolismData> metabolism = new java.util.HashMap<>();
        for (int i = 0; i < stages.length; i += 2) {
            metabolism.put((MetabolismStage) stages[i], (MetabolismData) stages[i + 1]);
        }
        return new ReagentData(id, id, "test", "", "", "", 0, Map.of(), 400f, 200f,
                ContrabandSeverityEnum.NONE, List.of(), metabolism, new MetamorphicSpriteData(null, null),
                0f, "fill-", false, 0f);
    }

    private static ReagentData prototype(String id, MetabolismStage stage) {
        return prototype(id, stage, new MetabolismData(List.of(), Map.of(), 1f));
    }
}
