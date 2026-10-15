package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceKey;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeldLiquidTransferPlanTest {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> SUGAR = ModReagents.createKey("sugar");

    @Test void recipientLimitRejectsWithoutChangingEitherAttachment() {
        var source = new ReagentAttachment(Map.of(WATER, 5f));
        var recipient = new ReagentAttachment(Map.of(SUGAR, 10f));
        var beforeSource = source.snapshotUnits();
        var beforeRecipient = recipient.snapshotUnits();

        var plan = HeldLiquidTransferPlan.plan(source, recipient, 1_000, 10f, 500, null);

        assertEquals(0, plan.admittedCents());
        assertEquals(beforeSource, source.snapshotUnits());
        assertEquals(beforeRecipient, recipient.snapshotUnits());
        assertEquals(beforeSource, plan.sourceAfter().snapshotUnits());
        assertEquals(beforeRecipient, plan.recipientAfter().snapshotUnits());
    }
}
