package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterProcessingPolicyTest {
    private static ResourceKey<ReagentData> key() {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "policy_test"));
    }

    @Test void allowsBufferDiscardWithNoInputButRejectsOtherInputlessCommands() {
        var reagent = key();
        var buffer = new ChemMasterBuffer(new ChemMasterBuffer.Snapshot(
                new ReagentAttachment(Map.of(reagent, 2f)).toComponent(), java.util.List.of(reagent)));
        assertTrue(ChemMasterProcessingPolicy.discardBufferWithoutInput(false,
                ChemMasterControlRequest.Operation.BUFFER_REAGENT, ChemMasterControlRequest.Mode.DISCARD,
                buffer, reagent, ChemMasterBuffer.Amount.ONE));
        assertEquals(100, buffer.quantities().get(reagent));
        assertFalse(ChemMasterProcessingPolicy.discardBufferWithoutInput(false,
                ChemMasterControlRequest.Operation.BUFFER_REAGENT, ChemMasterControlRequest.Mode.TRANSFER,
                buffer, reagent, ChemMasterBuffer.Amount.ONE));
        assertFalse(ChemMasterProcessingPolicy.discardBufferWithoutInput(true,
                ChemMasterControlRequest.Operation.BUFFER_REAGENT, ChemMasterControlRequest.Mode.DISCARD,
                buffer, reagent, ChemMasterBuffer.Amount.ONE));
    }

    @Test void fullRecipientDoesNotTriggerReaction() {
        var reagent = key();
        var source = new ReagentAttachment(Map.of(reagent, 1f));
        var fullRecipient = new ReagentAttachment(Map.of(reagent, 5f));
        var buffer = new ChemMasterBuffer();
        buffer.extractFrom(source, reagent, ChemMasterBuffer.Amount.ALL);
        assertEquals(0, buffer.returnTo(fullRecipient, reagent, ChemMasterBuffer.Amount.ALL, 500));
        assertFalse(ChemMasterProcessingPolicy.shouldReactOnReturn(0));
        assertTrue(ChemMasterProcessingPolicy.shouldReactOnReturn(1));
    }

    @Test void bodyUseOpenRouteRequiresEmptyHandAndIsNotBlockedByHeldItems() {
        assertTrue(ChemMasterSessionService.opensWorkspace(
                com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE, true, false));
        assertTrue(ChemMasterSessionService.opensWorkspace(
                com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.ACTIVATE_WORLD, true, false));
        assertFalse(ChemMasterSessionService.opensWorkspace(
                com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE, false, false));
        assertFalse(ChemMasterSessionService.opensWorkspace(
                com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE, true, true));
    }
}
