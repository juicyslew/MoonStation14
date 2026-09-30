package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Trusted-player value fixtures only: these do not claim a connected player or execute setGameMode. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleBodyCreativeRestoreGameTests {
    private LifecycleBodyCreativeRestoreGameTests() { }

    @GameTest(template = "empty")
    public static void nonemptyRestoredSnapshotAcceptedAndTamperingDenied(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        List<ItemStack> main = empty(36), armor = empty(4), offhand = empty(1);
        main.set(3, new ItemStack(Items.DIAMOND, 12));
        main.get(3).set(DataComponents.CUSTOM_NAME, Component.literal("builder"));
        armor.set(2, new ItemStack(Items.IRON_LEGGINGS));
        offhand.set(0, new ItemStack(Items.TORCH, 7));
        ItemStack cursor = new ItemStack(Items.OAK_PLANKS, 5);
        var snapshot = CreativeInventorySnapshot.capture(main, armor, offhand, cursor, 3, empty(27));
        var marker = new CreativeParkedInventory(account, snapshot);
        var restored = fixture(account, false, null, main, armor, offhand, cursor, 3);
        require(LifecycleDevelopmentMode.bodyCreativeRestored(snapshot, restored), "nonempty exact restore");
        require(!CarrierHandInventoryGate.allows(restored), "body empty gate must not accept restored items");
        require(LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, true, LifecycleDevelopmentMode.bodyCreativeRestored(snapshot, restored), true),
                "production completion policy accepts legitimate nonempty restore");

        List<ItemStack> wrongCount = new ArrayList<>(main);
        wrongCount.set(3, main.get(3).copyWithCount(11));
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, wrongCount, armor, offhand, cursor, 3)), "count");
        List<ItemStack> wrongItem = new ArrayList<>(main);
        wrongItem.set(3, new ItemStack(Items.EMERALD, 12));
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, wrongItem, armor, offhand, cursor, 3)), "item");
        List<ItemStack> wrongComponents = new ArrayList<>(main);
        wrongComponents.set(3, new ItemStack(Items.DIAMOND, 12));
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, wrongComponents, armor, offhand, cursor, 3)), "components");
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, main, armor, offhand, ItemStack.EMPTY, 3)), "cursor");
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, main, armor, offhand, cursor, 2)), "selection");
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, true, marker, main, armor, offhand, cursor, 3)), "marker remains");
        require(!LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, main, armor, offhand, cursor, 3, true)), "ender occupied");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(true, GameType.CREATIVE,
                true, true, false, true, true), "changed claim");
        require(!LifecycleDevelopmentMode.bodyCreativeCompleted(false, GameType.SPECTATOR,
                true, true, true, true, true), "canceled switch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyRestoredSnapshotAccepted(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        var snapshot = CreativeInventorySnapshot.capture(empty(36), empty(4), empty(1), ItemStack.EMPTY, 8, empty(27));
        require(LifecycleDevelopmentMode.bodyCreativeRestored(snapshot,
                fixture(account, false, null, empty(36), empty(4), empty(1), ItemStack.EMPTY, 8)),
                "empty supported snapshot still restores exactly");
        helper.succeed();
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, boolean marked, CreativeParkedInventory park,
            List<ItemStack> main, List<ItemStack> armor, List<ItemStack> offhand, ItemStack cursor, int selected) {
        return fixture(account, marked, park, main, armor, offhand, cursor, selected, false);
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, boolean marked, CreativeParkedInventory park,
            List<ItemStack> main, List<ItemStack> armor, List<ItemStack> offhand, ItemStack cursor, int selected,
            boolean enderOccupied) {
        List<ItemStack> menu = empty(46), ender = empty(27);
        for (int i = 5; i < 9; i++) menu.set(i, armor.get(8 - i));
        for (int i = 9; i < 36; i++) menu.set(i, main.get(i));
        for (int i = 36; i < 45; i++) menu.set(i, main.get(i - 36));
        menu.set(45, offhand.get(0));
        if (enderOccupied) ender.set(0, new ItemStack(Items.STONE));
        return new CarrierHandInventoryGate.Fixture(account, marked, park, main, armor, offhand,
                cursor, empty(4), ItemStack.EMPTY, ender, menu, selected);
    }

    private static List<ItemStack> empty(int count) {
        return new ArrayList<>(java.util.Collections.nCopies(count, ItemStack.EMPTY));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
