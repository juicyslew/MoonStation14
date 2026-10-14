package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.ProduceFactory;
import com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-seed-extractor"})
public final class SeedExtractorRegisteredPlayerGameTests {
    private SeedExtractorRegisteredPlayerGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 160)
    @GameTestTags({"botany-seed-extractor-inhands", "network"})
    public static void registeredPlayerExtractsAndPlantsFreshWheatSeedPacket(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-extractor-inhands-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos machinePos = helper.absolutePos(new BlockPos(3, 1, 3));
                BlockPos trayPos = helper.absolutePos(new BlockPos(5, 1, 3));
                level.setBlock(machinePos, ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
                level.setBlock(trayPos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                helper.runAfterDelay(1, () -> {
                    try {
                        var body = owner.body(); body.setPos(machinePos.getX() + .5, machinePos.getY() + 1, machinePos.getZ() - 1.5);
                        var machine = (SeedExtractorBlockEntity) level.getBlockEntity(machinePos);
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(trayPos);
                        require(machine != null && machine.isPowered() && tray != null && tray.plantState() != null, "machine and tray initialized/powered");
                        String hand = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).activeHand();
                        var produce = ProduceFactory.createHarvest(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
                        var loose = LooseItemEntity.create(level, body.position().add(0, .1, .8), produce);
                        loose.setNoGravity(true); require(level.addFreshEntity(loose), "real wheat produce published");
                        aimAt(body, loose);
                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), hand, hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS, "normal produce pickup");
                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                        var held = query.hands().stream().filter(h -> h.id().equals(hand)).findFirst()
                                .orElseThrow(() -> new AssertionError("missing source hand; returned hand ids=" + query.hands().stream().map(h -> h.id()).toList()));
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                        var use = new BodyBlockUseRequest(owner.epoch(), 1, query.revision(), hand, held.token(), machinePos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        String produceToken = held.token();
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), use) == BodyBlockUseResult.Outcome.PLACED, "ordinary admitted extractor USE consumes produce");
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), use) == BodyBlockUseResult.Outcome.DENIED,
                                "duplicate original extractor request is rejected");
                        var handsAfterExtract = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(handsAfterExtract.token(hand).filter(produceToken::equals).isEmpty(), "source produce ownership removed");
                        var packets = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8), e -> e.getItem().is(ModItems.WHEAT_SEEDS.get()));
                        int totalSeeds = packets.stream().mapToInt(e -> e.getItem().getCount()).sum()
                                + handsAfterExtract.handIds().stream().map(handsAfterExtract::stackCopy).flatMap(java.util.Optional::stream)
                                .filter(stack -> stack.is(ModItems.WHEAT_SEEDS.get())).mapToInt(ItemStack::getCount).sum();
                        require(totalSeeds >= 1 && totalSeeds <= 3, "inclusive resolved random yield 1..3 across world and hands");
                        var packet = packets.isEmpty() ? null : packets.getFirst();
                        String seedHand = handsAfterExtract.handIds().stream().filter(id -> handsAfterExtract.stackCopy(id)
                                .filter(stack -> stack.is(ModItems.WHEAT_SEEDS.get())).isPresent()).findFirst().orElse(null);
                        if (seedHand == null) {
                            require(packet != null, "extracted packet exists either in hands or in world");
                            aimAt(body, packet);
                            hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                            require(BodyHandItemTransfer.pickupSelected(fixture.player(), packet.getUUID(), hand, hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS, "normal pickup of extracted packet");
                            seedHand = hand;
                        }
                        hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        String selectedSeedHand = seedHand;
                        body.setPos(trayPos.getX() + .5, trayPos.getY() + 1, trayPos.getZ() - 1.5);
                        var plantQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 3));
                        var seedHeld = plantQuery.hands().stream().filter(h -> h.id().equals(selectedSeedHand)).findFirst()
                                .orElseThrow(() -> new AssertionError("missing extracted seed hand; returned hand ids=" + plantQuery.hands().stream().map(h -> h.id()).toList()));
                        var plantHit = RegisteredPlayerReadyDriver.aimAt(body, level, trayPos);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(owner.epoch(), 2, plantQuery.revision(), selectedSeedHand, seedHeld.token(), trayPos, plantHit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED, "ordinary USE plants extracted packet");
                        require(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat").equals(tray.plantState().seed()), "tray contains wheat");
                        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).token(selectedSeedHand).isEmpty(), "planted packet consumed");
                        helper.succeed();
                    } catch (Throwable failure) { helper.fail("registered extractor/player loop: " + failure); }
                    finally { fixture.close(); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("extractor player readiness: " + failure); }
        });
    }
    private static void aimAt(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.entity.item.ItemEntity item) {
        var delta = item.position().add(0, .2, 0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
