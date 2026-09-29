package com.juicyslew.moonstation14.gametest.power.ui;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleRequest;
import com.juicyslew.moonstation14.ms14.power.ui.ApcToggleResponse;
import com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApcMultiViewerGameTests {
    private static final int VIEWERS = 20;
    private static final int DATA_SLOTS = 9;

    private ApcMultiViewerGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oneToggleConvergesTwentyOpenMenusWithBoundedSlotFanout(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        FakePlayer[] players = new FakePlayer[VIEWERS];
        ApcMenu[] serverMenus = new ApcMenu[VIEWERS];
        ApcMenu[] clientMirrors = new ApcMenu[VIEWERS];
        int[] slotUpdates = new int[VIEWERS];

        Throwable failure = null;
        try {
            long initialRevision = device.breakerRevision();
            require(device.breakerClosed() && !device.tripLatched(), "placed APC starts closed and untripped");
            for (int i = 0; i < VIEWERS; i++) {
                FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "apc-view-" + i));
                players[i] = player;
                player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                player.getAbilities().instabuild = true;
                require(level.addFreshEntity(player), "viewer " + i + " must join the GameTest level");
                UUID session = UUID.randomUUID();
                player.containerMenu = new ApcMenu(1, player.getInventory(), pos, session, level, device);
                require(player.containerMenu instanceof ApcMenu, "viewer " + i + " holds a real server menu");
                ApcMenu serverMenu = (ApcMenu) player.containerMenu;
                serverMenus[i] = serverMenu;

                FriendlyByteBuf extraData = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    extraData.writeBlockPos(pos);
                    extraData.writeUUID(session);
                    ApcMenu mirror = new ApcMenu(serverMenu.containerId, player.getInventory(), extraData);
                    clientMirrors[i] = mirror;
                    require(mirror.displayedVisualState() == ApcVisualState.UNKNOWN,
                            "viewer " + i + " starts with unknown client visual state");
                    // Seed the initial vanilla data-slot snapshot before observing subsequent broadcasts.
                    mirror.setData(0, serverMenu.displayedBreaker());
                    long revision = serverMenu.displayedRevision();
                    for (int word = 0; word < 4; word++)
                        mirror.setData(word + 1, (short) (revision >>> (16 * word)));
                    mirror.setData(5, serverMenu.displayedBatteryPermille());
                    mirror.setData(6, serverMenu.displayedTripLatched() ? 1 : 0);
                    mirror.setData(7, (short) revision);
                    mirror.setData(8, serverMenu.displayedVisualState().ordinal());
                    require(mirror.hasReceivedInitialSnapshot(), "viewer " + i + " has an initial snapshot");
                    final int viewer = i;
                    serverMenu.addSlotListener(new ContainerListener() {
                        @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) { }
                        @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                            slotUpdates[viewer]++;
                            mirror.setData(index, value);
                        }
                    });
                    // Listener registration may send initial values; count only the transition below.
                    serverMenu.broadcastChanges();
                    slotUpdates[i] = 0;
                } finally {
                    extraData.release();
                }
            }

            ApcMenu source = serverMenus[0];
            ApcToggleResponse response = ApcMenuService.apply(players[0], new ApcToggleRequest(
                    source.containerId, source.session(), initialRevision, 1, false));
            require(response.accepted() && response.hasSnapshot() && !response.breakerClosed()
                            && !response.tripLatched() && response.revision() == initialRevision + 1,
                    "one valid request returns the authoritative transition");
            long changedRevision = device.breakerRevision();
            require(!device.breakerClosed() && !device.tripLatched() && changedRevision == initialRevision + 1,
                    "single device mutation, independent of viewer count");

            int totalUpdates = 0;
            for (int i = 0; i < VIEWERS; i++) {
                require(players[i].containerMenu == serverMenus[i] && serverMenus[i].boundDevice() == device,
                        "viewer " + i + " still holds the same live APC menu and device");
                serverMenus[i].broadcastChanges();
                require(serverMenus[i].displayedBreaker() == 0 && serverMenus[i].displayedRevision() == changedRevision
                                && !serverMenus[i].displayedTripLatched(), "server viewer " + i + " reads the BE");
                require(clientMirrors[i].displayedBreaker() == 0
                                && clientMirrors[i].displayedRevision() == changedRevision
                                && !clientMirrors[i].displayedTripLatched(),
                        "slot broadcast converges client-style viewer " + i);
                require(clientMirrors[i].displayedVisualState() == serverMenus[i].displayedVisualState(),
                        "slot broadcast converges visual state for viewer " + i);
                require(slotUpdates[i] > 0 && slotUpdates[i] <= DATA_SLOTS,
                        "viewer " + i + " receives at most one update per menu data slot");
                totalUpdates += slotUpdates[i];
            }
            require(totalUpdates <= VIEWERS * DATA_SLOTS, "fanout is bounded by viewers times menu data slots");
            for (ApcMenu menu : serverMenus) menu.broadcastChanges();
            int repeatedUpdates = 0;
            for (int updates : slotUpdates) repeatedUpdates += updates;
            require(repeatedUpdates == totalUpdates && device.breakerRevision() == changedRevision
                            && !device.breakerClosed() && !device.tripLatched(),
                    "unchanged rebroadcasts cause no extra slot traffic or device transition");
        } catch (RuntimeException | Error original) {
            failure = original;
            throw original;
        } finally {
            Throwable cleanupFailure = cleanup(players, clientMirrors);
            if (cleanupFailure != null) {
                if (failure != null) failure.addSuppressed(cleanupFailure);
                else if (cleanupFailure instanceof RuntimeException exception) throw exception;
                else if (cleanupFailure instanceof Error error) throw error;
            }
        }
        helper.succeed();
    }

    private static Throwable cleanup(FakePlayer[] players, ApcMenu[] clientMirrors) {
        Throwable failure = null;
        for (int i = 0; i < VIEWERS; i++) {
            FakePlayer player = players[i];
            if (player == null) continue;
            try {
                if (clientMirrors[i] != null) clientMirrors[i].removed(player);
            } catch (RuntimeException | Error cleanupError) {
                failure = appendFailure(failure, cleanupError);
            }
            try {
                if (player.containerMenu != player.inventoryMenu) {
                    try {
                        player.containerMenu.removed(player);
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                }
            } catch (RuntimeException | Error cleanupError) {
                failure = appendFailure(failure, cleanupError);
            }
            try {
                player.discard();
            } catch (RuntimeException | Error cleanupError) {
                failure = appendFailure(failure, cleanupError);
            }
        }
        return failure;
    }

    private static Throwable appendFailure(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
