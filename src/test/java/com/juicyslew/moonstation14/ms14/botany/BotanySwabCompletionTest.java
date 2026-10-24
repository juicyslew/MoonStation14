package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BotanySwabCompletionTest {
    private static GeneticValue value(String name, float nutrient) {
        var id = ResourceLocation.fromNamespaceAndPath("test", name);
        var biology = new SeedProfile.Biology(10, nutrient, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        var life = new SeedProfile.Lifecycle(3, 20, 21, 22, 7, 24,
                SeedProfile.Harvest.SELF_HARVEST, false, true);
        var profile = new SeedProfile(id, id, name, biology, life, Map.of(), Map.of(), Map.of(), List.of(), SeedProfile.Scream.DISABLED, "test-"+name+"-name");
        return new GeneticValue(id, profile);
    }

    @Test void normalSwabDelayUsesSharedFortyTickTimedActionDuration() {
        assertEquals(40, BotanySwabHandUseService.durationTicks(new com.juicyslew.moonstation14.ms14.tools.ToolProfile.BotanySwab(2).delaySeconds()));
    }

    @Test void crossPlanReadsCurrentDonorAndRecipientValuesAndSwapsCurrentRecipientReference() {
        var donor = new PlantSeedReference(value("donor", 1), true);
        var recipient = new PlantSeedReference(value("recipient", 2), true);
        donor.update(value("donor", 42));
        recipient.update(value("recipient", 84));
        var plan = BotanySwabHandUseService.prepareCurrent(donor, recipient, () -> .9);
        assertEquals(42, plan.trayAfter().current().profile().biology().nutrientConsumption());
        assertEquals(recipient, plan.swabAfter());
        assertNotSame(recipient, plan.trayAfter());
        assertTrue(plan.trayAfter().unique());
    }

    @Test void captureFromEmptyTrayKeepsNullSampleAndEmptyUsedSwabDoesNotCrossWithoutRecipient() {
        var emptyCapture = BotanySwabHandUseService.prepareCurrent(null, null, () -> .9);
        assertEquals(BotanySwabHandUseService.PlanKind.CAPTURE, emptyCapture.kind());
        assertNull(emptyCapture.swabAfter());
        var noRecipient = BotanySwabHandUseService.prepareCurrent(
                new PlantSeedReference(value("sample", 1), true), null, () -> .9);
        assertEquals(BotanySwabHandUseService.PlanKind.NOOP, noRecipient.kind());
    }

    @Test void deniedHeldCommitAndFailedPreflightNeverMutateTray() {
        AtomicInteger tray = new AtomicInteger();
        var denied = BotanySwabHandUseService.executeCross(() -> true,
                () -> BodyHeldItemMutation.Result.DENIED, tray::incrementAndGet);
        assertEquals(BodyHeldItemMutation.Result.DENIED, denied);
        var staleTarget = BotanySwabHandUseService.executeCross(() -> false,
                () -> BodyHeldItemMutation.Result.SUCCESS, tray::incrementAndGet);
        assertEquals(BodyHeldItemMutation.Result.DENIED, staleTarget);
        assertEquals(0, tray.get());
    }

    @Test void trayPublicationFailureAfterHeldCommitIsExplicitRecoveryAndNotNormalStop() {
        AtomicBoolean heldCommitted = new AtomicBoolean();
        var result = BotanySwabHandUseService.executeCross(() -> true, () -> {
            heldCommitted.set(true);
            return BodyHeldItemMutation.Result.SUCCESS;
        }, () -> { throw new IllegalStateException("injected target publication failure"); });
        assertTrue(heldCommitted.get());
        assertEquals(BodyHeldItemMutation.Result.RECOVERY_REQUIRED, result);
    }
}
