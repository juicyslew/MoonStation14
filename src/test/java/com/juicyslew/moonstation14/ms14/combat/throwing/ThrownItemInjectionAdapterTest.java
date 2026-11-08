package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

class ThrownItemInjectionAdapterTest {
    private static final ResourceKey<ReagentData> A = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "dose_test"));

    @Test void efficiencyKeepsFractionalCentAccountingAndDropsSubcentRemainder() {
        var dose = Map.of(A, 200L);
        assertEquals(Map.of(A, 100L), ThrownItemInjectionAdapter.efficientDose(dose, .5f));
        assertTrue(ThrownItemInjectionAdapter.efficientDose(Map.of(A, 1L), .5f).isEmpty());
        assertEquals(dose, ThrownItemInjectionAdapter.efficientDose(dose, 1f));
    }

    @Test void invalidEfficiencyOrEmptyDoseDeliversNothing() {
        assertTrue(ThrownItemInjectionAdapter.efficientDose(Map.of(), .5f).isEmpty());
        assertTrue(ThrownItemInjectionAdapter.efficientDose(Map.of(A, 100L), Float.NaN).isEmpty());
        assertTrue(ThrownItemInjectionAdapter.efficientDose(Map.of(A, 100L), 0f).isEmpty());
    }

    @Test void handlerRejectsWrongAttachmentWithoutPreparingOrConsumingDose() {
        var item = mock(com.juicyslew.moonstation14.entities.LooseItemEntity.class);
        var target = mock(net.minecraft.world.entity.LivingEntity.class);
        var profile = new ThrownItemProfile(Map.of(), false, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        var event = new ThrownItemEmbeddedEvent(item, target, profile, java.util.UUID.randomUUID());
        ThrownItemInjectionAdapter.onEmbedded(event);
        verifyNoInteractions(item, target);
    }

    @Test void sourceFirstCommitConsumesRemainderBeforeAttemptingTarget() {
        runCommitCase(true, false);
    }

    @Test void rejectedTargetDoesNotRefundCommittedSource() {
        runCommitCase(false, false);
    }

    @Test void failedSourceCommitNeverAttemptsTarget() {
        runCommitCase(true, true);
    }

    @Test void missingWorldTargetRejectsBeforePrepareOrCommit() {
        runTargetInvalidation(false, false);
    }

    @Test void removedTargetRejectsBeforePrepareOrCommit() {
        runTargetInvalidation(true, false);
    }

    @Test void targetInvalidatedDuringPrepareRejectsSourceCommit() {
        runTargetInvalidation(false, true);
    }

    private static void runCommitCase(boolean targetAccepts, boolean commitThrows) {
        runCommitCase(targetAccepts, commitThrows, false, false, false);
    }

    private static void runTargetInvalidation(boolean removed, boolean duringPrepare) {
        runCommitCase(true, false, !removed && !duringPrepare, removed, duringPrepare);
    }

    private static void runCommitCase(boolean targetAccepts, boolean commitThrows, boolean missingTarget,
                                      boolean removedTarget, boolean removeDuringPrepare) {
        var item = mock(com.juicyslew.moonstation14.entities.LooseItemEntity.class);
        var target = mock(net.minecraft.world.entity.LivingEntity.class);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var server = mock(net.minecraft.server.MinecraftServer.class);
        var itemId = java.util.UUID.randomUUID();
        var targetId = java.util.UUID.randomUUID();
         var attachment = new com.juicyslew.moonstation14.entities.ItemAttachment(targetId, 5,
                net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.Vec3(0, 0, 1),
                com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED);
        var source = mock(net.minecraft.world.item.ItemStack.class);
        var expected = mock(net.minecraft.world.item.ItemStack.class);
        var remainder = mock(net.minecraft.world.item.ItemStack.class);
        var state = new AtomicReference<>(source);
        var order = new ArrayList<String>();
        when(item.level()).thenReturn(level); when(target.level()).thenReturn(level);
        when(level.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
         when(item.getUUID()).thenReturn(itemId); when(target.getUUID()).thenReturn(targetId);
         when(target.getId()).thenReturn(5); when(target.isAddedToLevel()).thenReturn(true);
          when(target.isRemoved()).thenReturn(removedTarget);
          when(level.getEntity(itemId)).thenReturn(item); when(level.getEntity(targetId)).thenReturn(missingTarget ? null : target);
         when(item.isAddedToLevel()).thenReturn(true);
        when(item.isRemoved()).thenReturn(false); when(item.attachment()).thenReturn(java.util.Optional.of(attachment));
        when(item.getItem()).thenAnswer(invocation -> state.get());
        when(source.isEmpty()).thenReturn(false); when(source.copy()).thenReturn(expected);
        doAnswer(invocation -> { order.add("source"); if (commitThrows) throw new IllegalStateException(); state.set(invocation.getArgument(0)); return null; }).when(item).setItem(any());
        var configJson = com.google.gson.JsonParser.parseString("{\"inject_on_embed\":{\"solution_name\":\"melee\",\"transfer_cents\":1000}}");
        var profile = ThrownItemProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, configJson).getOrThrow();
        var event = new ThrownItemEmbeddedEvent(item, target, profile, java.util.UUID.randomUUID());
        var prepared = new com.juicyslew.moonstation14.ms14.combat.ChemicalInjectionDose.Prepared(remainder, Map.of(A, 1000L));
        var snapshot = mock(com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.Snapshot.class);
        try (MockedStatic<com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment> worn = mockStatic(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.class);
             MockedStatic<com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess> blood = mockStatic(com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.class);
             MockedStatic<com.juicyslew.moonstation14.ms14.combat.ChemicalInjectionDose> dose = mockStatic(com.juicyslew.moonstation14.ms14.combat.ChemicalInjectionDose.class);
             MockedStatic<net.minecraft.world.item.ItemStack> matches = mockStatic(net.minecraft.world.item.ItemStack.class)) {
            worn.when(() -> com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(target)).thenReturn(java.util.List.of());
            blood.when(() -> com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.inspect(target)).thenReturn(java.util.Optional.of(snapshot));
            blood.when(() -> com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.tryAddWholeReacting(eq(snapshot), eq(Map.of(A, 1000L))))
                    .thenAnswer(invocation -> { order.add("target"); return targetAccepts; });
             dose.when(() -> com.juicyslew.moonstation14.ms14.combat.ChemicalInjectionDose.prepare(expected, level, profile.injectOnEmbed().orElseThrow().policy(), 1)).thenAnswer(invocation -> {
                 if (removeDuringPrepare) when(target.isRemoved()).thenReturn(true);
                 return java.util.Optional.of(prepared);
             });
            matches.when(() -> net.minecraft.world.item.ItemStack.matches(source, expected)).thenReturn(true);
            matches.when(() -> net.minecraft.world.item.ItemStack.matches(remainder, remainder)).thenReturn(true);
            ThrownItemInjectionAdapter.onEmbedded(event);
        }
        if (commitThrows) { assertEquals(java.util.List.of("source"), order); assertSame(source, state.get()); }
        else if (missingTarget || removedTarget || removeDuringPrepare) { assertEquals(java.util.List.of(), order); assertSame(source, state.get()); }
        else { assertEquals(targetAccepts ? java.util.List.of("source", "target") : java.util.List.of("source", "target"), order); assertSame(remainder, state.get()); }
        if (missingTarget || removedTarget) assertTrue(order.isEmpty());
    }
}
