package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPuddleDrinkGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private BodyPuddleDrinkGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void registeredPuddleDrinkCommitsImmediately(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(bodyPos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "villager body created");
        body.setNoAi(true);
        body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        BlockPos puddlePos = bodyPos.offset(2, 0, 0);
        var puddleState = ModBlocks.PUDDLE.get().defaultBlockState();
        double targetY = puddlePos.getY() + puddleState.getShape(level, puddlePos).bounds().getCenter().y;
        body.setXRot((float) Math.toDegrees(Math.atan2(body.getEyeY() - targetY,
                Math.hypot(puddlePos.getX() + .5 - (bodyPos.getX() + .5),
                        puddlePos.getZ() + .5 - (bodyPos.getZ() + .5)))));
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human character enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body/hands initialized");

        level.setBlock(puddlePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(puddlePos, puddleState, 3);
        require(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity, "actual puddle block entity created");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 10f)));
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyPuddleDrinkService.start(body, hands.activeHand(), hands.revision(), puddlePos, () -> true)
                        == BodyLiquidInteraction.Result.SUCCESS, "visible puddle sip is accepted");
        require(water(puddle) == 900 && water(body) == 100,
                "one exact 100-cent dose commits immediately to stomach and conserves source");
        helper.succeed();
    }

    private static long water(PuddleBlockEntity puddle) {
        return ReagentUnits.fromFloat(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().getOrDefault(WATER, 0f));
    }

    private static long water(Villager body) {
        return ReagentUnits.fromFloat(MS14Provider.get(body, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f));
    }

    private static String diagnostics(net.minecraft.server.level.ServerLevel level, Villager body,
                                     PuddleBlockEntity puddle, BlockPos puddlePos, long startedAt) {
        var stomach = MS14Provider.get(body, MS14Bridges.STOMACH);
        var blood = MS14Provider.get(body, MS14Bridges.BLOODSTREAM);
        var source = MS14Provider.get(puddle, MS14Bridges.REAGENT);
        return "puddleWater=" + water(puddle) + ", bodyStomachWater=" + water(body)
                + ", stomachAllReagents=" + (stomach == null ? "null" : stomach.getMap())
                + ", bodyBloodWater=" + (blood == null ? "null" : blood.getMap().getOrDefault(WATER, 0f))
                + ", elapsedTicks=" + (level.getGameTime() - startedAt)
                + ", bodyAlive=" + body.isAlive() + ", hurtTime=" + body.hurtTime
                + ", health=" + body.getHealth() + ", bodyPos=" + body.position()
                + ", puddlePos=" + puddlePos + ", puddleBlock=" + level.getBlockState(puddlePos)
                + ", puddleBEIdentityMatches=" + (level.getBlockEntity(puddlePos) == puddle)
                + ", sourceBE=" + puddle.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(puddle))
                + ", sourceReagents=" + (source == null ? "null" : source.getMap());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
