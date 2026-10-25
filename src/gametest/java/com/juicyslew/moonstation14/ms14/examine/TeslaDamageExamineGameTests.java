package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.tesla.MachineryIntegrity;
import com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles;
import com.juicyslew.moonstation14.ms14.tesla.TeslaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises resolved Tesla machine profiles and persistent damage through authenticated ordinary ExamineService queries. */
@PrefixGameTestTemplate(false)
@GameTestTags("tesla_examine")
public final class TeslaDamageExamineGameTests {
    private TeslaDamageExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    public static void ordinaryExamineReportsResolvedCoilAndRodDamageBandsAndRepair(GameTestHelper helper) {
        var level = helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "teslaexamine");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { fixture.close(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { fixture.close(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { fixture.close(); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BuiltinExamineContributors.register();
                BlockPos coilPos = helper.absolutePos(new BlockPos(2, 1, 5));
                BlockPos rodPos = helper.absolutePos(new BlockPos(5, 1, 5));
                level.setBlock(coilPos, ModBlocks.TESLA_COIL.get().defaultBlockState(), 3);
                level.setBlock(rodPos, ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(), 3);
                var coil = machine(level, coilPos, ModTeslaProfiles.COIL_ID, 225);
                var rod = machine(level, rodPos, ModTeslaProfiles.ROD_ID, 300);
                // Move the authenticated active actor close enough for ordinary detail admission.
                active.body().teleportTo(coilPos.getX() + .5, coilPos.getY(), coilPos.getZ() - 1.5);
                assertMessage(fixture.player(), active.body().getUUID(), active.epoch(), coilPos, coil, 0, true);
                assertMessage(fixture.player(), active.body().getUUID(), active.epoch(), rodPos, rod, 0, true);

                // Damage is persisted as an integer, so probe the smallest representable value at or above each band boundary.
                for (int boundary = 10; boundary <= 90; boundary += 20) {
                    int expected = (boundary + 10) / 20;
                    MachineryIntegrity.clear(coil);
                    MachineryIntegrity.add(coil, (int) Math.ceil(coil.profile().destructionThreshold() * (boundary / 100.0)));
                    assertMessage(fixture.player(), active.body().getUUID(), active.epoch(), coilPos, coil, expected, true);
                    MachineryIntegrity.clear(rod);
                    MachineryIntegrity.add(rod, (int) Math.ceil(rod.profile().destructionThreshold() * (boundary / 100.0)));
                    assertMessage(fixture.player(), active.body().getUUID(), active.epoch(), rodPos, rod, expected, true);
                }
                // Persistence owner is authoritative; the existing repair operation clears it and next query is intact.
                MachineryIntegrity.clear(coil);
                MachineryIntegrity.add(coil, 90);
                require(coil.repairStructuralDamage(), "existing repair owner clears damaged coil");
                assertMessage(fixture.player(), active.body().getUUID(), active.epoch(), coilPos, coil, 0, true);
                require(coil.structuralDamage() == 0, "repair cleared persisted structural damage");
                helper.succeed();
            } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
        });
    }

    private static TeslaMachineBlockEntity machine(ServerLevel level, BlockPos pos, ResourceLocation id, int threshold) {
        if (!(level.getBlockEntity(pos) instanceof TeslaMachineBlockEntity machine))
            throw new GameTestAssertException("placed Tesla machine has live block entity: " + id);
        require(id.equals(machine.profileId()) && machine.profile() != null, "block binds resolved profile " + id);
        require(machine.profile().destructionThreshold() == threshold, "resolved profile destruction threshold " + threshold);
        require(machine.profile().examineDamageMessages().size() == 6, "profile composes six authored message keys");
        return machine;
    }

    private static void assertMessage(net.minecraft.server.level.ServerPlayer player, java.util.UUID actor, long epoch,
                                      BlockPos pos, TeslaMachineBlockEntity machine, int band, boolean expectedPublic) {
        var request = new ExamineRequest(1, actor, epoch, ExamineTarget.block(pos));
        var result = ExamineService.queryDiagnostic(player, request);
        require(result.admission() == ExamineService.Admission.ACCEPTED,
                "real ordinary ExamineService admission: " + result.admission());
        var entries = result.response().entries().stream().filter(entry -> entry.group().equals("damage")).toList();
        require(entries.size() == 1, "registered contributor emits exactly one damage message; entries=" + result.response().entries());
        var entry = entries.getFirst();
        require((entry.visibility() == ExamineEntry.Visibility.PUBLIC) == expectedPublic,
                "damage sentence is public ordinary-examine content, not a status/detail row");
        String expectedKey = machine.profile().examineDamageMessages().get(band);
        require(entry.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                        && translatable.getKey().equals(expectedKey),
                "ordinary examine resolves expected authored message key " + expectedKey + ", got " + entry.text());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
