package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.effect.EmoteEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmoteGameTests {
    private EmoteGameTests() { }

    @GameTest(template="empty", timeoutTicks=20)
    public static void livingDeliveryAndNonlivingAdmission(GameTestHelper helper) {
        ArmorStand living = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(3, 1, 1));
        int[] sounds = {0};
        int[] chats = {0};
        EmoteEffect.EmoteDelivery seam = new EmoteEffect.EmoteDelivery() {
            @Override public void sound(net.minecraft.server.level.ServerLevel level, Entity source, String soundId) {
                if (source != living || !soundId.equals("cough")) throw new GameTestAssertException("wrong sound/source");
                sounds[0]++;
            }
            @Override public void chat(net.minecraft.server.level.ServerLevel level, Entity source,
                                       net.minecraft.network.chat.Component line) { chats[0]++; }
        };
        require(EmoteEffect.apply("cough", true, living, helper.getLevel(), seam) == EffectResult.APPLIED,
                "living emote applies");
        require(sounds[0] == 1 && chats[0] == 1, "one sound and one requested chat delivery");
        require(EmoteEffect.apply("cough", false, living, helper.getLevel(), seam) == EffectResult.APPLIED,
                "living no-chat emote applies");
        require(sounds[0] == 2 && chats[0] == 1, "no-chat omits chat and still sends one sound");
        require(EmoteEffect.apply("cough", true, arrow, helper.getLevel(), seam)
                == EffectResult.SKIPPED_UNSUPPORTED, "nonliving target is skipped");
        require(sounds[0] == 2 && chats[0] == 1, "unsupported emits nothing");
        require(EmoteEffect.apply("not-registered", true, living, helper.getLevel(), seam)
                == EffectResult.SKIPPED_UNSUPPORTED, "unknown id is controlled");
        require(sounds[0] == 2 && chats[0] == 1, "unknown id emits nothing");
        var line = EmoteEffect.plan("cough", true, living).chat();
        require(line.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("moonstation14.emote.cough"), "localized per-emote key");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20)
    public static void effectSystemAdmitsLivingAndRejectsForcedNonliving(GameTestHelper helper) {
        ArmorStand living = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(3, 1, 1));
        EffectSystem system = EffectSystem.withDefaults();
        EffectData.Emote ordinary = new EffectData.Emote(EffectCommonData.DEFAULT,
                "cough", false, false, false);
        EffectData.Emote forced = new EffectData.Emote(EffectCommonData.DEFAULT,
                "cough", false, false, true);
        require(system.apply(ordinary, context(helper, living, 0.25f)) == EffectResult.APPLIED,
                "living target applies at low scale");
        require(system.apply(ordinary, context(helper, living, 3f)) == EffectResult.APPLIED,
                "living target applies at high scale");
        require(system.apply(forced, context(helper, arrow, 1f)) == EffectResult.SKIPPED_UNSUPPORTED,
                "force cannot admit nonliving target");
        helper.succeed();
    }

    private static EffectContext context(GameTestHelper helper, Entity entity, float scale) {
        return new EffectContext(helper.getLevel(), entity, scale, RandomSource.create(1),
                ConditionContext.unavailable(), EffectCause.MANUAL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
