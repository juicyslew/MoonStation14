package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.MedicalItemData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyringeServiceTest {
    private static final ResourceKey<ReagentData> A = key("a");
    private static final ResourceKey<ReagentData> B = key("b");
    private final ResourceLocation modeId = ResourceLocation.withDefaultNamespace("syringe_inject_mode");
    private final MedicalItemData.SyringePolicy policy = new MedicalItemData.SyringePolicy(
            15, modeId, List.of(modeId), 5, true, true, .1f, false, true);

    @Test void prototypeRejectsSyringeVariantsUnsupportedByTheBasicHandheldImplementation() {
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.SyringePolicy(
                15, modeId, List.of(modeId), 5, false, true, .1f, false, true));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.SyringePolicy(
                15, modeId, List.of(modeId), 5, true, false, .1f, false, true));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.SyringePolicy(
                15, modeId, List.of(modeId), 5, true, true, .1f, true, true));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.SyringePolicy(
                15, modeId, List.of(modeId), 5, true, true, .1f, false, false));
    }

    @Test void fullAndEmptyTransfersSelectOppositeResolvedModeAndRetainValidDose() {
        var drawId = ResourceLocation.withDefaultNamespace("draw");
        var injectId = ResourceLocation.withDefaultNamespace("inject");
        var modes = Map.of(
                drawId, new InjectorModeData(InjectorModeData.Kind.DRAW, List.of(5f, 10f, 15f), 5, 5, 0, .1f, .5f, .5f),
                injectId, new InjectorModeData(InjectorModeData.Kind.INJECT, List.of(5f, 10f, 15f), 5, 5, 0, .1f, .5f, .5f));
        var both = new MedicalItemData.SyringePolicy(20, drawId, List.of(drawId, injectId), 5, true, true, .1f, false, true);
        assertEquals(new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(injectId, 15),
                SyringeTransferModeTransition.nextAfterTransfer(both, modes,
                        new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(drawId, 15), 2000, 2000));
        assertEquals(new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(drawId, 10),
                SyringeTransferModeTransition.nextAfterTransfer(both, modes,
                        new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(drawId, 10), 500, 2000));
        assertEquals(new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(drawId, 15),
                SyringeTransferModeTransition.nextAfterTransfer(both, modes,
                        new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(injectId, 15), 0, 2000));
        assertEquals(new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(injectId, 10),
                SyringeTransferModeTransition.nextAfterTransfer(both, modes,
                        new com.juicyslew.moonstation14.ms14.medical.SyringeStackData(injectId, 10), 100, 2000));
    }

    @Test void admissionUsesValidSelectedAmountAndClampsToBothRealLimits() {
        var inject = new InjectorModeData(InjectorModeData.Kind.INJECT, List.of(5f, 10f, 15f), 5, 5, 0, .1f, .5f, .5f);
        assertEquals(300, SyringeService.admit(policy, inject, 10f, 800, 300).doseCents());
        assertNull(SyringeService.admit(policy, inject, 7f, 800, 300));
        var draw = new InjectorModeData(InjectorModeData.Kind.DRAW, List.of(5f, 10f, 15f), 5, 5, 0, .1f, .5f, .5f);
        assertEquals(200, SyringeService.admit(policy, draw, 10f, 200, 400).doseCents());
    }

    @Test void productionTransferPlanInjectsExactDoseAndConservesBothStores() {
        var plan = SyringeService.planTransfer(InjectorModeData.Kind.INJECT,
                Map.of(A, 300L, B, 200L), Map.of(A, 100L), 500, 1500, 2000);
        assertEquals(500, plan.doseCents());
        assertEquals(Map.of(A, 300L, B, 200L), plan.dose());
        assertEquals(Map.of(), plan.syringeAfter());
        assertEquals(Map.of(A, 400L, B, 200L), plan.patientAfter());
        assertEquals(600, ReagentUnits.total(plan.syringeAfter().values()) + ReagentUnits.total(plan.patientAfter().values()));
    }

    @Test void productionTransferPlanDrawsProportionalMixtureAndConservesBothStores() {
        var plan = SyringeService.planTransfer(InjectorModeData.Kind.DRAW,
                Map.of(B, 100L), Map.of(A, 300L, B, 200L), 500, 1000, 1000);
        assertEquals(Map.of(A, 300L, B, 200L), plan.dose());
        assertEquals(Map.of(A, 300L, B, 300L), plan.syringeAfter());
        assertEquals(Map.of(), plan.patientAfter());
        assertEquals(600, ReagentUnits.total(plan.syringeAfter().values()) + ReagentUnits.total(plan.patientAfter().values()));
    }

    @Test void completionReclampsToCurrentHeadroomAndZeroHeadroomIsNoOp() {
        var limited = SyringeService.planTransfer(InjectorModeData.Kind.INJECT, Map.of(A, 500L),
                Map.of(B, 100L), 500, 1500, 500);
        assertEquals(400, limited.doseCents());
        assertEquals(500, ReagentUnits.total(limited.patientAfter().values()));
        assertNull(SyringeService.planTransfer(InjectorModeData.Kind.INJECT, Map.of(A, 500L),
                Map.of(B, 100L), 500, 1500, 100));
    }

    @Test void resolvedPhysicalPostureUsesSelfPriorityAndDownedMultiplier() {
        var mode = new InjectorModeData(InjectorModeData.Kind.INJECT, List.of(5f), 5, 5, 0, .1f, .5f, .25f);
        var selfDown = SyringeService.resolvePosture(true, true);
        var down = SyringeService.resolvePosture(false, true);
        assertEquals(2.5, SyringeTiming.seconds(mode, 5, false, selfDown), 1e-9);
        assertEquals(1.25, SyringeTiming.seconds(mode, 5, false, down), 1e-9);
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }
}
