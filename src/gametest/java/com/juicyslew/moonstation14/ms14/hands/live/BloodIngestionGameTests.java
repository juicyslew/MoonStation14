package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
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

@GameTestTags({"timed-interaction", "ingestion", "chemistry", "physiology", "damage", "repair-blood-ingestion"})
@PrefixGameTestTemplate(false)
public final class BloodIngestionGameTests {
    private static final int TICKET_WARMUP_MAX_TICKS = 8;
    private static final int FIRST_DIGESTION_SAMPLE_TICK = 64;
    private static final int LAST_DIGESTION_SAMPLE_TICK = 156;
    private static final int FINAL_DIGESTION_CHECK_TICK = 220;
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> BLOOD =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "blood"));

    private BloodIngestionGameTests() { }

    // Readiness <=8 ticks + digestion observation offset 220 = <=228, leaving 12 ticks in the existing budget.
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 240)
    public static void humanDrinkingOwnBloodPuddleCausesDigestivePoison(GameTestHelper helper) {
        var level = helper.getLevel();
        var tickets = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(1, 1, 1)),
                helper.absolutePos(new BlockPos(6, 2, 3)), 1);
        Villager treated = body(helper, new BlockPos(2, 1, 2));
        Villager control = body(helper, new BlockPos(5, 1, 2));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity, "blood puddle created");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(pos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(BLOOD, 1f)));
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, TICKET_WARMUP_MAX_TICKS, () -> {
        require(tickets.entityTicking(treated.blockPosition()) && tickets.entityTicking(control.blockPosition()),
                "treated and control bodies are entity-ticking before ingestion starts: "
                        + tickets.diagnostic(treated.blockPosition()) + "; " + tickets.diagnostic(control.blockPosition()));
        LiveHands hands = treated.getData(ModDataAttachments.LIVE_HANDS.get());
        aimAtPuddle(treated, pos);
        require(PuddleTargetProbe.selected(level, treated, pos), "thin blood puddle selected");
        require(BodyTimedIngestionService.startPuddleOwned(treated, pos, hands.activeHand(), hands.revision(), 1, () -> true)
                .name().equals("OK"), "production timed puddle ingestion starts");
        // All later phase times are offsets from this successful ingestion start, not the original test tick.
        long ingestionStartedAt = helper.getTick();
        int treatedTickAtStart = treated.tickCount;
        int controlTickAtStart = control.tickCount;
        require(poison(treated) == 0f && poison(control) == 0f && blood(puddle) == 100,
                "no ingestion or damage occurs at operation start");
        AtomicBoolean sawPoison = new AtomicBoolean();
        float[] maxTypedPoison = {0f};
        boolean[] beforeDoseChecked = {false};
        boolean[] ingestedChecked = {false};
        BoundedGameTestWait.safeOnEachTick(helper, () -> {
            long elapsed = helper.getTick() - ingestionStartedAt;
            if (!beforeDoseChecked[0] && elapsed >= 45) {
                require(blood(puddle) == 100 && stomach(treated).isEmpty(),
                        "source and stomach unchanged before 60-tick deadline at ingestion tick " + elapsed);
                require(poison(treated) == 0f && poison(control) == 0f, "no pre-dose poison");
                beforeDoseChecked[0] = true;
            }
            if (!ingestedChecked[0] && elapsed >= 63) {
                // A block entity can be reused when bleeding immediately creates a new puddle;
                // its Java removal state therefore does not identify the original source.
                var currentPuddle = level.getBlockEntity(pos) instanceof PuddleBlockEntity current ? current : null;
                require(blood(puddle) == 0 || currentPuddle != puddle,
                        "original source inventory must be drained or replaced; originalSourceMl=" + blood(puddle)
                                + ", currentSourceMl=" + (currentPuddle == null ? 0 : blood(currentPuddle))
                                + ", sameBlockEntity=" + (currentPuddle == puddle)
                                + ", queuedSpill=" + (currentPuddle == null ? "none" : MS14Provider.get(currentPuddle, MS14Bridges.REAGENT).getMap())
                                + ", treatedBleedRate=" + (treated.getExistingDataOrNull(ModDataAttachments.BLOOD.get()) == null
                                ? 0d : treated.getData(ModDataAttachments.BLOOD.get()).bleedRate()));
                require(stomach(treated).getOrDefault(BLOOD, 0f) > 0f, "actual puddle dose reached stomach");
                require(!stomach(treated).containsKey(BLOOD) || ReagentUnits.fromFloat(stomach(treated).get(BLOOD)) > 0,
                        "positive blood dose retained for digestion");
                require(!MS14Provider.get(treated, MS14Bridges.REAGENT).getMap().containsKey(BLOOD),
                        "raw ingested blood is not directly placed in body reagent store");
                ingestedChecked[0] = true;
            }
            if (elapsed >= FIRST_DIGESTION_SAMPLE_TICK && elapsed <= LAST_DIGESTION_SAMPLE_TICK
                    && (elapsed - FIRST_DIGESTION_SAMPLE_TICK) % 5 == 0) {
                require(tickets.entityTicking(treated.blockPosition()) && tickets.entityTicking(control.blockPosition()),
                        "both digestion subjects remain entity-ticking at ingestion tick " + elapsed + ": "
                                + tickets.diagnostic(treated.blockPosition()) + "; " + tickets.diagnostic(control.blockPosition()));
                require(treated.tickCount > treatedTickAtStart && control.tickCount > controlTickAtStart,
                        "native body ticks processed digestion sample at ingestion tick " + elapsed
                                + "; treatedTickDelta=" + (treated.tickCount - treatedTickAtStart)
                                + ", controlTickDelta=" + (control.tickCount - controlTickAtStart));
                float currentPoison = poison(treated);
                if (currentPoison > 0f) sawPoison.set(true);
                maxTypedPoison[0] = Math.max(maxTypedPoison[0], currentPoison);
                require(poison(control) == 0f, "untreated healthy control remains poison-free at ingestion tick "
                        + elapsed + "; poison=" + poison(control));
            }
            if (elapsed < FINAL_DIGESTION_CHECK_TICK) return;
            require(tickets.entityTicking(treated.blockPosition()) && tickets.entityTicking(control.blockPosition()),
                    "both bodies remain entity-ticking through scheduled digestion: "
                            + tickets.diagnostic(treated.blockPosition()) + "; " + tickets.diagnostic(control.blockPosition()));
            require(treated.tickCount > treatedTickAtStart && control.tickCount > controlTickAtStart,
                    "native body ticks continued through final digestion check; treatedDelta="
                            + (treated.tickCount - treatedTickAtStart) + ", controlDelta="
                            + (control.tickCount - controlTickAtStart));
            require(sawPoison.get() && maxTypedPoison[0] > 0f,
                    "scheduled blood digestion produced typed poison damage; observedTypedPoison="
                            + maxTypedPoison[0] + ", currentTypedPoison=" + poison(treated)
                            + ", nativeTickDelta=" + (treated.tickCount - treatedTickAtStart));
            require(poison(control) == 0f, "matched untreated healthy control remains poison-free; poison="
                    + poison(control));
            require(treated.getExistingDataOrNull(ModDataAttachments.BLOOD.get()) == null
                            || treated.getData(ModDataAttachments.BLOOD.get()).bleedRate() == 0d,
                    "treated body has no ongoing bleed");
            tickets.close();
            helper.succeed();
        });
        }, treated.blockPosition(), control.blockPosition());
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
