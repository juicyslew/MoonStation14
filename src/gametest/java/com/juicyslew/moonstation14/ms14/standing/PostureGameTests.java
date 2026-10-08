package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestTags({"posture", "collision", "status", "timed-interaction"})
public final class PostureGameTests {
    private PostureGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 12)
    @GameTestTags({"standing", "audio", "medical-state", "posture-fall-audio"})
    public static void forcedDownEmitsResolvedSoundOnceAndMedicalRepeatIsQuiet(GameTestHelper helper) {
        Villager body = human(helper);
        helper.runAfterDelay(2, () -> {
            require(body.isAddedToLevel() && helper.getLevel().getEntity(body.getUUID()) == body
                            && !PostureSystem.isDown(body),
                    "Human is a registered live entity in initial standing posture before forced down");
            var emitted = new ArrayList<net.minecraft.resources.ResourceLocation>();
            PostureSystem.forceDownForTest(body, 0, (entity, sound) -> emitted.add(sound));
            require(emitted.equals(java.util.List.of(net.minecraft.resources.ResourceLocation.parse("moonstation14:body_fall"))),
                    "actual forced-down commit emits the resolved body-fall sound once");
            PostureSystem.forceDownForTest(body, 0, (entity, sound) -> emitted.add(sound));
            PostureSystem.applyMobStateChange(body, com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD,
                    (entity, sound) -> emitted.add(sound));
            require(emitted.size() == 1, "repeated knockdown and Critical-to-Dead while already down are silent");
            require(PostureSystem.isDown(body) && body.getPose() == Pose.SWIMMING,
                    "sound capture seam retains actual shared posture and physical commit");
            require(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(
                    net.minecraft.resources.ResourceLocation.parse("moonstation14:body_fall")).isPresent(),
                    "mapped body-fall sound event is registered");
            var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
            require(pig != null, "pig entity created");
            pig.setNoAi(true); pig.setNoGravity(true); pig.setPos(body.getX() + 3, body.getY(), body.getZ());
            require(helper.getLevel().addFreshEntity(pig)
                            && CharacterIdentitySystem.enroll(pig, helper.getLevel(),
                            net.minecraft.resources.ResourceLocation.parse("moonstation14:pig")),
                    "pig enrolled through typed pig prototype");
            var pigSounds = new ArrayList<net.minecraft.resources.ResourceLocation>();
            require(pig.isAddedToLevel() && helper.getLevel().getEntity(pig.getUUID()) == pig
                            && !PostureSystem.isDown(pig), "Pig has joined the active level while still standing");
            var pigActor = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(pig).orElse(null);
            var pigStanding = pigActor == null ? null : pigActor.component(com.juicyslew.moonstation14.ms14.character.components.StandingStateComponent.class).orElse(null);
            PostureSystem.applyMobStateChange(pig, com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL,
                    (entity, sound) -> pigSounds.add(sound));
            require(pigStanding != null && !pigStanding.standingEligible()
                            && PostureSystem.isDown(pig) && pigSounds.equals(java.util.List.of(
                    net.minecraft.resources.ResourceLocation.parse("moonstation14:body_fall"))),
                    "ready Pig with resolved ineligible StandingState still emits medical down sound");
            Villager startup = EntityType.VILLAGER.create(helper.getLevel());
            require(startup != null, "startup entity created outside the live level");
            startup.setNoAi(true); startup.setNoGravity(true); startup.setPos(body.getX() + 2, body.getY(), body.getZ());
            require(CharacterIdentitySystem.enroll(startup, helper.getLevel(), ModCharacters.HUMAN_ID),
                    "unadded startup entity enrolled against resolved Human prototype");
            var startupSounds = new ArrayList<net.minecraft.resources.ResourceLocation>();
            require(!startup.isAddedToLevel(), "startup fixture remains unadded before initialization transition");
            PostureSystem.forceDownForTest(startup, 0, (entity, sound) -> startupSounds.add(sound));
            require(PostureSystem.isDown(startup) && startupSounds.isEmpty(),
                    "initial down transition before live-level registration is physically applied without startup audio");
            require(helper.getLevel().addFreshEntity(startup), "initialized down body joins level");
            require(startup.isAddedToLevel() && helper.getLevel().getEntity(startup.getUUID()) == startup
                            && PostureSystem.isDown(startup) && startupSounds.isEmpty(),
                    "join registration does not replay startup down audio");
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "restored entity created");
            restored.setData(ModDataAttachments.POSTURE.get(), new PostureAttachment(
                    new PostureData(true, false, 0, 0)));
            restored.setNoAi(true); restored.setNoGravity(true); restored.setPos(body.getX() + 4, body.getY(), body.getZ());
            require(helper.getLevel().addFreshEntity(restored)
                            && CharacterIdentitySystem.enroll(restored, helper.getLevel(), ModCharacters.HUMAN_ID),
                    "already-down persisted body joined and enrolled");
            var restoredSounds = new ArrayList<net.minecraft.resources.ResourceLocation>();
            PostureSystem.applyMobStateChange(restored,
                    com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD,
                    (entity, sound) -> restoredSounds.add(sound));
            require(PostureSystem.isDown(restored) && restoredSounds.isEmpty(),
                    "restored down posture remains quiet through join and later medical state update");
            net.minecraft.world.entity.animal.Sheep custom = EntityType.SHEEP.create(helper.getLevel());
            require(custom != null, "custom-prototype entity created");
            require(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(
                    net.minecraft.resources.ResourceLocation.parse("moonstation14:ss14/items/drink")).isPresent(),
                    "custom override sound event registered");
            custom.setNoAi(true); custom.setNoGravity(true); custom.setPos(body.getX() + 6, body.getY(), body.getZ());
            require(CharacterIdentitySystem.enroll(custom, helper.getLevel(),
                            net.minecraft.resources.ResourceLocation.parse("moonstation14:posture_fall_audio_fixture"))
                            && helper.getLevel().addFreshEntity(custom),
                    "entity enrolled through audited GameTest-only inherited sound override");
            helper.runAfterDelay(2, () -> {
            var customSounds = new ArrayList<net.minecraft.resources.ResourceLocation>();
            PostureSystem.forceDownForTest(custom, 0, (entity, sound) -> customSounds.add(sound));
            require(customSounds.equals(java.util.List.of(net.minecraft.resources.ResourceLocation.parse("moonstation14:ss14/items/drink"))),
                    "live resolved inherited prototype override, not hardcoded default, selects emitted sound ID");
            helper.succeed();
        });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void onlyAcceptedOwnCrawlMovementUpdatesProneFacing(GameTestHelper helper) {
        Villager body = human(helper);
        require(PostureSystem.voluntaryToggle(body), "body enters voluntary down posture");
        PostureSystem.onOwnCrawlInput(body, true);
        PostureSystem.onOwnMovement(body, new Vec3(1, 0, 0));
        float crawlFacing = body.getData(ModDataAttachments.POSTURE.get()).data().movementFacingYaw();
        require(Math.abs(net.minecraft.util.Mth.wrapDegrees(crawlFacing + 90)) < .01f,
                "owner-authored crawl displacement updates facing");
        PostureSystem.onOwnCrawlInput(body, false);
        PostureSystem.onOwnMovement(body, new Vec3(0, 0, 1));
        require(body.getData(ModDataAttachments.POSTURE.get()).data().movementFacingYaw() == crawlFacing,
                "external or residual movement does not turn a stationary downed body");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    public static void voluntaryGetUpHonorsGraceAndCompletesTimer(GameTestHelper helper) {
        Villager body = human(helper);
        require(PostureSystem.voluntaryToggle(body) && PostureSystem.isDown(body), "voluntary toggle moves standing body down");
        require(!PostureSystem.voluntaryToggle(body) && PostureSystem.isDown(body), "early stand request is rejected during voluntary grace");
        helper.runAfterDelay(10, () -> {
            require(PostureSystem.voluntaryToggle(body), "stand request is accepted after grace expires");
            require(PostureSystem.isDown(body), "body remains down while get-up timer runs");
            helper.runAfterDelay(19, () -> {
                require(PostureSystem.isDown(body), "body remains down before the one-second deadline");
                helper.runAfterDelay(2, () -> {
                    require(!PostureSystem.isDown(body), "body stands after get-up timer completes");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    public static void criticalAndBlockedBodyCannotCompleteGetUp(GameTestHelper helper) {
        Villager body = human(helper);
        PostureSystem.forceDown(body, helper.getLevel().getServer().overworld().getGameTime());
        requireDamage(body, 100f);
        require(MobStateSystem.isCritical(body), "100 typed damage enters Critical");
        require(!PostureSystem.requestStand(body, null), "Critical body cannot begin get-up");
        requireDamage(body, -99f);
        require(MobStateSystem.isAlive(body) && PostureSystem.isDown(body), "healing below Critical threshold does not stand the body");
        BlockPos above = BlockPos.containing(body.position()).above();
        helper.getLevel().setBlock(above, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.runAfterDelay(11, () -> {
            require(!PostureSystem.requestStand(body, null), "standing-sized collision denies request after grace expires");
            helper.getLevel().setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            require(PostureSystem.requestStand(body, null), "request is accepted after clearing standing-space obstruction");
            helper.runAfterDelay(22, () -> {
                require(!PostureSystem.isDown(body), "get-up completes once standing space is clear");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void damageThresholdCancelsOnlyAtFive(GameTestHelper helper) {
        Villager body = human(helper);
        PostureSystem.forceDown(body, helper.getLevel().getServer().overworld().getGameTime());
        require(PostureSystem.requestStand(body, null), "get-up operation starts");
        requirePhysicalDamage(body, 4f);
        require(activeGetUp(body), "four typed damage remains below cancellation threshold");
        requirePhysicalDamage(body, 5f);
        require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .anyMatch(op -> op.kind().equals("standing") && op.phase() == TimedOperation.Phase.CANCELLED),
                "five typed damage cancels get-up");
        require(PostureSystem.isDown(body), "cancelled operation leaves body down");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void damageDuringVoluntaryGetUpRefreshesGraceWithoutEnablingAutoStand(GameTestHelper helper) {
        Villager body = human(helper);
        require(PostureSystem.voluntaryToggle(body), "body voluntarily goes down");
        helper.runAfterDelay(11, () -> {
            require(PostureSystem.requestStand(body, null), "get-up begins after voluntary grace");
            requirePhysicalDamage(body, 5f);
            long refreshedUntil = helper.getLevel().getServer().overworld().getGameTime() + 10;
            PostureData refreshed = body.getData(ModDataAttachments.POSTURE.get()).data();
            require(refreshed.down() && !refreshed.voluntaryAutoStand(), "damage preserves voluntary down policy");
            require(refreshed.knockdownUntil() == refreshedUntil, "damage refreshes deadline by crawler duration");
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                    .anyMatch(op -> op.kind().equals("standing") && op.phase() == TimedOperation.Phase.CANCELLED),
                    "five damage cancels get-up independently of crawler refresh threshold");
            helper.runAfterDelay(9, () -> {
                require(PostureSystem.isDown(body), "body remains down before refreshed deadline");
                helper.runAfterDelay(2, () -> {
                    require(PostureSystem.isDown(body), "voluntary posture does not auto-stand after refreshed grace");
                    require(!body.getData(ModDataAttachments.POSTURE.get()).data().voluntaryAutoStand(),
                            "damage did not enable automatic standing");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 45)
    public static void quietChemicalDamageDoesNotCancelGetUpOrRefreshGrace(GameTestHelper helper) {
        Villager body = human(helper);
        require(PostureSystem.voluntaryToggle(body), "body voluntarily goes down");
        helper.runAfterDelay(11, () -> {
            require(PostureSystem.requestStand(body, null), "get-up begins after voluntary grace");
            long originalDeadline = body.getData(ModDataAttachments.POSTURE.get()).data().knockdownUntil();
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 5f), 1f, true,
                    DamageSystem.Cause.CHEMICAL) == DamageSystem.Result.APPLIED, "quiet chemical typed damage is applied");
            require(activeGetUp(body), "quiet chemical damage does not cancel a physical-interruption action");
            PostureData current = body.getData(ModDataAttachments.POSTURE.get()).data();
            require(current.knockdownUntil() == originalDeadline,
                    "quiet chemical damage does not refresh physical knockdown grace");
            require(!current.voluntaryAutoStand(), "quiet chemical damage preserves voluntary stand policy");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void downBodyUsesLowCollisionBoxAndCannotGrowUnderCeiling(GameTestHelper helper) {
        Villager body = human(helper);
        var standing = body.getDimensions(Pose.STANDING);
        require(PostureSystem.voluntaryToggle(body), "body voluntarily goes down");
        require(body.getDimensions(Pose.STANDING).height() == standing.height(), "standing dimensions remain canonical");
        require(Math.abs(body.getBoundingBox().getYsize() - .6) < .001, "live collision box has prone height");
        require(body.getPose() == Pose.SWIMMING && Math.abs(body.getEyeHeight() - .4f) < .01,
                "native body pose and eye height reflect prone posture");
        var level = helper.getLevel();
        BlockPos feet = BlockPos.containing(body.position());
        level.setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);
        Vec3 before = body.position();
        body.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(.4, 0, 0));
        require(body.getX() > before.x, "prone body traverses horizontally beneath one-block ceiling");
        require(!PostureSystem.requestStand(body, null), "standing attempt is blocked by ceiling");
        require(PostureSystem.isDown(body) && body.getBoundingBox().getYsize() < standing.height(),
                "blocked growth leaves body prone");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"merge-main-regression"})
    public static void controlledActorCanTurnRetainedDeadBodyWithoutChangingMedicalState(GameTestHelper helper) {
        Villager actor = human(helper);
        Villager target = humanAt(helper, 4.5);
        PostureSystem.forceDown(target, helper.getLevel().getServer().overworld().getGameTime());
        requireDamage(target, 200f);
        require(MobStateSystem.isDead(target) && PostureSystem.isDown(target), "target is a retained downed DEAD character");
        require(target.getPose() == Pose.SWIMMING && Math.abs(target.getBoundingBox().getYsize() - .6) < .001,
                "native death transition preserves prone corpse pose and dimensions");
        PostureData before = target.getData(ModDataAttachments.POSTURE.get()).data();
        require(PostureSystem.turnOtherBody(actor, target), "conscious actor with a free hand can turn dead downed body");
        PostureData after = target.getData(ModDataAttachments.POSTURE.get()).data();
        require(after.faceUp() != before.faceUp() && after.down() == before.down()
                        && after.knockdownUntil() == before.knockdownUntil(),
                "turn changes facing only");
        require(MobStateSystem.isDead(target), "turn preserves DEAD medical state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"merge-main-regression"})
    public static void controlledActorCanTurnCriticalAndVoluntaryDownBodies(GameTestHelper helper) {
        Villager actor = human(helper);
        Villager critical = humanAt(helper, 4.5);
        PostureSystem.forceDown(critical, helper.getLevel().getServer().overworld().getGameTime());
        requireDamage(critical, 100f);
        boolean criticalFaceBeforeTurn = critical.getData(ModDataAttachments.POSTURE.get()).data().faceUp();
        require(MobStateSystem.isCritical(critical) && PostureSystem.turnOtherBody(actor, critical),
                "downed Critical target can be turned");
        require(MobStateSystem.isCritical(critical)
                        && critical.getData(ModDataAttachments.POSTURE.get()).data().faceUp() != criticalFaceBeforeTurn,
                "Critical state survives facing toggle");
        Villager voluntary = humanAt(helper, 6.5);
        require(PostureSystem.voluntaryToggle(voluntary), "second target voluntarily goes down");
        boolean voluntaryFaceBeforeTurn = voluntary.getData(ModDataAttachments.POSTURE.get()).data().faceUp();
        require(PostureSystem.turnOtherBody(actor, voluntary), "voluntarily down target can be turned");
        require(voluntary.getData(ModDataAttachments.POSTURE.get()).data().faceUp() != voluntaryFaceBeforeTurn,
                "voluntary target facing toggled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"merge-main-regression", "timed-interaction"})
    public static void repeatedTimedCompletionRunsOnceAndKeepsWorldTarget(GameTestHelper helper) {
        Villager body = human(helper);
        BlockPos target = helper.absolutePos(new BlockPos(2, 0, 2));
        AtomicInteger completions = new AtomicInteger();
        require(TimedInteractionSystem.start(body, null, 71, "timed-regression", "repeat-once", 2,
                        false, false, 1.0, 0.3, () -> 4, () -> true,
                        () -> completions.incrementAndGet() == 1
                                ? TimedInteractionSystem.Completion.REPEAT : TimedInteractionSystem.Completion.STOP,
                        java.util.Optional.of(target), () -> { }) == TimedInteractionEngine.StartResult.STARTED,
                "timed operation starts with dynamic repeat schedule and world target");
        require(TimedInteractionSystem.snapshot(body).views().getFirst().worldTarget().orElseThrow().equals(target),
                "authoritative snapshot retains operation world target");
        helper.runAfterDelay(3, () -> {
            var views = TimedInteractionSystem.snapshot(body).views();
            require(completions.get() == 1, "first completion callback runs once");
            require(views.size() == 1 && views.getFirst().phase() == TimedOperation.Phase.ACTIVE
                            && views.getFirst().durationTicks() == 4
                            && views.getFirst().worldTarget().orElseThrow().equals(target),
                    "repeat uses dynamic duration and retains world target in updated active snapshot");
            helper.runAfterDelay(5, () -> {
                var finished = TimedInteractionSystem.snapshot(body).views();
                require(completions.get() == 2, "second completion callback runs once, not twice");
                require(finished.size() == 1 && finished.getFirst().phase() == TimedOperation.Phase.COMPLETED,
                        "STOP outcome completes operation rather than being reprocessed as cancellation");
                helper.succeed();
            });
        });
    }

    private static boolean activeGetUp(Villager body) {
        return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .anyMatch(op -> op.kind().equals("standing") && op.phase() == TimedOperation.Phase.ACTIVE);
    }

    private static Villager human(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "human profile entity created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body)
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID),
                "entity enrolled through supported human character profile");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "enrolled body and hands initialize");
        return body;
    }

    private static Villager villagerAt(GameTestHelper helper, double x) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "villager created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(x, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body)
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID),
                "villager enrolled as Human");
        return body;
    }

    private static Villager humanAt(GameTestHelper helper, double x) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "human profile entity created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + x, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body)
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID)
                        && LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body),
                "target enrolled and hand capability initialized");
        body.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        return body;
    }

    private static void requireDamage(Villager body, float typed) {
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, typed), 1f, true)
                        == DamageSystem.Result.APPLIED, "typed damage/healing applied: " + typed);
    }

    private static void requirePhysicalDamage(Villager body, float typed) {
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, typed), 1f, true,
                        DamageSystem.Cause.COMBAT) == DamageSystem.Result.APPLIED,
                "physical typed damage applied: " + typed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
