package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pure routing fixtures; no unregistered player is treated as a connected lifecycle owner. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleDevelopmentCreativeCommandGameTests {
    private LifecycleDevelopmentCreativeCommandGameTests() { }

    @GameTest(template = "empty")
    public static void creativeRoutesOnlyExclusiveCommittedOwner(GameTestHelper helper) {
        require(LifecycleDevelopmentMode.creativeRoute(true, false) == LifecycleDevelopmentMode.CreativeRoute.BODY,
                "exact body selects body park");
        require(LifecycleDevelopmentMode.creativeRoute(false, true) == LifecycleDevelopmentMode.CreativeRoute.GHOST,
                "exact ghost preserves ghost park");
        require(LifecycleDevelopmentMode.creativeRoute(false, false) == LifecycleDevelopmentMode.CreativeRoute.NONE,
                "absent owner denied");
        require(LifecycleDevelopmentMode.creativeRoute(true, true) == LifecycleDevelopmentMode.CreativeRoute.NONE,
                "conflicting owners denied");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rawCreativeDeniedButScopedBodySwitchAllowed(GameTestHelper helper) {
        require(LifecycleDevelopmentMode.rejectRawBodyCreativeChange(true, GameType.CREATIVE, false),
                "external raw Creative denied");
        require(!LifecycleDevelopmentMode.rejectRawBodyCreativeChange(true, GameType.CREATIVE, true),
                "exact command transition accepted");
        require(!LifecycleDevelopmentMode.rejectRawBodyCreativeChange(false, GameType.CREATIVE, false),
                "unowned player unaffected");
        require(!LifecycleDevelopmentMode.rejectRawBodyCreativeChange(true, GameType.SPECTATOR, false),
                "internal spectator path unaffected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledOrUnverifiedBodySwitchNeverReportsSuccess(GameTestHelper helper) {
        require(LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, true, true, true), "verified transition succeeds");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(false, GameType.SPECTATOR,
                true, true, true, true, true), "canceled switch denied even after pre-event park");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, false, true, true), "invalid claim denied");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, true, false, true), "unrestored inventory denied");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                false, true, true, true, true), "missing exact detached owner denied");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, false, true, true, true), "remaining controller denied");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, true, true, false), "disconnected carrier denied");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
