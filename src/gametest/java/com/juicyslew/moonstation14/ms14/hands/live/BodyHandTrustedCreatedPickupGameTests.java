package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "hands-live"})
public final class BodyHandTrustedCreatedPickupGameTests {
    private BodyHandTrustedCreatedPickupGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags({"botany-fresh-recipient"})
    public static void trustedCreatedPickupUsesFreeHandsWithoutAimAndRejectsOccupiedHands(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-recipient");
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    ServerLevel level = (ServerLevel) helper.getLevel();
                    var body = ready.body();
                    var spawn = helper.absolutePos(new BlockPos(2, 1, 2));
                    body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
                    body.setYRot(0); // All packets are behind the body; no aim or ray is involved.
                    ItemStack packet = new ItemStack(ModItems.WHEAT_SEEDS.get(), 1);
                    var authority = HandActorAuthority.resolve(fixture.player()).orElseThrow();
                    var initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    require(initial != null && initial.handIds().size() >= 2, "ready body has two hands");
                    String firstHand = initial.handIds().get(0), secondHand = initial.handIds().get(1);
                    var first = publish(level, body, packet);
                    ItemStack wrongExpected = packet.copy(); wrongExpected.setCount(2);
                    require(BodyHandItemTransfer.pickupTrustedCreated(authority, first, wrongExpected, () -> true)
                                    == BodyHandItemTransfer.Result.DENIED,
                            "wrong expected stack rejected");
                    require(level.getEntity(first.getUUID()) == first && !first.isRemoved()
                                    && ItemStack.isSameItemSameComponents(first.getItem(), packet),
                            "wrong stack proof leaves published source untouched");
                    require(BodyHandItemTransfer.pickupTrustedCreated(authority, first, packet.copy(), () -> true)
                                    == BodyHandItemTransfer.Result.SUCCESS,
                            "first trusted packet acquired without targeting");
                    assertHeld(body, firstHand, packet, true);
                    require(first.isRemoved() && level.getEntity(first.getUUID()) == null,
                            "successful source is cleared and discarded");
                    var afterFirst = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    String firstToken = afterFirst.token(firstHand).orElseThrow();

                    var second = publish(level, body, packet);
                    require(BodyHandItemTransfer.pickupTrustedCreated(authority, second, packet.copy(), () -> true)
                                    == BodyHandItemTransfer.Result.SUCCESS,
                            "second trusted packet fills other free hand");
                    assertHeld(body, secondHand, packet, true);
                    var full = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    require(full.token(firstHand).filter(firstToken::equals).isPresent()
                                    && full.token(secondHand).filter(value -> !value.equals(firstToken)).isPresent(),
                            "both held stacks have distinct committed ownership tokens");
                    var third = publish(level, body, packet);
                    ItemStack thirdBefore = third.getItem().copy();
                    require(BodyHandItemTransfer.pickupTrustedCreated(authority, third, packet.copy(), () -> true)
                                    == BodyHandItemTransfer.Result.DENIED,
                            "third packet denied when all hands occupied");
                    require(level.getEntity(third.getUUID()) == third && !third.isRemoved()
                                    && ItemStack.isSameItemSameComponents(thirdBefore, third.getItem()),
                            "occupied-hand denial preserves exact world entity and stack");
                    helper.succeed();
                } catch (RuntimeException | Error failure) { throw failure; }
                finally { fixture.close(); }
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static LooseItemEntity publish(ServerLevel level, net.minecraft.world.entity.LivingEntity body, ItemStack stack) {
        var entity = LooseItemEntity.create(level, body.position().add(0, 0, -1), stack.copy());
        entity.setNoGravity(true);
        require(level.addFreshEntity(entity), "publish fresh output packet");
        return entity;
    }

    private static void assertHeld(net.minecraft.world.entity.LivingEntity body, String hand, ItemStack expected, boolean token) {
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(hands.stackCopy(hand).filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).isPresent(),
                "hand owns matching item, components, and count");
        require(!token || hands.token(hand).isPresent(), "hand publishes ownership token");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
