package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"interaction"})
public final class GiveCommandIntegrationGameTests {
    private GiveCommandIntegrationGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void creativeGiveExecutesRealCommandWithoutFakeDrop(GameTestHelper helper) {
        FakePlayer player = player(helper, "give-command");
        try {
            String worldBefore = describeWorldPickaxes(helper, player);
            execute(helper, player, "give @s moonstation14:pickaxe");
            require(player.getInventory().countItem(ModItems.PICKAXE.get()) == 1,
                    "creative give places one real pickaxe in inventory; isCreative=" + player.isCreative()
                            + ", hasInfiniteMaterials=" + player.getAbilities().instabuild
                            + ", inventoryPickaxeCount=" + player.getInventory().countItem(ModItems.PICKAXE.get())
                            + ", worldBefore/currentWorldCount=" + worldBefore + "/" + describeWorldPickaxes(helper, player));
            helper.runAfterDelay(1, () -> {
                try {
                    var loose = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                            player.getBoundingBox().inflate(8), entity -> entity.getItem().is(ModItems.PICKAXE.get()));
                    var allItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            player.getBoundingBox().inflate(8), entity -> entity.getItem().is(ModItems.PICKAXE.get()));
                    require(loose.isEmpty() && allItems.isEmpty(), "no fake visual or loose pickaxe drop after one tick"
                            + "; isCreative=" + player.isCreative() + ", hasInfiniteMaterials=" + player.getAbilities().instabuild
                            + ", inventoryPickaxeCount=" + player.getInventory().countItem(ModItems.PICKAXE.get())
                            + ", worldBefore/currentWorldCount=" + worldBefore + "/" + allItems.size()
                            + ", exactWorldEntities=" + describeEntities(allItems));
                } finally { player.discard(); }
                helper.succeed();
            });
        } catch (Exception e) {
            player.discard();
            throw new GameTestAssertException("registered give command failed: " + e);
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void reagentGiveValidatesBeforeReturningAndOverflowSpawnsOnce(GameTestHelper helper) {
        FakePlayer player = player(helper, "give-reagents");
        try {
            execute(helper, player, "give @s moonstation14:jug[moonstation14:reagent={\"moonstation14:spacelube\":10.0f}]");
            ItemStack jug = findJug(player);
            require(jug != null && jug.get(ModDataComponents.REAGENT.get()) != null,
                    "valid reagent component survives real give conversion");
            var reagent = jug.get(ModDataComponents.REAGENT.get());
            require(reagent.centContents().values().stream().mapToLong(Long::longValue).sum() == 1000,
                    "valid jug carries 1000 reagent cents");

            int inventoryBefore = player.getInventory().getContainerSize();
            int totalBefore = countJugs(player);
            int worldBefore = countWorldJugs(helper, player);
            boolean rejected = false;
            try {
                execute(helper, player, "give @s moonstation14:jug[moonstation14:reagent={\"moonstation14:not_a_reagent\":10.0f}]");
            } catch (Exception expected) { rejected = true; }
            require(rejected, "unknown positive reagent fails command execution");
            require(player.getInventory().getContainerSize() == inventoryBefore && countJugs(player) == totalBefore
                            && countWorldJugs(helper, player) == worldBefore,
                    "invalid reagent leaves inventory and world unchanged");

            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            int fullInventoryJugs = countJugs(player);
            int fullInventoryStones = player.getInventory().countItem(Items.STONE);
            int fullBefore = countWorldJugs(helper, player);
            execute(helper, player, "give @s moonstation14:jug[moonstation14:reagent={\"moonstation14:spacelube\":10.0f}]");
            require(countWorldJugs(helper, player) == fullBefore && countJugs(player) == fullInventoryJugs
                            && player.getInventory().countItem(Items.STONE) == fullInventoryStones,
                    "full creative inventory preserves native give behavior without a world drop or losing stones"
                            + "; worldJugs=" + fullBefore + "/" + countWorldJugs(helper, player)
                            + ", inventoryJugs=" + fullInventoryJugs + "/" + countJugs(player)
                            + ", stones=" + fullInventoryStones + "/" + player.getInventory().countItem(Items.STONE));

            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            execute(helper, player, "give @s moonstation14:jug[moonstation14:reagent={\"moonstation14:spacelube\":10.0f}]");
            var spawned = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8),
                    entity -> entity.getItem().is(ModItems.JUG.get()));
            require(spawned.size() == fullBefore + 1, "full survival inventory produces exactly one real jug drop");
            require(spawned.stream().filter(entity -> entity.getItem().get(ModDataComponents.REAGENT.get()) != null).count() == 1,
                    "overflow jug retains reagent component");
        } catch (Exception e) {
            throw new GameTestAssertException("give integration failed: " + e);
        } finally { player.discard(); }
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getX() + .5,
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getY(),
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getZ() + .5);
        if (!helper.getLevel().addFreshEntity(player)) throw new GameTestAssertException("FakePlayer added to level");
        return player;
    }

    private static void execute(GameTestHelper helper, FakePlayer player, String command) throws Exception {
        helper.getLevel().getServer().getCommands().getDispatcher().execute(command,
                player.createCommandSourceStack().withPermission(4));
    }

    private static ItemStack findJug(FakePlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.JUG.get())) return stack;
        }
        return null;
    }

    private static int countJugs(FakePlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(ModItems.JUG.get())) count += player.getInventory().getItem(i).getCount();
        return count;
    }

    private static int countWorldJugs(GameTestHelper helper, FakePlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8),
                entity -> entity.getItem().is(ModItems.JUG.get())).size();
    }

    private static String describeWorldPickaxes(GameTestHelper helper, FakePlayer player) {
        var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(8), entity -> entity.getItem().is(ModItems.PICKAXE.get()));
        return entities.size() + " entities=" + describeEntities(entities);
    }

    private static String describeEntities(java.util.List<? extends ItemEntity> entities) {
        return entities.stream().map(entity -> entity.getClass().getName() + "@" + entity.position())
                .toList().toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
