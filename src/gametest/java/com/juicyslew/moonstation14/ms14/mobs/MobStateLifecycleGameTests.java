package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;

import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "critical", "death"})
public final class MobStateLifecycleGameTests {
    private MobStateLifecycleGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void criticalHealingReturnsAlive(GameTestHelper helper) {
        Villager body = human(helper);
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        requireDamage(body, 100f);
        require(MobStateSystem.isCritical(body), "100 typed damage enters Critical");
        require(body.getMaxHealth() == 40f, "critical projection retains native maximum health");
        helper.runAfterDelay(1, () -> {
            requireDamage(body, -2f);
            require(MobStateSystem.isAlive(body), "healing below critical threshold returns Alive");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void explicitReviveResetsNativeDeath(GameTestHelper helper) {
        Villager body = human(helper);
        UUID id = body.getUUID();
        requireDamage(body, 200f);
        require(MobStateSystem.isDead(body) && body.isDeadOrDying(), "200 typed damage triggers native death");
        requireDamage(body, -100f);
        require(MobStateSystem.isDead(body) && body.isDeadOrDying(), "healing alone must not revive");
        helper.runAfterDelay(1, () -> {
            require(MobStateSystem.explicitRevive(body), "explicit revive succeeds after healing");
            require(body.getUUID().equals(id), "revive retains native UUID");
            require(MobStateSystem.isCritical(body) && body.getHealth() == 20f,
                    "revive restores critical state at native health 20");
            require(!body.isDeadOrDying(), "revive resets native death flags");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void operatorRecoveryClearsTypedDamageAndRevivesSameBody(GameTestHelper helper) {
        Villager body = human(helper);
        UUID id = body.getUUID();
        requireDamage(body, 200f);
        require(MobStateSystem.isDead(body), "200 typed damage enters Dead");
        require(DamageSystem.clearTypedDamageForRecovery(body), "trusted recovery clears typed ledger");
        require(MobStateSystem.isDead(body), "clearing damage alone does not weaken Dead gate");
        require(MobStateSystem.explicitRevive(body), "explicit revive succeeds only after ledger clear");
        require(body.getUUID().equals(id) && helper.getLevel().getEntity(id) == body,
                "recovery retains exact body UUID and world registration");
        require(MobStateSystem.isAlive(body) && body.getHealth() == body.getMaxHealth(),
                "zero typed damage revives to Alive at projected full health");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void deathRetainsCorpse(GameTestHelper helper) {
        Villager body = human(helper);
        UUID id = body.getUUID();
        requireDamage(body, 200f);
        require(MobStateSystem.isDead(body), "typed lethal damage marks body dead");
        helper.runAfterDelay(30, () -> {
            require(!body.isRemoved(), "dead body remains as a corpse after 30 ticks");
            require(!body.isAlive() && helper.getLevel().getEntity(id) == body,
                    "dead body remains registered in world");
            require(body.getUUID().equals(id), "corpse retains native UUID");
            requireDamage(body, -100f);
            require(MobStateSystem.isDead(body), "healing alone must not revive dead body");
            require(MobStateSystem.explicitRevive(body), "explicit revive succeeds after late healing");
            require(MobStateSystem.isCritical(body) && body.getHealth() == 20f,
                    "revive restores Critical at native health 20");
            helper.runAfterDelay(5, () -> {
                require(body.getUUID().equals(id) && helper.getLevel().getEntity(id) == body,
                        "revived body remains the same world-registered entity after 5 ticks");
                require(body.isAlive() && MobStateSystem.isCritical(body),
                        "revived body remains alive and Critical after 5 ticks");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void pigMedicalDeathSuppressesNativeLootButRetainsBody(GameTestHelper helper) {
        require(helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),
                "fixture requires mob loot enabled to exercise native death-loot cancellation");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Pig body = EntityType.PIG.create(helper.getLevel());
        require(body != null, "Pig entity created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body)
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "moonstation14", "pig")),
                "Pig enrolled through resolved resource-pig profile");
        UUID id = body.getUUID();
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        requireDamage(body, 200f);
        require(body.isDeadOrDying() && MobStateSystem.isDead(body),
                "typed lethal damage reaches native death and projected Dead state");
        require(body.getUUID().equals(id) && !body.isRemoved()
                        && helper.getLevel().getEntity(id) == body,
                "medical death retains the same world-registered native body");
        var nearby = body.getBoundingBox().inflate(2d);
        require(helper.getLevel().getEntitiesOfClass(ItemEntity.class, nearby).stream()
                        .noneMatch(item -> item.getItem().is(Items.PORKCHOP)),
                "medical death suppresses native porkchop loot");
        require(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, nearby).isEmpty(),
                "medical death suppresses native experience loot");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void removedCorpseRejectsExplicitRevive(GameTestHelper helper) {
        Villager body = human(helper);
        requireDamage(body, 200f);
        body.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        require(body.isRemoved(), "fixture body is removed");
        require(!MobStateSystem.explicitRevive(body), "explicit revive rejects removed body");
        require(body.isRemoved(), "explicit revive does not restore removed body");
            helper.succeed();
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
                "entity enrolled through the supported human character profile");
        require(BodySystem.reconcile(body), "enrolled body physiology reconciles");
        return body;
    }

    private static void requireDamage(LivingEntity body, float typed) {
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, typed), 1f, true)
                        == DamageSystem.Result.APPLIED,
                "typed blunt damage/healing applied: " + typed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
