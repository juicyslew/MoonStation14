package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.EquippedArmor;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/** End-to-end proof that actual resolved radiation PPE does not protect its wearer from horizon death. */
@GameTestTags("singularity-horizon")
@PrefixGameTestTemplate(false)
public final class SingularityHorizonPpeGameTests {
    private SingularityHorizonPpeGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void equippedRadiationSuitWearerDiesOnBodyContact(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        var body = PlayerCharacterHarnessRegistration.getEntityType().create(level);
        require(singularity != null && body != null, "registered singularity and player-character body created");
        singularity.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        body.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        require(level.addFreshEntity(singularity) && level.addFreshEntity(body), "both runtime entities admitted");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "horizon-rad-suited",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "body resolves exact human equipment composition");
        LiveHands state = LiveHands.initialize(body).orElseThrow(() -> fail("resolved human hands and equipment slots initialized"));
        LiveHands held = state.putWhole(state.revision(), "left", new ItemToken("rad-suit"),
                new ItemStack(ModEquipmentItems.SUIT_RAD.get())).state();
        require(LiveHandsCommitGateway.commitTrusted(body, state, held), "real radiation suit item enters authoritative hand state");
        require(BodyEquipmentTransfer.quickEquipOwned(body, "left", "rad-suit", held.revision(), () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS,
                "existing equipment transfer service equips the resolved suit into its declared slot");
        var worn = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                .filter(slot -> slot.id().equals("outerClothing")).findFirst().orElseThrow();
        require(worn.stack().filter(stack -> stack.is(ModEquipmentItems.SUIT_RAD.get())).isPresent(),
                "contacting body really wears the radiation suit, not a simulated protection flag");
        var reduced = EquippedArmor.modify(body, Map.of(DamageKeys.RADIATION, 10f));
        require(Math.abs(reduced.getOrDefault(DamageKeys.RADIATION, 0f) - .1f) < .001,
                "same equipped suit resolves its 0.01 radiation armor coefficient");

        singularity.tick();
        require(body.hasConfirmedDeath() && body.isDeadOrDying(),
                "radiation-suited owner body still reaches accepted native horizon death");
        require(!body.isRemoved(), "owned corpse remains present; horizon never discards character inventory");
        body.discard(); singularity.discard();
        helper.succeed();
    }

    private static GameTestAssertException fail(String message) { return new GameTestAssertException(message); }
    private static void require(boolean valid, String message) { if (!valid) throw fail(message); }
}
