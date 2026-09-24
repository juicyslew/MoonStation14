package com.juicyslew.moonstation14.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InvalidReagentGameTests {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> INVALID = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "nonexistent_test"));

    private InvalidReagentGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unknownGiveComponentParsesButBottleUseRejectsWithoutMutation(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        String command = "give @s moonstation14:bottle[moonstation14:reagent={\"moonstation14:nonexistent_test\":5.0f}]";
        var dispatcher = level.getServer().getCommands().getDispatcher();
        ParseResults<?> parsed = dispatcher.parse(command,
                level.getServer().createCommandSourceStack().withPermission(4));
        require(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(),
                "syntactically valid unknown reagent command must parse completely (not be executed)");

        Player player = mockPlayer(helper, level, new BlockPos(1, 1, 1));
        ItemStack invalid = bottle(Map.of(INVALID, 5f));
        player.setItemInHand(InteractionHand.MAIN_HAND, invalid);
        var rejected = ModItems.BOTTLE.get().use(level, player, InteractionHand.MAIN_HAND);
        require(rejected.getResult() == InteractionResult.FAIL, "unknown reagent bottle use must fail");
        require(invalid.getCount() == 1 && invalid.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(INVALID, 5f)),
                "rejected stack must retain its contents");
        require(!player.hasData(com.juicyslew.moonstation14.component.ModDataAttachments.STOMACH.get()),
                "rejected use must not materialize stomach state");

        ItemStack validSource = bottle(Map.of(WATER, 4f));
        MS14Provider.update(player, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(INVALID, 2f)));
        float ingested = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(validSource), player, level, 1f);
        require(ingested == 0f, "ingestion must reject an unknown reagent already stored in the stomach");
        require(validSource.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(WATER, 4f)),
                "invalid stomach destination must leave source untouched");
        require(MS14Provider.get(player, MS14Bridges.STOMACH).getMap().equals(Map.of(INVALID, 2f)),
                "invalid stomach destination must remain unchanged");

        ItemStack mixed = bottle(Map.of(WATER, 5f, INVALID, 5f));
        player.setItemInHand(InteractionHand.MAIN_HAND, mixed);
        require(ModItems.BOTTLE.get().use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
                "mixed valid/invalid bottle must reject atomically");
        require(mixed.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(WATER, 5f, INVALID, 5f)),
                "mixed bottle must not partially consume valid water");

        ItemStack valid = bottle(Map.of(WATER, 5f));
        player.setItemInHand(InteractionHand.MAIN_HAND, valid);
        require(ModItems.BOTTLE.get().use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "valid water bottle use remains supported");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidJugPlacementAndPuddleTickPreserveUnknownContents(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player player = mockPlayer(helper, level, new BlockPos(1, 1, 1));
        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(INVALID, 5f)));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 1));
        level.setBlock(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false);
        InteractionResult placed = ModItems.JUG.get().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, jug, hit));
        require(placed == InteractionResult.FAIL, "unknown reagent jug must fail before block placement");
        require(jug.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(INVALID, 5f)),
                "failed jug placement must preserve item contents");
        BlockPos expected = stone.above();
        require(!level.getBlockState(expected).is(ModBlocks.JUG.get()), "invalid jug must not create a jug block");

        BlockPos puddlePos = helper.absolutePos(new BlockPos(5, 1, 1));
        level.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity, "puddle block entity fixture missing");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(INVALID, 40f)));
        PuddleBlockEntity.tick(level, puddlePos, level.getBlockState(puddlePos), puddle);
        require(level.getBlockEntity(puddlePos) == puddle, "invalid puddle must not be deleted by tick");
        require(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().equals(Map.of(INVALID, 40f)),
                "invalid puddle contents must remain available for repair");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidBottleAndJugMidUseStopWithoutConsumingSource(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player bottlePlayer = mockPlayer(helper, level, new BlockPos(1, 1, 1));
        ItemStack bottle = bottle(Map.of(WATER, 5f));
        bottlePlayer.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        require(ModItems.BOTTLE.get().use(level, bottlePlayer, InteractionHand.MAIN_HAND)
                        .getResult().consumesAction() && bottlePlayer.isUsingItem(),
                "valid bottle must start use before its contents change");
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(INVALID, 5f)));
        ModItems.BOTTLE.get().onUseTick(level, bottlePlayer, bottle,
                ModItems.BOTTLE.get().getUseDuration(bottle, bottlePlayer) - com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS);
        require(!bottlePlayer.isUsingItem(), "invalid bottle use must stop");
        require(bottle.get(ModDataComponents.REAGENT.get()).centContents().equals(Map.of(INVALID, 500L)),
                "invalid bottle contents must remain available for repair");

        Player jugPlayer = mockPlayer(helper, level, new BlockPos(4, 1, 1), true);
        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 5f)));
        jugPlayer.setItemInHand(InteractionHand.MAIN_HAND, jug);
        require(ModItems.JUG.get().use(level, jugPlayer, InteractionHand.MAIN_HAND)
                        .getResult().consumesAction() && jugPlayer.isUsingItem(),
                "valid sneaking jug must start held use");
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(INVALID, 5f)));
        ModItems.JUG.get().onUseTick(level, jugPlayer, jug,
                ModItems.JUG.get().getUseDuration(jug, jugPlayer) - com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS);
        require(!jugPlayer.isUsingItem(), "invalid jug use must stop");
        require(jug.get(ModDataComponents.REAGENT.get()).centContents().equals(Map.of(INVALID, 500L)),
                "invalid jug contents must remain available for repair");
        helper.succeed();
    }

    private static ItemStack bottle(Map<ResourceKey<ReagentData>, Float> contents) {
        ItemStack stack = new ItemStack(ModItems.BOTTLE.get());
        stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent(contents));
        return stack;
    }

    private static Player mockPlayer(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        return mockPlayer(helper, level, pos, false);
    }

    private static Player mockPlayer(GameTestHelper helper, ServerLevel level, BlockPos pos, boolean sneaking) {
        BlockPos absolute = helper.absolutePos(pos);
        Player player = new Player(level, absolute, 0f,
                new GameProfile(UUID.randomUUID(), "invalid-reagent-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isShiftKeyDown() { return sneaking; }
        };
        player.setPos(absolute.getX(), absolute.getY(), absolute.getZ());
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
