package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Policy fixtures only: these do not simulate a connected ServerPlayer or prove command acceptance. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DetachedCreativeHandoffPolicyGameTests {
    private DetachedCreativeHandoffPolicyGameTests() { }

    private static final class Fixture {
        GameType mode = GameType.CREATIVE;
        boolean marker;
        boolean clean;
        boolean disconnected;
        boolean synced;
        int syncs;
        boolean clearedBeforeSwitch;
        int parks;
        int restores;

        boolean run(boolean existing, boolean switchResult, GameType resultingMode) {
            return LifecycleDevelopmentMode.returningBodySpectatorPolicy(true, mode, existing,
                    () -> clean, () -> {
                        parks++;
                        marker = true;
                        clean = true;
                        return CreativeCarrierTransition.Result.PARKED;
                    }, () -> {
                        clearedBeforeSwitch = synced && marker && clean;
                        mode = resultingMode;
                        return switchResult;
                    }, () -> mode, () -> {
                        restores++;
                        marker = false;
                        clean = false; // Restored items are no longer an empty carrier.
                        return CreativeCarrierTransition.Result.RESTORED;
                    }, () -> { synced = true; syncs++; }, () -> disconnected = true);
        }
    }

    @GameTest(template = "empty")
    public static void canceledSwitchRestoresNewPark(GameTestHelper helper) {
        Fixture f = new Fixture();
        require(!f.run(false, false, GameType.CREATIVE) && f.parks == 1 && f.restores == 1
                && !f.marker && f.synced && !f.disconnected, "canceled switch rollback");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void successfulSwitchKeepsPark(GameTestHelper helper) {
        Fixture f = new Fixture();
        require(f.run(false, true, GameType.SPECTATOR) && f.parks == 1 && f.restores == 0
                && f.marker && f.clean && f.clearedBeforeSwitch && f.syncs == 1
                && !f.disconnected, "spectator retains marker and syncs clear before switch");
        Fixture existing = new Fixture();
        existing.marker = true;
        existing.clean = true;
        require(existing.run(true, true, GameType.SPECTATOR) && existing.parks == 0
                && existing.marker && existing.clearedBeforeSwitch && existing.syncs == 1,
                "existing clean park synced before switch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ambiguousSwitchAndDirtyParkDisconnect(GameTestHelper helper) {
        Fixture f = new Fixture();
        require(!f.run(false, false, GameType.SPECTATOR) && f.disconnected
                && f.marker && f.restores == 0, "false spectator is ambiguous");
        Fixture dirty = new Fixture();
        dirty.marker = true;
        require(!dirty.run(true, true, GameType.SPECTATOR) && dirty.disconnected
                && dirty.parks == 0 && dirty.restores == 0, "dirty parked carrier is not overwritten");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
