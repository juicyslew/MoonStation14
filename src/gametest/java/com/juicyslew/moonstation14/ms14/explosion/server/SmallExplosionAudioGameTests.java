package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

import java.util.ArrayList;

@PrefixGameTestTemplate(false)
@GameTestTags({"explosion-audio", "explosion-queue"})
public final class SmallExplosionAudioGameTests {
    private SmallExplosionAudioGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", batch = "small-explosion-audio-registered", timeoutTicks = 100)
    public static void queuedSmallBlastUsesRegisteredCameraAndOverlappingNearFarAudio(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "small-explosion-audio");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var level = helper.getLevel();
                var origin = helper.absolutePos(new BlockPos(4, 2, 4));
                require(level.hasChunkAt(origin) && level.getBlockState(origin).isAir(), "source is loaded air");
                var body = ready.body();
                body.setPos(origin.getX() + 3.5, origin.getY() + .5, origin.getZ() + .5);
                body.setNoGravity(true);
                require(fixture.player().getCamera() == body, "registered player camera is the controlled body before enqueue");
                require(body.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) == 9,
                        "camera starts three blocks from the source for the one-iteration small-far cue");
                var parkedCarrier = helper.absolutePos(new BlockPos(128, 2, 4));
                fixture.player().setPos(parkedCarrier.getX(), parkedCarrier.getY(), parkedCarrier.getZ());
                fixture.player().setCamera(body);
                require(fixture.player().distanceToSqr(body) > 100 * 100,
                        "registered carrier starts independently more than 100 blocks from camera");
                while (fixture.pollOutbound() != null) { }
                var source = new ExplosiveSourceData(.6, 1, 1,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "default"));
                var plan = BlastPlanner.plan(new BlastPlanner.Pos(0, 0, 0), source.proposal(), p -> BlastPlanner.Cell.OPEN);
                require(plan.expansionIterations() == 1, "small blast proposal expands once, enabling shared far cue");
                require(QueuedExplosionSystem.enqueue(level, origin, source) == QueuedExplosionSystem.Outcome.QUEUED,
                        "queued system accepts default small blast");
                var snapshots = new ArrayList<GrenadeSoundPayload>();
                var packetTypeCounts = new java.util.LinkedHashMap<String, Integer>();
                pollUntilExecuted(helper, fixture, body, origin, snapshots, packetTypeCounts, 0);
            } catch (Throwable failure) { fixture.close(); helper.fail("small explosion audio fixture: " + failure); }
        });
    }

    private static void pollUntilExecuted(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                          net.minecraft.world.entity.Entity body, BlockPos origin,
                                          ArrayList<GrenadeSoundPayload> snapshots,
                                          java.util.Map<String, Integer> packetTypeCounts, int elapsed) {
        helper.runAfterDelay(1, () -> {
            boolean continuationScheduled = false;
            try {
                Object packet;
                while ((packet = fixture.pollOutbound()) != null) {
                    packetTypeCounts.merge(packet.getClass().getSimpleName(), 1, Integer::sum);
                    if (packet instanceof ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof GrenadeSoundPayload payload) snapshots.add(payload);
                }
                if (snapshots.stream().noneMatch(p -> p.soundSet() == GrenadeSoundPayload.SoundSet.SMALL)
                        && elapsed < 10) {
                    pollUntilExecuted(helper, fixture, body, origin, snapshots, packetTypeCounts, elapsed + 1);
                    continuationScheduled = true;
                    return;
                }
                        var small = snapshots.stream().filter(p -> p.soundSet() == GrenadeSoundPayload.SoundSet.SMALL).toList();
                        var camera = fixture.player().getCamera();
                        var sourceLoaded = helper.getLevel().hasChunkAt(origin);
                        var sourceState = helper.getLevel().getBlockState(origin);
                        require(small.size() == 2, boundedDiagnostic("queued execution expected exactly two small cues; actual="
                                + small.size() + ", observed=" + snapshots.size() + ", elapsed=" + elapsed
                                + ", gameTime=" + helper.getLevel().getGameTime() + ", expectedIterations=1"
                                + ", sourceLoaded=" + sourceLoaded + ", source=" + sourceState
                                + ", cameraIsBody=" + (camera == body) + ", cameraRemoved=" + (camera == null || camera.isRemoved())
                                + ", cameraPos=" + (camera == null ? "null" : camera.position())
                                + ", sourceDistanceSquared=" + (camera == null ? "n/a"
                                : camera.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5))
                                + ", packetTypes=" + packetTypeCounts + ", cues=" + summarize(snapshots)));
                        var near = small.stream().filter(p -> p.mode() == GrenadeSoundPayload.Mode.NEAR).toList();
                        var far = small.stream().filter(p -> p.mode() == GrenadeSoundPayload.Mode.FAR).toList();
                        require(near.size() == 1 && far.size() == 1, "exactly one small near and far payload");
                        var n = near.get(0); var f = far.get(0);
                        require(n.gain() == 1f && n.x() == origin.getX() + .5 && n.y() == origin.getY() + .5 && n.z() == origin.getZ() + .5,
                                "near payload carries full gain at source coordinates");
                        require(Math.abs(f.gain() - Math.pow(10, 2 / 20.0)) < .00001 && f.x() == 0 && f.y() == 0 && f.z() == 0,
                                "far payload carries +2dB and UI-zero coordinates");
                        require(snapshots.stream().noneMatch(p -> p.soundSet() == GrenadeSoundPayload.SoundSet.GRENADE),
                                "no normal grenade payload");
                        require(body.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > 4
                                        && body.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) <= 25
                                        && camera == body,
                                "registered camera remains the controlled body in the small-far annulus; carrier may follow camera natively");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("small explosion audio packet route: " + failure); }
            finally { if (!continuationScheduled) fixture.close(); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static String describe(GrenadeSoundPayload payload) {
        return payload.soundSet() + "/" + payload.mode() + " source=(" + payload.x() + "," + payload.y() + "," + payload.z() + ")";
    }

    private static String summarize(java.util.List<GrenadeSoundPayload> payloads) {
        return payloads.stream().map(p -> p.mode() + "@" + p.x() + "," + p.y() + "," + p.z()).toList().toString();
    }

    static String boundedDiagnostic(String message) {
        final int limit = 900;
        if (message.length() <= limit) return message;
        return message.substring(0, limit - 15) + "...[truncated]";
    }
}
