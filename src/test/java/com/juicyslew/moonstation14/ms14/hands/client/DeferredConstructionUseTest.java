package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeferredConstructionUseTest {
    private static final UUID BODY = UUID.randomUUID();
    private static DeferredConstructionUse.Intent intent(String recipe, int required) {
        return new DeferredConstructionUse.Intent(BODY, 4, 12, "left", "steel-token", "moonstation14:steel",
                required, 8, recipe, 90, new BlockPos(2, 3, 4), Direction.UP);
    }
    private static DeferredConstructionUse.Current fresh(UUID body, String recipe, String token, String item, int count, long revision) {
        return new DeferredConstructionUse.Current(body, 4, 13, "left", token, item, count,
                revision, recipe, 90, new BlockPos(2, 3, 4), Direction.UP, true, true);
    }

    @Test void captureAcrossRefreshReplaysAtCapturedSecondTargetOnceAndAllowsReservedCountDrift() {
        var slot = new DeferredConstructionUse();
        var girder = intent("moonstation14:steel_wall", 2);
        assertTrue(slot.capture(girder)); // refresh barrier: this is the one retained second click at target B
        assertTrue(slot.pending());
        assertNull(slot.takeForReplay(fresh(BODY, "moonstation14:steel_wall", "steel-token", "moonstation14:steel", 1, 8)));
        assertFalse(slot.pending());

        assertTrue(slot.capture(girder));
        var replay = slot.takeForReplay(fresh(BODY, "moonstation14:steel_wall", "steel-token", "moonstation14:steel", 2, 8));
        assertNotNull(replay);
        assertEquals(new BlockPos(2, 3, 4), replay.target());
        assertNull(slot.takeForReplay(fresh(BODY, "moonstation14:steel_wall", "steel-token", "moonstation14:steel", 2, 10)));
        assertFalse(slot.pending());
    }

    @Test void girderAndPipeUseSameOwnerAndIdentityMaterialGuardsDiscardInvalidIntent() {
        for (var selected : new DeferredConstructionUse.Intent[] {
                intent("moonstation14:steel_wall", 2), intent("moonstation14:disposal_straight", 2) }) {
            var slot = new DeferredConstructionUse();
            assertTrue(slot.capture(selected));
            assertNull(slot.takeForReplay(fresh(UUID.randomUUID(), selected.recipe(), "steel-token", "moonstation14:steel", 2, 9)));
            assertTrue(slot.capture(selected));
            var changedPlan = fresh(BODY, selected.recipe(), "steel-token", "moonstation14:steel", 2, 10);
            assertNull(slot.takeForReplay(changedPlan));
            assertTrue(slot.capture(selected));
            var changedRecipe = fresh(BODY, "moonstation14:other_recipe", "steel-token", "moonstation14:steel", 2, 9);
            assertNull(slot.takeForReplay(changedRecipe));
            assertTrue(slot.capture(selected));
            assertNull(slot.takeForReplay(fresh(BODY, selected.recipe(), "changed-token", "moonstation14:steel", 2, 9)));
            assertTrue(slot.capture(selected));
            assertNull(slot.takeForReplay(fresh(BODY, selected.recipe(), "steel-token", "minecraft:iron_ingot", 2, 9)));
        }
    }

    @Test void onlyOnePendingClickIsRetainedAndContextDismissalClearsIt() {
        var slot = new DeferredConstructionUse();
        var first = intent("moonstation14:steel_wall", 2);
        var later = new DeferredConstructionUse.Intent(BODY, 4, 12, "left", "steel-token", "moonstation14:steel",
                2, 8, first.recipe(), 90, new BlockPos(9, 9, 9), Direction.NORTH);
        assertTrue(slot.capture(first));
        assertTrue(slot.capture(later));
        assertEquals(first.target(), slot.intent().target());
        slot.clear();
        assertFalse(slot.pending());
    }
}
