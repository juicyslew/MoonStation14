package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalConstructionClientPolicyTest {
    @Test void readinessUsesResolvedRequiredCountAndMaterial() {
        int recipeCount = 5;
        assertEquals(DisposalConstructionClient.Readiness.WRONG_MATERIAL,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, ItemStack.EMPTY).state());
        assertEquals(DisposalConstructionClient.Readiness.WRONG_MATERIAL,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 64)).state());
        assertEquals(DisposalConstructionClient.Readiness.INSUFFICIENT,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(ModItems.STEEL.get(), 4)).state());
        var ready = DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(ModItems.STEEL.get(), 5));
        assertEquals(DisposalConstructionClient.Readiness.READY, ready.state());
        assertEquals(5, ready.required());
    }

    @Test void paletteWrapsAcrossSupportedShapes() {
        int size = DisposalConstructionClient.supportedShapes().size();
        assertEquals(size - 1, DisposalConstructionClient.wrapShapeIndex(-1));
        assertEquals(0, DisposalConstructionClient.wrapShapeIndex(size));
        assertTrue(DisposalConstructionClient.supportedShapes().contains("disposal_pipe"));
    }

    @Test void stateCorrelationRejectsWrongBodyEpochAndOlderPackets() {
        UUID body = UUID.randomUUID();
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body, 4, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 3, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(UUID.randomUUID(), 4, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 4, 8, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 4, 9, 1, body, 4, 9, 8, 2));
    }
}
