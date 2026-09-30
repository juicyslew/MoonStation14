package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.ms14.hands.quarantine.ParkedInventoryPacketPolicy;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Unregistered fixtures cannot impersonate the connected account, even with a copied marker. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParkedInventoryPacketPolicyGameTests {
    private ParkedInventoryPacketPolicyGameTests() { }

    @GameTest(template = "empty")
    public static void unregisteredMarkerDoesNotGateOtherPlayers(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer fixture = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(id, "unregistered-park"), ClientInformation.createDefault());
        if (fixture.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())
                || CreativeParkedInventory.existingFor(fixture).isPresent()
                || ParkedInventoryPacketPolicy.deny(false, false, false))
            throw new AssertionError("absent park must leave vanilla packets alone");
        var empty = CreativeInventorySnapshot.capture(Collections.nCopies(36, ItemStack.EMPTY),
                Collections.nCopies(4, ItemStack.EMPTY), List.of(ItemStack.EMPTY), ItemStack.EMPTY, 0, List.of());
        fixture.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), new CreativeParkedInventory(id, empty));
        if (CreativeParkedInventory.existingFor(fixture).isPresent()
                || ParkedInventoryPacketPolicy.deny(false, true, false))
            throw new AssertionError("unregistered marker cannot authorize packet gating");
        fixture.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(UUID.randomUUID(), empty));
        if (CreativeParkedInventory.existingFor(fixture).isPresent()
                || ParkedInventoryPacketPolicy.deny(false, true, false))
            throw new AssertionError("unregistered mismatched marker cannot gate packets");
        helper.succeed();
    }
}
