package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockToolInteraction;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Registered body-hand screwdriver DoAfter completion while the authored Sable frame carries its HUMAN. */
@GameTestTags({"shuttle-airlock-access", "shuttle"})
@PrefixGameTestTemplate(false)
public final class MovingAirlockPanelGameTests {
    private MovingAirlockPanelGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_moving_airlock_panel")
    public static void screwdriverPanelDoAfterSurvivesNativeFrameCarry(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(720, 300, 640), "moving-panel-owner",
                3.5, 1, 3.5, (fixture, body) -> {
                    var level = fixture.level();
                    var cabin = fixture.cabin();
                    BlockPos doorPos = cabin.at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(doorPos) instanceof AirlockBlockEntity door)
                            || door.prototype() == null)
                        throw new GameTestAssertException("authored lower airlock owner/prototype missing");
                    var authority = fixture.owner().authority();
                    UUID bodyId = body.getUUID();
                    UUID mindId = authority.mindId().value();
                    long epoch = authority.epoch();
                    var initialHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (initialHands == null || initialHands.activeHand().isBlank())
                        throw new GameTestAssertException("lifecycle body lacks an issued active hand");
                    var issued = FixtureHeldTool.issue(fixture.owner().actor(), initialHands.activeHand(),
                            new ItemStack(ModItems.SCREWDRIVER.get()));
                    if (ToolQualities.profile(issued.stack()) == null
                            || !ToolQualities.has(issued.stack(), ToolProfile.Quality.SCREWING)
                            || door.prototype().wiresPanel().openingToolQuality() != ToolProfile.Quality.SCREWING)
                        throw new GameTestAssertException("resolved door/tool profiles do not authorize screwdriver panel work");
                    int duration = AirlockToolInteraction.panelDurationTicks(door.prototype().wiresPanel().toggleDelaySeconds());
                    if (duration < 1 || door.panelOpen())
                        throw new GameTestAssertException("authored panel must start closed with positive resolved DoAfter duration");

                    fixture.owner().trackViewToward(body, () -> {
                        var point = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
                        return point == null ? null : point.worldPoint();
                    });
                    Vec3 frameBefore = LoadedFrameRayQuery.currentTargetPoint(level, doorPos).worldPoint();
                    Vec3 bodyBefore = body.position();
                    SableFixtureScene.clearStoneSupportPad(level, fixture.scene());
                    RigidBodyHandle.of(fixture.plot().subLevel()).addLinearAndAngularVelocity(
                            new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
                    BoundedGameTestWait.await(helper, 35,
                            () -> "native frame/body failed to carry HUMAN before panel use: frame="
                                    + LoadedFrameRayQuery.currentTargetPoint(level, doorPos)
                                    + ", body=" + body.position(),
                            () -> {
                                var target = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
                                var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
                                return target != null && target.worldPoint().subtract(frameBefore).horizontalDistance() > .3
                                        && actorPoint != null && fixture.plot().engineId().equals(actorPoint.frameId())
                                        && body.position().subtract(bodyBefore).horizontalDistance() > .3 && body.onGround();
                            }, () -> startPanelWork(helper, fixture, body, door, doorPos, issued, duration,
                                    bodyId, mindId, epoch));
                });
    }

    private static void startPanelWork(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                       PlayerCharacterHarnessEntity body, AirlockBlockEntity door, BlockPos doorPos,
                                       FixtureHeldTool.Issued issued, int duration,
                                       UUID bodyId, UUID mindId, long epoch) {
        var level = fixture.level();
        var actor = fixture.owner().actor();
        var authority = fixture.owner().authority();
        Direction face = face(level, body, doorPos);
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != issued.revision()
                || !hands.activeHand().equals(issued.hand())
                || !hands.token(issued.hand()).filter(issued.token()::equals).isPresent()
                || !hands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.SCREWDRIVER.get())).isPresent())
            throw new GameTestAssertException("owner-published screwdriver hand/token/revision changed before panel request");
        var target = BodyBlockUseTargetProbe.probe(level, body, doorPos, face);
        if (target == null || !fixture.plot().engineId().equals(target.frameId()))
            throw new GameTestAssertException("actual face probe did not retain the exact moving cabin frame");
        var request = new BodyBlockUseRequest(epoch, 1, issued.revision(), issued.hand(), issued.token(),
                doorPos, face, false, BodyBlockUseRequest.Intent.USE);
        fixture.owner().dispatch(request);
        String result = fixture.owner().drainBlockUseOutbounds(request.sequence());
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        TimedOperation active = activePanel(state, door.identity());
        if (active == null || active.durationTicks() != duration || !active.breakOnMove()
                || !active.breakOnDamage() || door.panelOpen())
            throw new GameTestAssertException("registered body USE did not start the resolved screwdriver panel DoAfter; "
                    + "result=" + result + ", operation=" + active + ", expectedTicks=" + duration);
        var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (actorPoint == null || !fixture.plot().engineId().equals(actorPoint.frameId()))
            throw new GameTestAssertException("DoAfter start lost its live Sable actor tracking frame");
        var frameAtStart = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
        Vec3 bodyAtStart = body.position();
        Vec3 storageAtStart = actorPoint.storagePoint();
        BoundedGameTestWait.await(helper, duration + 18,
                () -> "airlock panel DoAfter did not complete on its resolved deadline; operation="
                        + activePanel(body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()), door.identity())
                        + ", body=" + body.position() + ", frame="
                        + LoadedFrameRayQuery.currentTargetPoint(level, doorPos),
                () -> {
                    TimedOperation current = operation(body, active.id());
                    return current != null && current.phase() != TimedOperation.Phase.ACTIVE;
                }, () -> {
                    TimedOperation completed = operation(body, active.id());
                    var frameAfter = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
                    var actorAfter = LoadedFrameRayQuery.currentActorPoint(level, body);
                    var currentHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (completed == null || completed.phase() != TimedOperation.Phase.COMPLETED
                            || completed.durationTicks() != duration || !door.panelOpen()
                            || level.getBlockEntity(doorPos) != door
                            || !door.identity().toString().equals(active.sourceKey())
                            || frameAfter == null || !fixture.plot().engineId().equals(frameAfter.frameId())
                            || !body.getUUID().equals(bodyId) || !fixture.owner().authority().mindId().value().equals(mindId)
                            || fixture.owner().authority().epoch() != epoch
                            || actorAfter == null || !fixture.plot().engineId().equals(actorAfter.frameId())
                            || actorAfter.storagePoint().distanceTo(storageAtStart) > .3
                            || body.position().subtract(bodyAtStart).horizontalDistance() <= .3
                            || frameAtStart == null || frameAfter.worldPoint().subtract(frameAtStart.worldPoint()).horizontalDistance() <= .3
                            || currentHands == null || currentHands.revision() != issued.revision()
                            || !currentHands.token(issued.hand()).filter(issued.token()::equals).isPresent()
                            || !currentHands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.SCREWDRIVER.get())).isPresent()
                            || !HandActorAuthority.revalidate(actor, authority))
                        throw new GameTestAssertException("panel DoAfter did not complete while preserving body/frame/door/tool authority; "
                                + "operation=" + completed + ", panelOpen=" + door.panelOpen() + ", frame=" + frameAfter
                                + ", actor=" + actorAfter + ", body=" + body.position());
                    helper.succeed();
                });
    }

    private static TimedOperation activePanel(TimedOperationState state, UUID identity) {
        if (state == null) return null;
        return state.operations().stream().filter(operation -> operation.phase() == TimedOperation.Phase.ACTIVE
                        && operation.kind().equals("airlock-panel") && operation.sourceKey().equals(identity.toString()))
                .findFirst().orElse(null);
    }

    private static TimedOperation operation(PlayerCharacterHarnessEntity body, long id) {
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state == null ? null : state.operations().stream().filter(value -> value.id() == id).findFirst().orElse(null);
    }

    private static Direction face(net.minecraft.server.level.ServerLevel level,
                                  net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, pos, face) != null) return face;
        throw new GameTestAssertException("native body ray failed to hit authored airlock panel; feet=" + body.position()
                + ", eye=" + body.getEyePosition() + ", look=" + body.getLookAngle() + ", target="
                + LoadedFrameRayQuery.currentTargetPoint(level, pos));
    }
}
