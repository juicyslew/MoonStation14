package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestTags({"shuttle-menu-access", "shuttle"})
@PrefixGameTestTemplate(false)
public final class MovingApcMenuGameTests {
    private MovingApcMenuGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850, batch = "shuttle_moving_apc_menu")
    public static void movingFrameBodyOpensAndTogglesCurrentApcThenRejectsStaleContext(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(640, 300, 640), "moving-apc-owner", 4.5, 4, 8.5, (fixture, body) -> {
            var pos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.APC);
            // Aim at the resolved WEST-mounted appearance's mapped panel interior in world coordinates.
            Vec3 contact = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), pos).worldPoint().add(.4, 0, 0);
            long sequence = fixture.owner().viewTo(body, contact);
            Vec3 direction = contact.subtract(body.getEyePosition()).normalize();
            BoundedGameTestWait.await(helper, 10,
                    () -> "registered view intent not applied through sequence " + sequence,
                    () -> fixture.owner().drainAppliedSequence() >= sequence && body.getLookAngle().dot(direction) > .995,
                    () -> afterViewInput(helper, fixture, body));
        });
    }

    private static void afterViewInput(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                       net.minecraft.world.entity.LivingEntity body) {
            var level = fixture.level();
            var livePoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            var published = fixture.owner().latestSnapshot().get();
            if (livePoint == null || !fixture.plot().engineId().equals(livePoint.frameId()) || published == null
                    || published.frameState().isEmpty()
                    || !fixture.plot().engineId().equals(published.frameState().orElseThrow().frameId()))
                throw new GameTestAssertException("actual lifecycle character snapshot omitted/wrong native frame: point="
                        + livePoint + ", snapshot=" + published);
            var framePoint = published.frameState().orElseThrow();
            if (Math.abs(framePoint.x() - livePoint.storagePoint().x) > .25
                    || Math.abs(framePoint.y() - livePoint.storagePoint().y) > .25
                    || Math.abs(framePoint.z() - livePoint.storagePoint().z) > .25)
                throw new GameTestAssertException("published storage point differs from live native actor point: snapshot="
                        + framePoint + ", live=" + livePoint);
            var actor = fixture.owner().actor();
            var authority = fixture.owner().authority();
            var pos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.APC);
            if (!(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity device))
                throw new GameTestAssertException("authored moving-cabin APC owner is absent");
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(body))
                throw new GameTestAssertException("live first-join hand owner was not published for block-use admission");
            var active = hands.activeHand();
            long revision = hands.revision();
            Direction hitFace = face(level, body, pos);
            BodyBlockUseRequest openRequest = new BodyBlockUseRequest(authority.epoch(), 1, revision, active, null,
                    pos, hitFace, false, BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
            fixture.owner().dispatch(openRequest);
            var blockUseOutbounds = fixture.owner().drainBlockUseOutbounds(openRequest.sequence());
             if (!(actor.containerMenu instanceof ApcMenu menu) || menu.boundDevice() != device
                    || !menu.bodyAuthority().equals(authority) || !menu.bodyAuthority().mindId().equals(authority.mindId())
                    || menu.bodyAuthority().epoch() != authority.epoch()
                    || !java.util.Objects.equals(menu.expectedFrameId(), fixture.plot().engineId()))
                throw new GameTestAssertException("registered body world-use did not open exact frame-bound APC menu; result="
                        + blockUseOutbounds + ", face=" + hitFace + ", handsRevision=" + hands.revision()
                        + ", currentTarget=" + LoadedFrameRayQuery.currentTargetPoint(level, pos)
                        + ", container=" + actor.containerMenu.getClass().getName()
                        + ", deviceMatch=" + (actor.containerMenu instanceof ApcMenu candidate && candidate.boundDevice() == device)
                          + ", authorityMatch=" + (actor.containerMenu instanceof ApcMenu candidate && candidate.bodyAuthority() == authority)
                          + ", frame=" + (actor.containerMenu instanceof ApcMenu candidate ? candidate.expectedFrameId() : null));
            Vec3 targetDelta = LoadedFrameRayQuery.currentTargetPoint(level, pos).worldPoint().subtract(body.getEyePosition()).normalize();
            long turnSequence = fixture.owner().viewTo(body, body.getEyePosition().subtract(targetDelta.scale(8)));
            BoundedGameTestWait.await(helper, 10, () -> "registered APC turn-away view intent not applied: " + turnSequence,
                    () -> fixture.owner().drainAppliedSequence() >= turnSequence,
                    () -> afterTurnAway(helper, fixture, body, actor, device, menu, pos));
    }

    private static void afterTurnAway(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                      net.minecraft.world.entity.LivingEntity body,
                                      net.minecraft.server.level.ServerPlayer actor, PowerDeviceBlockEntity device,
                                      ApcMenu menu, BlockPos pos) {
            var level = fixture.level();
            if (actor.containerMenu != menu || !menu.stillValid(actor))
                throw new GameTestAssertException("APC UI closed on aim change away from the device");
            long initialRevision = menu.currentRevision();
            var toggled = ApcMenuService.apply(actor, new ApcToggleRequest(menu.containerId, menu.session(),
                    initialRevision, 1, false));
            if (!toggled.accepted() || device.breakerClosed() || device.breakerRevision() != initialRevision + 1)
                throw new GameTestAssertException("ApcMenuService rejected the actual authorized breaker action");

            Vec3 frameBefore = LoadedFrameRayQuery.currentTargetPoint(level, pos).worldPoint();
            Vec3 bodyBefore = body.position();
            // The authored support pad exists only for fixture construction; clearing it lets the cabin move freely.
            SableFixtureScene.clearStoneSupportPad(level, fixture.scene());
            RigidBodyHandle handle = RigidBodyHandle.of(fixture.plot().subLevel());
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
            BoundedGameTestWait.await(helper, 35, () -> "native moving APC frame/body did not translate; start="
                            + frameBefore + ", current=" + LoadedFrameRayQuery.currentTargetPoint(level, pos)
                            + ", bodyStart=" + bodyBefore + ", bodyNow=" + body.position()
                            + ", velocity=" + handle.getLinearVelocity(new org.joml.Vector3d()),
                    () -> {
                        var current = LoadedFrameRayQuery.currentTargetPoint(level, pos);
                        return current != null && current.worldPoint().subtract(frameBefore).horizontalDistance() > .1
                                && body.position().subtract(bodyBefore).horizontalDistance() > .1;
             }, () -> {
                        var current = LoadedFrameRayQuery.currentTargetPoint(level, pos);
                        if (current == null || !fixture.plot().engineId().equals(current.frameId())
                                || body.position().subtract(bodyBefore).horizontalDistance() <= .1)
                            throw new GameTestAssertException("native APC/body pose did not retain exact frame after motion");
                        BoundedGameTestWait.await(helper, 10, () -> "aim-independent APC menu closed during native translation",
                                () -> actor.containerMenu == menu && menu.stillValid(actor),
                                () -> afterMovedAim(helper, fixture, body, actor, device, menu, pos, bodyBefore));
                    });
    }

    private static void afterMovedAim(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                      net.minecraft.world.entity.LivingEntity body,
                                      net.minecraft.server.level.ServerPlayer actor, PowerDeviceBlockEntity device,
                                      ApcMenu menu, BlockPos pos, Vec3 bodyBefore) {
                        var level = fixture.level();
                        var current = LoadedFrameRayQuery.currentTargetPoint(level, pos);
                        if (current == null || !fixture.plot().engineId().equals(current.frameId())
                                || body.position().subtract(bodyBefore).horizontalDistance() <= .1
                                || !menu.validContext(actor) || !menu.expectedFrameId().equals(current.frameId()))
                            throw new GameTestAssertException("moved body/menu did not retain exact live APC frame/current target; "
                                    + "current=" + current + ", body=" + body.position() + ", menuValid=" + menu.validContext(actor)
                                    + ", expectedFrame=" + menu.expectedFrameId() + ", sameDevice="
                                    + (level.getBlockEntity(pos) == device) + ", originalFace=" + menu.bodyRequest().face()
                                    + ", currentProbe=" + BodyBlockUseTargetProbe.probe(level, body, pos, menu.bodyRequest().face())
                                    + ", actorMenuSame=" + (actor.containerMenu == menu) + ", actorMenu="
                                    + actor.containerMenu.getClass().getSimpleName() + ", inMenuAuthority="
                                    + ", authority=" + com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(actor));
                        var reclosed = ApcMenuService.apply(actor, new ApcToggleRequest(menu.containerId, menu.session(),
                                menu.currentRevision(), 2, true));
                        if (!reclosed.accepted() || !device.breakerClosed())
                            throw new GameTestAssertException("current-pose body-probed APC continuation failed after native motion");

                         // This cell is on the live eye-to-APC-center segment, above the raised tread.
                         BlockPos blocker = fixture.cabin().origin().offset(4, 5, 8);
                        fixture.plot().setStorageBlock(blocker, Blocks.STONE.defaultBlockState());
                        var blocked = ApcMenuService.apply(actor, new ApcToggleRequest(menu.containerId, menu.session(),
                                menu.currentRevision(), 3, false));
                        if (blocked.accepted() || !device.breakerClosed())
                            throw new GameTestAssertException("current world/frame blocker did not refuse stale aimed menu action");
                        fixture.plot().setStorageBlock(blocker, Blocks.AIR.defaultBlockState());

                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        if (!PowerDeviceFixturePlacement.placeApc(level, pos, Direction.WEST)
                                || level.getBlockEntity(pos) == device || menu.validContext(actor))
                            throw new GameTestAssertException("replacement APC inherited the old exact BE/frame menu identity");
                        var replaced = ApcMenuService.apply(actor, new ApcToggleRequest(menu.containerId, menu.session(),
                                menu.currentRevision(), 4, false));
                        if (replaced.hasSnapshot() || replaced.accepted())
                            throw new GameTestAssertException("old APC menu acted on its replacement block entity");
                        helper.succeed();
    }

    private static Direction face(net.minecraft.server.level.ServerLevel level,
                                  net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, pos, face) != null) return face;
        throw new GameTestAssertException("server-side body ray did not hit APC; "
                + MovingFrameRayDiagnostic.describe(level, body, pos) + ", block=" + level.getBlockState(pos));
    }
}
