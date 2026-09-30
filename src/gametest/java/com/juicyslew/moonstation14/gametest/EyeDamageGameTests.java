package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageAttachment;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EyeDamageGameTests {
    private EyeDamageGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void thresholdHealingAndCleanupAreAuthoritative(GameTestHelper helper) {
        Villager character = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();

        require(apply(effects, helper, character, 1, 1f) == EffectResult.APPLIED,
                "eye damage must apply");
        require(state(character) != null && state(character).damage() == 1,
                "one eye damage must create one damage");
        for (int i = 0; i < 8; i++) {
            require(apply(effects, helper, character, 1, 1f) == EffectResult.APPLIED,
                    "damage step must apply");
        }
        require(state(character).isBlind() && EyeDamageSystem.isBlind(character),
                "nine eye damage must be blind");

        EyeDamageAttachment unchanged = state(character);
        require(apply(effects, helper, character, 1, 1f) == EffectResult.APPLIED,
                "clamped damage must remain applied");
        require(state(character) == unchanged, "clamped damage must not replace attachment");
        require(apply(effects, helper, character, -1, .5f) == EffectResult.APPLIED,
                "negative half damage must floor to -1");
        require(state(character).damage() == 8 && !state(character).isBlind(),
                "healing below nine must reverse eye-derived blindness");
        require(apply(effects, helper, character, -99, 1f) == EffectResult.APPLIED,
                "healing to zero must apply");
        require(state(character) == null, "zero eye damage must remove attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void noOpAndUnsupportedTargetsDoNotMaterialize(GameTestHelper helper) {
        EffectSystem effects = EffectSystem.withDefaults();
        Villager absent = spawn(helper, 1);
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(3, 1, 1));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 1, 1));

        require(apply(effects, helper, absent, 1, 0f) == EffectResult.APPLIED,
                "zero scale must be an applied no-op");
        require(state(absent) == null, "supported no-op must not materialize state");
        require(apply(effects, helper, absent, -1, 1f) == EffectResult.APPLIED,
                "default amount must remain a healing operation");
        require(state(absent) == null, "healing an absent state must not materialize state");
        require(apply(effects, helper, arrow, 1, 1f) == EffectResult.SKIPPED_UNSUPPORTED,
                "nonliving eye target must be unsupported");
        require(!arrow.hasData(ModDataAttachments.EYE_DAMAGE.get()),
                "unsupported target must not materialize state");
        require(apply(effects, helper, stand, 1, 1f) == EffectResult.SKIPPED_UNSUPPORTED,
                "unbound ArmorStand must not gain eye damage");
        require(!stand.hasData(ModDataAttachments.EYE_DAMAGE.get()), "unbound target remains unmaterialized");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mismatchedHostPreservesRawEyeStateButDoesNotProjectBlindness(GameTestHelper helper) {
        Villager host = spawn(helper, 1);
        var saved = new EyeDamageAttachment(new com.juicyslew.moonstation14.ms14.eye.EyeDamageComponent(9));
        host.setData(ModDataAttachments.EYE_DAMAGE.get(), saved);
        require(EyeDamageSystem.isBlind(host), "eligible persisted damage projects blindness");
        var wrong = new CharacterIdentityAttachment();
        wrong.bind(net.minecraft.resources.ResourceLocation.parse("moonstation14:pig"));
        host.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), wrong);
        require(apply(EffectSystem.withDefaults(), helper, host, 1, 1f) == EffectResult.SKIPPED_UNSUPPORTED,
                "mismatched host must deny mutations");
        require(state(host) == saved && EyeDamageSystem.damage(host) == 9 && !EyeDamageSystem.isBlind(host),
                "raw diagnostic state survives while blindness remains inert");
        helper.succeed();
    }

    private static Villager spawn(GameTestHelper helper, int x) {
        Villager stand = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 1, 1));
        stand.setNoAi(true);
        CharacterIdentitySystem.enroll(stand, helper.getLevel(), ModCharacters.HUMAN_ID);
        return stand;
    }

    private static EffectResult apply(EffectSystem effects, GameTestHelper helper,
                                      Entity target, int amount, float scale) {
        return effects.apply(new EffectData.EyeDamage(EffectCommonData.DEFAULT, amount),
                new EffectContext(helper.getLevel(), target, scale, RandomSource.create(7L),
                        ConditionContext.unavailable(), EffectCause.MANUAL));
    }

    private static EyeDamageAttachment state(Villager character) {
        return character.getExistingDataOrNull(ModDataAttachments.EYE_DAMAGE.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
