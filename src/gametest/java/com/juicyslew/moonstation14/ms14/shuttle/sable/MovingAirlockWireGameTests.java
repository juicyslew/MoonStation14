package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireOpen;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real body-hand wire-menu query/action continuity during native Sable frame motion. */
@GameTestTags({"shuttle-airlock-access", "shuttle"})
@PrefixGameTestTemplate(false)
public final class MovingAirlockWireGameTests {
    private MovingAirlockWireGameTests() { }

    @GameTestTags("shuttle-pry-power-context")
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_moving_airlock_wire")
    public static void registeredWireSessionQueriesAndActsDuringNativeFrameCarry(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(840, 300, 640), "moving-wire-owner",
                3.5, 1, 3.5, (fixture, body) -> {
                    var level = fixture.level();
                    var cabin = fixture.cabin();
                    BlockPos root = cabin.at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door)
                            || door.prototype() == null || !door.prototype().wiresPanelSecurity().wiresAccessible())
                        throw new GameTestAssertException("authored airlock lacks a resolved accessible wire panel");
                    if (!door.togglePanel(door.identity(), door.state().revision(), false) || !door.panelOpen())
                        throw new GameTestAssertException("fixture could not configure the authored panel open");

                    var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (hands == null || hands.activeHand().isBlank())
                        throw new GameTestAssertException("lifecycle body lacks an active hand");
                    var tool = FixtureHeldTool.issue(fixture.owner().actor(), hands.activeHand(),
                            new ItemStack(ModItems.WIRECUTTER.get()));
                    if (!ToolQualities.has(tool.stack(), ToolProfile.Quality.CUTTING))
                        throw new GameTestAssertException("resolved wirecutter lacks cutting quality");
                    UUID bodyId = body.getUUID();
                    UUID mindId = fixture.owner().authority().mindId().value();
                    long epoch = fixture.owner().authority().epoch();

                    aimAt(fixture, body, root);
                    BoundedGameTestWait.await(helper, 12,
                            () -> "normal standing-view intent did not acquire authored airlock before wire UI open: look="
                                    + body.getLookAngle() + ", position=" + body.position(),
                            () -> hasFace(level, body, root), () -> {
                    var openRequest = useRequest(fixture, body, root, tool, 1);
                    fixture.owner().dispatch(openRequest);
                    List<Object> opened = new ArrayList<>();
                    AirlockWireOpen[] openCapture = new AirlockWireOpen[1];
                    AirlockWireSnapshot[] initialCapture = new AirlockWireSnapshot[1];
                    BoundedGameTestWait.await(helper, 8, () -> "registered body USE did not emit actual wire open+snapshot; "
                                    + "payloads=" + opened + ", body=" + body.position() + ", look=" + body.getLookAngle(),
                            () -> {
                                opened.addAll(drain(fixture.owner().channel()));
                                openCapture[0] = opened.stream().filter(AirlockWireOpen.class::isInstance)
                                        .map(AirlockWireOpen.class::cast).findFirst().orElse(null);
                                initialCapture[0] = opened.stream().filter(AirlockWireSnapshot.class::isInstance)
                                        .map(AirlockWireSnapshot.class::cast).filter(snapshot -> snapshot.requestId() == 0)
                                        .findFirst().orElse(null);
                                return openCapture[0] != null && initialCapture[0] != null;
                            }, () -> continueWithWireSession(helper, fixture, body, door, root, tool, bodyId, mindId,
                                    epoch, openCapture[0], initialCapture[0]));
                    });
                });
    }

    private static BodyBlockUseRequest useRequest(MovingCabinAuthorityFixture.Ready fixture,
                                                   net.minecraft.world.entity.LivingEntity body,
                                                   BlockPos target, FixtureHeldTool.Issued tool, long sequence) {
        Direction face = face(fixture.level(), body, target);
        var probe = BodyBlockUseTargetProbe.probe(fixture.level(), body, target, face);
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (probe == null || !fixture.plot().engineId().equals(probe.frameId()) || hands == null
                || hands.revision() != tool.revision() || !hands.activeHand().equals(tool.hand())
                || !hands.token(tool.hand()).filter(tool.token()::equals).isPresent())
            throw new GameTestAssertException("fresh registered-use probe/hand does not match live cabin authority");
        return new BodyBlockUseRequest(fixture.owner().epoch(), sequence, tool.revision(), tool.hand(), tool.token(),
                target, face, false, BodyBlockUseRequest.Intent.USE);
    }

    private static void continueWithWireSession(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                                AirlockBlockEntity door, BlockPos root, FixtureHeldTool.Issued tool,
                                                UUID bodyId, UUID mindId, long epoch, AirlockWireOpen window,
                                                AirlockWireSnapshot initial) {
        if (!initial.accepted() || initial.containerId() != window.windowId()
                || !initial.session().equals(window.session()) || !initial.cuttingTool() || initial.rows().isEmpty())
            throw new GameTestAssertException("registered body USE returned invalid actual wire menu state: " + initial);
        var level = fixture.level();
        Vec3 frameBefore = LoadedFrameRayQuery.currentTargetPoint(level, root).worldPoint();
        Vec3 bodyBefore = body.position();
        RigidBodyHandle.of(fixture.plot().subLevel()).addLinearAndAngularVelocity(
                new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
        BoundedGameTestWait.await(helper, 35,
                () -> "native cabin failed to carry HUMAN before wire query: body=" + body.position(),
                () -> {
                    var target = LoadedFrameRayQuery.currentTargetPoint(level, root);
                    var actor = LoadedFrameRayQuery.currentActorPoint(level, body);
                    return target != null && target.worldPoint().subtract(frameBefore).horizontalDistance() > .3
                            && actor != null && fixture.plot().engineId().equals(actor.frameId())
                            && body.position().subtract(bodyBefore).horizontalDistance() > .3 && body.onGround();
                 }, () -> {
                    var actorStart = LoadedFrameRayQuery.currentActorPoint(level, body);
                    fixture.owner().viewTo(body, body.position().add(0, 0, 1));
                    long queryId = 1;
                    fixture.owner().dispatch(new AirlockWireRequest(window.windowId(), window.session(), queryId,
                            door.state().revision(), -1, AirlockWireRequest.Operation.QUERY));
                    List<Object> queryPackets = new ArrayList<>();
                    AirlockWireSnapshot[] queryCapture = new AirlockWireSnapshot[1];
                    BoundedGameTestWait.await(helper, 8, () -> "live wire QUERY received no registered response: " + queryPackets,
                            () -> {
                                queryPackets.addAll(drain(fixture.owner().channel()));
                                queryCapture[0] = lastSnapshot(queryPackets, queryId);
                                return queryCapture[0] != null;
                            }, () -> afterWireQuery(helper, fixture, body, door, root, tool, bodyId, mindId, epoch,
                                    window, actorStart, frameBefore, bodyBefore, queryCapture[0]));
                });
    }

    private static void afterWireQuery(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                       net.minecraft.world.entity.LivingEntity body, AirlockBlockEntity door,
                                       BlockPos root, FixtureHeldTool.Issued tool, UUID bodyId, UUID mindId, long epoch,
                                       AirlockWireOpen window, LoadedFrameRayQuery.ActorPoint actorStart,
                                       Vec3 actionFrameStart, Vec3 actionBodyStart, AirlockWireSnapshot query) {
        if (!query.accepted() || !query.session().equals(window.session()) || query.rootRevision() != door.state().revision())
            throw new GameTestAssertException("aim-independent live wire QUERY failed during frame carry: " + query);
        var row = query.rows().stream().filter(value -> !value.cut()).findFirst().orElse(null);
        if (row == null) throw new GameTestAssertException("fresh wire-session snapshot has no uncut opaque row");
        long revision = door.state().revision();
        BoundedGameTestWait.safeDelay(helper, 5, () -> sendWireCut(helper, fixture, body, door, root, tool,
                bodyId, mindId, epoch, window, actorStart, actionFrameStart, actionBodyStart, row, revision));
    }

    private static void sendWireCut(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                    net.minecraft.world.entity.LivingEntity body, AirlockBlockEntity door,
                                    BlockPos root, FixtureHeldTool.Issued tool, UUID bodyId, UUID mindId, long epoch,
                                    AirlockWireOpen window, LoadedFrameRayQuery.ActorPoint actorStart,
                                    Vec3 actionFrameStart, Vec3 actionBodyStart, AirlockWireSnapshot.Row row,
                                    long beforeRevision) {
        long actionId = 2;
        fixture.owner().dispatch(new AirlockWireRequest(window.windowId(), window.session(), actionId,
                beforeRevision, row.id(), AirlockWireRequest.Operation.CUT));
        List<Object> packets = new ArrayList<>();
        AirlockWireSnapshot[] action = new AirlockWireSnapshot[1];
        BoundedGameTestWait.await(helper, 8, () -> "registered wire CUT received no response: " + packets,
                () -> {
                    packets.addAll(drain(fixture.owner().channel()));
                    action[0] = lastSnapshot(packets, actionId);
                    return action[0] != null;
                }, () -> afterWireCut(helper, fixture, body, door, root, tool, bodyId, mindId, epoch,
                        window, actorStart, actionFrameStart, actionBodyStart, row, beforeRevision, action[0]));
    }

    private static void afterWireCut(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                     net.minecraft.world.entity.LivingEntity body, AirlockBlockEntity door,
                                     BlockPos root, FixtureHeldTool.Issued tool, UUID bodyId, UUID mindId, long epoch,
                                     AirlockWireOpen window, LoadedFrameRayQuery.ActorPoint actorStart,
                                     Vec3 actionFrameStart, Vec3 actionBodyStart, AirlockWireSnapshot.Row row,
                                     long beforeRevision, AirlockWireSnapshot action) {
        var changedRow = action.rows().stream().filter(value -> value.id() == row.id()).findFirst().orElse(null);
        var actorAfter = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
        var frameAfter = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), root);
        var liveHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (!action.accepted() || !action.session().equals(window.session())
                || action.rootRevision() <= beforeRevision || changedRow == null || !changedRow.cut()
                || frameAfter == null || !fixture.plot().engineId().equals(frameAfter.frameId())
                || actionFrameStart.subtract(frameAfter.worldPoint()).horizontalDistance() <= .3
                || actionBodyStart.subtract(body.position()).horizontalDistance() <= .3
                || actorStart == null || actorAfter == null
                || !fixture.plot().engineId().equals(actorAfter.frameId())
                || actorStart.storagePoint().distanceTo(actorAfter.storagePoint()) > .3
                || !body.getUUID().equals(bodyId)
                || !fixture.owner().authority().mindId().value().equals(mindId)
                || fixture.owner().authority().epoch() != epoch
                || liveHands == null || liveHands.revision() != tool.revision()
                || !liveHands.token(tool.hand()).filter(tool.token()::equals).isPresent())
            throw new GameTestAssertException("registered CUT did not mutate the actual row during native frame carry: "
                    + "action=" + action + ", row=" + changedRow + ", body=" + body.position()
                    + ", startActor=" + actorStart + ", endActor=" + actorAfter + ", frameStart=" + actionFrameStart
                    + ", frameEnd=" + frameAfter + ", frameDelta=" + (frameAfter == null ? null
                    : frameAfter.worldPoint().subtract(actionFrameStart).horizontalDistance())
                    + ", bodyDelta=" + body.position().subtract(actionBodyStart).horizontalDistance()
                    + ", handRevision=" + (liveHands == null ? null : liveHands.revision()));

        AirlockBlockEntity oldDoor = door;
        UUID oldIdentity = door.identity();
        fixture.plot().setStorageBlock(root, Blocks.AIR.defaultBlockState());
        fixture.plot().setStorageBlock(root.above(), Blocks.AIR.defaultBlockState());
        fixture.plot().setStorageBlock(root, com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get().defaultBlockState());
        fixture.plot().setStorageBlock(root.above(), com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get().defaultBlockState()
                .setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER));
        if (!(fixture.level().getBlockEntity(root) instanceof AirlockBlockEntity replacement)
                || replacement == oldDoor || replacement.identity().equals(oldIdentity))
            throw new GameTestAssertException("normal same-frame airlock replacement did not create a fresh root identity");
        replacement.togglePanel(replacement.identity(), replacement.state().revision(), false);
        fixture.owner().dispatch(new AirlockWireRequest(window.windowId(), window.session(), 3,
                replacement.state().revision(), -1, AirlockWireRequest.Operation.QUERY));
        BoundedGameTestWait.safeDelay(helper, 3, () -> {
            List<Object> stale = drain(fixture.owner().channel());
            if (lastSnapshot(stale, 3) != null || fixture.level().getBlockEntity(root) != replacement
                    || !replacement.panelOpen())
                throw new GameTestAssertException("replaced root accepted stale wire session or was mutated: " + stale);
            helper.succeed();
        });
    }

    private static void aimAt(MovingCabinAuthorityFixture.Ready fixture,
                              net.minecraft.world.entity.LivingEntity body, BlockPos root) {
        fixture.owner().trackViewToward(body, () -> {
            var point = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), root);
            return point == null ? null : point.worldPoint();
        });
    }

    private static Direction face(net.minecraft.server.level.ServerLevel level,
                                  net.minecraft.world.entity.LivingEntity body, BlockPos target) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, target, face) != null) return face;
        throw new GameTestAssertException("normal body ray did not nominate the authored door: " + body.position());
    }

    private static boolean hasFace(net.minecraft.server.level.ServerLevel level,
                                   net.minecraft.world.entity.LivingEntity body, BlockPos target) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, target, face) != null) return true;
        return false;
    }

    private static List<Object> drain(io.netty.channel.embedded.EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
        List<Object> found = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null)
            collect(packet, found);
        return found;
    }

    private static void collect(Object packet, List<Object> found) {
        if (packet instanceof ClientboundCustomPayloadPacket payload) found.add(payload.payload());
        else if (packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle)
            bundle.subPackets().forEach(child -> collect(child, found));
    }

    private static AirlockWireSnapshot lastSnapshot(List<Object> payloads, long requestId) {
        return payloads.stream().filter(AirlockWireSnapshot.class::isInstance).map(AirlockWireSnapshot.class::cast)
                .filter(snapshot -> snapshot.requestId() == requestId).reduce((first, second) -> second).orElse(null);
    }
}
