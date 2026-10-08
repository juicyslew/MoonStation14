package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.authlib.GameProfile;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "hands", "posture"})
public final class NativeMedicalHandsGameTests {
    private NativeMedicalHandsGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void nativeHandsAffectCrawlingAndDropOnCriticalNotification(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        FakePlayer body = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "native-medical-hands"));
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body), "native FakePlayer added to world");

        LiveHands hands = LiveHands.initialize(body).orElseThrow(() -> fail("typed hands initialized"));
        require(hands.handIds().size() == 2 && hands.handIds().stream().allMatch(id -> hands.stackCopy(id).isEmpty()),
                "typed hands start empty independently of native inventory");
        body.getInventory().selected = 0;
        body.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        body.getInventory().setItem(40, new ItemStack(Items.GOLD_INGOT, 2));
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        body.getInventory().setItem(38, armor.copy());

        require(MobStateSystem.policy(body) != null, "native player auto-enrolled in medical policy");
        require(MobStateSystem.isAlive(body), "native FakePlayer starts alive before hands fixture: state="
                + body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get())
                + ", health=" + body.getHealth() + "/" + body.getMaxHealth());
        require(PostureSystem.voluntaryToggle(body), "native player can voluntarily crawl");
        requireClose(PostureSystem.crawlMultiplier(body), 0, "two occupied native hands stop crawling");

        // Native slots and typed-hand reservations are separate sources in the resolved hand union.
        body.setMainArm(HumanoidArm.RIGHT);
        body.getInventory().setItem(40, ItemStack.EMPTY);
        UUID rightTarget = UUID.randomUUID();
        LiveHands reservedRight = hands.reserveHand(hands.revision(), "right", rightTarget)
                .orElseThrow(() -> fail("reserve typed right hand"));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), reservedRight);
        requireClose(PostureSystem.crawlMultiplier(body), .2,
                "occupied native main hand plus same-side typed reservation count as one union hand");
        requireClose(PostureSystem.standTime(body), 4d / 3d,
                "one free hand shortens crawler stand time");
        LiveHands releasedRight = reservedRight.releaseHand("right", rightTarget)
                .orElseThrow(() -> fail("release exact right-hand reservation"));
        UUID leftTarget = UUID.randomUUID();
        LiveHands reservedLeft = releasedRight.reserveHand(releasedRight.revision(), "left", leftTarget)
                .orElseThrow(() -> fail("reserve typed left hand"));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), reservedLeft);
        requireClose(PostureSystem.crawlMultiplier(body), 0,
                "occupied native main hand plus opposite typed reservation occupy both union hands");
        requireClose(PostureSystem.standTime(body), 2,
                "no free hands retain base crawler stand time");
        LiveHands releasedLeft = reservedLeft.releaseHand("left", leftTarget)
                .orElseThrow(() -> fail("release exact left-hand reservation"));
        body.setData(ModDataAttachments.LIVE_HANDS.get(), releasedLeft);
        body.setMainArm(HumanoidArm.LEFT);
        requireClose(PostureSystem.crawlMultiplier(body), .2,
                "native main-hand mapping follows left main arm after typed reservation release");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), hands);
        body.setMainArm(HumanoidArm.RIGHT);
        body.getInventory().setItem(40, ItemStack.EMPTY);
        requireClose(PostureSystem.crawlMultiplier(body), .2, "one native hand permits reduced crawling");
        body.getInventory().setItem(0, ItemStack.EMPTY);
        requireClose(PostureSystem.crawlMultiplier(body), .4, "free native hands restore crawler speed");

        body.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        body.getInventory().setItem(40, new ItemStack(Items.GOLD_INGOT, 2));
        // FakePlayer.hurt is a no-op in this fixture: this covers medical-event consumer behavior,
        // not native damage-to-state transition. Native hurt parity requires a connected server player.
        body.setPose(Pose.SWIMMING);
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(
                new MobStateData(MobStateComponent.State.CRITICAL, 1)));
        NeoForge.EVENT_BUS.post(new MobStateChangedEvent(body,
                MobStateComponent.State.ALIVE, MobStateComponent.State.CRITICAL));
        require(MobStateSystem.isCritical(body), "critical notification synchronized state attachment");
        require(body.getInventory().getItem(0).isEmpty() && body.getInventory().getItem(40).isEmpty(),
                "critical transition clears both native hand slots");
        require(ItemStack.isSameItemSameComponents(body.getInventory().getItem(38), armor),
                "critical transition retains native armor unchanged");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(8));
        require(drops.size() == 2, "exactly two loose native hand drops exist");
        require(drops.stream().filter(e -> e.getItem().is(Items.IRON_INGOT) && e.getItem().getCount() == 3).count() == 1
                        && drops.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT) && e.getItem().getCount() == 2).count() == 1,
                "drops preserve native stack counts exactly once");
        require(drops.stream().map(ItemEntity::getUUID).distinct().count() == 2,
                "each native hand drop has a distinct world entity UUID");
        helper.succeed();
    }

    private static GameTestAssertException fail(String message) { return new GameTestAssertException(message); }
    private static void requireClose(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .001, message + " (actual " + actual + ")");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
