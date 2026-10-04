package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;
import java.util.Map;

public final class BodyPuddleTargetProbeGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private BodyPuddleTargetProbeGameTests() { }

    public static void thinPuddleMarginCanBeSelectedAndStarted(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(1, 1, 2));
        Villager body = body(level, bodyPos);
        BlockPos puddlePos = bodyPos.offset(4, 0, 0);
        puddle(level, puddlePos);
        double aimY = puddlePos.getY() + .205625;
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - aimY, 4)));
        var from = body.getEyePosition();
        var to = from.add(body.getLookAngle().scale(5));
        var outlineHit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, body));
        require(outlineHit.getType() != HitResult.Type.BLOCK || !outlineHit.getBlockPos().equals(puddlePos),
                "native outline ray 0.19 above thin puddle misses it");
        require(Objects.equals(PuddleTargetProbe.find(level, body), puddlePos), "ray .19 above thin .015625 puddle remains targetable within margin");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyPuddleDrinkService.start(body, hands.activeHand(), hands.revision(), puddlePos, () -> true)
                == BodyLiquidInteraction.Result.SUCCESS, "real service accepts probe-selected puddle");
        require(water((PuddleBlockEntity) level.getBlockEntity(puddlePos)) == 900,
                "successful start commits one immediate unit");
        require(ReagentUnits.fromFloat(MS14Provider.get(body, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f)) == 100,
                "successful start admits same unit to stomach");
        helper.succeed();
    }

    public static void wallOccludesPuddleProbeAndDrinkStart(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, bodyPos);
        BlockPos target = bodyPos.offset(2, 0, 0);
        puddle(level, target);
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - (target.getY() + .015625), 2)));
        level.setBlock(bodyPos.offset(1, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        require(PuddleTargetProbe.find(level, body) == null, "stone wall occludes puddle");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyPuddleDrinkService.start(body, hands.activeHand(), hands.revision(), target, () -> true)
                == BodyLiquidInteraction.Result.DENIED, "service refuses occluded puddle");
        helper.succeed();
    }

    public static void actualCenterBeyondFiveBlocksIsOutOfReach(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, bodyPos);
        BlockPos target = bodyPos.offset(5, 0, 0);
        puddle(level, target);
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - (target.getY() + .015625), 5)));
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(PuddleTargetProbe.find(level, body) == null, "puddle center beyond five-block reach rejected despite expanded ray box");
        require(BodyPuddleDrinkService.start(body, hands.activeHand(), hands.revision(), target, () -> true)
                == BodyLiquidInteraction.Result.DENIED, "service refuses out-of-reach puddle");
        helper.succeed();
    }

    private static Villager body(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        body.setYRot(-90); body.setYHeadRot(-90);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "trusted character enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body and hands initialized");
        return body;
    }

    private static void puddle(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity, "actual puddle block entity created");
        MS14Provider.update(level.getBlockEntity(pos), MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 10f)));
    }

    private static long water(PuddleBlockEntity puddle) {
        return ReagentUnits.fromFloat(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().getOrDefault(WATER, 0f));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
