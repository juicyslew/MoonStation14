package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"medical-state", "observer"})
@PrefixGameTestTemplate(false)
public final class LifecycleObserverMovementGameTests {
    private LifecycleObserverMovementGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void boundServerCorpseUsesServerResolvedObserverMovement(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered character harness is created");
        body.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        body.setNoAi(true);
        require(helper.getLevel().addFreshEntity(body), "harness joins the server world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "server-observer-policy",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness has an exact saved human binding");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                        == DamageSystem.Result.APPLIED,
                "typed damage confirms native corpse death");

        var human = PrototypeRuntime.serverCharacters().get(ModCharacters.HUMAN_ID);
        require(human != null && human.component(ObserverMovementComponent.class).isEmpty(),
                "human corpse composition does not contain observer movement");
        var observer = PrototypeRuntime.serverCharacters().get(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "observer"));
        require(observer != null && observer.component(ObserverMovementComponent.class).isPresent(),
                "resolved observer prototype supplies server-authoritative movement inherited from observer base");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
