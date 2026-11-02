package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dispatches positionless firelock syntax through the production server command dispatcher. */
@GameTestTags({"firelock_fixture", "fixture-command-grammar"})
@PrefixGameTestTemplate(false)
public final class FirelockFixtureCommandGrammarGameTests {
    private FirelockFixtureCommandGrammarGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredPositionlessFirelockCommandsParseAndFailClosed(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register((ServerLevel) helper.getLevel(), "firelock-command-grammar");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close(net.minecraft.gametest.framework.GameTestInfo info) {
                try { fixture.close(); }
                catch (Throwable cleanup) {
                    Throwable original = info.getError();
                    if (original != null) original.addSuppressed(cleanup); else info.fail(cleanup);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { close(oldTest); }
        });
        var source = fixture.player().createCommandSourceStack().withPermission(2);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        // Production registration contributes the grammar to the server's live dispatcher.
        require(dispatcher.getRoot().getChild("ms14") != null, "registered ms14 command root");
        expect(dispatcher, "ms14 firelock_fixture build", source, 0);
        expect(dispatcher, "ms14 firelock_fixture initialize", source, 0);
        expect(dispatcher, "ms14 firelock_fixture status", source, 0);
        expect(dispatcher, "ms14 firelock_fixture enter", source, 0);
        expect(dispatcher, "ms14 firelock_fixture reset", source, 0);
        expect(dispatcher, "ms14 firelock_fixture scenario pressure_warning", source, 0);
        expect(dispatcher, "ms14 firelock_fixture scenario pressure_danger", source, 0);
        expect(dispatcher, "ms14 firelock_fixture power on", source, 0);
        expect(dispatcher, "ms14 firelock_fixture power off", source, 0);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    public static void registeredPlayerBuildRemembersPositionlessSite(GameTestHelper helper) {
        var level = (ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "firelock-build-command");
        var other = RegisteredServerPlayerFixture.register(level, "firelock-other-operator");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close(net.minecraft.gametest.framework.GameTestInfo info) {
                try { other.close(); fixture.close(); }
                catch (Throwable cleanup) {
                    Throwable original = info.getError();
                    if (original != null) original.addSuppressed(cleanup); else info.fail(cleanup);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { close(oldTest); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var clear = helper.absolutePos(new net.minecraft.core.BlockPos(2, 22, 2));
                active.body().teleportTo(clear.getX() + .5, clear.getY(), clear.getZ() + .5);
                active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var source = fixture.player().createCommandSourceStack().withPermission(2);
                var dispatcher = level.getServer().getCommands().getDispatcher();
                require(dispatch(dispatcher, "ms14 firelock_fixture build", source) == 1,
                        "positionless build uses authorized registered player's body");
                var origin = active.body().blockPosition().offset(3, 0, -VentilationFixturePlan.DEPTH / 2);
                require(VentilationFixtureSavedData.existing(level).flatMap(data -> data.entry(origin))
                                .filter(VentilationFixtureSavedData.Entry::firelockVariant).isPresent(),
                        "public build command constructed and saved the nearby firelock fixture");
                require(dispatch(dispatcher, "ms14 firelock_fixture status", source) == 1,
                        "positionless status resolves the builder's remembered site");
                require(dispatch(dispatcher, "ms14 firelock_fixture status", other.player().createCommandSourceStack().withPermission(2)) == 0,
                        "different operator cannot inherit builder's remembered site");
                awaitRegisteredEndpoints(helper, dispatcher, source, level, origin, 0);
            } catch (RuntimeException | Error failure) {
                closeFixture(fixture, failure);
                closeFixture(other, failure);
                throw failure;
            }
        });
    }

    private static void awaitRegisteredEndpoints(GameTestHelper helper,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source, ServerLevel level, net.minecraft.core.BlockPos origin, int elapsed) {
        var plan = VentilationFixturePlan.firelockPlan();
        var transport = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport();
        boolean ready = transport.lookupAt(origin.offset(plan.alarm())).size() == 1
                && transport.lookupAt(origin.offset(plan.vent())).size() == 1
                && transport.lookupAt(origin.offset(plan.firelock())).size() == 1;
        if (ready) {
            require(dispatch(dispatcher, "ms14 firelock_fixture initialize", source) == 1,
                    "positionless initialize resolves remembered site after endpoints register");
            helper.succeed();
            return;
        }
        if (elapsed >= 40) throw new GameTestAssertException("production fixture endpoints did not register before command initialize");
        helper.runAfterDelay(1, () -> awaitRegisteredEndpoints(helper, dispatcher, source, level, origin, elapsed + 1));
    }

    private static int dispatch(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String command, net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException syntax) {
            throw new GameTestAssertException("registered production dispatcher rejected " + command + ": " + syntax.getMessage());
        }
    }

    private static void closeFixture(RegisteredServerPlayerFixture fixture, Throwable original) {
        try { fixture.close(); } catch (Throwable cleanup) { original.addSuppressed(cleanup); }
    }

    private static void expect(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command, net.minecraft.commands.CommandSourceStack source, int expected) {
        try {
            int result = dispatcher.execute(command, source);
            require(result == expected, command + " dispatched to guarded handler; result=" + result);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException syntax) {
            throw new GameTestAssertException("registered grammar rejected " + command + ": " + syntax.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
