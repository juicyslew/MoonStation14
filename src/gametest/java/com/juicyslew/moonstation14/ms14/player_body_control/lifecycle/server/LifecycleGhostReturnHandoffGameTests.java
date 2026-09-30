package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Policy fixture: production supplies the exact RETURNING_GHOSTS owner and verified carrier. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleGhostReturnHandoffGameTests {
    private LifecycleGhostReturnHandoffGameTests() { }

    @GameTest(template = "empty")
    public static void canceledGhostSwitchRestoresSafePark(GameTestHelper helper) {
        int[] writes = new int[3];
        boolean result = LifecycleDevelopmentMode.returningGhostSpectatorPolicy(true, GameType.CREATIVE, false,
                () -> writes[0] > 0 && writes[1] == 0,
                () -> { writes[0]++; return CreativeCarrierTransition.Result.PARKED; },
                () -> false, () -> GameType.CREATIVE,
                () -> { writes[1]++; return CreativeCarrierTransition.Result.RESTORED; },
                () -> writes[2]++, () -> { throw new AssertionError("safe rollback disconnected"); });
        require(!result && writes[0] == 1 && writes[1] == 1 && writes[2] == 2,
                "canceled ghost switch restores and syncs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void successfulGhostSwitchClearsClientBeforePlay(GameTestHelper helper) {
        int[] syncs = new int[1];
        int[] restores = new int[1];
        require(LifecycleDevelopmentMode.returningGhostSpectatorPolicy(true, GameType.CREATIVE, false,
                () -> true, () -> CreativeCarrierTransition.Result.PARKED,
                () -> { require(syncs[0] == 1, "cleared before Spectator switch"); return true; },
                () -> GameType.SPECTATOR,
                () -> { restores[0]++; return CreativeCarrierTransition.Result.RESTORED; },
                () -> syncs[0]++, () -> { throw new AssertionError("successful switch disconnected"); }),
                "parked ghost return");
        require(syncs[0] == 1 && restores[0] == 0, "ghost retains park");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
