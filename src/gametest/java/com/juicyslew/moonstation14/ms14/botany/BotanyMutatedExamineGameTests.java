package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.examine.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Authenticated, read-only examination of a mutated living plant. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany-examine", "examination"})
public final class BotanyMutatedExamineGameTests {
    private BotanyMutatedExamineGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=3400)
    public static void authenticatedExamineReadsMutatedPlantAndRepeatedQueryIsReadOnly(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests.runAuthenticatedPlanting(
                helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_SEEDS.get()),
                "mutated-examine", (h, fixture, epoch, hand, tray, pos, level, done) -> {
                    try {
                        var plant = tray.plantState();
                        var original = plant.genetics();
                        h.assertTrue(original != null, "authenticated planting initializes genetics");
                        var composed = BotanyFixtureScenarios.ligneousPreset(
                                BotanyFixtureScenarios.wheatCanScreamPreset(original));
                        plant.genetics(composed);
                        plant.touch(); tray.commitMutation();
                        long revision = plant.revision();
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player())
                                .orElseThrow(() -> new GameTestAssertException("registered player body authority resolves")).body();
                        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver
                                .aimAt(body, level, pos);
                        var actorUuid = body.getUUID();
                        var request = new ExamineRequest(1, actorUuid, epoch,
                                ExamineTarget.block(pos));
                        var first = ExamineService.queryDiagnostic(fixture.player(), request);
                        h.assertTrue(first.admission() == ExamineService.Admission.ACCEPTED && first.response().accepted(),
                                "normal authenticated ExamineService admits mutated tray: " + first.admission());
                        String responseText = first.response().description().getString();
                        for (var entry : first.response().entries()) responseText += entry.text().getString();
                        h.assertTrue(responseText.toLowerCase(java.util.Locale.ROOT).contains("ligneous")
                                        && responseText.toLowerCase(java.util.Locale.ROOT).contains("scream"),
                                "mutated tray response describes ligneous and scream traits");
                        h.assertFalse(responseText.toLowerCase(java.util.Locale.ROOT).contains("chemical"),
                                "ordinary examination does not dump genetic chemical data");
                        h.assertTrue(first.response().entries().size() <= ExamineResponse.MAX_ENTRIES,
                                "ordinary response respects 64-entry cap");
                        var wire = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
                        try {
                            ExamineResponse.STREAM_CODEC.encode(wire, first.response());
                            var decoded = ExamineResponse.STREAM_CODEC.decode(wire);
                            h.assertTrue(decoded.accepted() && decoded.entries().size() == first.response().entries().size(),
                                    "ordinary mutated response fits and survives actual response codec");
                        } finally { wire.release(); }
                        long afterFirst = plant.revision();
                        var second = ExamineService.queryDiagnostic(fixture.player(), new ExamineRequest(2,
                                actorUuid, epoch, ExamineTarget.block(pos)));
                        h.assertTrue(second.admission() == ExamineService.Admission.ACCEPTED && second.response().accepted(),
                                "repeated authenticated examination remains admitted");
                        h.assertTrue(plant.revision() == revision && afterFirst == revision,
                                "examination queries do not mutate plant genetics/revision");
                        h.assertTrue(first.response().entries().size() == second.response().entries().size(),
                                "repeated read-only query returns stable ordinary detail set");
                        fixture.close(); done.set(true); h.succeed();
                    } catch (Throwable failure) {
                        h.testInfo.fail(failure); fixture.close(); done.set(true);
                    }
                });
    }
}
