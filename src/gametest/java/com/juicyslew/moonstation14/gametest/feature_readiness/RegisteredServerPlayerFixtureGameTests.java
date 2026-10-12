package com.juicyslew.moonstation14.gametest.feature_readiness;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registration-layer fixture only; it does not establish authenticated controlled-body acceptance. */
@PrefixGameTestTemplate(false)
@GameTestTags({"feature-readiness"})
public final class RegisteredServerPlayerFixtureGameTests {
    private RegisteredServerPlayerFixtureGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void normalPlayerListRegistrationAndRemoval(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(
                helper.getLevel(), "readiness-registration");
        try {
            if (!fixture.isRegisteredAndAlive())
                throw new GameTestAssertException("placeNewPlayer registers a live ServerPlayer");
        } finally {
            fixture.close();
        }
        if (helper.getLevel().getServer().getPlayerList().getPlayer(fixture.player().getUUID()) != null
                || !fixture.player().isRemoved())
            throw new GameTestAssertException("PlayerList.remove cleans up the registered player");
        helper.succeed();
    }
}
