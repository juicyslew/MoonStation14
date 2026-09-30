package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Unregistered ServerPlayers are inventory fixtures only, never connected account/body authority. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeCarrierTransitionGameTests {
    private static final CreativeCarrierTransition.FixtureWriteHook NO_HOOK = (player, slot, stack) -> { };

    private CreativeCarrierTransitionGameTests() { }

    @GameTest(template = "empty")
    public static void unregisteredFixtureCannotUseProductionEntryPoints(GameTestHelper helper) {
        ServerPlayer player = fixture(helper, UUID.randomUUID());
        ItemStack item = new ItemStack(Items.DIAMOND, 3);
        player.getInventory().items.set(0, item);
        require(CreativeCarrierTransition.park(player) == CreativeCarrierTransition.Result.DENIED
                && player.getInventory().items.get(0) == item
                && !player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()), "unregistered park");
        CreativeParkedInventory marker = new CreativeParkedInventory(player.getUUID(), snapshot(player));
        player.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), marker);
        require(CreativeCarrierTransition.restore(player) == CreativeCarrierTransition.Result.DENIED
                && player.getInventory().items.get(0) == item
                && player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == marker,
                "unregistered restore");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSlotsParkSaveLoadAndRestore(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        ServerPlayer player = fixture(helper, account);
        for (int i = 0; i < 36; i++) player.getInventory().items.set(i, new ItemStack(Items.STONE, i + 1));
        for (int i = 0; i < 4; i++) player.getInventory().armor.set(i, new ItemStack(Items.DIAMOND, i + 2));
        player.getInventory().offhand.set(0, new ItemStack(Items.EMERALD, 13));
        player.inventoryMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 19));
        player.getInventory().selected = 8;
        ItemStack named = player.getInventory().items.get(17);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("conserved name"));
        CreativeInventorySnapshot original = snapshot(player);
        require(CreativeCarrierTransition.parkTrustedFixture(player, original, NO_HOOK)
                == CreativeCarrierTransition.Result.PARKED, "park result");
        assertEmpty(player);
        require(player.getInventory().selected == 8, "park selection unchanged");
        CreativeParkedInventory marker = marker(player);
        require(marker != null && marker.account().equals(account), "park attachment present");
        require(CreativeCarrierTransition.parkTrustedFixture(player, snapshot(player), NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED && marker(player) == marker, "double park");

        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        require(saved.contains("Inventory", Tag.TAG_LIST)
                && saved.getList("Inventory", Tag.TAG_COMPOUND).isEmpty()
                && saved.contains("neoforge:attachments", Tag.TAG_COMPOUND), "empty vanilla inventory and park co-saved");
        ServerPlayer loaded = fixture(helper, account);
        loaded.load(saved);
        assertEmpty(loaded);
        require(marker(loaded) != null && marker(loaded).account().equals(account), "loaded park");
        require(CreativeCarrierTransition.restoreTrustedFixture(loaded, NO_HOOK)
                == CreativeCarrierTransition.Result.RESTORED, "restore result");
        require(marker(loaded) == null && loaded.getInventory().selected == 8, "marker removed and selection restored");
        for (int i = 0; i < CreativeInventorySnapshot.SLOT_COUNT; i++) {
            ItemStack actual = slot(loaded, i);
            ItemStack expected = original.stackCopy(i);
            require(ItemStack.matches(actual, expected) && actual.getCount() == expected.getCount(), "slot " + i);
        }
        require(slot(loaded, 17).getHoverName().getString().equals("conserved name"), "component restored");
        require(CreativeCarrierTransition.restoreTrustedFixture(loaded, NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED, "double restore");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void occupiedTargetsAndInjectedFailures(GameTestHelper helper) {
        ServerPlayer player = fixture(helper, UUID.randomUUID());
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 5));
        CreativeInventorySnapshot original = snapshot(player);
        var failClear = onceAt(1);
        require(CreativeCarrierTransition.parkTrustedFixture(player, original, failClear)
                == CreativeCarrierTransition.Result.DENIED, "clear failure rolled back");
        require(marker(player) == null && ItemStack.matches(slot(player, 0), original.stackCopy(0)), "clear rollback");
        require(CreativeCarrierTransition.parkTrustedFixture(player, original, NO_HOOK)
                == CreativeCarrierTransition.Result.PARKED, "retry only after verified rollback");
        CreativeParkedInventory marker = marker(player);
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.STONE));
        require(CreativeCarrierTransition.restoreTrustedFixture(player, NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED && marker(player) == marker, "Ender target denied");
        player.getEnderChestInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().items.set(4, new ItemStack(Items.EMERALD));
        require(CreativeCarrierTransition.restoreTrustedFixture(player, NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED && marker(player) == marker
                && slot(player, 4).is(Items.EMERALD), "occupied target denied without overwrite");
        player.getInventory().items.set(4, ItemStack.EMPTY);
        require(CreativeCarrierTransition.restoreTrustedFixture(player, onceAt(1))
                == CreativeCarrierTransition.Result.RECOVERY_REQUIRED, "restore failure requires recovery");
        assertEmpty(player);
        require(marker(player) == marker, "restore rollback retains marker");

        ServerPlayer ambiguous = fixture(helper, UUID.randomUUID());
        ambiguous.getInventory().items.set(0, new ItemStack(Items.STONE, 7));
        CreativeInventorySnapshot value = snapshot(ambiguous);
        require(CreativeCarrierTransition.parkTrustedFixture(ambiguous, value, (p, index, stack) -> {
            if (index == 1 && stack == ItemStack.EMPTY) {
                p.getInventory().items.set(0, new ItemStack(Items.EMERALD));
                throw new IllegalStateException("competing inventory mutation");
            }
        }) == CreativeCarrierTransition.Result.RECOVERY_REQUIRED, "ambiguous clear requires recovery");
        require(marker(ambiguous) != null && slot(ambiguous, 0).is(Items.EMERALD), "no unsafe rollback/removal");
        require(CreativeCarrierTransition.parkTrustedFixture(ambiguous, value, NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED, "no second owner on retry");

        ServerPlayer contestedRestore = fixture(helper, UUID.randomUUID());
        contestedRestore.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 4));
        require(CreativeCarrierTransition.parkTrustedFixture(contestedRestore, snapshot(contestedRestore), NO_HOOK)
                == CreativeCarrierTransition.Result.PARKED, "contested restore setup");
        CreativeParkedInventory contestedMarker = marker(contestedRestore);
        require(CreativeCarrierTransition.restoreTrustedFixture(contestedRestore, (p, index, stack) -> {
            if (index == 1) {
                p.getInventory().items.set(0, new ItemStack(Items.EMERALD));
                throw new IllegalStateException("competing restore mutation");
            }
        }) == CreativeCarrierTransition.Result.RECOVERY_REQUIRED, "ambiguous restore requires recovery");
        require(marker(contestedRestore) == contestedMarker && slot(contestedRestore, 0).is(Items.EMERALD),
                "ambiguous restore retains marker and does not overwrite competing item");
        require(CreativeCarrierTransition.restoreTrustedFixture(contestedRestore, NO_HOOK)
                == CreativeCarrierTransition.Result.DENIED, "occupied ambiguous restore cannot retry");

        ServerPlayer changedStack = fixture(helper, UUID.randomUUID());
        changedStack.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 4));
        require(CreativeCarrierTransition.parkTrustedFixture(changedStack, snapshot(changedStack), NO_HOOK)
                == CreativeCarrierTransition.Result.PARKED, "changed-stack setup");
        CreativeParkedInventory changedMarker = marker(changedStack);
        require(CreativeCarrierTransition.restoreTrustedFixture(changedStack, (p, index, stack) -> {
            if (index == 1) slot(p, 0).setCount(2);
        }) == CreativeCarrierTransition.Result.RECOVERY_REQUIRED, "mutated restored reference fails value check");
        require(marker(changedStack) == changedMarker && slot(changedStack, 0).getCount() == 2,
                "mutated restored reference retains ownership marker");
        helper.succeed();
    }

    private static CreativeCarrierTransition.FixtureWriteHook onceAt(int target) {
        return new CreativeCarrierTransition.FixtureWriteHook() {
            boolean fired;
            @Override public void beforeWrite(ServerPlayer player, int slot, ItemStack stack) {
                if (!fired && slot == target) {
                    fired = true;
                    throw new IllegalStateException("injected write failure");
                }
            }
        };
    }

    private static ServerPlayer fixture(GameTestHelper helper, UUID id) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(id, "transition-fixture"), ClientInformation.createDefault());
        return player;
    }

    private static CreativeInventorySnapshot snapshot(ServerPlayer player) {
        return CreativeInventorySnapshot.capture(player.getInventory().items, player.getInventory().armor,
                player.getInventory().offhand, player.inventoryMenu.getCarried(), player.getInventory().selected, List.of());
    }

    private static ItemStack slot(ServerPlayer player, int i) {
        if (i < 36) return player.getInventory().items.get(i);
        if (i < 40) return player.getInventory().armor.get(i - 36);
        if (i == 40) return player.getInventory().offhand.get(0);
        return player.inventoryMenu.getCarried();
    }

    private static CreativeParkedInventory marker(ServerPlayer player) {
        return player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
    }

    private static void assertEmpty(ServerPlayer player) {
        for (int i = 0; i < 42; i++) require(slot(player, i) == ItemStack.EMPTY, "physical empty slot " + i);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
