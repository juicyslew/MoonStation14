package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.BodyCarrierIsolationBootstrap;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyCarrierIsolationBootstrapGameTests {
    private BodyCarrierIsolationBootstrapGameTests() { }

    private static List<ItemStack> empty(int count) {
        return new ArrayList<>(Collections.nCopies(count, ItemStack.EMPTY));
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, boolean marked,
            CreativeParkedInventory park, List<ItemStack> main, int selected) {
        return new CarrierHandInventoryGate.Fixture(account, marked, park, main, empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46), selected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @GameTest(template = "empty")
    public static void cleanEmptyReadyPolicyPersistsWithoutConnectedClaim(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        AtomicReference<CreativeParkedInventory> installed = new AtomicReference<>();
        var clean = fixture(account, false, null, empty(36), 7);
        require(BodyCarrierIsolationBootstrap.decide(clean, installed::set)
                == BodyCarrierIsolationBootstrap.Result.INITIALIZED, "clean unmarked creates marker");
        CreativeParkedInventory park = installed.get();
        require(park != null && park.account().equals(account) && park.snapshot().selectedHotbarIndex() == 7,
                "account and selection retained");
        for (int i = 0; i < CreativeInventorySnapshot.SLOT_COUNT; i++)
            require(park.snapshot().stackCopy(i).isEmpty(), "no item created in parked slot " + i);
        require(clean.main().stream().allMatch(stack -> stack == ItemStack.EMPTY), "carrier unchanged");
        var marked = fixture(account, true, park, empty(36), 7);
        require(BodyCarrierIsolationBootstrap.decide(marked, ignored -> {
            throw new GameTestAssertException("existing marker overwritten");
        }) == BodyCarrierIsolationBootstrap.Result.PRESERVED, "repeat Ready preserves exact marker");

        ServerPlayer source = player(helper, account);
        source.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), park);
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        ServerPlayer loaded = player(helper, account);
        loaded.load(saved);
        CreativeParkedInventory restored = loaded.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        require(restored != null && restored.account().equals(account)
                && restored.snapshot().selectedHotbarIndex() == 7, "empty marker survives save/load");
        require(loaded.getInventory().items.stream().allMatch(ItemStack::isEmpty), "load does not create carrier items");
        require(!CarrierHandInventoryGate.allows(loaded), "unregistered fixture cannot claim connected authority");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtyOrMismatchedMarkerCannotBeReplaced(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        var wrong = new CreativeParkedInventory(UUID.randomUUID(), CreativeInventorySnapshot.capture(
                empty(36), empty(4), empty(1), ItemStack.EMPTY, 2, empty(27)));
        var installed = new AtomicReference<CreativeParkedInventory>();
        require(BodyCarrierIsolationBootstrap.decide(fixture(account, true, wrong, empty(36), 0), installed::set)
                == BodyCarrierIsolationBootstrap.Result.REJECTED && installed.get() == null,
                "mismatched marker not overwritten");
        List<ItemStack> dirty = empty(36);
        dirty.set(0, new ItemStack(Items.DIAMOND, 3));
        require(BodyCarrierIsolationBootstrap.decide(fixture(account, false, null, dirty, 0), installed::set)
                == BodyCarrierIsolationBootstrap.Result.REJECTED && installed.get() == null
                && dirty.get(0).getCount() == 3, "Creative items cannot be silently marked or removed");
        require(BodyCarrierIsolationBootstrap.decide(fixture(account, true, null, empty(36), 0), installed::set)
                == BodyCarrierIsolationBootstrap.Result.REJECTED && installed.get() == null,
                "unreadable marker fails closed");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, UUID account) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(account, "isolation-fixture"), ClientInformation.createDefault());
    }
}
