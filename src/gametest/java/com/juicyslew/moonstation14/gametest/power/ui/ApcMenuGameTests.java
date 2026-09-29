package com.juicyslew.moonstation14.gametest.power.ui;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleRequest;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleResponse;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApcMenuGameTests {
    private ApcMenuGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openedServerMenuAllowsOneToggleAndRefreshesAllViewers(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        FakePlayer first = player(level, "apc-first", pos);
        FakePlayer second = player(level, "apc-second", pos);
        require(level.addFreshEntity(first) && level.addFreshEntity(second), "FakePlayers must join the GameTest level");
        installMenu(first, pos, device);
        installMenu(second, pos, device);
        require(first.containerMenu instanceof ApcMenu && second.containerMenu instanceof ApcMenu,
                "both viewers must hold real server-side APC menus");
        ApcMenu firstMenu = (ApcMenu) first.containerMenu;
        ApcMenu secondMenu = (ApcMenu) second.containerMenu;
        ApcToggleResponse invalidSession = ApcMenuService.apply(first, new ApcToggleRequest(firstMenu.containerId,
                UUID.randomUUID(), firstMenu.currentRevision(), 0, false));
        require(!invalidSession.accepted() && !invalidSession.hasSnapshot() && device.breakerClosed(),
                "request with a different immutable session token is rejected without leaking menu state");
        long initialRevision = firstMenu.currentRevision();
        var request = new ApcToggleRequest(firstMenu.containerId, firstMenu.session(), initialRevision, 1, false);
        ApcToggleResponse accepted = ApcMenuService.apply(first, request);
        require(accepted.accepted() && accepted.hasSnapshot() && !accepted.breakerClosed()
                        && accepted.revision() == initialRevision + 1,
                "current desired-state intent is accepted with authoritative result");
        require(!device.breakerClosed() && firstMenu.displayedBreaker() == 0 && secondMenu.displayedBreaker() == 0,
                "both live menus read the authoritative BE state after mutation");
        require(firstMenu.displayedRevision() == initialRevision + 1
                        && secondMenu.displayedRevision() == initialRevision + 1,
                "full-width revision refreshes through menu data slots");
        ApcToggleResponse replay = ApcMenuService.apply(first, request);
        require(!replay.accepted() && replay.hasSnapshot() && !device.breakerClosed()
                        && replay.revision() == initialRevision + 1,
                "replayed stale request is explicitly rejected with current authorized snapshot");
        ApcToggleResponse idempotent = ApcMenuService.apply(first, new ApcToggleRequest(firstMenu.containerId,
                firstMenu.session(), firstMenu.currentRevision(), 2, false));
        require(idempotent.accepted() && device.breakerRevision() == initialRevision + 1,
                "repeated desired state is accepted idempotently without a second mutation");
        require(ApcMenuService.apply(first, new ApcToggleRequest(firstMenu.containerId, firstMenu.session(),
                firstMenu.currentRevision(), 3, true)).accepted(), "breaker can be reclosed before overload proof");
        device.observeActualApcOutput(1, 1, false, 0);
        device.observeActualApcOutput(2, 2, true, 20_001);
        device.observeActualApcOutput(3, 3, true, 20_001);
        for (long tick = 4; tick <= 64; tick++)
            device.observeActualApcOutput(tick, tick, true, 20_001);
        require(device.tripLatched() && firstMenu.displayedTripLatched() && secondMenu.displayedTripLatched(),
                "trip latch is exposed by standard menu data to every open viewer");
        ApcToggleResponse reclosed = ApcMenuService.apply(first, new ApcToggleRequest(firstMenu.containerId,
                firstMenu.session(), firstMenu.currentRevision(), 4, true));
        require(reclosed.accepted() && reclosed.hasSnapshot() && !reclosed.tripLatched()
                        && firstMenu.displayedBreaker() == 1 && secondMenu.displayedBreaker() == 1
                        && !firstMenu.displayedTripLatched() && !secondMenu.displayedTripLatched(),
                "manual reclose clears and synchronizes the trip latch for all open viewers");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleMenuRejectsRemovedOrReplacedDeviceAndContextChanges(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        FakePlayer player = player(level, "apc-stale", pos);
        require(level.addFreshEntity(player), "FakePlayer must join GameTest level");
        installMenu(player, pos, device);
        ApcMenu menu = (ApcMenu) player.containerMenu;
        ApcToggleRequest request = new ApcToggleRequest(menu.containerId, menu.session(), menu.currentRevision(), 0, false);
        player.setPos(pos.getX() + 30.5, pos.getY() + .5, pos.getZ() + .5);
        require(!ApcMenuService.apply(player, request).hasSnapshot() && device.breakerClosed(), "out-of-range request is rejected");
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(!ApcMenuService.apply(player, request).hasSnapshot() && device.breakerClosed(), "removed device cannot be toggled");
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        require(!ApcMenuService.apply(player, request).hasSnapshot(), "replacement BE does not inherit old menu identity");
        helper.succeed();
    }

    private static FakePlayer player(ServerLevel level, String name, BlockPos pos) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        player.getAbilities().instabuild = true;
        return player;
    }

    private static void installMenu(FakePlayer player, BlockPos pos, PowerDeviceBlockEntity device) {
        player.containerMenu = new ApcMenu(1, player.getInventory(), pos, UUID.randomUUID(),
                player.serverLevel(), device);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
