package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "give", "command"})
public final class BodyGiveCoreGameTests {
    private BodyGiveCoreGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void occupiedActiveRoutesToOtherAvailableHand(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands.WholeResult occupied = before.putWhole(before.revision(), before.activeHand(),
                new ItemToken("occupied"), new ItemStack(Items.DIRT, 64));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied.state());
        ItemStack grant = new ItemStack(Items.STONE, 4);
        require(BodyGiveService.giveTrustedBody(body, grant, () -> true), "grant accepted");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String other = after.handIds().stream().filter(id -> !id.equals(after.activeHand())).findFirst().orElseThrow();
        require(after.stackCopy(other).filter(stack -> stack.is(Items.STONE) && stack.getCount() == 4).isPresent(),
                "second available hand receives chunk");
        require(after.stackCopy(after.activeHand()).filter(stack -> stack.is(Items.DIRT) && stack.getCount() == 64).isPresent(),
                "existing active contents unchanged");
        require(grant.isEmpty(), "grant input cleared only after complete transfer");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void emptyActiveHandReceivesIndependentCopyBeforeInputClears(GameTestHelper helper) {
        Villager body = body(helper);
        ItemStack chunk = new ItemStack(Items.STONE, 4);
        ItemStack expected = chunk.copy();
        require(BodyGiveService.giveTrustedBody(body, chunk, () -> true), "grant accepted");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after.stackCopy(after.activeHand()).filter(stack -> ItemStack.matches(stack, expected)).isPresent(),
                "body owns exact grant copy");
        require(chunk.isEmpty(), "native chunk input is consumed after commit");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void nativeTenItemChunksConserveThirteenBrutepacksAcrossHands(GameTestHelper helper) {
        Villager body = body(helper);
        ItemStack ten = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BRUTEPACK.get(), 10);
        ItemStack three = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BRUTEPACK.get(), 3);
        require(ten.getMaxStackSize() == 10, "registered brute pack native stack cap");
        require(BodyGiveService.giveTrustedBody(body, ten, () -> true), "first native chunk accepted");
        require(BodyGiveService.giveTrustedBody(body, three, () -> true), "second native chunk accepted");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        int held = hands.handIds().stream().map(hands::stackCopy).flatMap(java.util.Optional::stream)
                .filter(stack -> stack.is(com.juicyslew.moonstation14.item.ModItems.BRUTEPACK.get()))
                .mapToInt(ItemStack::getCount).sum();
        require(held == 13 && ten.isEmpty() && three.isEmpty(), "separate vanilla chunks conserve exact count 10+3");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void unsupportedNativeContainerCandidateFallsBackWholeToWorld(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE, 2))));
        require(!com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService
                .representable(before.grant(before.revision(), chest).state(), body), "native container payload is not representable in hand snapshot");
        ItemStack grant = chest.copy();
        require(BodyGiveService.giveTrustedBody(body, grant, () -> true), "world fallback accepted");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before,
                "unrepresentable candidate leaves exact hands snapshot unchanged");
        var drops = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                body.getBoundingBox().inflate(8), entity -> entity.getItem().is(Items.CHEST)
                        && entity.getItem().has(DataComponents.CONTAINER));
        require(drops.size() == 1 && drops.get(0).getItem().get(DataComponents.CONTAINER).stream().findAny().isPresent()
                        && grant.isEmpty(), "complete component-bearing item is world-owned");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void noHandPigReceivesWorldGrantWithoutMaterializingHands(GameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        require(BodyHandBootstrap.ensure(pig) == BodyHandBootstrap.Result.NO_HANDS, "native pig profile has no hands");
        ItemStack grant = new ItemStack(Items.STONE, 2);
        require(BodyGiveService.giveTrustedBody(pig, grant, () -> true), "no-hand body world grant accepted");
        require(!pig.hasData(ModDataAttachments.LIVE_HANDS.get()) && grant.isEmpty(), "no hand layout created and input consumed");
        var drops = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                pig.getBoundingBox().inflate(8), entity -> entity.getItem().is(Items.STONE) && entity.getItem().getCount() == 2);
        require(drops.size() == 1, "exact stack dropped at no-hand body position");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void fullHandsRouteRemainderToBodyWorldAndMergeToNativeCap(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack held = new ItemStack(Items.STONE, 62);
        held.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("preserved"));
        String active = before.activeHand();
        LiveHands.WholeResult first = before.putWhole(before.revision(), active, new ItemToken("merge"), held);
        String other = first.state().handIds().stream().filter(id -> !id.equals(active)).findFirst().orElseThrow();
        LiveHands.WholeResult second = first.state().putWhole(first.state().revision(), other,
                new ItemToken("full-other"), new ItemStack(Items.DIRT, 64));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), second.state());
        ItemStack grant = new ItemStack(Items.STONE, 5);
        grant.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("preserved"));
        require(BodyGiveService.giveTrustedBody(body, grant, () -> true), "grant accepted");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after.stackCopy(active).filter(stack -> stack.getCount() == 64
                && Component.literal("preserved").equals(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME))).isPresent(),
                "partial merge reaches native maximum and preserves components");
        var drops = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                body.getBoundingBox().inflate(8), entity -> entity.getItem().is(Items.STONE)
                        && entity.getItem().getCount() == 3);
        require(drops.size() == 1 && grant.isEmpty(), "remaining exact quantity is world-owned, input consumed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void failedPrecommitRevalidationLeavesHandsWorldAndInputUnchanged(GameTestHelper helper) {
        Villager body = body(helper);
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String active = before.activeHand();
        LiveHands.WholeResult first = before.putWhole(before.revision(), active, new ItemToken("existing-stone"),
                new ItemStack(Items.STONE, 62));
        String other = first.state().handIds().stream().filter(id -> !id.equals(active)).findFirst().orElseThrow();
        LiveHands.WholeResult second = first.state().putWhole(first.state().revision(), other,
                new ItemToken("other-full"), new ItemStack(Items.DIRT, 64));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), second.state());
        LiveHands committedBefore = second.state();
        ItemStack input = new ItemStack(Items.STONE, 5);
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        boolean success = BodyGiveService.giveTrustedBody(body, input, () -> checks.incrementAndGet() == 1);
        require(!success && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == committedBefore,
                "stale authority before hand commit preserves exact previous snapshot");
        require(input.getCount() == 5, "failed grant preserves parsed chunk count");
        require(helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                body.getBoundingBox().inflate(8), entity -> entity.getItem().is(Items.STONE)).isEmpty(),
                "failed revalidation removes the newly-created overflow owner");
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.PRESERVED
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).compatible(body),
                "joined body already has compatible live hands");
        return body;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
