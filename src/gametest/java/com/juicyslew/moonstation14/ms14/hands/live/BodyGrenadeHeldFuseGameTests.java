package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class BodyGrenadeHeldFuseGameTests {
    private BodyGrenadeHeldFuseGameTests() { }

    public static void heldFuseTicksAndExpiresOnce(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        body.setNoGravity(true);
        LiveHands hands = LiveHands.initialize(body).orElseThrow();
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get());
        long deadline = level.getGameTime() + 60;
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                deadline, false, false));
        var inserted = hands.putWhole(hands.revision(), "left", new ItemToken("held-fuse"), grenade);
        require(inserted.succeeded(), "grenade inserted");
        hands = inserted.state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), hands);
        LiveHands initial = hands;
        BodyGrenadeHeldFuseService.tickHand(body, level, hands, "left", () -> true);
        LiveHands tickZero = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(tickZero != initial && tickZero.revision() == initial.revision() + 1,
                "initial tick stores primed fuse state");
        GrenadeFuseData tickZeroFuse = tickZero.stackCopy("left").orElseThrow()
                .get(ModDataComponents.GRENADE_FUSE.get());
        require(tickZeroFuse != null && tickZeroFuse.beepedAtPrime() && !tickZeroFuse.beepedAtForty(),
                "tick zero persists prime beep without marking forty-tick beep");

        BlockPos wall = body.blockPosition().east();
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        BlockPos bodyPosition = body.blockPosition();
        helper.runAfterDelay(40, () -> {
            require(body.blockPosition().equals(bodyPosition), "body moved before forty-tick beep: "
                    + body.blockPosition() + " expected " + bodyPosition);
            BodyGrenadeHeldFuseService.tickHand(body, level, tickZero, "left", () -> true);
            LiveHands secondBeep = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(secondBeep.revision() == tickZero.revision(), "second beep does not invalidate hand snapshot");
            GrenadeFuseData secondBeepFuse = secondBeep.stackCopy("left").orElseThrow()
                    .get(ModDataComponents.GRENADE_FUSE.get());
            require(secondBeepFuse != null && secondBeepFuse.beepedAtForty(), "forty-tick beep state persisted");
            require(secondBeep.token("left").equals(tickZero.token("left")), "second beep retains ownership token");
            BodyGrenadeHeldFuseService.tickHand(body, level, secondBeep, "left", () -> true);
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == secondBeep,
                    "repeat same tick does not duplicate update or beep state");
            // GameTest scheduling may run the callback on the boundary tick itself. Wait
            // beyond the deadline and still guard against server-time scheduling drift.
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 25, () -> {
                require(body.blockPosition().equals(bodyPosition), "body moved before detonation: "
                        + body.blockPosition() + " expected " + bodyPosition);
                long now = level.getGameTime();
                LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                GrenadeFuseData currentFuse = current == null ? null : current.stackCopy("left")
                        .map(stack -> stack.get(ModDataComponents.GRENADE_FUSE.get())).orElse(null);
                require(now >= deadline, "held fuse callback ran before deadline: now=" + now
                        + ", deadline=" + deadline + ", phase=" + (currentFuse == null ? "<missing>" : currentFuse.phase())
                        + ", revision=" + (current == null ? "<missing>" : current.revision()));
                BodyGrenadeHeldFuseService.tickHand(body, level, secondBeep, "left", () -> true);
                LiveHands expired = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(expired.stackCopy("left").isEmpty() && expired.token("left").isEmpty(),
                        "body-owned item is removed before explosion");
                require(level.getBlockState(wall).isAir(), "blast did not breach steel wall: body="
                        + body.blockPosition() + ", wall=" + wall + ", wallState=" + level.getBlockState(wall));
                BodyGrenadeHeldFuseService.tickHand(body, level, expired, "left", () -> true);
                require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == expired,
                        "repeated expiry cannot detonate again");
                helper.succeed();
            });
        });
    }

    public static void invalidAuthorityDoesNotPublishHeldFuse(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        LiveHands hands = LiveHands.initialize(body).orElseThrow();
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get());
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                level.getGameTime() + 60, false, false));
        hands = hands.putWhole(hands.revision(), "left", new ItemToken("denied"), grenade).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), hands);
        BodyGrenadeHeldFuseService.tickHand(body, level, hands, "left", () -> false);
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                "failed authority revalidation fails closed");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
