package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "hands"})
public final class MedicalIncapacitationGameTests {
    private MedicalIncapacitationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void criticalTransitionForceDropsNpcHandsAndHealingRestoresAction(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "human-profile villager created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        // The pickup probe follows the body's eye ray; match the established pickup fixture pose.
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(helper.getLevel().addFreshEntity(body)
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID)
                        && BodySystem.reconcile(body), "villager enrolled as reconciled human body");
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        body.setHealth(40f);
        LiveHands hands = LiveHands.initialize(body).orElseThrow(() ->
                new GameTestAssertException("human live hands initialize"));
        String marker = "medical-drop-" + UUID.randomUUID();
        ItemStack expectedLeft = new ItemStack(Items.DIAMOND, 2);
        expectedLeft.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(marker + "-left"));
        ItemStack expectedRight = new ItemStack(Items.EMERALD, 3);
        expectedRight.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(marker + "-right"));
        ItemEntity left = new ItemEntity(helper.getLevel(), body.getX() + 3, body.getY() + 1.5, body.getZ(), expectedLeft.copy());
        ItemEntity right = new ItemEntity(helper.getLevel(), body.getX() + 3, body.getY() + 1.5, body.getZ(), expectedRight.copy());
        ItemStack unrelatedStack = new ItemStack(Items.STICK);
        ItemEntity unrelated = new ItemEntity(helper.getLevel(), body.getX() + 1, body.getY() + 1, body.getZ(), unrelatedStack.copy());
        require(helper.getLevel().addFreshEntity(left) && helper.getLevel().addFreshEntity(right),
                "two source items spawned");
        require(helper.getLevel().addFreshEntity(unrelated), "unrelated nearby item spawned");
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess
                        .pickupOwned(body, left.getUUID(), "left", hands.revision())
                        == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                "NPC first hand pickup succeeds");
        LiveHands afterLeft = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess
                        .pickupOwned(body, right.getUUID(), "right", afterLeft.revision())
                        == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                "NPC second hand pickup succeeds");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                        == DamageSystem.Result.APPLIED, "100 typed damage applied");
        require(MobStateSystem.isCritical(body), "damage automatically enters Critical");
        LiveHands emptied = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(emptied != null && emptied.stackCopy("left").isEmpty() && emptied.stackCopy("right").isEmpty(),
                "both hand ownership stacks are empty");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(8)).stream()
                .filter(entity -> entity.getItem().getHoverName().getString().startsWith(marker + "-"))
                .toList();
        require(drops.size() == 2, "exactly two loose world item entities produced");
        require(drops.stream().filter(entity -> ItemStack.isSameItemSameComponents(entity.getItem(), expectedLeft)
                        && entity.getItem().getCount() == expectedLeft.getCount()).count() == 1
                        && drops.stream().filter(entity -> ItemStack.isSameItemSameComponents(entity.getItem(), expectedRight)
                        && entity.getItem().getCount() == expectedRight.getCount()).count() == 1,
                "each original hand stack is dropped exactly once");
        require(unrelated.isAlive() && ItemStack.isSameItemSameComponents(unrelated.getItem(), unrelatedStack),
                "unrelated nearby item remains intact");
        require(!CharacterControlSystem.canAct(body), "critical body cannot act");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, -2f), 1f, true)
                        == DamageSystem.Result.APPLIED, "healing below critical threshold applied");
        require(MobStateSystem.isAlive(body) && CharacterControlSystem.canAct(body),
                "healed body without stun can act again");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
