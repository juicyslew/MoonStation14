package com.juicyslew.moonstation14.ms14.shuttle.fixture.command;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@GameTestTags({"shuttle-fixture-command", "shuttle", "command"})
@PrefixGameTestTemplate(false)
public final class ShuttleFixtureCommandGameTests {
    private ShuttleFixtureCommandGameTests() { }

    @GameTestTags({"shuttle-manual-jump-bay"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    public static void manualBuildAuthorsBoardingBayAndPreservesCanonicalCabin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var region = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.acquire(
                helper, new BlockPos(2860, 300, 1200), 28, 12, 24);
        com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.awaitEmpty(helper, region, 40, () -> {
            BlockPos origin = region.origin().offset(4, 0, 4);
            CommandBuiltCabinFixture built = CommandBuiltCabinFixture.buildManual(helper, origin);
            BlockPos storage = built.subLevel().getPlot().getCenterBlock();
            for (int x = 9; x <= 18; x++) for (int z = 1; z <= 11; z++) {
                require(level.getBlockState(storage.offset(x, 0, z)).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()),
                        "annex has authored steel collision floor");
                for (int y = 1; y <= 5; y++) require(level.getBlockState(storage.offset(x, y, z)).isAir(),
                        "annex interior remains clear for standing and jumping");
            }
            for (int x = 12; x <= 14; x++) for (int y = 1; y <= 3; y++)
                require(level.getBlockState(storage.offset(x, y, 0)).isAir(), "front opening is directly walkable");
            require(level.getBlockState(storage.offset(12, 0, 0)).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()),
                    "front entrance has continuous walkable steel sill");
            require(level.getBlockState(storage.offset(8, 2, 6)).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()),
                    "bay remains separated from canonical cabin by its shared-frame wall");

            require(level.getBlockEntity(storage.offset(ShuttleCabinFixture.AIRLOCK)) instanceof AirlockBlockEntity
                            && level.getBlockEntity(storage.offset(ShuttleCabinFixture.MACHINE)) instanceof AirAlarmBlockEntity
                            && level.getBlockEntity(storage.offset(ShuttleCabinFixture.VENT)) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity
                            && level.getBlockEntity(storage.offset(ShuttleCabinFixture.SCRUBBER)) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity
                            && level.getBlockEntity(storage.offset(ShuttleCabinFixture.PIPE)) instanceof VisualPipeBlockEntity,
                    "manual variant retains canonical airlock, alarm, ventilation, and pipe devices");
            require(AtmosphereService.INSTANCE.readAtmosphere(level, storage.offset(ShuttleCabinFixture.MIN_ROOM_CELL)).isPresent(),
                    "canonical sealed room still has its initialized atmosphere claim");
            CapturedOutput status = new CapturedOutput();
            require(execute(level.getServer().getCommands().getDispatcher(),
                    "ms14 shuttle_fixture status " + built.subLevel().getUniqueId(),
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2).withSource(status)) == 1
                            && status.message != null && status.message.contains("alarm100W-path=")
                            && status.message.contains("airlock20W-path=") && status.message.contains("room=FINITE"),
                    "canonical alarm/airlock power and sealed finite-room readiness remain observable on manual variant");
            built.close();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void permissionAndUnsafeOriginsRefuseWithoutAllocatingOrOverwriting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var region = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.acquire(
                helper, new BlockPos(2740, 300, 1200), 28, 12, 24);
        com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.awaitEmpty(helper, region, 40, () -> {
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var container = SubLevelContainer.getContainer(level);
        Set<UUID> before = ids(container);
        boolean denied = false;
        try {
            dispatcher.execute("ms14 shuttle_fixture build 2 1 2",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(0));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied && ids(container).equals(before), "non-operator command is denied before allocating a Sable plot");

        int consoleDefault = execute(dispatcher, "ms14 shuttle_fixture build",
                level.getServer().createCommandSourceStack().withLevel(level).withPermission(2));
        require(consoleDefault == 0 && ids(container).equals(before), "console without an explicit origin cannot default to a location");

        BlockPos origin = region.origin().offset(4, 0, 4);
        BlockPos occupiedAnnex = origin.offset(16, 1, 2);
        level.setBlock(occupiedAnnex, Blocks.STONE.defaultBlockState(), 3);
        String command = "ms14 shuttle_fixture build " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
        int refused = execute(dispatcher, command, level.getServer().createCommandSourceStack().withLevel(level).withPermission(2));
        require(refused == 0 && ids(container).equals(before)
                        && level.getBlockState(occupiedAnnex).is(Blocks.STONE),
                "with the otherwise-empty loaded footprint, its sole occupied annex cell refuses before allocation and is preserved");
        level.setBlock(occupiedAnnex, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    public static void explicitBuildStatusAndWritableNudgeUseActualSableOwners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var region = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.acquire(
                helper, new BlockPos(2920, 300, 1200), 28, 12, 24);
        BlockPos origin = region.origin().offset(4, 0, 4);
        com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixtureScene.awaitEmpty(helper, region, 40, () -> {
            var container = SubLevelContainer.getContainer(level);
            CommandBuiltCabinFixture built = CommandBuiltCabinFixture.build(helper, origin);
            var sub = built.subLevel();
            UUID id = sub.getUniqueId();
            var handle = RigidBodyHandle.of(sub);
            require(handle != null && handle.isValid(), "authored plot has its live native rigid body");
            var storage = sub.getPlot().getCenterBlock();
            var airlock = level.getBlockEntity(storage.offset(ShuttleCabinFixture.AIRLOCK));
            var alarm = level.getBlockEntity(storage.offset(ShuttleCabinFixture.MACHINE));
            var pipe = level.getBlockEntity(storage.offset(ShuttleCabinFixture.PIPE));
            require(airlock instanceof AirlockBlockEntity && alarm instanceof AirAlarmBlockEntity
                            && pipe instanceof VisualPipeBlockEntity,
                    "same resolved prefab contains live airlock, alarm, and pipe block entities");
            require(AtmosphereService.INSTANCE.readAtmosphere(level, storage.offset(ShuttleCabinFixture.MIN_ROOM_CELL)).isPresent(),
                    "same authored plot owns a finite room mixture");
            require(LoadedPipeGasOwner.activeSnapshot(level, storage.offset(ShuttleCabinFixture.PIPE), VisualPipeBlockEntity.PRIMARY).isPresent(),
                    "same authored plot owns live pipe gas state");
            var pipeEntity = (VisualPipeBlockEntity) pipe;
            UUID oldPipeId = pipeEntity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).orElseThrow();

            String uuid = id.toString();
            String shortId = "ship-" + uuid.replace("-", "").substring(0, 8);
            CapturedOutput listOutput = new CapturedOutput();
            require(execute(level.getServer().getCommands().getDispatcher(), "ms14 shuttle_fixture list",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2).withSource(listOutput)) >= 1
                            && listOutput.message != null && listOutput.message.contains(shortId),
                    "loaded list exposes the human-readable ship ID");
            CapturedOutput statusOutput = new CapturedOutput();
            require(execute(level.getServer().getCommands().getDispatcher(), "ms14 shuttle_fixture status " + shortId,
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2).withSource(statusOutput)) == 1,
                    "status resolves the human-readable ID to actual loaded Sable owner");
            require(statusOutput.message != null && statusOutput.message.contains(shortId) && statusOutput.message.contains("alarm100W-path=")
                            && statusOutput.message.contains("airlock20W-path=") && statusOutput.message.contains("pipeGas=owner=")
                            && statusOutput.message.contains("moles={OXYGEN=") && statusOutput.message.contains("room=FINITE"),
                    "status reports both current power meters, room mixture, and pipe owner/gas rather than stopping at one owner");
            Vector3d beforeNudge = handle.getLinearVelocity(new Vector3d());
            require(execute(level.getServer().getCommands().getDispatcher(),
                    "ms14 shuttle_fixture nudge " + uuid + " 0.5 0 0",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)) == 1,
                    "nudge executes on the native body");
            Vector3d afterNudge = handle.getLinearVelocity(new Vector3d());
            require(Math.abs(afterNudge.x - beforeNudge.x - 0.5) < 0.01,
                    "one diagnostic command applies exactly one native linear-velocity delta");
            require(execute(level.getServer().getCommands().getDispatcher(),
                    "ms14 shuttle_fixture nudge " + shortId + " 2.01 0 0",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)) == 1,
                    "finite delta above former arbitrary bound is accepted");
            expectInvalidDouble(level.getServer().getCommands().getDispatcher(),
                    "ms14 shuttle_fixture nudge " + uuid + " NaN 0 0",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2), "NaN is rejected");
            expectInvalidDouble(level.getServer().getCommands().getDispatcher(),
                    "ms14 shuttle_fixture nudge " + uuid + " Infinity 0 0",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2), "infinity is rejected");
            require(execute(level.getServer().getCommands().getDispatcher(), "ms14 shuttle_fixture status " + uuid,
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)) == 1,
                    "full UUID compatibility is preserved");
            int unknown = execute(level.getServer().getCommands().getDispatcher(), "ms14 shuttle_fixture status ship-deadbeef",
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2));
            require(unknown == 0, "unknown ship ID is refused");
            require(container.getSubLevel(id) == sub, "status/nudge preserve the exact engine UUID");
            built.close();
            var inventory = com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level)
                    .orElseThrow().snapshot();
            var pipeKey = new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(
                    storage.offset(ShuttleCabinFixture.PIPE),
                    com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY);
            require(inventory.get(pipeKey).isEmpty() && inventory.retiredSnapshot().containsKey(oldPipeId),
                    "command-fixture teardown removes live pipe key and archives its exact old identity synchronously");
            helper.succeed();
        });
    }

    private static Set<UUID> ids(SubLevelContainer container) {
        Set<UUID> result = new HashSet<>();
        container.getAllSubLevels().forEach(sub -> result.add(sub.getUniqueId()));
        return result;
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command, net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("fixture command failed to parse/execute: " + failure.getMessage());
        }
    }

    private static void expectInvalidDouble(
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String command, net.minecraft.commands.CommandSourceStack source, String expectation) {
        CapturedOutput output = new CapturedOutput();
        try {
            dispatcher.execute(command, source.withSource(output));
            throw new GameTestAssertException("expected invalid double to be rejected: " + expectation);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            require(failure.getMessage().contains("Expected double"),
                    "non-finite argument rejection is specifically the double parser: " + failure.getMessage());
            require(output.message == null, "invalid double never reaches command execution");
        }
    }

    private static final class CapturedOutput implements CommandSource {
        private String message;
        @Override public void sendSystemMessage(Component component) { message = component.getString(); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
