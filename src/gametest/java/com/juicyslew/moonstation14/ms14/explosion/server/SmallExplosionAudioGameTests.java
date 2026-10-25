package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;

@PrefixGameTestTemplate(false)
@GameTestTags({"explosion-audio", "explosion-queue"})
public final class SmallExplosionAudioGameTests {
    private SmallExplosionAudioGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
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
                fixture.player().setCamera(body);
                var carrier = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER,
                        helper.absolutePos(new BlockPos(100, 2, 4)));
                while (fixture.pollOutbound() != null) { }
                var source = new ExplosiveSourceData(.1, 1, .001,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "default"));
                require(QueuedExplosionSystem.enqueue(level, origin, source) == QueuedExplosionSystem.Outcome.QUEUED,
                        "queued system accepts default small blast");
                helper.runAfterDelay(2, () -> {
                    try {
                        var snapshots = new ArrayList<GrenadeSoundPayload>();
                        Object packet;
                        while ((packet = fixture.pollOutbound()) != null)
                            if (packet instanceof GrenadeSoundPayload payload) snapshots.add(payload);
                        var small = snapshots.stream().filter(p -> p.soundSet() == GrenadeSoundPayload.SoundSet.SMALL).toList();
                        require(small.size() == 2, "one snapshot contains exactly two small audio payloads");
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
                                        && carrier.distanceToSqr(body) > 100 * 100,
                                "camera is in the small-far annulus and native carrier is independently distant");
                        helper.succeed();
                    } catch (Throwable failure) { helper.fail("small explosion audio packet route: " + failure); }
                    finally { fixture.close(); carrier.discard(); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("small explosion audio fixture: " + failure); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
