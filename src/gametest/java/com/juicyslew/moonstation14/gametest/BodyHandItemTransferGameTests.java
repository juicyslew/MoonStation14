package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** An unregistered fixture is NOT a genuine committed-player action. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyHandItemTransferGameTests {
    private BodyHandItemTransferGameTests() { }

    @GameTest(template = "empty")
    public static void unregisteredFixtureCannotTransferWorldOrBodyStacks(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        if (LiveHands.initialize(body).isEmpty()) throw new AssertionError("fixture must have human hands");
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemEntity source = new ItemEntity(helper.getLevel(), body.getX(), body.getY(), body.getZ(),
                new ItemStack(Items.DIAMOND, 3));
        if (!helper.getLevel().addFreshEntity(source)) throw new AssertionError("source spawn failed");
        ServerPlayer fixture = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "transfer-fixture"), ClientInformation.createDefault());
        if (BodyHandItemTransfer.pickup(fixture, source.getUUID(), "left", before.revision())
                != BodyHandItemTransfer.Result.DENIED
                || BodyHandItemTransfer.drop(fixture, "left", "invalid", before.revision())
                != BodyHandItemTransfer.Result.DENIED
                || source.isRemoved() || source.getItem().getCount() != 3
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !fixture.getInventory().isEmpty())
            throw new AssertionError("unregistered actor changed item, body, or carrier");
        helper.succeed();
    }
}
