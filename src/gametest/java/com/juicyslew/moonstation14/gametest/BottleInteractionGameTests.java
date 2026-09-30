package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.UUID;

import static com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS;
import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BottleInteractionGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<ReagentData> SUGAR = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "sugar"));

    private BottleInteractionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredBottleUseCallbackIngestsOnlyAtSipInterval(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player player = mockPlayer(helper, level, new BlockPos(1, 1, 1));
        require(StomachSystem.isEligible(player), "bound player must carry the Stomach component");
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 20f)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        MS14Provider.update(player, MS14Bridges.REAGENT,
                new ReagentAttachment(Map.of(SUGAR, 11f)));

        require(player.getItemInHand(InteractionHand.MAIN_HAND) == bottle,
                "main hand must reference the actual registered bottle stack");
        require(bottle.getItem() == ModItems.BOTTLE.get(), "fixture must use the registered MoonStation14 bottle");
        require(player.level() == level, "mock player must belong to this GameTest server level");
        int duration = bottle.getItem().getUseDuration(bottle, player);
        InteractionResultHolder<ItemStack> result = bottle.getItem().use(level, player, InteractionHand.MAIN_HAND);
        require(result.getResult().consumesAction(), "BottleItem.use must start consuming the nonempty bottle");
        require(player.isUsingItem(), "mock player must enter the item-use state");

        bottle.getItem().onUseTick(level, player, bottle, duration - SIP_INTERVAL_TICKS + 1);
        require(reagentTotal(bottle) == 20f, "bottle must not ingest one tick before the sip boundary");
        require(stomachWater(player) == 0f, "stomach must remain unchanged before the sip boundary");

        bottle.getItem().onUseTick(level, player, bottle, duration - SIP_INTERVAL_TICKS);
        require(reagentTotal(bottle) == 20f - UNITS_PER_SIP,
                "one due callback must remove exactly UNITS_PER_SIP from bottle contents");
        require(stomachWater(player) == UNITS_PER_SIP,
                "one due callback must add exactly UNITS_PER_SIP to stomach water");
        require(MS14Provider.get(player, MS14Bridges.REAGENT).getMap().equals(Map.of(SUGAR, 11f)),
                "bottle ingestion must leave shared REAGENT untouched");

        Player fullPlayer = mockPlayer(helper, level, new BlockPos(4, 1, 1));
        ItemStack fullBottle = new ItemStack(ModItems.BOTTLE.get());
        fullBottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 20f)));
        fullPlayer.setItemInHand(InteractionHand.MAIN_HAND, fullBottle);
        MS14Provider.update(fullPlayer, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(SUGAR, StomachSystem.CAPACITY)));
        InteractionResultHolder<ItemStack> fullResult = fullBottle.getItem().use(level, fullPlayer,
                InteractionHand.MAIN_HAND);
        require(fullResult.getResult().consumesAction(), "nonempty bottle should start even when stomach is full");
        fullBottle.getItem().onUseTick(level, fullPlayer, fullBottle,
                fullBottle.getItem().getUseDuration(fullBottle, fullPlayer) - SIP_INTERVAL_TICKS);
        require(reagentTotal(fullBottle) == 20f, "full stomach must preserve bottle contents");
        require(MS14Provider.get(fullPlayer, MS14Bridges.STOMACH).getMap().equals(
                        Map.of(SUGAR, StomachSystem.CAPACITY)),
                "full stomach remains unchanged by bottle callback");
        helper.succeed();
    }

    private static Player mockPlayer(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        Player player = new Player(level, helper.absolutePos(pos), 0f,
                new GameProfile(UUID.randomUUID(), "bottle-callback-test")) {
            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        player.setPos(helper.absolutePos(pos).getX(), helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ());
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(player, level);
        return player;
    }

    private static float reagentTotal(ItemStack stack) {
        double total = stack.get(ModDataComponents.REAGENT.get()).contents().values().stream()
                .mapToDouble(Float::doubleValue).sum();
        return total > Float.MAX_VALUE ? Float.NaN : (float) total;
    }

    private static float stomachWater(Player player) {
        return MS14Provider.get(player, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
