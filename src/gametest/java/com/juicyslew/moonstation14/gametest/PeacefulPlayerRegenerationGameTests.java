package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PeacefulPlayerRegenerationGameTests {
    private PeacefulPlayerRegenerationGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void eligibleCharacterSkipsPeacefulNutritionButExplicitHealingWorks(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty originalDifficulty = server.getWorldData().getDifficulty();
        boolean originalNaturalRegeneration = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        Player player = new Player(level, helper.absolutePos(new BlockPos(1, 1, 1)), 0f,
                new GameProfile(UUID.randomUUID(), "peaceful-regeneration-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(player, level);
        require(HungerSystem.isEligible(player), "bound player test body must carry Hunger");
        player.setHealth(player.getMaxHealth() - 5f);
        player.setData(ModDataAttachments.DAMAGE.get(),
                new DamageData(new DamageMap(Map.of(DamageKeys.BLUNT, 25f))));
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0f);
        var initialLedger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();

        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, server);
            // tickCount begins at zero, so vanilla's Peaceful 20-tick health/saturation
            // branch and 10-tick food branch would all run on this invocation.
            player.aiStep();

            require(player.getHealth() == player.getMaxHealth() - 5f,
                    "Peaceful Player.aiStep must not naturally heal eligible character bodies");
            require(player.getFoodData().getFoodLevel() == 10
                            && player.getFoodData().getSaturationLevel() == 0f,
                    "Peaceful Player.aiStep must not refill character food or saturation");
            require(player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().equals(initialLedger),
                    "suppressed Peaceful regeneration must leave typed damage unchanged");

            float beforeExplicitHeal = player.getHealth();
            player.heal(1f);
            var healedLedger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(player.getHealth() == beforeExplicitHeal + 1f,
                    "explicit medicine-style healing remains active in Peaceful difficulty");
            require(healedLedger != null
                            && Math.abs(healedLedger.getMap().getOrDefault(DamageKeys.BLUNT, 0f) - 20f) < .001f,
                    "explicit healing must continue reducing typed damage");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(originalNaturalRegeneration, server);
            server.setDifficulty(originalDifficulty, true);
        }
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
