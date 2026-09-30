package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.ConditionData;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.hunger.HungerComponent;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivityAttachment;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungerGameTests {
    private HungerGameTests() { }

    @GameTest(template="empty", timeoutTicks=20)
    public static void fractionalApplyClampAndAttachment(GameTestHelper helper) {
        Player character = boundPlayer(helper);
        EffectSystem system = EffectSystem.withDefaults();
        require(HungerSystem.read(character) == 150f && !character.hasData(ModDataAttachments.HUNGER.get()), "default must not materialize");
        require(apply(system, helper, character, 0f, 1f, ConditionContext.unavailable()) == EffectResult.APPLIED,
                "zero-factor default application must be an applied no-op");
        require(!character.hasData(ModDataAttachments.HUNGER.get()),
                "no-op on absent/default hunger must not materialize attachment");
        require(apply(system, helper, character, Float.MAX_VALUE, Float.MAX_VALUE,
                ConditionContext.unavailable()) == EffectResult.FAILED,
                "nonfinite factor-scale result must fail");
        require(!character.hasData(ModDataAttachments.HUNGER.get()),
                "invalid application must not materialize attachment");
        require(apply(system, helper, character, 1.5f, 1f, ConditionContext.unavailable()) == EffectResult.APPLIED, "fractional apply");
        require(HungerSystem.read(character) == 151.5f, "fraction must be retained");
        require(character.hasData(ModDataAttachments.HUNGER.get()), "state attached; attachment registration is persistent/synced");
        var beforeInvalid = character.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        require(apply(system, helper, character, Float.MAX_VALUE, Float.MAX_VALUE,
                ConditionContext.unavailable()) == EffectResult.FAILED,
                "nonfinite factor-scale result must fail for materialized hunger");
        require(character.getExistingDataOrNull(ModDataAttachments.HUNGER.get()) == beforeInvalid
                        && HungerSystem.read(character) == 151.5f,
                "invalid application must leave existing state unchanged");
        apply(system, helper, character, 100f, 1f, ConditionContext.unavailable());
        require(HungerSystem.read(character) == 200f, "upper clamp");
        apply(system, helper, character, -500f, 1f, ConditionContext.unavailable());
        require(HungerSystem.read(character) == 0f, "lower clamp");
        ArmorStand unsupported = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(3,1,1));
        MS14Provider.update(unsupported, MS14Bridges.HUNGER, new HungerAttachment(new HungerComponent(72f)));
        require(!HungerSystem.satiate(unsupported, 5f, 1f) && HungerSystem.read(unsupported) == 72f,
                "saved unsupported hunger remains readable but cannot be satiated");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20)
    public static void authoritativeAndInjectedHungerConditionsAndUnsupportedTarget(GameTestHelper helper) {
        Player character = boundPlayer(helper);
        Entity arrow = helper.spawn(EntityType.ARROW, new BlockPos(3,1,1));
        EffectSystem system = EffectSystem.withDefaults();
        apply(system, helper, character, -50f, 1f, ConditionContext.unavailable()); // 100
        EffectData.SatiateHunger gated = new EffectData.SatiateHunger(
                new EffectCommonData(java.util.List.of(new ConditionData.HungerCondition(100f,100f,false)),1f,0f,true), 1f);
        require(system.apply(gated, context(helper, character, ConditionContext.unavailable())) == EffectResult.APPLIED, "authoritative boundary is inclusive");
        ConditionContext injected = ConditionContext.builder().hunger(25f).build();
        EffectData.SatiateHunger injectedGate = new EffectData.SatiateHunger(
                new EffectCommonData(java.util.List.of(new ConditionData.HungerCondition(25f,25f,false)),1f,0f,true), 1f);
        require(system.apply(injectedGate, context(helper, character, injected)) == EffectResult.APPLIED, "explicit hunger preserved");
        EffectData.SatiateHunger inverted = new EffectData.SatiateHunger(
                new EffectCommonData(java.util.List.of(new ConditionData.HungerCondition(25f,25f,true)),1f,0f,true), 1f);
        require(system.apply(inverted, context(helper, character, injected)) == EffectResult.SKIPPED_CONDITION, "inversion applies after inclusive match");
        require(system.apply(new EffectData.SatiateHunger(EffectCommonData.DEFAULT,1f), context(helper,arrow,ConditionContext.unavailable())) == EffectResult.SKIPPED_UNSUPPORTED, "nonliving unsupported");
        require(!arrow.hasData(ModDataAttachments.HUNGER.get()), "unsupported target remains unmaterialized");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20)
    public static void eligibleInitializationDecayAndIndependentProjection(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1,1,1));
        villager.setNoAi(true);
        require(HungerSystem.isEligible(villager) && villager.hasData(ModDataAttachments.HUNGER.get()),
                "bound villager initializes hunger at join");
        float initial = HungerSystem.read(villager);
        require(initial >= 110f && initial < 150f, "initial hunger is in [110,150)");
        require(!HungerSystem.initializeIfEligible(villager, helper.getLevel())
                        && HungerSystem.read(villager) == initial, "initialized hunger is never rerolled");
        var activity = villager.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        require(activity != null && activity.isActive(EntityActivity.HUNGER), "positive hunger schedules derived work");

        MS14Provider.update(villager, MS14Bridges.HUNGER,
                new HungerAttachment(new HungerComponent(50f)));
        MS14Provider.update(villager, MS14Bridges.THIRST,
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(150f)));
        ThirstSystem.reconcile(villager, helper.getLevel());
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "thirst slowdown is projected before hunger decay");
        require(HungerSystem.decayOneSecond(villager), "one hunger due invocation applies one second");
        require(HungerSystem.read(villager) < 50f, "starving threshold decays with upstream modifier");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HungerSystem.STARVING_MODIFIER_ID) != null,
                "starvation has independent slowdown");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "hunger projection preserves thirst slowdown");
        MS14Provider.update(villager, MS14Bridges.HUNGER,
                new HungerAttachment(new HungerComponent(150f)));
        HungerSystem.reconcile(villager, helper.getLevel());
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HungerSystem.STARVING_MODIFIER_ID) == null,
                "recovery removes only hunger slowdown");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "hunger recovery preserves thirst slowdown");
        EntityActivitySystem.reconcile(villager);
        EntityActivityAttachment flags = villager.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        require(flags != null && flags.isActive(EntityActivity.HUNGER), "positive default value remains scheduled");

        var unsupported = helper.spawn(EntityType.ZOMBIE, new BlockPos(4,1,1));
        require(!HungerSystem.isEligible((net.minecraft.world.entity.LivingEntity) unsupported)
                        && !unsupported.hasData(ModDataAttachments.HUNGER.get()),
                "noneligible living entity receives no passive hunger attachment");
        helper.succeed();
    }

    private static EffectResult apply(EffectSystem system, GameTestHelper helper, Entity entity, float factor, float scale, ConditionContext conditions) {
        return system.apply(new EffectData.SatiateHunger(EffectCommonData.DEFAULT,factor), new EffectContext(helper.getLevel(),entity,scale,RandomSource.create(1),conditions,EffectCause.MANUAL));
    }
    private static Player boundPlayer(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player player = new Player(level, helper.absolutePos(new BlockPos(1,1,1)), 0f,
                new GameProfile(UUID.randomUUID(), "hunger-enrollment-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(player, level);
        require(HungerSystem.isEligible(player), "test player must be bound to Hunger");
        return player;
    }
    private static EffectContext context(GameTestHelper helper, Entity entity, ConditionContext conditions) {
        return new EffectContext(helper.getLevel(),entity,1f,RandomSource.create(1),conditions,EffectCause.MANUAL);
    }
    private static void require(boolean condition,String message) { if (!condition) throw new GameTestAssertException(message); }
}
