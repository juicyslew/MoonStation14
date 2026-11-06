package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import java.util.Map;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native admitted-actor probe and transactional revalidation against a rotated loose cargo profile. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-cargo-query", "hands", "interaction"})
public final class CargoPickupGeometryGameTests {
    private CargoPickupGeometryGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 600)
    @GameTestTags({"hands-nominated-pickup-regression"})
    public static void nativeFrameCargoProbeAndPickupRevalidateOrientedGeometry(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1800, -47, 1500), "CargoQueryPickup",
                2.5, 1, 2.5, 0, (fixture, body) -> MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body,
                        () -> exercise(helper, fixture, body)));
    }

    private static void exercise(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                 LivingEntity body) {
        var level = fixture.level();
        var actor = fixture.owner().actor();
        ResourceKey<ReagentData> water = ModReagents.createKey("water");
        ItemStack stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(Map.of(water, 300L)));
        require(stack.get(ModDataComponents.REAGENT.get()).centContents().get(water) == 300L,
                "jug source contains exactly 300 cents (3 units)");
        body.setYRot(0);
        body.setYHeadRot(0);
        body.setXRot(0);
        Vec3 eye = body.getEyePosition();
        LooseItemEntity item = LooseItemEntity.createForActor(body, new Vec3(eye.x - .50, eye.y, eye.z + 2), stack.copy());
        item.setPos(eye.x - .50, eye.y - item.profile().physicalHeight() / 2, eye.z + 2);
        item.setRotationAngle((float) (Math.PI / 4));
        if (!level.addFreshEntity(item)) throw new GameTestAssertException("rotated jug spawn rejected");
        try {
            Vec3 from = body.getEyePosition(), to = from.add(body.getLookAngle().scale(BodyItemTargetProbe.MAX_REACH));
            var shape = LooseItemQueryGeometry.forEntity(item, 1);
            require(shape != null, "resolved cargo query shape exists");
            require(item.getBoundingBox().clip(from, to).isEmpty()
                            && item.getBoundingBox().inflate(BodyItemTargetProbe.TARGET_MARGIN).clip(from, to).isEmpty(),
                    "world-Z grazing ray misses both old physical and old inflated AABBs");
            require(shape.inflated(BodyItemTargetProbe.TARGET_MARGIN).clip(from, to) != null,
                    "shared oriented tolerance admits grazing ray");
            require(BodyItemTargetProbe.probeSelected(level, body, item.getUUID(), BodyItemTargetProbe.MAX_REACH) != null,
                    "authoritative selected probe admits oriented-margin hit missed by old AABB adapter");
            var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(hands != null, "actor hands exist");
            String hand = hands.activeHand();
            var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
            var ledgerBefore = ledger.snapshot();
            ItemStack sourceBefore = item.getItem().copy();
            float originalSpin = item.getRotationAngle();
            Vec3 originalPosition = item.position();
            item.setRotationAngle(originalSpin + (float) (Math.PI / 4));
            require(LooseItemQueryGeometry.forEntity(item, 1).inflated(BodyItemTargetProbe.TARGET_MARGIN).clip(from, to) == null,
                    "changed spin makes prior grazing ray miss oriented margin");
            require(BodyItemTargetProbe.probeSelected(level, body, item.getUUID(), BodyItemTargetProbe.MAX_REACH) == null,
                    "ray-selection probe rejects changed-orientation hit");
            require(BodyItemTargetProbe.probeNominatedPickup(level, body, item.getUUID(), BodyItemTargetProbe.MAX_REACH) != null,
                    "changed gaze-hit geometry remains reachable for explicit nomination");
            item.setPos(originalPosition.x + BodyItemTargetProbe.MAX_REACH + 2, originalPosition.y, originalPosition.z);
            require(BodyItemTargetProbe.probeNominatedPickup(level, body, item.getUUID(), BodyItemTargetProbe.MAX_REACH) == null,
                    "current nominated probe rejects target beyond physical nearest-point reach");
            require(BodyHandItemTransfer.pickupSelected(actor, item.getUUID(), hand, hands.revision(), ignored -> { })
                    == BodyHandItemTransfer.Result.DENIED, "transaction rejects target currently out of reach");
            var handsAfterDenied = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(!item.isRemoved() && ItemStack.matches(item.getItem(), sourceBefore)
                            && handsAfterDenied != null && handsAfterDenied.compatible(body)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).revision() == hands.revision()
                            && ledger.snapshot().revision() == ledgerBefore.revision()
                            && ledger.snapshot().locations().equals(ledgerBefore.locations()),
                    "denied stale hit retains exact source, compatible hands and unchanged optional-ledger snapshot");
            item.setRotationAngle(originalSpin);
            item.setPos(originalPosition);
            body.setYRot(180);
            body.setYHeadRot(180);
            body.setXRot(0);
            require(item.position().subtract(body.getEyePosition()).dot(body.getLookAngle()) < 0,
                    "selected cargo pickup target is behind the body look direction");
            require(BodyItemTargetProbe.probeNominatedPickup(level, body, item.getUUID(), BodyItemTargetProbe.MAX_REACH) != null,
                    "explicit nomination validates current reach and LOS independently of body look");
            require(BodyHandItemTransfer.pickupSelected(actor, item.getUUID(), hand, hands.revision(), ignored -> { })
                    == BodyHandItemTransfer.Result.SUCCESS, "selected API commits nominated item while body looks away");
            var after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var token = after == null ? null : after.token(hand).orElse(null);
            require(item.isRemoved() && level.getEntity(item.getUUID()) == null && after != null
                            && after.compatible(body) && token != null && !token.isBlank()
                            && ItemStack.matches(after.stackCopy(hand).orElseThrow(), sourceBefore)
                            && ledger.snapshot().revision() == ledgerBefore.revision()
                            && ledger.snapshot().locations().equals(ledgerBefore.locations()),
                    "whole component-bearing stack transfers to compatible tokenized authoritative hand; optional ledger unchanged");
        } finally {
            if (!item.isRemoved()) item.discard();
        }
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
