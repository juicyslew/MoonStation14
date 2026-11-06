package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Native admin motion with an ordinarily controlled HUMAN and real filled loose cargo aboard. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-admin-motion", "shuttle"})
public final class ShuttleAdminPassengerMotionGameTests {
    private ShuttleAdminPassengerMotionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 500)
    public static void authorizedNativeMotionCarriesControlledPassengerAndWaterJug(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2040, 300, 1660), "admin-passenger-motion",
                2.5, 1, 2.5, (fixture, body) -> begin(helper, fixture, body));
    }

    private static void begin(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                              net.minecraft.world.entity.LivingEntity body) {
        var level = fixture.level();
        var plot = fixture.plot();
        var sub = plot.subLevel();
        ShuttleFixtureMotionService.registerFixture(sub);
        var point = LoadedFrameRayQuery.currentActorPoint(level, body);
        require(point != null && point.frameId().equals(plot.engineId()), "controlled HUMAN admitted to exact source frame");
        require(body.isAlive() && body.onGround() && !body.isNoGravity() && !body.noPhysics,
                "real lifecycle HUMAN is alive, supported, and retains ordinary gravity/physics");
        var localStanding = new Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z);
        var authorityBody = fixture.owner().authority().body();
        var mindId = fixture.owner().authority().mindId().value();
        require(authorityBody == body && mindId != null,
                "real actor authority and mind still identify the controlled body");

        ItemStack stack = new ItemStack(ModItems.JUG.get());
        var jug = (com.juicyslew.moonstation14.item.custom.JugItem) ModItems.JUG.get();
        require(jug.getCapacity(level) >= 20f, "resolved jug prototype capacity admits 20 units");
        var reagent = new ReagentAttachment();
        reagent.specificAdd(ModReagents.createKey("water"), 20f, jug.getCapacity(level));
        stack.set(ModDataComponents.REAGENT.get(), reagent.toComponent());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("motion passenger cargo"));
        var localCargo = new Vector3d(plot.storageOrigin().getX() + 5.5, plot.storageOrigin().getY() + 1.15,
                plot.storageOrigin().getZ() + 2.5);
        var worldCargo = sub.logicalPose().transformPosition(localCargo);
        LooseItemEntity cargo = LooseItemEntity.createForActor(body,
                new net.minecraft.world.phys.Vec3(worldCargo.x, worldCargo.y, worldCargo.z), stack);
        if (!level.addFreshEntity(cargo)) throw new GameTestAssertException("normal actor-context cargo spawn rejected");
        helper.testInfo.addListener(new GameTestListener() {
            private void clean() { if (!cargo.isRemoved()) cargo.discard(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest,
                    net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
        });
        try {
            ReagentComponent contents = cargo.getItem().get(ModDataComponents.REAGENT.get());
            require(contents != null && contents.centContents().equals(java.util.Map.of(
                    ModReagents.createKey("water"), 2000L)), "live loose jug contains exactly 20 units of water");
            require(cargo.getItem().getCount() == 1 && !cargo.isRemoved(), "one live jug entity remains in world");
            ItemStack cargoSource = cargo.getItem().copy();
            var source = fixture.owner().actor().createCommandSourceStack().withPermission(2);
            var dispatcher = level.getServer().getCommands().getDispatcher();
            String id = plot.engineId().toString();
            double startY = sub.logicalPose().position().y();
            double startX = sub.logicalPose().position().x();
            var initialOrientation = new org.joml.Quaterniond(sub.logicalPose().orientation());
            helper.runAfterDelay(10, () -> {
                try {
                    var settled = LoadedFrameRayQuery.currentActorPoint(level, cargo);
                    require(settled != null && plot.engineId().equals(settled.frameId()),
                            "settled live cargo is admitted to exact source frame");
                    require(cargo.onGround(), "cargo settles on ordinary source-frame floor support");
                    var cargoStanding = new Vector3d(settled.storagePoint().x, settled.storagePoint().y,
                            settled.storagePoint().z);
                    startMotion(helper, fixture, body, cargo, cargoSource, localStanding, cargoStanding,
                            startY, startX, initialOrientation, mindId, source, dispatcher, id);
                } catch (RuntimeException | AssertionError failure) {
                    if (!cargo.isRemoved()) cargo.discard();
                    throw failure;
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (!cargo.isRemoved()) cargo.discard();
            throw failure;
        }
    }

    private static void startMotion(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.LivingEntity body, LooseItemEntity cargo, ItemStack cargoSource,
            Vector3d localStanding, Vector3d cargoStanding, double startY, double startX,
            org.joml.Quaterniond initialOrientation, java.util.UUID mindId,
            net.minecraft.commands.CommandSourceStack source,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String id) {
            var level = fixture.level();
            var plot = fixture.plot();
            var sub = fixture.plot().subLevel();
            require(HandActorAuthority.revalidate(fixture.owner().actor(), fixture.owner().authority()),
                    "real actor authority revalidates before commanded motion");
            require(execute(dispatcher, "ms14 shuttle_fixture motion " + id + " 0 .75 0 0 20", source) == 1,
                    "authorized actor command starts native lift target");
            helper.runAfterDelay(60, () -> {
                try {
                    require(sub.logicalPose().position().y() > startY + .7, "native cabin lifts above support");
                    var hoverY = sub.logicalPose().position().y();
                    require(execute(dispatcher, "ms14 shuttle_fixture motion " + id + " 1 0 0 .05 12", source) == 1,
                            "same authorized owner retargets native translation and yaw");
                    helper.runAfterDelay(100, () -> {
                        try {
                            var pose = sub.logicalPose();
                            require(pose.position().x() > startX + 2, "native cabin travels over two world-X meters");
                            var relative = new org.joml.Quaterniond(initialOrientation).invert()
                                    .mul(new org.joml.Quaterniond(pose.orientation())).normalize();
                            double yaw = Math.atan2(2 * (relative.w * relative.y + relative.x * relative.z),
                                    1 - 2 * (relative.y * relative.y + relative.x * relative.x));
                            require(Math.abs(yaw) > .1, "native cabin acquires measurable commanded yaw");
                            require(Math.abs(pose.position().y() - hoverY) < 1,
                                    "altitude remains bounded during translation/hover phase");
                            require(pose.position().y() > startY + .7, "cabin remains airborne after hover phase");
                            var moved = LoadedFrameRayQuery.currentActorPoint(level, body);
                            require(moved != null && moved.frameId().equals(plot.engineId()), "passenger remains in source frame");
                            require(new Vector3d(moved.storagePoint().x, moved.storagePoint().y,
                                            moved.storagePoint().z).distance(localStanding) < .05,
                                    "controlled HUMAN standing point remains stable in frame-local coordinates");
                            require(body.isAlive() && body.onGround(), "controlled HUMAN remains alive and supported");
                            require(!cargo.isRemoved() && level.getEntity(cargo.getUUID()) == cargo
                                            && ItemStack.matches(cargoSource, cargo.getItem()),
                                    "same live cargo entity remains aboard after motion");
                            var afterContents = cargo.getItem().get(ModDataComponents.REAGENT.get());
                            require(afterContents != null && afterContents.centContents().equals(java.util.Map.of(
                                    ModReagents.createKey("water"), 2000L)), "cargo payload remains exactly conserved");
                            var cargoPoint = LoadedFrameRayQuery.currentActorPoint(level, cargo);
                            require(cargoPoint != null && fixture.plot().engineId().equals(cargoPoint.frameId()),
                                    "cargo remains admitted to exact source frame");
                            require(new Vector3d(cargoPoint.storagePoint().x, cargoPoint.storagePoint().y,
                                            cargoPoint.storagePoint().z).distance(cargoStanding) < .05,
                                    "cargo remains stable in source-frame local coordinates");
                            require(cargo.onGround(), "cargo remains ordinarily supported by frame floor");
                            require(level.getEntity(body.getUUID()) == body
                                            && HandActorAuthority.revalidate(fixture.owner().actor(), fixture.owner().authority())
                                            && fixture.owner().authority().body() == body
                                            && fixture.owner().authority().mindId().value().equals(mindId),
                                    "owner authority and mind still resolve to same HUMAN");
                            var snapshot = ShuttleFixtureMotionService.snapshot(level, sub);
                            require(snapshot != null && snapshot.active() && snapshot.remainingTicks() > 0,
                                    "fixture controller remains authorized and active");
                            require(execute(dispatcher, "ms14 shuttle_fixture motion_stop " + id, source) == 1,
                                    "authorized owner stops fixture controller");
                            helper.succeed();
                        } finally {
                            if (!cargo.isRemoved()) cargo.discard();
                        }
                    });
                } catch (RuntimeException | AssertionError failure) {
                    if (!cargo.isRemoved()) cargo.discard();
                    throw failure;
                }
            });
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command, net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("motion command failed: " + failure.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
