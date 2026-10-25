package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native coverage of resolved Udder capability enrollment and its persistent production owner. */
@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "character", "hands"})
public final class UdderMilkingGameTests {
    private UdderMilkingGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void registeredCowUsesResolvedUdderAndProductionWaitsUntilDue(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var cowType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("cow");
        require(cowType != null, "registered cow host exists");
        var cow = cowType.get().create(level);
        require(cow != null, "cow creates");
        cow.setPos(helper.absolutePos(new BlockPos(4, 2, 4)).getX() + .5,
                helper.absolutePos(new BlockPos(4, 2, 4)).getY(), helper.absolutePos(new BlockPos(4, 2, 4)).getZ() + .5);
        require(level.addFreshEntity(cow), "cow enters live world");
        // Cow inherits the animal hunger policy (okay threshold 50); establish a
        // deterministic threshold-boundary value instead of random join nutrition.
        cow.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(49)));
        var resolved = UdderSystem.capability(cow).orElseThrow(() -> new AssertionError("cow prototype resolves Udder"));
        require(resolved.growthDelaySeconds() == 30f && resolved.solutionCapacity() == 250f,
                "cow uses resolved prototype overrides, not service constants");
        UdderSystem.tick(cow);
        var initialized = cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get());
        require(initialized != null && initialized.available() == 0
                        && initialized.nextGrowthTick() == level.getGameTime() + UdderSystem.delayTicks(resolved),
                "first observation initializes persistent next-production deadline without creating milk");
        cow.setData(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get(),
                new UdderAttachment(0, level.getGameTime(), resolved.solutionName()));
        UdderSystem.tick(cow);
        require(cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get()).available() == 0,
                "below resolved animal okay threshold does not produce milk");
        cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get()).setHunger(50);
        cow.setData(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get(),
                new UdderAttachment(0, level.getGameTime(), resolved.solutionName()));
        UdderSystem.tick(cow);
        var produced = cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get());
        require(produced.available() == resolved.quantity(), "due healthy production adds configured quantity once");
        UdderSystem.tick(cow);
        require(cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get()).available()
                        == resolved.quantity(), "repeated same-tick query cannot duplicate production");
        cow.kill();
        UdderSystem.tick(cow);
        require(cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get()).available()
                        == resolved.quantity(), "dead cow produces no milk");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 240)
    public static void alternateTargetMilkingUsesAuthenticatedTimedRequestAndConservesMilk(GameTestHelper helper) {
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture.register(helper.getLevel(), "udder-alt-use");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                 var body = owner.body(); var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
                 // Give this fixture private space rather than sharing the lifecycle body's default origin.
                 var bodyAnchor = helper.absolutePos(new BlockPos(5, 1, 5));
                 body.moveTo(bodyAnchor.getX() + .5, bodyAnchor.getY(), bodyAnchor.getZ() + .5);
                 // Keep this autonomous NPC and its interaction owner inside a real, unobstructed stall.
                 // The NPC behavior runtime does not honor vanilla setNoAi, so the timed request must
                 // remain valid through ordinary movement rather than relying on a frozen test entity.
                 int stallX = net.minecraft.util.Mth.floor(body.getX());
                 int stallY = net.minecraft.util.Mth.floor(body.getY()) - 1;
                 int stallZ = net.minecraft.util.Mth.floor(body.getZ());
                 for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 2; dz++) {
                     if (Math.abs(dx) != 1 && dz != -1 && dz != 2) continue;
                     for (int dy = 0; dy <= 1; dy++) {
                         level.setBlock(new net.minecraft.core.BlockPos(stallX + dx, stallY + dy, stallZ + dz),
                                 net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), 3);
                     }
                 }
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var hand = hands.activeHand();
                var container = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER.get());
                var granted = hands.grant(hands.revision(), container);
                require(granted.changed() && com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.commitOwned(body, hands, granted.state()),
                        "refillable beaker enters authoritative hand");
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(), 2)).reason()
                                == com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "fresh authenticated hand snapshot follows fixture ownership update");
                var current = granted.state(); var token = current.token(hand).orElseThrow();
                 var cowType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("cow");
                 var cow = cowType.get().create(level); require(cow != null, "cow creates");
                 cow.setNoAi(true);
                 cow.setPos(body.getX(), body.getY(), body.getZ() + 1.25);
                require(level.addFreshEntity(cow) && UdderSystem.capability(cow).isPresent(), "live cow resolves Udder");
                cow.setData(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get(),
                        new UdderAttachment(25, level.getGameTime() + 1000, UdderSystem.capability(cow).orElseThrow().solutionName()));
                var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow();
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.inRange(body, cow, 2.5), "cow is in interaction reach");
                var request = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ALTERNATE_TARGET,
                        hand, authority.epoch(), 101, current.revision(), cow.getUUID(), token);
                var response = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(), request);
                require(response.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "authenticated Alt+E route starts timed milking");
                 var timed = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                 require(timed != null && !timed.operations().isEmpty(), "milking waits for the five-second interaction");
                  var finished = new boolean[1];
                  com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeOnEachTick(helper, () -> {
                      // During the intentional wall-occlusion phase, monitor physical range without requiring LOS.
                      if (!finished[0]) require(cow.isAlive()
                                      && com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.rootWithinReach(body, cow, 2.5),
                              "physical corridor keeps autonomous cow within root range; body="
                                      + body.position() + " cow=" + cow.position());
                  });
                com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.damage(body, 1);
                require(cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get()).available() == 25f,
                        "damage cancellation cannot harvest milk");
                require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).orElseThrow().getCount() == 1, "held container remains owned after cancellation");
                var fresh = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var second = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ALTERNATE_TARGET,
                        hand, authority.epoch(), 102, fresh.revision(), cow.getUUID(), token);
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(), second).reason()
                                == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "fresh Alt+E request restarts an admitted milking interaction");
                var wall = net.minecraft.core.BlockPos.containing(body.getEyePosition().lerp(cow.getEyePosition(), .5));
                helper.runAtTickTime(3, () -> level.setBlock(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3));
                helper.runAtTickTime(108, () -> {
                    try {
                        var udder = cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get());
                        var held = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).orElseThrow();
                        var bridge = com.juicyslew.moonstation14.ms14.reagent.ReagentSystem.bridgeForHolder(held);
                        var contents = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(held, bridge);
                        var milk = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("milk");
                        require(udder.available() == 25f && contents.getMap().getOrDefault(milk, 0f) == 0f,
                                "new wall occlusion cancels timed completion without mutating source or recipient");
                        require(level.getEntity(cow.getUUID()) == cow && cow.isAlive(), "milking retains canonical source animal");
                        level.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        var third = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ALTERNATE_TARGET,
                                hand, authority.epoch(), 103, body.getExistingDataOrNull(
                                        com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision(),
                                cow.getUUID(), token);
                         var visibleRequest = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(), third);
                         require(visibleRequest.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                                 "clear line of sight allows fresh timed milking request; reason=" + visibleRequest.reason()
                                         + " body=" + body.position() + " cow=" + cow.position());
                        helper.runAtTickTime(212, () -> {
                            try {
                                var remaining = cow.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.UDDER.get());
                                var finishedStack = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                        .stackCopy(hand).orElseThrow();
                                var target = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(finishedStack,
                                        com.juicyslew.moonstation14.ms14.reagent.ReagentSystem.bridgeForHolder(finishedStack));
                                 float received = target.getMap().getOrDefault(milk, 0f);
                                 float conserved = remaining.available() + received;
                                 require(remaining.available() == 0 && received == 25f && conserved == 25f,
                                         "visible timed completion expected full 25-unit transfer; source="
                                                 + remaining.available() + " recipient=" + received + " total=" + conserved);
                                 finished[0] = true;
                                 helper.succeed();
                            } catch (Throwable failure) { throw new net.minecraft.gametest.framework.GameTestAssertException("visible milk completion: " + failure); }
                        });
                    } catch (Throwable failure) { throw new net.minecraft.gametest.framework.GameTestAssertException("milk completion: " + failure); }
                });
            } catch (Throwable failure) { throw new net.minecraft.gametest.framework.GameTestAssertException("authenticated Udder fixture: " + failure); }
        });
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
