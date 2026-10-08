package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "give", "command"})
public final class BodyGiveCoreGameTests {
    private BodyGiveCoreGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void coreGrantConsumesInputIntoBodyHand(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "body hands initialized");
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack input = new ItemStack(Items.STONE, 3);
        // Trusted core-consumer fixture: this validates admission/transfer, not command actor authentication.
        require(BodyGiveService.giveTrustedBody(body, input, () -> true), "grant accepted");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after != before && after.revision() == before.revision() + 1, "committed hand revision");
        require(after.stackCopy(after.activeHand()).filter(stack -> stack.is(Items.STONE)
                && stack.getCount() == 3).isPresent(), "active hand owns exact requested stack");
        require(input.isEmpty(), "successful grant clears vanilla chunk input");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
