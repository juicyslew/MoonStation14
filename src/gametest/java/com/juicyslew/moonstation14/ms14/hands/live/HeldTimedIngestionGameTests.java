package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@GameTestTags({"timed-interaction", "ingestion", "hands", "chemistry"})
@PrefixGameTestTemplate(false)
public final class HeldTimedIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> ACETONE =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "acetone"));
    private HeldTimedIngestionGameTests() { }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 110)
    public static void heldDoseWaitsRepeatsAndStopsAtEmpty(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 1, () -> true);
        h.runAfterDelay(10, () -> { require(source(f) == 1000, "no dose before profile deadline"); });
        h.runAfterDelay(23, () -> {
            require(source(f) == 500, "first 500-cent dose");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 15, () -> { require(source(f) == 500, "repeat waits complete interval"); });
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 23, () -> {
                require(source(f) == 0, "second exact source dose");
                LiveHands now = hands(f); require(now.revision() == f.revision + 2, "one owner revision per dose");
                require(now.token("left").filter(f.token::equals).isPresent(), "empty container remains held");
                require(!active(f), "empty source operation completed without another empty cycle"); h.succeed();
            });
        });
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 45)
    public static void pillTransfersWholeContentsAfterTwelveTicksAndConsumesOwnerTokenOnce(GameTestHelper h) {
        ItemStack pill = new ItemStack(ModItems.PILL.get());
        Fixture f = fixtureWithItem(h, pill, 12);
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 700, () -> true).name().equals("OK"),
                "resolved pill profile starts ordinary timed ingestion");
        h.runAfterDelay(11, () -> {
            require(hands(f).token("left").filter(f.token::equals).isPresent()
                    && source(f) == 1200, "pill and full source amount remain unchanged before the 12-tick deadline");
        });
        h.runAfterDelay(15, () -> {
            require(hands(f).token("left").isEmpty() && hands(f).revision() == f.revision + 1,
                    "committed whole-dose pill is removed once through the hand owner revision");
            require(ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider
                    .get(f.body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f)) == 1200,
                    "all pill contents transfer together");
            require(!active(f), "single-use pill operation stops rather than repeating");
            h.succeed();
        });
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 35)
    public static void canceledPillIngestionDoesNotTransferOrConsume(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.PILL.get()), 8);
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 701, () -> true).name().equals("OK"),
                "timed pill operation starts");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 702, () -> true).name().equals("OK"),
                "same valid action cancels");
        h.runAfterDelay(14, () -> {
            require(hands(f).token("left").filter(f.token::equals).isPresent() && source(f) == 800,
                    "canceled pill remains untouched");
            require(ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider
                    .get(f.body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f)) == 0,
                    "cancellation admits no early dose");
            h.succeed();
        });
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 35)
    public static void pillTooLargeForRemainingStomachRoomDoesNotTransferAnyPillMixture(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.PILL.get()), 12);
        com.juicyslew.moonstation14.ms14.MS14Provider.update(f.body,
                com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH,
                new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(Map.of(ACETONE, 49f)));
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 703, () -> true).name().equals("OK"),
                "resolved whole-dose pill starts at insufficient remaining stomach capacity");
        h.runAfterDelay(13, () -> {
            require(hands(f).token("left").filter(f.token::equals).isPresent() && source(f) == 1200,
                    "capacity rejection preserves exact pill contents and owner token");
            var stomachAfter = com.juicyslew.moonstation14.ms14.MS14Provider
                    .get(f.body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
            require(stomachAfter.getOrDefault(ACETONE, 0f) > 0f && !stomachAfter.containsKey(WATER),
                    "capacity rejection admits none of the pill mixture; existing acetone may independently digest");
            require(!active(f), "capacity failure stops the completion without retrying or consuming");
            h.succeed();
        });
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    public static void registeredActorGetsOnePillCapacityActionbarOnlyAtTimedCompletion(GameTestHelper h) {
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture
                .register((net.minecraft.server.level.ServerLevel) h.getLevel(), "pill-capacity");
        h.runAtTickTime(2, () -> {
            try {
                var activeBody = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver
                        .readyAndQuery(fixture);
                var body = activeBody.body();
                ItemStack pill = new ItemStack(ModItems.PILL.get());
                pill.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 12f)));
                var handsBefore = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = handsBefore.activeHand();
                String token = "ready-pill-capacity";
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, pill),
                        "registered Ready actor owns pill through normal hands commit owner");
                com.juicyslew.moonstation14.ms14.MS14Provider.update(body,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH,
                        new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(Map.of(ACETONE, 49f)));
                var issued = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(
                        fixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(activeBody.epoch(), 2));
                require(issued.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "fresh full inventory proof issued for the actual Ready actor");
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.requireFreshQuery(fixture.player());
                var held = issued.hands().stream().filter(slot -> slot.id().equals(hand)).findFirst().orElseThrow();
                require(token.equals(held.token()), "request uses the token from the issued full inventory state");
                while (fixture.pollOutbound() != null) { }
                var request = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_IN_HAND,
                        hand, activeBody.epoch(), 3, issued.revision(), null, token);
                var ack = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(), request);
                require(ack.accepted(), "normal authenticated USE_IN_HAND request starts pill ingestion");
                h.runAfterDelay(11, () -> {
                    require(capacityMessages(fixture) == 0, "no capacity feedback before the 12-tick completion deadline");
                    h.runAfterDelay(2, () -> {
                        try {
                            var messages = capacityActionbars(fixture);
                            require(messages.size() == 1, "exactly one capacity actionbar reaches the registered actor");
                            var translatable = (net.minecraft.network.chat.contents.TranslatableContents) messages.getFirst().content().getContents();
                            require(translatable.getKey().equals("ingestion-you-cannot-ingest-any-more"),
                                    "actionbar carries the exact SS14-compatible localized capacity key");
                            require(((net.minecraft.network.chat.contents.TranslatableContents) ((net.minecraft.network.chat.Component) translatable.getArgs()[0])
                                    .getContents()).getKey().equals("ingestion-verb-swallow"), "localized verb is swallow");
                            require(messages.getFirst().overlay(), "feedback uses the actionbar channel");
                            var after = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                            var afterPill = after.stackCopy(hand).orElse(null);
                            require(after.token(hand).filter(token::equals).isPresent() && afterPill != null
                                            && ReagentUnits.fromFloat(afterPill.get(ModDataComponents.REAGENT.get()).contents().getOrDefault(WATER, 0f)) == 1200,
                                    "capacity rejection preserves the pill and exact owner token");
                            var stomachAfter = com.juicyslew.moonstation14.ms14.MS14Provider
                                    .get(body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
                            require(stomachAfter.getOrDefault(ACETONE, 0f) > 0f && !stomachAfter.containsKey(WATER),
                                    "capacity rejection admits none of the pill mixture; existing acetone may independently digest");
                            require(capacityMessages(fixture) == 0, "no duplicate capacity actionbar follows completion");
                            h.succeed();
                        } finally { fixture.close(); }
                    });
                });
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    private static java.util.List<net.minecraft.network.protocol.game.ClientboundSystemChatPacket> capacityActionbars(
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture) {
        var result = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSystemChatPacket>();
        Object outbound;
        while ((outbound = fixture.pollOutbound()) != null) {
            if (outbound instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket packet
                    && packet.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                    && translated.getKey().equals("ingestion-you-cannot-ingest-any-more")) result.add(packet);
            if (outbound instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle)
                for (var nested : bundle.subPackets())
                    if (nested instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket packet
                            && packet.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                            && translated.getKey().equals("ingestion-you-cannot-ingest-any-more")) result.add(packet);
        }
        return result;
    }

    private static int capacityMessages(com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture) {
        return capacityActionbars(fixture).size();
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 25)
    public static void registeredChemistryOutputItemsResolveCapacityTypeAndCanisterAdmission(GameTestHelper h) {
        ItemStack pill = new ItemStack(ModItems.PILL.get());
        require(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) pill.getItem()).getCapacity(pill, h.getLevel()) == 20f,
                "live pill capacity comes from resolved local container composition");
        require(!((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) pill.getItem()).canReact(pill, h.getLevel()),
                "resolved pill food solution suppresses container reactions");
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        require(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) bottle.getItem()).getCapacity(bottle, h.getLevel()) == 30f,
                "existing canonical BottleItem keeps its resolved 30u capacity");
        require(pill.get(ModDataComponents.PILL_TYPE.get()).type() == 0
                && pill.copy().get(ModDataComponents.PILL_TYPE.get()).type() == 0,
                "registered pill type is stable on copied serialized item state");
        ItemStack canister = new ItemStack(ModItems.PILL_CANISTER.get());
        var empty = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(canister).orElseThrow();
        require(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.width(canister) == 5
                && com.juicyslew.moonstation14.ms14.storage.grid.GridContents.height(canister) == 2,
                "canister dimensions are resolved from inherited Storage prototype");
        var packed = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(java.util.List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("pill-child", pill, 0, 0,
                        com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0)));
        require(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(canister, packed).isPresent(),
                "canister accepts registered pill with persistent item token");
        var forbidden = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(java.util.List.of(
                new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry("not-pill", new ItemStack(net.minecraft.world.item.Items.STICK), 0, 0,
                        com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0)));
        require(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(canister, forbidden).isEmpty(),
                "canister whitelist rejects non-pill items");
        h.succeed();
    }

    @GameTestTags({"chemmaster-output"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void canisterStoresAndReturnsPillsThroughExistingOwnedGridTransactions(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.BOTTLE.get()), 1);
        LiveHands state = hands(f);
        var oldBottle = state.takeWhole(state.revision(), "left", new ItemToken(f.token));
        require(oldBottle.succeeded(), "fixture bottle removed using pure whole-stack transition");
        state = oldBottle.state();
        String hostToken = "pill-canister-host";
        String pillToken = "stored-pill-token";
        var withHost = state.putWhole(state.revision(), "left", new ItemToken(hostToken), new ItemStack(ModItems.PILL_CANISTER.get()));
        require(withHost.succeeded(), "canister assigned an owned stack token");
        state = withHost.state();
        var withPill = state.putWhole(state.revision(), "right", new ItemToken(pillToken), new ItemStack(ModItems.PILL.get()));
        require(withPill.succeeded(), "pill assigned its own hand token");
        f.body.setData(ModDataAttachments.LIVE_HANDS.get(), withPill.state());
        int anchorX = 2;
        LiveHands stagedGrid = withPill.state().insertHeldGrid(withPill.state().revision(), "left", hostToken, "right", pillToken,
                new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(anchorX, 0),
                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0).orElseThrow();
        require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.representable(stagedGrid, f.body),
                "candidate canister state is representable by normal hand inventory snapshots");
        require(withPill.state().insertHeldGrid(withPill.state().revision(), "left", hostToken, "right", pillToken,
                new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(anchorX, 0),
                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0).isPresent(),
                "resolved canister grid accepts pill in pure candidate");
        var stored = BodyHeldGridTransfer.transferOwned(f.body, "left", hostToken, "right", pillToken,
                withPill.state().revision(), new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(anchorX, 0),
                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0, null, null, true, () -> true);
        require(stored == BodyHeldGridTransfer.Result.SUCCESS, "existing hand-to-grid owner transaction stores pill: " + stored);
        LiveHands afterStore = hands(f);
        var grid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(afterStore.stackCopy("left").orElseThrow()).orElseThrow();
        require(grid.entries().size() == 1 && grid.entries().getFirst().token().equals(pillToken)
                && afterStore.token("right").isEmpty(), "one pill token now belongs to the canister cell");
        var taken = BodyHeldGridTransfer.transferOwned(f.body, "left", hostToken, "right", pillToken,
                afterStore.revision(), new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(anchorX, 0),
                null, null, null, false, () -> true);
        require(taken == BodyHeldGridTransfer.Result.SUCCESS, "existing grid-to-hand owner transaction extracts pill");
        LiveHands finalState = hands(f);
        require(finalState.token("right").filter(pillToken::equals).isPresent()
                && finalState.stackCopy("right").orElseThrow().is(ModItems.PILL)
                && com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(finalState.stackCopy("left").orElseThrow()).orElseThrow().entries().isEmpty(),
                "retrieved pill retains its exact token and can be used normally");
        h.succeed();
    }

    @GameTestTags({"hands-liquid", "beaker"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 80)
    public static void resolvedBeakerDrinkConsumesFiveUnitsAfterTwentyTicks(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.BEAKER.get()), 6);
        // Keep metabolism from consuming the freshly transferred dose on the same due tick.
        // The body's deterministic metabolism bucket differs across fixture/entity ordering.
        waitForBeakerSafeStart(h, f, () -> runResolvedBeakerDrinkAssertions(h, f));
    }

    private static void runResolvedBeakerDrinkAssertions(GameTestHelper h, Fixture f) {
        long start = h.getLevel().getGameTime();
        long sourceBefore = source(f);
        long stomachBefore = stomachWater(f.body);
        require(!com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM.isDue(start + 20, f.body.getId()),
                "fixture avoids the body's metabolism bucket at the exact drink completion tick");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 90, () -> true).name().equals("OK"),
                "resolved drink policy starts the existing timed ingestion operation");
        var operation = f.body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(o -> o.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
        require(operation.durationTicks() == 20 && operation.dueTick() == start + 20,
                "resolved beaker profile retains its 5u/20-tick source accounting");
        h.runAfterDelay(10, () -> require(source(f) == 600, "beaker remains unchanged before its 20-tick deadline"));
        h.runAfterDelay(23, () -> {
            require(sourceBefore - source(f) == 500 && source(f) == 100,
                    "resolved 5u serving removes exactly 500 cents from the 6u source");
            long stomachDelta = stomachWater(f.body) - stomachBefore;
            require(stomachDelta >= 450 && stomachDelta <= 500,
                    "completed serving reaches actual stomach; up to 50 cents may be metabolized at the adjacent tick; delta="
                            + stomachDelta + " gameTime=" + h.getLevel().getGameTime());
            h.succeed();
        });
    }

    private static long stomachWater(Villager body) {
        return ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider
                .get(body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f));
    }

    private static void waitForBeakerSafeStart(GameTestHelper helper, Fixture fixture, Runnable action) {
        long start = helper.getLevel().getGameTime();
        if (!com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM
                .isDue(start + 20, fixture.body.getId())) {
            action.run();
        } else {
            helper.runAfterDelay(1, () -> waitForBeakerSafeStart(helper, fixture, action));
        }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void heldTokenMayMoveHandsAndBodyWithoutCancelling(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 2, () -> true);
        LiveHands before = hands(f);
        var moved = before.transferWhole(before.revision(), "left", "right", new ItemToken(f.token));
        require(moved.succeeded(), "whole item moved to other hand"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), moved.state());
        var selectRight = moved.state().selectActive(moved.state().revision(), "right");
        require(selectRight.isPresent(), "different active hand selected"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), selectRight.orElseThrow());
        var selectLeft = selectRight.orElseThrow().selectActive(selectRight.orElseThrow().revision(), "left");
        require(selectLeft.isPresent(), "original active hand selected again"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), selectLeft.orElseThrow());
        require(hands(f).token("right").filter(f.token::equals).isPresent() && hands(f).activeHand().equals("left"),
                "original token remains in right while active hand returns left");
        h.runAfterDelay(8, () -> f.body.setPos(f.body.getX() + .2, f.body.getY(), f.body.getZ() + .5));
        h.runAfterDelay(22, () -> { require(source(f) == 500, "original token consumed after transfer, selection change, and movement"); h.succeed(); });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void leavingHandsCancelsHeldIngestion(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 3, () -> true);
        ItemStack droppedStack = hands(f).stackCopy("left").orElseThrow().copy();
        AtomicReference<java.util.UUID> droppedId = new AtomicReference<>();
        require(BodyLooseThrow.throwOwned(f.body, "left", f.token, hands(f).revision(), () -> true, droppedId::set).name().equals("SUCCESS"), "whole held item dropped through owner transfer seam");
        require(hands(f).stackCopy("left").isEmpty(), "owner hand emptied by throw");
        h.runAfterDelay(2, () -> {
            var dropped = h.getLevel().getEntity(droppedId.get());
            require(dropped instanceof net.minecraft.world.entity.item.ItemEntity item && !item.isRemoved()
                    && item.getUUID().equals(droppedId.get()) && ItemStack.matches(droppedStack, item.getItem())
                    && ReagentUnits.fromFloat(item.getItem().get(ModDataComponents.REAGENT.get()).contents().getOrDefault(WATER, 0f)) == 1000,
                    "actual dropped item retains exact source contents");
            require(source(f) == 0 && cancelled(f), "dropped source cancels without consuming held source"); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void authorityLossCancelsAndStaleDuplicateCannotCancel(GameTestHelper h) {
        Fixture f = fixture(h, 10); AtomicBoolean authority = new AtomicBoolean(true); start(f, 4, authority::get);
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision - 1, 4, authority::get).name().equals("DENIED"), "stale revision duplicate denied");
        require(active(f), "denial preserves original active operation");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 5, authority::get).name().equals("OK"), "fresh same-gesture request cancels operation");
        require(cancelled(f), "duplicate toggle cancellation recorded");
        Fixture lost = fixture(h, 10); AtomicBoolean live = new AtomicBoolean(true); start(lost, 6, live::get); live.set(false);
        h.runAfterDelay(2, () -> { require(source(lost) == 1000 && cancelled(lost), "authority loss cancels without dose"); h.succeed(); });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void humanAtResolvedHydrationCapCannotStartHeldDrink(GameTestHelper h) {
        Fixture f = fixture(h, 10);
        var level = (net.minecraft.server.level.ServerLevel) f.body.level();
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(f.body, level);
        var thirstBridge = com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST;
        var priorThirst = com.juicyslew.moonstation14.ms14.MS14Provider.get(f.body, thirstBridge).thirst();
        com.juicyslew.moonstation14.ms14.MS14Provider.updateIfChanged(f.body, thirstBridge,
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(priorThirst),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(600f)));
        require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(f.body),
                "Human uses resolved 600-unit hydration cap");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 70, () -> true).name().equals("DENIED"),
                "resolved DRINK held source is rejected at maximum hydration");
        require(source(f) == 1000 && state(f) == null,
                "denied held drink leaves source untouched and creates no operation");
        h.succeed();
    }

    @GameTestTags({"chemmaster-output", "ingestion", "timed-interaction"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void wholeDosePillStillCompletesAtResolvedHydrationCap(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.PILL.get()), 12);
        var level = (net.minecraft.server.level.ServerLevel) f.body.level();
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(f.body, level);
        var thirstBridge = com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST;
        var priorThirst = com.juicyslew.moonstation14.ms14.MS14Provider.get(f.body, thirstBridge).thirst();
        com.juicyslew.moonstation14.ms14.MS14Provider.updateIfChanged(f.body, thirstBridge,
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(priorThirst),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(600f)));
        require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(f.body),
                "Human is at resolved hydration cap");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 71, () -> true).name().equals("OK"),
                "resolved FOOD-kind whole-dose pill is not blocked by drink hydration policy");
        h.runAfterDelay(13, () -> {
            require(hands(f).token("left").isEmpty() && source(f) == 0,
                    "pill is consumed as one committed whole-source dose at hydration cap");
            require(ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider
                    .get(f.body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f)) > 0,
                    "pill solution reaches the stomach");
            require(!active(f), "single-use pill does not repeat");
            h.succeed();
        });
    }

    @GameTestTags({"botany", "botany-food"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 55)
    public static void registeredActorEatsHarvestedTomatoOnlyAtTimedCompletion(GameTestHelper h) {
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture
                .register((net.minecraft.server.level.ServerLevel) h.getLevel(), "tomato-food");
        h.runAtTickTime(2, () -> {
            try {
                var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var isolatedPos = h.absolutePos(new BlockPos(2, 1, 2)).getCenter();
                body.setPos(isolatedPos.x, isolatedPos.y, isolatedPos.z);
                var initialStomach = com.juicyslew.moonstation14.ms14.MS14Provider.get(body,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
                var stomachBefore = new java.util.HashMap<String, Long>();
                initialStomach.forEach((key, value) -> stomachBefore.put(key.toString(), ReagentUnits.fromFloat(value)));
                var seedId = com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("tomato");
                ItemStack tomato = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(seedId, 10f);
                var source = tomato.get(ModDataComponents.REAGENT.get()).centContents();
                long dose = ReagentUnits.total(source.values());
                require(dose == 441, "tomato harvest carries its actual generated 441-cent chemistry");
                var hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = "ready-tomato-food";
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, tomato), "tomato assigned through live hands owner");
                waitForTomatoSafeCompletion(h, fixture, active, body, tomato, source, hand, token, stomachBefore);
            } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
        });
    }

    private static void waitForTomatoSafeCompletion(GameTestHelper h,
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.Active active,
            net.minecraft.world.entity.LivingEntity body, ItemStack tomato,
            Map<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> source,
            String hand, String token, Map<String, Long> stomachBefore) {
        long nextMetabolism = h.getLevel().getGameTime() + 10;
        if (!com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM.isDue(nextMetabolism, body.getId())) {
            h.runAfterDelay(1, () -> waitForTomatoSafeCompletion(h, fixture, active, body, tomato, source, hand, token, stomachBefore));
            return;
        }
        try {
                // Align only this fixture's request: the next metabolism bucket is 10 ticks
                // away, then the completion proof at +22 is 8 ticks after that bucket.
                var issued = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(), 2));
                require(issued.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "fresh inventory proof issued");
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.requireFreshQuery(fixture.player());
                require(issued.hands().stream().anyMatch(slot -> slot.id().equals(hand) && token.equals(slot.token())), "request token is freshly proven");
                while (fixture.pollOutbound() != null) { }
                var ack = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_IN_HAND,
                                hand, active.epoch(), 3, issued.revision(), null, token));
                require(ack.accepted(), "authenticated held USE_IN_HAND admitted food ingestion");
                var initial = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(token.equals(initial.token(hand).orElse(null)) && ItemStack.matches(tomato, initial.stackCopy(hand).orElseThrow()),
                        "timed start does not consume or alter the harvest");
                var operation = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .filter(op -> op.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
                require(operation.durationTicks() == 20, "resolved edible profile uses one-second completion");
                h.runAfterDelay(19, () -> {
                    var at19 = com.juicyslew.moonstation14.ms14.MS14Provider.get(body,
                            com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
                    require(token.equals(((LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())).token(hand).orElse(null)),
                            "harvest remains held before completion; t=" + h.getLevel().getGameTime());
                    String preCompletion = at19.entrySet().stream().map(e -> e.getKey() + "=" + ReagentUnits.fromFloat(e.getValue()))
                            .collect(java.util.stream.Collectors.joining(","));
                    var before = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    require(token.equals(before.token(hand).orElse(null)), "harvest remains held before completion");
                    h.runAfterDelay(3, () -> {
                        try {
                            var after = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                            require(after.token(hand).isEmpty(), "destroy-on-empty harvest token is removed after committed dose");
                            var stomach = com.juicyslew.moonstation14.ms14.MS14Provider.get(body,
                                    com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
                            for (var reagent : source.entrySet())
                                require(ReagentUnits.fromFloat(stomach.getOrDefault(reagent.getKey(), 0f)) == reagent.getValue(),
                                        "actual generated tomato reagent reaches stomach exactly: " + reagent.getKey()
                                                + " expected=" + reagent.getValue() + " actual="
                                                + ReagentUnits.fromFloat(stomach.getOrDefault(reagent.getKey(), 0f))
                                                + " baseline=" + stomachBefore + " preCompletion=" + preCompletion
                                                + " t=" + h.getLevel().getGameTime() + " body=" + body.getUUID()
                                                + " pos=" + body.position());
                            h.succeed();
                        } finally { fixture.close(); }
                    });
                });
        } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
    }

    @GameTestTags({"botany", "botany-food"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void registeredHumanRejectsHarvestedWheatWithoutChangingHeldSource(GameTestHelper h) {
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture
                .register((net.minecraft.server.level.ServerLevel) h.getLevel(), "wheat-food");
        h.runAtTickTime(2, () -> {
            try {
                var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                ItemStack wheat = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(
                        com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"), 10f);
                var hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = "ready-wheat-food";
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, wheat), "wheat assigned through live hands owner");
                var issued = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(), 2));
                require(issued.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "fresh inventory proof issued");
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.requireFreshQuery(fixture.player());
                while (fixture.pollOutbound() != null) { }
                var ack = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_IN_HAND,
                                hand, active.epoch(), 3, issued.revision(), null, token));
                require(!ack.accepted(), "ordinary human admission rejects special-digestion wheat");
                var after = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(token.equals(after.token(hand).orElse(null)) && ItemStack.matches(wheat, after.stackCopy(hand).orElseThrow()),
                        "rejected wheat source and exact token remain unchanged");
                require(body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()) == null
                        || body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().isEmpty(),
                        "rejection creates no timed ingestion operation");
                h.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTestTags({"ingestion", "botany", "botany-food"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void wheatWholeSourceTransactionRejectsCurrentSpecialDigestionProfile(GameTestHelper h) {
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture
                .register((net.minecraft.server.level.ServerLevel) h.getLevel(), "wheat-whole-source");
        h.runAtTickTime(2, () -> {
            try {
                var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                ItemStack wheat = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(
                        com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"), 10f);
                var hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = "whole-wheat-food";
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, wheat), "actual wheat put through live hands owner");
                hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var held = hands.stackCopy(hand).orElseThrow();
                var profile = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.serverLookup(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()));
                require(profile != null && profile.edible().requiresSpecialDigestion(), "whole-source case has resolved flagged profile");
                var reagentBefore = held.get(ModDataComponents.REAGENT.get());
                require(reagentBefore != null, "harvested wheat has materialized source chemistry");
                long actualSourceCents = ReagentUnits.total(reagentBefore.centContents().values());
                require(actualSourceCents == 650L, "generated wheat composition has the expected 650-cent source");
                var stomachBefore = MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap();
                long stomachUsed = ReagentUnits.total(ReagentUnits.fromMap(stomachBefore).values());
                require(actualSourceCents > 0 && actualSourceCents <= com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability.STOMACH_CAPACITY_CENTS - stomachUsed,
                        "whole-source request fits the actual current stomach capacity");
                var result = BodyLiquidInteraction.consumeTimedEdibleOwned(body, hand, token, hands.revision(),
                        Math.toIntExact(actualSourceCents), () -> true, profile.completionSound(), profile.completionVolumeDb(), profile.completionPitchVariation());
                require(result == BodyLiquidInteraction.Result.DENIED, "whole-source transaction denies wheat at final guard");
                var afterHands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(token.equals(afterHands.token(hand).orElse(null))
                                && ItemStack.matches(held, afterHands.stackCopy(hand).orElseThrow()),
                        "denied whole-source transaction retains source contents and ledger token");
                require(reagentBefore.equals(afterHands.stackCopy(hand).orElseThrow().get(ModDataComponents.REAGENT.get())),
                        "denied whole-source transaction retains the exact generated reagent map");
                require(stomachBefore.equals(MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap()),
                        "denied whole-source transaction leaves stomach unchanged");
                h.succeed();
            } finally { fixture.close(); }
        });
    }

    private static Fixture fixture(GameTestHelper h, float units) {
        return fixtureWithItem(h, new ItemStack(ModItems.BOTTLE.get()), units);
    }
    private static Fixture fixtureWithItem(GameTestHelper h, ItemStack source, float units) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level); require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true); body.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "eligible body initialized");
        LiveHands initial = hands(body);
        source.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        var put = initial.putWhole(initial.revision(), "left", new ItemToken("bottle"), source); require(put.succeeded(), "filled source inserted");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), put.state());
        return new Fixture(body, put.state().token("left").orElseThrow(), put.state().revision());
    }
    private static void start(Fixture f, long seq, java.util.function.BooleanSupplier auth) {
        require(BodyTimedIngestionService.startHeldOwned(f.body,"left",f.token,f.revision,seq,auth).name().equals("OK"), "held ingestion started");
        require(source(f)==1000,"start does not transfer immediately");
    }
    private static LiveHands hands(Fixture f) { return hands(f.body); }
    private static LiveHands hands(Villager b) { return b.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static long source(Fixture f) { var r=f.body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).stackCopy("left"); if(r.isEmpty()) r=hands(f).stackCopy("right"); return r.map(s -> { var c=s.get(ModDataComponents.REAGENT.get()); return c==null?0L:ReagentUnits.fromFloat(c.contents().getOrDefault(WATER,0f)); }).orElse(0L); }
    private static TimedOperationState state(Fixture f) { return f.body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()); }
    private static boolean active(Fixture f) { return state(f).operations().stream().anyMatch(o -> o.phase()==TimedOperation.Phase.ACTIVE); }
    private static boolean cancelled(Fixture f) { return state(f).operations().stream().anyMatch(o -> o.phase()==TimedOperation.Phase.CANCELLED); }
    private record Fixture(Villager body, String token, long revision) { }
    private static void require(boolean b,String m) { if(!b) throw new GameTestAssertException(m); }
}
