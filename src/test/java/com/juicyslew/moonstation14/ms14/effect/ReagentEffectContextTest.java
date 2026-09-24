package com.juicyslew.moonstation14.ms14.effect;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentEffectContextTest {
    private static final ResourceKey<ReagentData> REAGENT = ModReagents.createKey("context-reagent");

    @Test
    void carriesTypedRoutingDataAndLeavesOrganUnavailable() {
        IReagentTrait trait = () -> 100f;
        TraitHandler<IReagentTrait> source = new TraitHandler<>(new Object(), trait);
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 2f));
        ReagentData prototype = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"context-reagent\"}")).getOrThrow();
        MetabolismSystem.SourceSnapshot snapshot = new MetabolismSystem.SourceSnapshot(
                REAGENT, prototype, 2f, Map.of(REAGENT, 2f));

        ReagentEffectContext context = new ReagentEffectContext(source, solution, REAGENT,
                MetabolismStage.BLOODSTREAM, MetabolizerProfile.SHARED_LIVING, snapshot, 0.5f,
                new ReagentEffectTransactionState(REAGENT, 0.5f),
                Optional.empty());

        assertSame(source, context.source());
        assertSame(solution, context.solution());
        assertSame(solution, EffectHandlers.transactionSolution(context));
        assertEquals(REAGENT, context.reagent());
        assertEquals(MetabolismStage.BLOODSTREAM, context.stage());
        assertSame(MetabolizerProfile.SHARED_LIVING, context.profile());
        assertSame(snapshot, context.sourceSnapshot());
        assertEquals(0.5f, context.ordinaryRemoved());
        assertEquals(0.5f, context.transactionState().remainingReservation());
        assertTrue(context.organ().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> context.sourceSnapshot().quantities().put(REAGENT, 9f));
    }

    @Test
    void metabolismConditionViewRetainsExactlyConsumedPreRemovalQuantity() {
        MetabolismSystem.SourceSnapshot snapshot = new MetabolismSystem.SourceSnapshot(
                REAGENT, prototype(), .5f, Map.of(REAGENT, .5f));

        ConditionContext context = TickHooks.metabolismConditionContext(
                snapshot, Map.of(), Map.of());

        assertEquals(.5f, context.sourceReagentQuantities().orElseThrow().get(REAGENT));
    }

    @Test
    void metabolismConditionViewIsFreshAfterLiveMutation() {
        MetabolismSystem.SourceSnapshot snapshot = new MetabolismSystem.SourceSnapshot(
                REAGENT, prototype(), .5f, Map.of(REAGENT, .5f));
        ReagentAttachment live = new ReagentAttachment();

        ConditionContext first = TickHooks.metabolismConditionContext(snapshot, Map.of(), live.getMap());
        live.specificAdd(REAGENT, .25f, 200_000f);
        ConditionContext second = TickHooks.metabolismConditionContext(snapshot, Map.of(), live.getMap());

        assertEquals(.5f, first.sourceReagentQuantities().orElseThrow().get(REAGENT));
        assertEquals(.75f, second.sourceReagentQuantities().orElseThrow().get(REAGENT));
    }

    @Test
    void laterConditionViewSeesReservedQuantityAfterItIsConsumedByAnAdjustment() {
        ReagentAttachment live = new ReagentAttachment();
        ReagentData prototype = prototype();
        MetabolismSystem.SourceSnapshot snapshot = new MetabolismSystem.SourceSnapshot(
                REAGENT, prototype, 1f, Map.of(REAGENT, 1f));
        ReagentEffectTransactionState state = new ReagentEffectTransactionState(REAGENT, 1f);
        IReagentTrait trait = () -> 10f;
        ReagentEffectContext effectContext = new ReagentEffectContext(
                new TraitHandler<>(new Object(), trait), live, REAGENT,
                MetabolismStage.BLOODSTREAM, MetabolizerProfile.SHARED_LIVING, snapshot, 1f,
                state, Optional.empty());

        assertEquals(EffectResult.APPLIED, EffectHandlers.adjustReagent(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -1f),
                1f, Optional.of(effectContext)));
        ConditionContext later = com.juicyslew.moonstation14.eventhooks.TickHooks.metabolismConditionContext(
                snapshot, Map.of(), live.getMap(), state);

        assertEquals(0f, later.sourceReagentQuantities().orElseThrow().get(REAGENT));
        assertEquals(0f, state.remainingReservation());
    }

    private static ReagentData prototype() {
        return ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"context-reagent\"}")).getOrThrow();
    }
}
