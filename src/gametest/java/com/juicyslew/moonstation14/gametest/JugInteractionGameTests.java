package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.JugBlockEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.InteractionResultHolder;

import java.util.Map;
import java.util.UUID;

import static com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS;
import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JugInteractionGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<ReagentData> SUGAR = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "sugar"));

    private JugInteractionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredHeldJugSneakUseIngestsAtSipBoundaryAndPreservesFullSource(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        require(EntityType.PLAYER.is(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.ELIGIBLE_ENTITY_TYPES),
                "the registered thirst eligibility tag must include players");

        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)), true);
        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 20f)));
        player.setItemInHand(InteractionHand.MAIN_HAND, jug);
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));

        require(jug.getItem() == ModItems.JUG.get(), "fixture must use the registered MoonStation14 jug item");
        require(player.getItemInHand(InteractionHand.MAIN_HAND) == jug,
                "main hand must reference the actual registered jug stack");
        InteractionResultHolder<ItemStack> result = jug.getItem().use(level, player, InteractionHand.MAIN_HAND);
        require(result.getResult().consumesAction(), "sneaking with a nonempty jug starts drinking");
        require(player.isUsingItem(), "sneaking player enters the item-use state");

        int duration = jug.getItem().getUseDuration(jug, player);
        jug.getItem().onUseTick(level, player, jug, duration - SIP_INTERVAL_TICKS + 1);
        require(reagentTotal(jug) == 20f, "held jug must not transfer before the sip boundary");
        require(stomachWater(player) == 0f, "stomach must remain unchanged before the sip boundary");

        jug.getItem().onUseTick(level, player, jug, duration - SIP_INTERVAL_TICKS);
        require(reagentTotal(jug) == 20f - UNITS_PER_SIP,
                "one due callback removes exactly UNITS_PER_SIP from held jug contents");
        require(stomachWater(player) == UNITS_PER_SIP,
                "one due held-jug callback adds exactly UNITS_PER_SIP to stomach");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "held jug drinking leaves shared REAGENT unchanged");

        Player fullPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(5, 2, 2)), true);
        ItemStack fullJug = new ItemStack(ModItems.JUG.get());
        fullJug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 20f)));
        fullPlayer.setItemInHand(InteractionHand.MAIN_HAND, fullJug);
        MS14Provider.update(fullPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(SUGAR, StomachSystem.CAPACITY)));
        InteractionResultHolder<ItemStack> fullResult = fullJug.getItem().use(level, fullPlayer,
                InteractionHand.MAIN_HAND);
        require(fullResult.getResult().consumesAction(), "sneaking should start even when stomach is full");
        fullJug.getItem().onUseTick(level, fullPlayer, fullJug,
                fullJug.getItem().getUseDuration(fullJug, fullPlayer) - SIP_INTERVAL_TICKS);
        require(reagentTotal(fullJug) == 20f, "full stomach preserves held jug contents");
        require(fullPlayer.isUsingItem(), "full stomach does not prematurely stop jug use while source remains");
        require(MS14Provider.get(fullPlayer, MS14Bridges.STOMACH).getMap()
                        .equals(Map.of(SUGAR, StomachSystem.CAPACITY)), "full stomach remains unchanged");
        require(MS14Provider.get(fullPlayer, MS14Bridges.REAGENT).getMap().isEmpty(),
                "full stomach drinking does not route jug contents into shared REAGENT");

        Player nonSneakingPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(8, 2, 2)), false);
        ItemStack thrownJug = new ItemStack(ModItems.JUG.get());
        thrownJug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 20f)));
        nonSneakingPlayer.setItemInHand(InteractionHand.MAIN_HAND, thrownJug);
        InteractionResultHolder<ItemStack> throwResult = thrownJug.getItem().use(level, nonSneakingPlayer,
                InteractionHand.MAIN_HAND);
        require(throwResult.getResult().consumesAction(), "non-sneaking jug use retains its throw action");
        require(nonSneakingPlayer.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "non-sneaking survival use consumes the thrown jug");
        require(stomachWater(nonSneakingPlayer) == 0f, "throwing a jug does not create stomach contents");
        require(MS14Provider.get(nonSneakingPlayer, MS14Bridges.REAGENT).getMap().isEmpty(),
                "throwing a jug does not write to shared body REAGENT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredJugEmptyHandDispatcherIngestsAndRejectsWithoutMutation(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourcePos = placeJug(helper, new BlockPos(2, 1, 2), 12f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));

        InteractionResult drank = dispatchEmptyHand(level, sourcePos, player);
        require(drank == InteractionResult.SUCCESS, "accepted nonempty jug sip returns SUCCESS");
        require(jugWater(level, sourcePos) == 7f, "one empty-hand click removes exactly five jug units");
        require(stomachWater(player) == 5f, "the same click adds exactly five units to player stomach");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "jug drinking leaves player shared REAGENT unchanged");

        BlockPos fullPos = placeJug(helper, new BlockPos(5, 1, 2), 12f);
        Player fullPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(5, 2, 2)));
        MS14Provider.update(fullPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(SUGAR, StomachSystem.CAPACITY)));
        require(dispatchEmptyHand(level, fullPos, fullPlayer) == InteractionResult.PASS,
                "full stomach causes the jug's empty-hand callback to return PASS");
        require(jugWater(level, fullPos) == 12f, "full stomach does not drain jug source");
        require(MS14Provider.get(fullPlayer, MS14Bridges.STOMACH).getMap()
                        .equals(Map.of(SUGAR, StomachSystem.CAPACITY)), "full stomach remains unchanged");
        require(MS14Provider.get(fullPlayer, MS14Bridges.REAGENT).getMap().isEmpty(),
                "rejected jug sip does not alter shared REAGENT");

        BlockPos emptyPos = placeJug(helper, new BlockPos(8, 1, 2), 0f);
        Player emptyPlayer = mockPlayer(level, helper.absolutePos(new BlockPos(8, 2, 2)));
        require(dispatchEmptyHand(level, emptyPos, emptyPlayer) == InteractionResult.PASS,
                "empty jug causes the empty-hand callback to return PASS");
        require(jugWater(level, emptyPos) == 0f && stomachWater(emptyPlayer) == 0f,
                "empty jug does not mutate source or stomach");
        require(MS14Provider.get(emptyPlayer, MS14Bridges.REAGENT).getMap().isEmpty(),
                "empty jug does not mutate shared REAGENT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredBottleOnJugRefillsWithoutDrinking(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourcePos = placeJug(helper, new BlockPos(2, 1, 2), 12f);
        Player player = mockPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        MS14Provider.update(player, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(SUGAR, 11f)));
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 10f)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);

        BlockState state = level.getBlockState(sourcePos);
        ItemInteractionResult result = state.useItemOn(bottle, level, player, InteractionHand.MAIN_HAND, hit(sourcePos));
        require(result.consumesAction(), "registered bottle-on-jug interaction consumes the action");
        require(jugWater(level, sourcePos) == 17f, "bottle transfers five units into the jug");
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER) == 5f,
                "bottle source decreases by the transferred quantity");
        require(stomachWater(player) == 0f, "bottle-on-jug does not drink into the player's stomach");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "bottle-on-jug leaves player shared REAGENT unchanged");
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

    private static BlockPos placeJug(GameTestHelper helper, BlockPos relativePos, float water) {
        BlockPos pos = helper.absolutePos(relativePos);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.JUG.get().defaultBlockState(), 3);
        require(helper.getLevel().getBlockState(pos).is(ModBlocks.JUG.get()), "fixture contains the registered JUG block");
        require(helper.getLevel().getBlockEntity(pos) instanceof JugBlockEntity,
                "registered jug creates its actual block entity");
        MS14Provider.update(helper.getLevel().getBlockEntity(pos), MS14Bridges.REAGENT,
                new ReagentAttachment(water == 0f ? Map.of() : Map.of(WATER, water)));
        return pos;
    }

    private static Player mockPlayer(ServerLevel level, BlockPos pos) {
        return mockPlayer(level, pos, false);
    }

    private static Player mockPlayer(ServerLevel level, BlockPos pos, boolean sneaking) {
        Player player = new Player(level, pos, 0f, new GameProfile(UUID.randomUUID(), "jug-dispatch-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isShiftKeyDown() { return sneaking; }
        };
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    private static float jugWater(ServerLevel level, BlockPos pos) {
        return MS14Provider.get((JugBlockEntity) level.getBlockEntity(pos), MS14Bridges.REAGENT)
                .getMap().getOrDefault(WATER, 0f);
    }

    private static float stomachWater(Player player) {
        return MS14Provider.get(player, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f);
    }

    private static float reagentTotal(ItemStack stack) {
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        return component == null ? 0f : (float) component.contents().values().stream()
                .mapToDouble(Float::doubleValue).sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
