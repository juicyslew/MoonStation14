package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.inventory.BodyCraftInventoryService;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"construction", "hands", "crafting"})
@PrefixGameTestTemplate(false)
public final class BodyCraftInventoryServiceGameTests {
    private BodyCraftInventoryServiceGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags("crafting")
    public static void fakeCarrierCannotCraftFromBodyOwnedSteel(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "body hand bootstrap");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = initial.handIds().getFirst();
        ItemToken steelToken = new ItemToken("body-steel-input");
        LiveHands stocked = initial.putWhole(initial.revision(), hand, steelToken,
                new ItemStack(ModItems.STEEL.get(), 2)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stocked);

        var fakeCarrier = FakePlayerFactory.getMinecraft(helper.getLevel());
        require(BodyCraftInventoryService.craftBody(fakeCarrier, stocked.revision(),
                stack -> stack.is(ModItems.STEEL.get()), 1, new ItemStack(ModItems.METAL_ROD.get(), 2))
                == BodyCraftInventoryService.Result.DENIED, "fake carrier cannot craft using body-owned steel");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after == stocked && after.revision() == stocked.revision()
                        && after.token(hand).filter(steelToken.value()::equals).isPresent()
                        && after.stackCopy(hand).orElseThrow().getCount() == 2,
                "denial preserves exact body snapshot, revision, steel token, and count");
        require(fakeCarrier.getInventory().items.stream().allMatch(ItemStack::isEmpty),
                "carrier inventory remains without craft output or transferred source");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
