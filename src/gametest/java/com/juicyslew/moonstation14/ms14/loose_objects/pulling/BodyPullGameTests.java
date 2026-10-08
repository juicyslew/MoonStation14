package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"pulling", "medical-state", "hands"})
public final class BodyPullGameTests {
    private BodyPullGameTests() { }

    @GameTestTags("suite-stabilization")
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void pullsDeadHumanTargetAndReleasesWhenSourceBecomesCritical(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager source = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        LiveHands sourceHands = LiveHands.initialize(source).orElseThrow();
        LiveHands targetHands = LiveHands.initialize(target).orElseThrow();
        require(DamageSystem.applyHealthChange(target, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(target), "target enters native DEAD state");
        PullFixtureAim.aimAtVisibleInteractionPoint(source, target);
        var targetId = target.getUUID();
        require(BodyPullService.toggleFixture(source, targetId, "left", sourceHands.revision(), () -> true)
                == BodyPullService.Result.SUCCESS, "dead human target can be pulled");
        require(BodyPullService.toggleFixture(target, source.getUUID(), "left", targetHands.revision(), () -> true)
                != BodyPullService.Result.SUCCESS, "another body cannot reserve the already-pulled target");
        require(source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).reservation("left")
                .equals(java.util.Optional.of(targetId)), "pull reserves source hand");
        require(source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "successful pull publishes pulling posture flag");

        source.setPos(source.getX() - 1.5, source.getY(), source.getZ());
        for (int i = 0; i < 5; i++) BodyPullService.onPostServerTick(null);
        require(level.getEntity(targetId) == target && !target.isRemoved() && target.getUUID().equals(targetId)
                && BodyPullService.isPulling(source), "dead target remains leased without identity replacement");
        var endpoint = source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        require(endpoint != null && endpoint.constraints().size() == 1
                        && endpoint.constraints().get(0).other().equals(targetId),
                "pull publishes endpoint state for the native spring consumer");
        require(MobStateSystem.isDead(target), "pull retains DEAD medical state");

        require(DamageSystem.applyHealthChange(source, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED, "source receives lethal medical damage");
        require(!BodyPullService.isPulling(source)
                && source.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).reservation("left").isEmpty(),
                "medical incapacitation stops pull and releases its hand");
        require(!source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "stopped pull clears pulling posture flag");
        helper.succeed();
    }

    @GameTestTags("suite-stabilization")
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void tetherRetainsLeaseAtElasticExtensionAndClearsOnReservationLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager source = human(level, origin.getX() + 2.5, origin.getY(), origin.getZ() + .5);
        Villager target = human(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        source.setYRot(90);
        source.setYHeadRot(90);
        PullFixtureAim.aimAtVisibleInteractionPoint(source, target);
        LiveHands hands = LiveHands.initialize(source).orElseThrow();
        var targetId = target.getUUID();
        require(BodyPullService.toggleFixture(source, targetId, "left", hands.revision(), () -> true)
                        == BodyPullService.Result.SUCCESS,
                "pull starts with a live target and free hand");
        double targetBeforeFollow = target.getX();
        source.setPos(source.getX() + 1.5, source.getY(), source.getZ());
        BodyPullService.onPostServerTick(null);
        require(BodyPullService.isPulling(source), "finite elastic extension does not detach the lease");
        var endpoint = source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        require(endpoint != null && endpoint.constraints().get(0).self().position().x() == source.getX()
                        && endpoint.constraints().get(0).otherSnapshot().position().x() == targetBeforeFollow,
                "elastic extension refreshes the synchronized spring endpoint snapshots");

        source.removeData(ModDataAttachments.LIVE_HANDS.get());
        BodyPullService.onPostServerTick(null);
        require(!BodyPullService.isPulling(source), "lost reservation releases the pull lease");
        require(source.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()) == null,
                "lost reservation removes synchronized rope state");
        require(!source.getExistingDataOrNull(ModDataAttachments.POSTURE.get()).data().pulling(),
                "lost reservation clears the pulling posture projection");
        helper.succeed();
    }

    private static Villager human(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "human-profile villager created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(x, y, z);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID)
                && BodySystem.reconcile(body), "villager enrolled as human body");
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        return body;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
