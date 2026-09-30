package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GhostDamageImmunityGameTests {
    private GhostDamageImmunityGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void observerRejectsDirectAndEnvironmentalDamageButAllowsExplicitRemoval(GameTestHelper helper) {
        GhostMobHarnessEntity ghost = helper.spawn(GhostMobHarnessRegistration.getEntityType(), new BlockPos(1, 1, 1));
        var sources = helper.getLevel().damageSources();
        float initialHealth = ghost.getHealth();
        require(initialHealth == 1f, "ghost starts with one health point");
        require(!ghost.getType().canSerialize(), "transient ghost type remains non-serializable");

        DamageSource[] attacks = {sources.generic(), sources.drown(), sources.inWall(),
                sources.onFire(), sources.magic(), sources.fellOutOfWorld(), sources.genericKill()};
        for (DamageSource attack : attacks) {
            require(!ghost.hurt(attack, 10f), "ghost rejected " + attack.getMsgId());
            require(ghost.getHealth() == initialHealth && !ghost.isDeadOrDying() && ghost.isAlive(),
                    "ghost remains alive and undamaged after " + attack.getMsgId());
        }

        helper.runAfterDelay(2, () -> {
            require(ghost.getHealth() == initialHealth && !ghost.isDeadOrDying() && !ghost.isRemoved(),
                    "ghost survives server ticks after damage attempts");
            ghost.discard();
            require(ghost.isRemoved(), "explicit lifecycle-style discard still removes ghost");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryBodyRemainsDamageable(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        float initialHealth = body.getHealth();
        require(body.hurt(helper.getLevel().damageSources().generic(), 2f), "ordinary body accepts damage");
        require(body.getHealth() < initialHealth, "ordinary body loses health");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
