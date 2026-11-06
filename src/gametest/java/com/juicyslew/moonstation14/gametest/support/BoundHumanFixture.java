package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Minimal real bound-HUMAN fixture reused by stationary and moving-cabin proofs. */
public final class BoundHumanFixture {
    private BoundHumanFixture() { }

    public static PlayerCharacterHarnessEntity create(ServerLevel level, Vec3 feet, String profile) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(level);
        if (body == null) throw new GameTestAssertException("registered human harness could not be created");
        body.setPos(feet.x, feet.y, feet.z);
        body.setNoAi(true);
        if (!level.addFreshEntity(body) || !PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), profile,
                UUID.randomUUID(), ModCharacters.HUMAN_ID))
            throw new GameTestAssertException("normal bound human harness enrollment failed");
        if (!BodySystem.reconcile(body) || !LungSystem.reconcile(body)
                || BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.REJECTED)
            throw new GameTestAssertException("real human body/lung/hand bootstrap failed");
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        return body;
    }
}
