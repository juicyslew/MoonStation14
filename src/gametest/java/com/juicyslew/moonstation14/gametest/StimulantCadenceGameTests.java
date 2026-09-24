package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.MovementSpeedProjection;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StimulantCadenceGameTests {
    private static final ResourceKey<ReagentData> STIMULANTS = ModReagents.createKey("stimulants");
    private static final ResourceKey<StatusEffectData> SPEED =
            ModStatusEffects.createKey("reagentspeedstatuseffect");
    private static final ResourceKey<StatusEffectData> JITTER = ModStatusEffects.createKey("jitter");

    private StimulantCadenceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stimulantStatusesStayContinuousOnlyWithAnOngoingReservoir(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        MS14Provider.update(character, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(STIMULANTS, 5f)));
        EntityActivitySystem.update(character, EntityActivity.REAGENT_METABOLISM, true);
        Set<EntityActivity> metabolism = Set.of(EntityActivity.REAGENT_METABOLISM);
        ServerLevel level = (ServerLevel) helper.getLevel();

        TickHooks.runDueActivities(character, level, metabolism);
        require(close(stomachAmount(character), 4.75f),
                "one stomach pass transfers the cent-canonicalized source amount");
        require(close(bodyAmount(character), .12f),
                "stomach transfer efficacy floors body admission to cents");
        require(!has(character, SPEED) && !has(character, JITTER),
                "the stomach pass does not also metabolize newly transferred body reagent");

        TickHooks.runDueActivities(character, level, metabolism);
        require(close(bodyAmount(character), .12f),
                "the body pass consumes its dose and the stomach contributes exactly one new dose");
        require(close(stomachAmount(character), 4.50f),
                "each metabolism pass transfers its cent-canonicalized source amount once");
        assertActiveAt(character, SPEED, 21);
        assertActiveAt(character, JITTER, 21);
        var speedModifierId = MovementSpeedProjection.modifierId(SPEED);
        var speedModifier = character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(speedModifierId);
        require(speedModifier != null && Math.abs(speedModifier.amount() - .25d) < .000001d,
                "stimulants project the 1.25 movement multiplier");

        for (int tick = 0; tick < 20; tick++) {
            StatusEffectSystem.advanceOneTick(character);
            require(has(character, SPEED) && has(character, JITTER),
                    "ongoing stimulant statuses remain active during every intervening status tick");
            require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(speedModifierId) != null,
                    "the transient speed projection remains present throughout the status countdown");
        }
        require(remaining(character, SPEED) == 1 && remaining(character, JITTER) == 1,
                "both ongoing status instances reach one tick before their metabolism refresh");

        TickHooks.runDueActivities(character, level, metabolism);
        assertActiveAt(character, SPEED, 21);
        assertActiveAt(character, JITTER, 21);
        require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(speedModifierId) == speedModifier,
                "refresh keeps the existing speed projection instead of removing and recreating it");
        require(close(bodyAmount(character), .12f)
                        && close(stomachAmount(character), 4.25f),
                "refreshing effects does not double-consume or double-transfer stimulant");

        // Drain the ongoing source from status lifetime, then prove an isolated
        // body dose uses its unmodified 2-second/10-tick duration.
        for (int tick = 0; tick < 20; tick++) StatusEffectSystem.advanceOneTick(character);
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment());
        MS14Provider.update(character, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(STIMULANTS, .125f)));
        EntityActivitySystem.update(character, EntityActivity.REAGENT_METABOLISM, true);
        TickHooks.runDueActivities(character, level, metabolism);
        assertActiveAt(character, SPEED, 10);
        assertActiveAt(character, JITTER, 10);
        for (int tick = 0; tick < 9; tick++) {
            StatusEffectSystem.advanceOneTick(character);
            require(has(character, SPEED) && has(character, JITTER),
                    "isolated stimulant effects remain active until their tenth tick");
        }
        StatusEffectSystem.advanceOneTick(character);
        require(!has(character, SPEED) && !has(character, JITTER),
                "isolated body stimulant statuses expire naturally after ten ticks");
        require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(speedModifierId) == null,
                "natural speed-status expiry removes its transient movement modifier");
        helper.succeed();
    }

    private static float bodyAmount(Villager character) {
        return MS14Provider.get(character, MS14Bridges.REAGENT).getMap().getOrDefault(STIMULANTS, 0f);
    }

    private static float stomachAmount(Villager character) {
        return MS14Provider.get(character, MS14Bridges.STOMACH).getMap().getOrDefault(STIMULANTS, 0f);
    }

    private static boolean has(Villager character, ResourceKey<StatusEffectData> key) {
        return StatusEffectSystem.hasStatus(((com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait) character)
                .toHandleSelf(), character.level(), key);
    }

    private static int remaining(Villager character, ResourceKey<StatusEffectData> key) {
        return MS14Provider.get(character, MS14Bridges.STATUS_EFFECT).get(key)
                .orElseThrow().remainingDurationTicks().orElseThrow();
    }

    private static void assertActiveAt(Villager character,
            ResourceKey<StatusEffectData> key,
            int ticks) {
        require(has(character, key) && remaining(character, key) == ticks,
                key.location() + " should be active for " + ticks + " ticks");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < .000001f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
