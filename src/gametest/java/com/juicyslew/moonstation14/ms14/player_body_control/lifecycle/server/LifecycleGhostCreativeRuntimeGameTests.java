package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Unregistered player fixture verifies production ownership rejection, not command acceptance. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleGhostCreativeRuntimeGameTests {
    private LifecycleGhostCreativeRuntimeGameTests() { }

    @GameTest(template = "empty")
    public static void unregisteredGhostCannotRegisterOrRestoreCarrier(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        ServerPlayer fixture = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(account, "ghost-park-fixture"), ClientInformation.createDefault());
        var inventory = fixture.getInventory();
        CreativeParkedInventory marker = new CreativeParkedInventory(account,
                CreativeInventorySnapshot.capture(inventory.items, inventory.armor, inventory.offhand,
                        fixture.inventoryMenu.getCarried(), inventory.selected, List.of()));
        fixture.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), marker);
        LifecycleDevelopmentMode.creativeModeChangeReturned(fixture, GameType.CREATIVE, true);
        if (fixture.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != marker)
            throw new AssertionError("unrelated Creative callback consumed marker");
        // Even a direct production entry point must reject the absent exact transition owner.
        if (LifecycleDevelopmentMode.completeGhostCreativePark(fixture, null))
            throw new AssertionError("unregistered fixture registered ghost park");
        if (fixture.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != marker)
            throw new AssertionError("unregistered fixture lost marker");
        helper.succeed();
    }
}
