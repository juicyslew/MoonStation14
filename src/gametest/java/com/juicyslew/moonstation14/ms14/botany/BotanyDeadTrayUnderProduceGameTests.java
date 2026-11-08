package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class BotanyDeadTrayUnderProduceGameTests {
    private BotanyDeadTrayUnderProduceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 300)
    @GameTestTags({"botany-harvest", "botany-pickup"})
    public static void registeredActorClearsDeadTrayUnderProduceAndPicksItUp(GameTestHelper helper) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedPlanting(helper,
                new net.minecraft.world.item.ItemStack(ModItems.WHEAT_SEEDS.get()), "dead-tray-under-output",
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    Throwable failure = null;
                    try {
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                        var plant = tray.plantState();
                        plant.state(plant.age(), 0, plant.lastProduceAge(), plant.skipAging(), false, true);
                        tray.setChanged();
                        require(plant.status() == PlantInstance.Status.DEAD, "tray is DEAD before ordinary USE");

                        var produce = ProduceFactory.createHarvest(plant.seedReference(), 5);
                        require(FreshWorldOutputBatch.publishSystemHarvest(level, pos, java.util.List.of(produce), () -> true, () -> {})
                                        == BodyHandItemTransfer.Result.SUCCESS,
                                "fresh output batch publishes produce under test");
                        var output = level.getEntitiesOfClass(LooseItemEntity.class, new AABB(pos).inflate(2),
                                entity -> !entity.getItem().isEmpty() && entity.getItem().is(produce.getItem()))
                                .stream().findFirst().orElseThrow();

                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                        query.revision(), hand, null, pos, hit.getDirection(), false,
                                        BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                                "ordinary authenticated USE clears dead plant under produce");
                        require(plant.status() == PlantInstance.Status.EMPTY, "dead tray plant is EMPTY after USE");
                        require(!output.isRemoved() && !output.getItem().isEmpty(), "produce remains available after clearing dead plant");

                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        aimAt(body, output);
                        require(BodyHandItemTransfer.pickupSelected(fixture.player(), output.getUUID(), hand, hands.revision(), ignored -> {})
                                        == BodyHandItemTransfer.Result.SUCCESS,
                                "actor ordinarily picks up produce after clearing dead plant");
                    } catch (Throwable error) {
                        failure = error;
                    }
                    finish(h, fixture, finished, failure);
                });
    }

    private static void aimAt(net.minecraft.world.entity.LivingEntity body, LooseItemEntity item) {
        var delta = item.position().add(0, .2, 0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }

    private static void finish(GameTestHelper helper,
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
            AtomicBoolean finished, Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        if (failure == null) helper.succeed();
        else helper.testInfo.fail(new AssertionError("dead tray clearing beneath produce", failure));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
