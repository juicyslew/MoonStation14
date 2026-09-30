package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyHandSelectionGateGameTests {
    private BodyHandSelectionGateGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyLegacyMetadataAllowsLiveToggleButTokensDeny(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands first = LiveHands.initialize(body).orElseThrow();
            HandAttachment legacy = new HandAttachment(HandComponent.from(
                    HandState.create(List.of("left", "right"), "left")));
            body.setData(ModDataAttachments.HANDS.get(), legacy);
            LiveHands second = first.selectActive(first.revision(), "right").orElseThrow();
            require(BodyHandRequestService.selectionAllowed(body, first, second, 0),
                    "empty legacy metadata permits active change");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), second);
            require(second.compatible(body) && body.getExistingDataOrNull(ModDataAttachments.HANDS.get()) == legacy
                            && legacy.state().activeHand().equals("left") && second.activeHand().equals("right")
                            && second.revision() == 1,
                    "toggle publishes only live hands and preserves historical legacy active");
            LiveHands third = second.selectActive(second.revision(), "left").orElseThrow();
            require(BodyHandRequestService.selectionAllowed(body, second, third, 1),
                    "historical metadata remains compatible on return toggle");
            body.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(List.of("left", "right")).place("left", new ItemToken("old-token")).state())));
            require(!second.compatible(body) && !BodyHandRequestService.selectionAllowed(body, second, third, 1)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == second,
                    "legacy token fails closed without changing live revision");
            body.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(List.of("right", "left")))));
            require(!BodyHandRequestService.selectionAllowed(body, second, third, 1),
                    "legacy hand ID order mismatch fails closed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stunDeniesSelectionWithoutRevisionChange(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands before = LiveHands.initialize(body).orElseThrow();
            LiveHands after = before.selectActive(before.revision(), "right").orElseThrow();
            require(BodyHandRequestService.selectionAllowed(body, before, after, 0), "unstunned selection allowed");
            require(CharacterControlSystem.applyStun(body, 40), "bound body accepts stun");
            require(!BodyHandRequestService.selectionAllowed(body, before, after, 0)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                            && before.revision() == 0 && before.activeHand().equals("left"),
                    "stun rejects final selection gate and retains the exact hand snapshot");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
