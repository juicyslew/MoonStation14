package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Value policy only: never fabricates a connected ServerPlayer or lifecycle body. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeNoWriteDenialGameTests {
    private CreativeNoWriteDenialGameTests() { }

    @GameTest(template = "empty")
    public static void occupiedCreativeNoWriteDenialStaysCreative(GameTestHelper helper) {
        boolean[] disconnected = {false}, reported = {false}, switched = {false};
        var attempt = new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.DENIED,
                true, CreativeCarrierSnapshotProbe.Reason.ENDER_OCCUPIED);
        require(!LifecycleDevelopmentMode.returningBodySpectatorAttemptPolicy(true, GameType.CREATIVE, false,
                () -> false, () -> attempt, () -> { switched[0] = true; return true; }, () -> GameType.CREATIVE,
                () -> { throw new AssertionError("must not restore"); }, () -> { throw new AssertionError("must not sync"); },
                () -> disconnected[0] = true, () -> reported[0] = true), "no handoff");
        require(reported[0] && !disconnected[0] && !switched[0], "no-write refusal leaves Creative intact");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ambiguousDenialAndDirtyParkDisconnect(GameTestHelper helper) {
        for (var result : new CreativeCarrierTransition.Result[] {
                CreativeCarrierTransition.Result.DENIED, CreativeCarrierTransition.Result.RECOVERY_REQUIRED }) {
            boolean[] disconnected = {false}, reported = {false};
            require(!LifecycleDevelopmentMode.returningBodySpectatorAttemptPolicy(true, GameType.CREATIVE, false,
                    () -> false, () -> new CreativeCarrierTransition.Attempt(result, false,
                            CreativeCarrierSnapshotProbe.Reason.READY), () -> true, () -> GameType.CREATIVE,
                    () -> CreativeCarrierTransition.Result.RESTORED, () -> { },
                    () -> disconnected[0] = true, () -> reported[0] = true), "ambiguous result");
            require(disconnected[0] && !reported[0], "ambiguous denial disconnects");
        }
        boolean[] disconnected = {false};
        require(!LifecycleDevelopmentMode.returningBodySpectatorAttemptPolicy(true, GameType.CREATIVE, true,
                () -> false, () -> { throw new AssertionError("must not repark"); }, () -> true,
                () -> GameType.CREATIVE, () -> CreativeCarrierTransition.Result.RESTORED, () -> { },
                () -> disconnected[0] = true, () -> { }), "dirty marker");
        require(disconnected[0], "dirty marker disconnects");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
