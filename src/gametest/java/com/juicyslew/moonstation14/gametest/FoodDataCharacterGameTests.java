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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoodDataCharacterGameTests {
    private FoodDataCharacterGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void eligibleCharacterSkipsVanillaFoodTickButExplicitHealingWorks(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        require(level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),
                "test relies on the default enabled natural-regeneration gamerule; do not mutate it");
        Player player = new Player(level, helper.absolutePos(new BlockPos(1, 1, 1)), 0f,
                new GameProfile(UUID.randomUUID(), "food-data-character-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(player, level);
        require(HungerSystem.isEligible(player), "bound player test body must carry Hunger");
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20f);
        player.getFoodData().setExhaustion(0f);
        player.setHealth(player.getMaxHealth() - 5f);
        player.setData(ModDataAttachments.DAMAGE.get(),
                new DamageData(new DamageMap(Map.of(DamageKeys.BLUNT, 25f))));

        float initialHealth = player.getHealth();
        float initialFood = player.getFoodData().getFoodLevel();
        float initialSaturation = player.getFoodData().getSaturationLevel();
        float initialExhaustion = player.getFoodData().getExhaustionLevel();
        var initialLedger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();
        for (int i = 0; i < 200; i++) player.getFoodData().tick(player);

        require(player.getHealth() == initialHealth, "vanilla food tick must not naturally regenerate character health");
        require(player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().equals(initialLedger),
                "suppressed food tick must not silently change typed damage");
        require(player.getFoodData().getFoodLevel() == initialFood
                        && player.getFoodData().getSaturationLevel() == initialSaturation
                        && player.getFoodData().getExhaustionLevel() == initialExhaustion,
                "vanilla food tick must not drain food, saturation, or exhaustion for character bodies");

        player.heal(1f);
        require(player.getHealth() == initialHealth + 1f, "explicit vanilla heal call remains active");
        var healedLedger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(healedLedger != null
                        && Math.abs(healedLedger.getMap().getOrDefault(DamageKeys.BLUNT, 0f) - 20f) < .001f,
                "explicit healing must continue reducing the typed damage ledger");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
