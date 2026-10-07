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

@PrefixGameTestTemplate(false)
@GameTestTags({"posture", "collision", "status", "timed-interaction"})
public final class PostureGameTests {
    private PostureGameTests() { }

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
    public static void controlledActorCanTurnRetainedDeadBodyWithoutChangingMedicalState(GameTestHelper helper) {
        Villager actor = human(helper);
        Villager target = humanAt(helper, 4.5);
        PostureSystem.forceDown(target, helper.getLevel().getServer().overworld().getGameTime());
        requireDamage(target, 200f);
        require(MobStateSystem.isDead(target) && PostureSystem.isDown(target), "target is a retained downed DEAD character");
        PostureData before = target.getData(ModDataAttachments.POSTURE.get()).data();
        require(PostureSystem.turnOtherBody(actor, target), "conscious actor with a free hand can turn dead downed body");
        PostureData after = target.getData(ModDataAttachments.POSTURE.get()).data();
        require(!after.faceUp() && after.down() && after.knockdownUntil() == before.knockdownUntil(),
                "turn changes facing only");
        require(MobStateSystem.isDead(target), "turn preserves DEAD medical state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void controlledActorCanTurnCriticalAndVoluntaryDownBodies(GameTestHelper helper) {
        Villager actor = human(helper);
        Villager critical = humanAt(helper, 4.5);
        PostureSystem.forceDown(critical, helper.getLevel().getServer().overworld().getGameTime());
        requireDamage(critical, 100f);
        require(MobStateSystem.isCritical(critical) && PostureSystem.turnOtherBody(actor, critical),
                "downed Critical target can be turned");
        require(MobStateSystem.isCritical(critical) && !critical.getData(ModDataAttachments.POSTURE.get()).data().faceUp(),
                "Critical state survives turn");
        Villager voluntary = humanAt(helper, 6.5);
        require(PostureSystem.voluntaryToggle(voluntary), "second target voluntarily goes down");
        boolean voluntaryFaceBeforeTurn = voluntary.getData(ModDataAttachments.POSTURE.get()).data().faceUp();
        require(PostureSystem.turnOtherBody(actor, voluntary), "voluntarily down target can be turned");
        require(voluntary.getData(ModDataAttachments.POSTURE.get()).data().faceUp() != voluntaryFaceBeforeTurn,
                "voluntary target facing toggled");
        helper.succeed();
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
