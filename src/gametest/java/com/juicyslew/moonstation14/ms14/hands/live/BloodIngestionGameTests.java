package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"timed-interaction", "ingestion", "chemistry", "physiology", "damage"})
@PrefixGameTestTemplate(false)
public final class BloodIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> BLOOD =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "blood"));

    private BloodIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 240)
    public static void humanDrinkingOwnBloodPuddleCausesDigestivePoison(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager treated = body(helper, new BlockPos(2, 1, 2));
        Villager control = body(helper, new BlockPos(5, 1, 2));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity, "blood puddle created");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(pos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(BLOOD, 1f)));
        LiveHands hands = treated.getData(ModDataAttachments.LIVE_HANDS.get());
        aimAtPuddle(treated, pos);
        require(PuddleTargetProbe.selected(level, treated, pos), "thin blood puddle selected");
        require(BodyTimedIngestionService.startPuddleOwned(treated, pos, hands.activeHand(), hands.revision(), 1, () -> true)
                .name().equals("OK"), "production timed puddle ingestion starts");
        require(poison(treated) == 0f && poison(control) == 0f && blood(puddle) == 100,
                "no ingestion or damage occurs at operation start");
        helper.runAfterDelay(45, () -> {
            require(blood(puddle) == 100 && stomach(treated).isEmpty(), "source and stomach unchanged before 60-tick deadline");
            require(poison(treated) == 0f && poison(control) == 0f, "no pre-dose poison");
        });
        helper.runAfterDelay(63, () -> {
            require(level.getBlockState(pos).isAir(), "consumed source puddle removed");
            require(stomach(treated).getOrDefault(BLOOD, 0f) > 0f, "actual puddle dose reached stomach");
            require(!stomach(treated).containsKey(BLOOD) || ReagentUnits.fromFloat(stomach(treated).get(BLOOD)) > 0,
                    "positive blood dose retained for digestion");
            require(!MS14Provider.get(treated, MS14Bridges.REAGENT).getMap().containsKey(BLOOD),
                    "raw ingested blood is not directly placed in body reagent store");
        });
        AtomicBoolean sawPoison = new AtomicBoolean();
        sample(helper, treated, control, sawPoison, 64, 156);
        helper.runAfterDelay(220, () -> {
            require(sawPoison.get(), "scheduled blood digestion produced typed poison damage");
            require(poison(control) == 0f, "matched untreated healthy control remains poison-free");
            require(treated.getExistingDataOrNull(ModDataAttachments.BLOOD.get()) == null
                            || treated.getData(ModDataAttachments.BLOOD.get()).bleedRate() == 0d,
                    "treated body has no ongoing bleed");
            helper.succeed();
        });
    }

    private static void sample(GameTestHelper h, Villager treated, Villager control, AtomicBoolean seen, int tick, int end) {
        if (tick > end) return;
        h.runAfterDelay(tick == 64 ? 64 : 5, () -> {
            float current = poison(treated);
            if (current > 0f) seen.set(true);
            require(poison(control) == 0f, "untreated control has no poison during observation");
            sample(h, treated, control, seen, tick + 5, end);
        });
    }

    private static Villager body(GameTestHelper helper, BlockPos local) {
        BlockPos pos = helper.absolutePos(local);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "human body created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body) && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID), "human enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "healthy physiology and hands initialized");
        return body;
    }

    private static void aimAtPuddle(Villager body, BlockPos pos) {
        var level = body.level();
        var bounds = level.getBlockState(pos).getShape(level, pos, CollisionContext.of(body)).bounds();
        Vec3 target = new Vec3(pos.getX() + (bounds.minX + bounds.maxX) / 2, pos.getY() + (bounds.minY + bounds.maxY) / 2,
                pos.getZ() + (bounds.minZ + bounds.maxZ) / 2);
        Vec3 delta = target.subtract(body.getEyePosition());
        body.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        body.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        body.setYHeadRot(body.getYRot()); body.setYBodyRot(body.getYRot());
    }

    private static float poison(Villager body) {
        DamageData data = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return data == null ? 0f : data.getMap().getOrDefault(DamageKeys.POISON, 0f);
    }
    private static Map<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Float> stomach(Villager body) {
        ReagentAttachment a = body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()); return a == null ? Map.of() : a.getMap();
    }
    private static long blood(PuddleBlockEntity p) { return ReagentUnits.fromFloat(MS14Provider.get(p, MS14Bridges.REAGENT).getMap().getOrDefault(BLOOD, 0f)); }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
