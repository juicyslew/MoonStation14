package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
public final class PuddleInteractionGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<ReagentData> SUGAR = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "sugar"));

    private PuddleInteractionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredPuddleEmptyHandDispatcherIngestsFiveAndRejectsEmptyOrFull(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        require(EntityType.PLAYER.is(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.ELIGIBLE_ENTITY_TYPES),
                "the registered eligibility tag must include players");
        BlockPos sourcePos = placePuddle(helper, new BlockPos(2, 1, 2), 20f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));

        InteractionResult drank = dispatchEmptyHand(level, sourcePos, player);
        require(drank == InteractionResult.SUCCESS, "nonempty eligible puddle use should be successful");
        require(puddleWater(level, sourcePos) == 15f, "one empty-hand click removes exactly five puddle units");
        require(stomachWater(player) == 5f, "the same click adds exactly five units to player stomach");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "puddle drinking leaves shared REAGENT unchanged");

        BlockPos fullPos = placePuddle(helper, new BlockPos(8, 1, 2), 20f);
        Player fullPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(8, 2, 2)));
        MS14Provider.update(fullPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(SUGAR, StomachSystem.CAPACITY)));
        require(dispatchEmptyHand(level, fullPos, fullPlayer) == InteractionResult.PASS,
                "a full stomach returns PASS through its empty-hand callback");
        require(puddleWater(level, fullPos) == 20f, "full stomach does not drain puddle source");
        require(MS14Provider.get(fullPlayer, MS14Bridges.STOMACH).getMap()
                        .equals(Map.of(SUGAR, StomachSystem.CAPACITY)),
                "full stomach remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exactCentDrinkingRemovesPuddleAfterStomachCommit(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourcePos = placePuddle(helper, new BlockPos(2, 1, 2), .01f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));

        require(dispatchEmptyHand(level, sourcePos, player) == InteractionResult.SUCCESS,
                "the final one-cent dose is accepted by ordinary drinking");
        require(stomachWater(player) == .01f, "the exact final cent commits to the stomach");
        require(level.getBlockState(sourcePos).isAir() && level.getBlockEntity(sourcePos) == null,
                "the puddle block and block entity disappear when its committed source reaches zero");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oneCentResidueRemainsVisibleAndStaleEmptyPuddleExpiresOnTick(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos positivePos = placePuddle(helper, new BlockPos(2, 1, 2), .01f);
        require(level.getBlockState(positivePos).is(ModBlocks.PUDDLE.get())
                        && level.getBlockState(positivePos).getValue(
                        com.juicyslew.moonstation14.block.custom.PuddleBlock.FILL_LEVEL) >= 1,
                "a one-cent source remains as a visible puddle");
        require(ReagentUnits.fromFloat(puddleWater(level, positivePos)) == 1L,
                "the remaining cent is not discarded");

        BlockPos stalePos = helper.absolutePos(new BlockPos(6, 1, 2));
        level.setBlock(stalePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(stalePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        helper.runAfterDelay(1, () -> {
            require(level.getBlockState(stalePos).isAir() && level.getBlockEntity(stalePos) == null,
                    "a preexisting empty puddle is removed by its server tick");
            require(level.getBlockState(positivePos).is(ModBlocks.PUDDLE.get())
                            && ReagentUnits.fromFloat(puddleWater(level, positivePos)) == 1L,
                    "removing an empty puddle does not disturb a neighboring positive source");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredPuddleDispatcherAcceptsCentDoseNearCapacity(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourcePos = placePuddle(helper, new BlockPos(2, 1, 2), .01f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(player, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(WATER, 49f)));
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));
        long sourceBefore = ReagentUnits.fromFloat(puddleWater(level, sourcePos));
        long stomachBefore = ReagentUnits.fromFloat(stomachWater(player));

        InteractionResult result = dispatchEmptyHand(level, sourcePos, player);

        long sourceDelta = sourceBefore;
        long stomachDelta = ReagentUnits.fromFloat(stomachWater(player)) - stomachBefore;
        require(result == InteractionResult.SUCCESS,
                "a positive float-representable dose must not take the PASS path");
        require(sourceDelta == 1L && sourceDelta == stomachDelta,
                "one cent source and stomach deltas must match exactly");
        require(level.getBlockState(sourcePos).isAir() && level.getBlockEntity(sourcePos) == null,
                "the fully drained one-cent source disappears");
        require(ReagentUnits.fromFloat(stomachWater(player)) <= 5_000L,
                "stomach never exceeds its original 50-unit capacity");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "drinking does not mutate shared body REAGENT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredPuddleDrinkingAcceptsAwkwardSameKeyAndMixedFloatGrids(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sameKeyPos = placePuddle(helper, new BlockPos(2, 1, 2), 10.1f);
        Player sameKeyPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(sameKeyPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, 49.9f)));

        InteractionResult sameKeyResult = dispatchEmptyHand(level, sameKeyPos, sameKeyPlayer);
        long sameSourceDelta = ReagentUnits.fromFloat(10.1f) - ReagentUnits.fromFloat(puddleWater(level, sameKeyPos));
        long sameStomachDelta = ReagentUnits.fromFloat(stomachWater(sameKeyPlayer)) - ReagentUnits.fromFloat(49.9f);
        require(sameKeyResult == InteractionResult.SUCCESS && sameSourceDelta == 10L,
                "49.9/10.1 same-key float grid must accept an ordinary drink");
        require(sameSourceDelta == sameStomachDelta,
                "same-key source and stomach deltas must match exactly");
        require(stomachWater(sameKeyPlayer) <= StomachSystem.CAPACITY,
                "same-key stomach remains within its capacity");

        BlockPos mixedPos = placePuddle(helper, new BlockPos(6, 1, 2), 10.1f);
        PuddleBlockEntity mixedPuddle = (PuddleBlockEntity) level.getBlockEntity(mixedPos);
        MS14Provider.update(mixedPuddle, MS14Bridges.REAGENT,
                new ReagentAttachment(Map.of(WATER, 10.1f, SUGAR, 10.1f)));
        Player mixedPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(6, 2, 2)));
        MS14Provider.update(mixedPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, 49.9f)));

        InteractionResult mixedResult = dispatchEmptyHand(level, mixedPos, mixedPlayer);
        Map<ResourceKey<ReagentData>, Float> remaining = MS14Provider.get(mixedPuddle, MS14Bridges.REAGENT).getMap();
        Map<ResourceKey<ReagentData>, Float> stomach = MS14Provider.get(mixedPlayer, MS14Bridges.STOMACH).getMap();
        long waterSourceDelta = ReagentUnits.fromFloat(10.1f) - ReagentUnits.fromFloat(remaining.get(WATER));
        long sugarSourceDelta = ReagentUnits.fromFloat(10.1f) - ReagentUnits.fromFloat(remaining.get(SUGAR));
        long waterStomachDelta = ReagentUnits.fromFloat(stomach.get(WATER)) - ReagentUnits.fromFloat(49.9f);
        long sugarStomachDelta = ReagentUnits.fromFloat(stomach.getOrDefault(SUGAR, 0f));
        require(mixedResult == InteractionResult.SUCCESS && waterSourceDelta == 5L && sugarSourceDelta == 5L,
                "mixed 49.9/10.1 source must accept a positive drink for every mixture key");
        require(waterSourceDelta == waterStomachDelta && sugarSourceDelta == sugarStomachDelta,
                "mixed source and stomach per-key deltas must match exactly");
        require(ReagentUnits.total(ReagentUnits.fromMap(stomach).values()) <= 5_000L,
                "mixed stomach remains within its capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredBottleOnPuddleUsesItemInteractionWithoutPlayerDrain(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourcePos = placePuddle(helper, new BlockPos(2, 1, 2), 20f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                new ReagentComponent(Map.of(WATER, 10f)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);

        BlockState state = level.getBlockState(sourcePos);
        ItemInteractionResult result = state.useItemOn(bottle, level, player, InteractionHand.MAIN_HAND,
                hit(sourcePos));
        require(result.consumesAction(), "registered bottle-on-puddle interaction should consume the action");
        require(puddleWater(level, sourcePos) == 25f, "bottle transfers exactly five units into the puddle");
        require(bottle.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get())
                        .contents().get(WATER) == 5f,
                "bottle source decreases by the transferred quantity");
        require(stomachWater(player) == 0f, "bottle-on-puddle must not drain into the player's stomach");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "bottle-on-puddle leaves player shared REAGENT unchanged");
        helper.succeed();
    }

    private static InteractionResult dispatchEmptyHand(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        ItemInteractionResult itemResult = state.useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND,
                hit(pos));
        require(itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "empty held stack must request default block interaction before useWithoutItem");
        return state.useWithoutItem(level, player, hit(pos));
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static BlockPos placePuddle(GameTestHelper helper, BlockPos relativePos, float water) {
        BlockPos pos = helper.absolutePos(relativePos);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(helper.getLevel().getBlockState(pos).is(ModBlocks.PUDDLE.get()),
                "fixture contains the registered PUDDLE block over solid support");
        require(helper.getLevel().getBlockEntity(pos) instanceof PuddleBlockEntity,
                "registered puddle creates its actual block entity");
        MS14Provider.update(helper.getLevel().getBlockEntity(pos), MS14Bridges.REAGENT,
                new ReagentAttachment(water == 0f ? Map.of() : Map.of(WATER, water)));
        return pos;
    }

    private static Player mockPlayer(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0f, new GameProfile(UUID.randomUUID(), "puddle-dispatch-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    private static float puddleWater(ServerLevel level, BlockPos pos) {
        return MS14Provider.get((PuddleBlockEntity) level.getBlockEntity(pos), MS14Bridges.REAGENT)
                .getMap().getOrDefault(WATER, 0f);
    }

    private static float stomachWater(Player player) {
        return MS14Provider.get(player, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
