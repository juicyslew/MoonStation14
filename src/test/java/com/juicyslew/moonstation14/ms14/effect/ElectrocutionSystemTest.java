package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectrocutionSystemTest {
    @AfterEach
    void resetWarningState() {
        ElectrocutionSystem.resetWarnings();
        ElectrocutionSystem.resetWarningSink();
    }

    @Test
    void arithmeticUsesTwoFloatStagesAndTwoTruncations() {
        EffectData.Electrocute effect = effect(2f, 5, true, true, .9f);

        ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect, .5f);

        assertEquals(2, plan.scaledDamage());
        assertEquals(1, plan.effectiveDamage());
        // Combined math would truncate 5 * .5 * .9 = 2.25 to 2.
    }

    @Test
    void floatPrecisionIsPreservedAtEachBoundary() {
        EffectData.Electrocute effect = effect(1f, 10, true, true, .51f);
        float scale = 0.30000004f;

        ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect, scale);

        assertEquals((int) ((float) 10 * scale), plan.scaledDamage());
        assertEquals((int) ((float) plan.scaledDamage() * .51f), plan.effectiveDamage());
    }

    @Test
    void scaleChangesDamageButNeverDuration() {
        EffectData.Electrocute effect = effect(.1f, 3, true, true, 1f);

        ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect, 2f);

        assertEquals(6, plan.effectiveDamage());
        assertEquals(2, plan.stunTicks().orElseThrow());
    }

    @Test
    void coefficientThresholdIsStrictAndCoefficientsAboveOneAmplify() {
        assertTrue(ElectrocutionSystem.plan(effect(1f, 5, true, true, .5f), 1f)
                .stunTicks().isEmpty());
        assertEquals(20, ElectrocutionSystem.plan(effect(1f, 5, true, true, .5001f), 1f)
                .stunTicks().orElseThrow());
        assertEquals(10, ElectrocutionSystem.plan(effect(1f, 5, true, true, 2f), 1f)
                .effectiveDamage());
    }

    @Test
    void zeroInputsAndSecondStageRoundingAreAppliedNoOps() {
        for (EffectData.Electrocute effect : List.of(
                effect(2f, 0, true, true, 1f),
                effect(2f, 5, true, true, 1f),
                effect(2f, 5, true, true, 0f),
                effect(2f, 1, true, true, .1f))) {
            float scale = effect.shockDamage() == 5 ? 0f : 1f;
            ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect, scale);
            assertEquals(EffectResult.APPLIED, plan.result());
            assertEquals(0, plan.effectiveDamage());
            assertTrue(plan.stunTicks().isEmpty());
        }
    }

    @Test
    void zeroTimeDamagesWithoutStunning() {
        ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect(0f, 5, true, true, 1f), 1f);

        assertEquals(5, plan.effectiveDamage());
        assertTrue(plan.stunTicks().isEmpty());
    }

    @Test
    void refreshMapsToUpdateAndNonRefreshMapsToAdd() {
        assertEquals(StatusEffectOperation.UPDATE,
                ElectrocutionSystem.plan(effect(1f, 5, true, true, 1f), 1f).operation());
        assertEquals(StatusEffectOperation.ADD,
                ElectrocutionSystem.plan(effect(1f, 5, false, true, 1f), 1f).operation());
    }

    @Test
    void insulationPolicyRejectsBeforePotentialMutationAndWarnsOnce() {
        List<String> warnings = new ArrayList<>();
        ElectrocutionSystem.setWarningSink(warnings::add);

        EffectData.Electrocute effect = effect(1f, 5, true, false, 1f);
        ElectrocutionSystem.Plan plan = ElectrocutionSystem.plan(effect, 1f);
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, plan.result());
        assertTrue(plan.insulationRejected());

        // The pure plan is intentionally side-effect free; the warning is
        // emitted by the world boundary, once per process policy.
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidDirectValuesFailSafely() {
        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(Float.NaN, 5, true, true, 1f), 1f).result());
        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(1f, 5, true, true, Float.NaN), 1f).result());
        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(1f, 5, true, true, 1f), Float.POSITIVE_INFINITY).result());
        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(1f, Integer.MAX_VALUE, true, true, 2f), 2f).result());
    }

    @Test
    void integerBoundariesRejectExactFloatTwoToThirtyOneAndAcceptGreatestLowerFloat() {
        float twoToThirtyOne = 2147483648f;
        float greatestLower = Math.nextDown(twoToThirtyOne);

        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(0f, 1, true, true, 1f), twoToThirtyOne).result());
        ElectrocutionSystem.Plan acceptedFirstStage = ElectrocutionSystem.plan(
                effect(0f, 1, true, true, 1f), greatestLower);
        assertEquals((int) greatestLower, acceptedFirstStage.scaledDamage());
        assertEquals((int) greatestLower, acceptedFirstStage.effectiveDamage());

        // The first stage is in range, but the second-stage product is exactly
        // float 2^31 and must be rejected independently.
        assertEquals(EffectResult.FAILED,
                ElectrocutionSystem.plan(effect(0f, 1073741824, true, true, 2f), 1f).result());
        float greatestSecondStage = Math.nextDown(2f);
        ElectrocutionSystem.Plan acceptedSecondStage = ElectrocutionSystem.plan(
                effect(0f, 1073741824, true, true, greatestSecondStage), 1f);
        assertEquals((int) ((float) 1073741824 * greatestSecondStage),
                acceptedSecondStage.effectiveDamage());
    }

    private static EffectData.Electrocute effect(float time, int damage, boolean refresh,
                                                  boolean bypass, float coefficient) {
        return new EffectData.Electrocute(EffectCommonData.DEFAULT, time, damage,
                refresh, bypass, coefficient);
    }
}
