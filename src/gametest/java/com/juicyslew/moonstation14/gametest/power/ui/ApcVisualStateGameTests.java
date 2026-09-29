package com.juicyslew.moonstation14.gametest.power.ui;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApcVisualStateGameTests {
    private ApcVisualStateGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void visualSlotIsIndependentOfBreakerCommitAndUnsolvedDevicesStayUnknown(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "visual-slot"));
        UUID session = UUID.randomUUID();
        ApcMenu server = new ApcMenu(1, player.getInventory(), pos, session, level, device);
        FriendlyByteBuf opening = new FriendlyByteBuf(Unpooled.buffer());
        try {
            opening.writeBlockPos(pos);
            opening.writeUUID(session);
            ApcMenu client = new ApcMenu(1, player.getInventory(), opening);
            require(client.displayedVisualState() == ApcVisualState.UNKNOWN, "no initial slot yet");
            client.setData(7, 0);
            require(client.hasReceivedInitialSnapshot() && client.displayedVisualState() == ApcVisualState.UNKNOWN,
                    "initial breaker commit arrives before the independent visual slot");
            client.setData(8, ApcVisualState.CHARGING.ordinal());
            require(client.displayedVisualState() == ApcVisualState.CHARGING, "single valid word commits atomically");
            client.setData(8, -42);
            require(client.displayedVisualState() == ApcVisualState.UNKNOWN, "invalid wire word fails closed");
            client.setData(8, ApcVisualState.FULL.ordinal());
            client.setData(0, 0);
            client.setData(1, 1);
            client.setData(7, 1);
            require(client.displayedVisualState() == ApcVisualState.FULL,
                    "unchanged visual slot need not be resent after a breaker commit");
            client.setData(8, ApcVisualState.LACK.ordinal());
            require(client.displayedVisualState() == ApcVisualState.LACK, "new visual word follows breaker commit");
            client.setData(0, 1);
            client.setData(1, 2);
            client.setData(7, 2);
            require(client.displayedVisualState() == ApcVisualState.LACK,
                    "same visual enum survives another breaker revision without a slot-eight packet");
            require(server.displayedVisualState() == ApcVisualState.UNKNOWN
                            && PowerRuntime.apcVisualState(level, device) == ApcVisualState.UNKNOWN,
                    "unsolved APC has no invented supply or charge state");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            require(server.displayedVisualState() == ApcVisualState.UNKNOWN,
                    "removed device cannot inherit a displayed sample");
            client.removed(player);
        } finally {
            opening.release();
        }
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
