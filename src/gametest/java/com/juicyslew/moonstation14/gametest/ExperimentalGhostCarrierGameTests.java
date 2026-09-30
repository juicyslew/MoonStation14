package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExperimentalGhostCarrierGameTests {
    private ExperimentalGhostCarrierGameTests() { }

    @GameTest(template = "empty")
    public static void unregisteredParkedCarrierAndBootstrappedBodyHaveNoHandAuthority(GameTestHelper helper) {
        var body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        if (BodyHandBootstrap.ensure(body) != BodyHandBootstrap.Result.INITIALIZED)
            throw new GameTestAssertException("body hand fixture");
        UUID account = UUID.randomUUID();
        ServerPlayer carrier = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(account, "unregistered-ghost"), ClientInformation.createDefault());
        var snapshot = CreativeInventorySnapshot.capture(Collections.nCopies(36, ItemStack.EMPTY),
                Collections.nCopies(4, ItemStack.EMPTY), Collections.nCopies(1, ItemStack.EMPTY),
                ItemStack.EMPTY, 0, Collections.nCopies(27, ItemStack.EMPTY));
        carrier.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(account, snapshot));
        if (HandActorAuthority.resolve(carrier).isPresent() || GhostMobHarnessControl.activeHarness(carrier).isPresent()
                || GhostMobHarnessControl.isExperimentalCarrier(carrier))
            throw new GameTestAssertException("unregistered carrier acquired hand or ghost authority");
        helper.succeed();
    }
}
