package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Guards the distinct active ghost identity and durable corpse identity contract. */
@PrefixGameTestTemplate(false)
@GameTestTags({"observer", "lifecycle"})
public final class LifecycleMedicalObserverIdentityGameTests {
    private LifecycleMedicalObserverIdentityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 10)
    public static void activeGhostUsesGhostBodyWhileDurableClaimRetainsCorpse(GameTestHelper helper) {
        UUID corpse = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        require(!LifecycleMedicalObserverBridge.activeGhostBodyMatches(corpse, ghost),
                "active profile must not identify the retained corpse as its body");
        require(LifecycleMedicalObserverBridge.activeGhostBodyMatches(ghost, ghost),
                "active GHOST profile body must be the exact session ghost");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
