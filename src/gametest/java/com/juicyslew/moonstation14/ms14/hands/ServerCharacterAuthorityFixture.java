package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless NeoForge-compatible mock connection, not connected-client UI proof. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-authority", "shuttle"})
public final class ServerCharacterAuthorityFixture {
    private ServerCharacterAuthorityFixture() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 95, batch = "shuttle_authority_lifecycle")
    public static void normalSurvivalFirstJoinIssuesBodyHandAuthority(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "authority-fixture", ignored -> helper.succeed());
    }
}
