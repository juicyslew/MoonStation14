package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.item.custom.StationFloorTileItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTilePryItem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BodyConstructionItemUseTest {
    @Test
    void floorItemsAreRecognizedByBodyConstructionDispatcher() {
        assertTrue(BodyFloorToolUse.supportsType(StationFloorTileItem.class));
        assertTrue(BodyFloorToolUse.supportsType(StationFloorTilePryItem.class));
        assertFalse(BodyFloorToolUse.supportsType(Item.class));
    }

    @Test
    void nullExpectedTokenIsRejectedWithoutDereference() {
        assertFalse(BodyWorldItemActions.expectedTokenMatches(null, Optional.of("present")));
        assertFalse(BodyWorldItemActions.expectedTokenMatches("stale", Optional.of("present")));
        assertTrue(BodyWorldItemActions.expectedTokenMatches("present", Optional.of("present")));
        assertFalse(BodyWorldItemActions.expectedTokenMatches("present", Optional.empty()));
    }

    @Test
    void floorCompensationOnlyClaimsRestorationForOriginalOrVerifiedRollback() {
        assertTrue(BodyFloorToolUse.compensationRestored(true, false, false, false, true));
        assertTrue(BodyFloorToolUse.compensationRestored(false, true, true, true, true));
        assertFalse(BodyFloorToolUse.compensationRestored(false, false, false, false, false));
        assertFalse(BodyFloorToolUse.compensationRestored(false, true, true, true, false));
        assertEquals(BodyBlockUseResult.Outcome.RECOVERY_REQUIRED,
                BodyFloorToolUse.compensationOutcome(BodyHeldItemMutation.Result.DENIED, false, true));
    }
}
