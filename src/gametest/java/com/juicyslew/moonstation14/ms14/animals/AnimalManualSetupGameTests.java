package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native owner setup receipt for actual Minecraft give/summon and registered bottle spill affordances. */
@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "moproach-cleaning", "manual-setup", "chemistry"})
public final class AnimalManualSetupGameTests {
    private AnimalManualSetupGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"animals", "moproach-cleaning", "manual-setup", "chemistry"})
    public static void summonEmptyBeakerAndGiveSpillBottleMatchManualSetup(GameTestHelper helper)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        var fixture = RegisteredServerPlayerFixture.register(level, "animalsetup");
        try {
            var player = fixture.player();
            var dispatcher = level.getServer().getCommands().getDispatcher();
            BlockPos beakerPos = origin.east().east();
            String summon = "summon moonstation14:loose_item " + commandCoordinate(beakerPos.getX() + .5) + " "
                    + commandCoordinate(beakerPos.getY()) + " " + commandCoordinate(beakerPos.getZ() + .5)
                    + " {Item:{id:\"moonstation14:beaker\",count:1}}";
            var serverSource = level.getServer().createCommandSourceStack().withLevel(level)
                    .withPosition(beakerPos.getCenter()).withPermission(4);
            var summonParse = dispatcher.parse(summon, serverSource);
            require(summonParse.getExceptions().isEmpty() && !summonParse.getReader().canRead(),
                    "documented empty-beaker summon command parses fully; exceptions=" + summonParse.getExceptions()
                            + ", unread=" + (summonParse.getReader().canRead()
                            ? summonParse.getReader().getString().substring(summonParse.getReader().getCursor()) : "<none>"));
            dispatcher.execute(summonParse);
            var summoned = level.getEntitiesOfClass(LooseItemEntity.class,
                    new net.minecraft.world.phys.AABB(beakerPos).inflate(1),
                    entity -> entity.getItem().is(ModItems.BEAKER.get()));
            require(summoned.size() == 1 && summoned.get(0) instanceof LooseItemEntity,
                    "summon creates exactly the registered loose-item beaker entity");
            ItemStack emptyBeaker = summoned.get(0).getItem();
            ReagentComponent emptyContents = emptyBeaker.get(ModDataComponents.REAGENT.get());
            require(emptyContents == null || emptyContents.contents().isEmpty(),
                    "summoned receiver is empty");
            var beaker = ModItems.BEAKER.get();
            require(beaker.getCapacity(emptyBeaker, level) > 0
                            && beaker.refillableSolution(emptyBeaker, level).enabled()
                            && beaker.solutionTransfer(emptyBeaker, level).canReceive(),
                    "resolved beaker prototype exposes the shared liquid receiver contract");

            var giveSource = player.createCommandSourceStack().withPermission(4);
            String give = "give @s moonstation14:bottle[moonstation14:reagent={\"moonstation14:blood\":10.0f}]";
            var giveParse = dispatcher.parse(give, giveSource);
            require(giveParse.getExceptions().isEmpty() && !giveParse.getReader().canRead(),
                    "blood bottle setup command parses fully through vanilla item-component syntax");
            dispatcher.execute(giveParse);
            ItemStack bottle = find(player, ModItems.BOTTLE.get());
            require(!bottle.isEmpty() && bottle.get(ModDataComponents.REAGENT.get()) != null
                            && bottle.get(ModDataComponents.REAGENT.get()).contents().getOrDefault(ModReagents.createKey("blood"), 0f) == 10f,
                    "give creates actual reagent-bearing MoonStation14 bottle");

            BlockPos support = origin.east();
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            player.setShiftKeyDown(true);
            var hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
            var spill = bottle.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, bottle, hit));
            player.setShiftKeyDown(false);
            require(spill.consumesAction(), "ordinary shift-right-click bottle interaction is accepted");
            BlockPos puddlePos = support.above();
            require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity,
                    "bottle spill creates canonical production puddle above solid support");
            var puddle = (PuddleBlockEntity) level.getBlockEntity(puddlePos);
            require(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap()
                            .equals(java.util.Map.of(ModReagents.createKey("blood"), 5f)),
                    "5-unit source bottle setup becomes matching blood puddle via normal BottleItem use");
            require(bottle.get(ModDataComponents.REAGENT.get()).contents()
                            .equals(java.util.Map.of(ModReagents.createKey("blood"), 5f)),
                    "normal spill conserves remaining bottle solution for owner manual setup");
            helper.succeed();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } finally {
            fixture.close();
        }
    }

    private static ItemStack find(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        throw new GameTestAssertException("expected item missing from registered player inventory: " + item);
    }

    private static String commandCoordinate(double value) {
        return java.math.BigDecimal.valueOf(value).toPlainString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
