package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.ms14.hands.quarantine.ParkedInventoryDirectPolicy;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Runtime mixin audit plus an unregistered fixture; never treats a fixture as the account holder. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParkedInventoryDirectGuardGameTests {
    private ParkedInventoryDirectGuardGameTests() { }

    @GameTest(template = "empty")
    public static void transformedRoutesAndFixtureIsolation(GameTestHelper helper) {
        for (String method : List.of("moonstation14$denyAdd", "moonstation14$denyAddAt",
                "moonstation14$denyRemove", "moonstation14$denyRemoveNoUpdate",
                "moonstation14$denyRemoveReference", "moonstation14$denySet", "moonstation14$denyNoArg",
                "moonstation14$denyPlaceBack", "moonstation14$denyPlaceBackWithSync",
                "moonstation14$denyReplace", "moonstation14$denyClearMatching",
                "moonstation14$denySelectedRemoval", "moonstation14$denyPickedItem",
                "moonstation14$denyPickSlot", "moonstation14$denyHotbarScroll", "moonstation14$denyLoad")) {
            boolean installed = false;
            for (var declared : Inventory.class.getDeclaredMethods())
                if (declared.getName().contains(method)) installed = true;
            if (!installed) throw new AssertionError("Inventory mixin missing at runtime: " + method);
        }
        boolean dropInstalled = false;
        for (var declared : ServerPlayer.class.getDeclaredMethods())
            if (declared.getName().contains("moonstation14$denyDirectDrop")) dropInstalled = true;
        if (!dropInstalled) throw new AssertionError("ServerPlayer drop mixin missing at runtime");
        boolean handInstalled = false;
        for (var declared : HandActorAuthority.class.getDeclaredMethods())
            if (declared.getName().contains("moonstation14$denyDirtyCarrierHandAction")) handInstalled = true;
        if (!handInstalled) throw new AssertionError("hand authority dirty-carrier mixin missing at runtime");

        UUID id = UUID.randomUUID();
        ServerPlayer fixture = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(id, "direct-fixture"), ClientInformation.createDefault());
        var empty = CreativeInventorySnapshot.capture(Collections.nCopies(36, ItemStack.EMPTY),
                Collections.nCopies(4, ItemStack.EMPTY), List.of(ItemStack.EMPTY), ItemStack.EMPTY, 0, List.of());
        fixture.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(UUID.randomUUID(), empty));
        if (ParkedInventoryDirectPolicy.deny(fixture.getInventory()) || ParkedInventoryDirectPolicy.denyHolder(fixture))
            throw new AssertionError("unregistered mismatched marker must not impersonate connected player");
        ItemStack input = new ItemStack(Items.STONE, 3);
        if (!fixture.getInventory().add(input) || !input.isEmpty()
                || fixture.getInventory().getItem(0).getCount() != 3)
            throw new AssertionError("unregistered fixture must retain vanilla Inventory.add behavior");
        if (fixture.getInventory().removeItem(0, 1).getCount() != 1
                || fixture.getInventory().getItem(0).getCount() != 2)
            throw new AssertionError("unregistered fixture must retain vanilla Inventory.removeItem behavior");
        if (ParkedInventoryDirectPolicy.denyDirtyHandAction(fixture))
            throw new AssertionError("unregistered fixture cannot claim hand-action authorization or denial");
        helper.succeed();
    }
}
