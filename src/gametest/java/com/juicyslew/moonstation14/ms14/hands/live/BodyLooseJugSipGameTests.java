package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyLooseJugSipGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private BodyLooseJugSipGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyHandSipsLooseJugConservingContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID),
                "human identity bound");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body),
                "live hands and body system initialized");
        require(StomachSystem.isEligible(body), "human body has eligible stomach");
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(empty != null && empty.activeHand().equals("left") && empty.revision() == 0,
                "active hand starts empty at revision zero");

        ItemStack jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 10f)));
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), jug);
        target.setNoPickUpDelay();
        require(level.addFreshEntity(target), "filled loose jug spawned");
        var targetId = target.getUUID();

        require(BodyLiquidInteraction.sipOwned(body, targetId, empty.activeHand(), null, empty.revision(), () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "trusted body route sips loose jug");
        require(!target.isRemoved() && level.getEntity(targetId) == target
                        && water(target.getItem()) == 5f,
                "same world jug remains with five units");
        require(water(MS14Provider.get(body, MS14Bridges.STOMACH).getMap()) == 5f,
                "stomach receives the exact five-unit dose");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after != null && after.activeHand().equals("left") && after.stackCopy("left").isEmpty(),
                "empty active hand remains empty");
        require(BodyLiquidInteraction.sipOwned(body, targetId, after.activeHand(), null, after.revision(), () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "immediate repeat is rejected by sip cooldown");
        require(water(target.getItem()) == 5f && water(MS14Provider.get(body, MS14Bridges.STOMACH).getMap()) == 5f,
                "cooldown denial conserves both source and stomach");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ownedJugFillTransfersBothDirectionsConservingContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human body created");
        body.setNoAi(true);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID),
                "human identity bound");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body),
                "live hands and body system initialized");
        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = empty.activeHand();
        LiveHands.WholeResult inserted = empty.putWhole(empty.revision(), hand, new com.juicyslew.moonstation14.ms14.hands.ItemToken("jug"),
                filledJug(10));
        require(inserted.succeeded(), "filled held jug inserted");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        LiveHands before = inserted.state();
        String token = before.token(hand).orElseThrow();
        LooseItemEntity target = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + .5), new ItemStack(ModItems.JUG.get()));
        target.setNoPickUpDelay();
        require(level.addFreshEntity(target), "empty loose jug spawned");
        UUID targetId = target.getUUID();

        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, before.revision(), false, () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "held jug fills loose jug");
        LiveHands poured = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(poured.revision() == before.revision() + 1 && poured.token(hand).filter(token::equals).isPresent(),
                "fill preserves ownership and advances revision once");
        require(water(poured.stackCopy(hand).orElseThrow()) == 5f && water(target.getItem()) == 5f,
                "forward transfer leaves five units in each jug");
        require(waterCents(poured.stackCopy(hand).orElseThrow()) + waterCents(target.getItem()) == 1000,
                "forward transfer conserves integer cents");
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, poured.revision(), true, () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "loose jug fills held jug");
        LiveHands returned = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(returned.revision() == poured.revision() + 1 && returned.token(hand).filter(token::equals).isPresent(),
                "reverse fill preserves ownership and advances revision once");
        require(water(returned.stackCopy(hand).orElseThrow()) == 10f && water(target.getItem()) == 0f,
                "reverse transfer restores ten units to held jug");
        require(waterCents(returned.stackCopy(hand).orElseThrow()) + waterCents(target.getItem()) == 1000,
                "reverse transfer conserves integer cents");

        LiveHands snapshot = returned;
        ItemStack heldSnapshot = snapshot.stackCopy(hand).orElseThrow();
        ItemStack worldSnapshot = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, "stale", snapshot.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "stale owner token is rejected");
        requireUnchanged(body, target, snapshot, heldSnapshot, worldSnapshot, hand, "stale token denial");
        target.setItem(filledJug(ModItems.JUG.get().getCapacity()));
        worldSnapshot = target.getItem().copy();
        require(BodyLiquidInteraction.fillOwned(body, targetId, hand, token, snapshot.revision(), false, () -> true)
                        == BodyLiquidInteraction.Result.DENIED,
                "full target is rejected");
        requireUnchanged(body, target, snapshot, heldSnapshot, worldSnapshot, hand, "full target denial");
        helper.succeed();
    }

    private static ItemStack filledJug(float units) {
        ItemStack stack = new ItemStack(ModItems.JUG.get());
        if (units > 0) stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        return stack;
    }

    private static long waterCents(ItemStack stack) {
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        return component == null ? 0 : ReagentUnits.fromFloat(component.contents().getOrDefault(WATER, 0f));
    }

    private static void requireUnchanged(Villager body, LooseItemEntity target, LiveHands expected,
                                         ItemStack held, ItemStack world, String hand, String reason) {
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(current == expected && current.revision() == expected.revision()
                        && current.stackCopy(hand).filter(s -> ItemStack.isSameItemSameComponents(s, held)).isPresent()
                        && ItemStack.isSameItemSameComponents(target.getItem(), world),
                reason + " preserves both owner components and revision");
    }

    private static float water(ItemStack stack) {
        ReagentComponent reagents = stack.get(ModDataComponents.REAGENT.get());
        return reagents == null ? 0f : reagents.contents().getOrDefault(WATER, 0f);
    }

    private static float water(Map<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Float> map) {
        return map.getOrDefault(WATER, 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
