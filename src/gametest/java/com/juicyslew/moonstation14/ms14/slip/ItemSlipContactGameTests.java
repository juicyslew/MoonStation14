package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestTags({"slip", "movement", "item-slip"})
public final class ItemSlipContactGameTests {
    private ItemSlipContactGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void acceptedMovementContactsResolvedSlipperyItemComposition(GameTestHelper helper) {
        FakePlayer player = null;
        AtomicInteger puddleEvents = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<UUID> fixtureActor = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target().getUUID().equals(fixtureActor.get())) puddleEvents.incrementAndGet();
        };
        boolean listening = false;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos floor = new BlockPos(2, 0, 2);
            helper.setBlock(floor, Blocks.STONE);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "item-slip-contact"));
            fixtureActor.set(player.getUUID());
            putAt(player, helper, floor.above());
            require(level.addFreshEntity(player), "FakePlayer/ServerPlayer added to test level");
            SlipSystem.addListener(listener);
            listening = true;
            FakePlayer actor = player;
            helper.runAfterDelay(2, () -> {
                try {
                    require(CharacterIdentitySystem.enroll(actor, level, ModCharacters.HUMAN_ID), "FakePlayer enrolled as human");
                    actor.setDeltaMovement(Vec3.ZERO);
                    actor.move(MoverType.SELF, new Vec3(0, -1, 0));
                    require(actor.onGround(), "downward collision establishes grounded player");
                    require(CharacterIdentitySystem.resolve(actor).isPresent(), "human composition resolves");
                    require(StaminaSystem.state(actor).damage() == 0f && !PostureSystem.isDownPosture(actor), "actor starts eligible and unaffected");

                    LooseItemEntity item = source(level, actor, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(.2, 0, 0));
                    require(!actor.hasData(ModDataAttachments.STATUS_EFFECT.get()) && StaminaSystem.state(actor).damage() == 0f,
                            "ordinary stack is inert without materializing effects");

                    ItemSlipperyComposition composition = new ItemSlipperyComposition(
                            new ItemStepTriggerData(true, .3, 1, false), SlipSystem.looseItemResponse(.5, 1.5, 1.5, .5, false));
                    item.getItem().set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), composition);
                    require(LooseObjectProfile.resolveSlipperyForStack(item.getItem(), false).equals(composition), "stack resolves its exact override composition");
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(.2, 0, 0));
                    require(StaminaSystem.state(actor).damage() == 25f, "item response applies configured stamina damage");
                    require(Math.abs(StatusEffectSystem.frictionFactor(actor) - .5d) < 1e-6, "item response applies configured friction");
                    var effects = actor.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
                    require(effects.get(ModStatusEffects.createKey("statuseffectstunned")).orElseThrow().remainingDurationTicks().orElseThrow() == 10, "stun lasts ten ticks");
                    require(effects.get(ModStatusEffects.createKey("knockdown")).orElseThrow().remainingDurationTicks().orElseThrow() == 30, "knockdown lasts thirty ticks");
                    require(PostureSystem.isDownPosture(actor) && !SlipSystem.isSliding(actor), "response is prone without sliding");
                    require(puddleEvents.get() == 0, "item contact does not publish puddle SlipEvent");
                    helper.succeed();
                } finally {
                    SlipSystem.removeListener(listener);
                    actor.discard();
                }
            });
        } catch (RuntimeException | Error failure) {
            if (listening) SlipSystem.removeListener(listener);
            if (player != null) player.discard();
            throw failure;
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void cancelledItemTriggerCanRetryButAcceptedContactLatches(GameTestHelper helper) {
        FakePlayer player = null;
        AtomicInteger gates = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean reject = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<FakePlayer> fixtureActor = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<LooseItemEntity> fixtureSource = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Predicate<ItemStepContactSystem.TriggerContext> gate = attempt -> {
            if (attempt.target() != fixtureActor.get() || attempt.source() != fixtureSource.get()) return true;
            gates.incrementAndGet();
            return !reject.get();
        };
        java.util.function.Predicate<ItemStepContactSystem.TriggerContext> latchObserver = attempt -> {
            if (attempt.target() == fixtureActor.get() && attempt.source() == fixtureSource.get()) gates.incrementAndGet();
            return true;
        };
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target() == fixtureActor.get()) events.incrementAndGet();
        };
        boolean gateAdded = false, listenerAdded = false;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos floor = new BlockPos(2, 0, 2);
            for (int x = 1; x <= 7; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "item-slip-retry"));
            fixtureActor.set(player);
            putAt(player, helper, floor.above());
            require(level.addFreshEntity(player), "FakePlayer added");
            gateAdded = ItemStepContactSystem.addTriggerGate(gate);
            listenerAdded = SlipSystem.addListener(listener);
            require(gateAdded && listenerAdded, "temporary item hooks registered");
            FakePlayer actor = player;
            helper.runAfterDelay(2, () -> {
                try {
                    require(CharacterIdentitySystem.enroll(actor, level, ModCharacters.HUMAN_ID), "FakePlayer enrolled");
                    actor.setDeltaMovement(Vec3.ZERO);
                    actor.move(MoverType.SELF, new Vec3(0, -1, 0));
                    LooseItemEntity source = source(level, actor, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
                    fixtureSource.set(source);
                    source.getItem().set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), new ItemSlipperyComposition(
                            new ItemStepTriggerData(true, .3, 1, false), SlipSystem.looseItemResponse(.5, 0, 1.5, .5, false)));
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(.2, 0, 0));
                    require(gates.get() == 1 && events.get() == 0 && StaminaSystem.state(actor).damage() == 0f, "rejected pretrigger has no response");
                    require(ItemStepContactSystem.removeTriggerGate(gate), "rejected gate removed");
                    reject.set(false);
                    require(ItemStepContactSystem.addTriggerGate(latchObserver), "latch observer registered");
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(.2, 0, 0));
                    require(events.get() == 0 && StaminaSystem.state(actor).damage() == 25f,
                            "same-overlap retry is admitted after pretrigger rejection");
                    require(gates.get() == 2, "gate ran once on newly accepted item contact");
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(.2, 0, 0));
                    require(gates.get() == 2 && events.get() == 0 && StaminaSystem.state(actor).damage() == 25f,
                            "latched item contact neither reruns trigger gate nor reapplies response");
                    putAt(actor, helper, new BlockPos(6, 1, 2));
                    SlipSystem.reconcileTarget(actor);
                    require(!feetOverlap(actor, source), "actor has fully separated from item source");
                    StatusEffectSystem.clearAll(((IStatusEffectTrait) actor).toHandleSelf(), level);
                    actor.setDeltaMovement(Vec3.ZERO);
                    actor.move(MoverType.SELF, new Vec3(0, -1, 0));
                    require(actor.onGround(), "actor grounded on return lane before re-entry");
                    putAt(actor, helper, new BlockPos(2, 1, 2));
                    require(feetOverlap(actor, source), "actor feet physically overlap source on re-entry");
                    SlipSystem.onAcceptedPlayerMovement(actor, new Vec3(-.2, 0, 0));
                    require(gates.get() == 3 && StaminaSystem.state(actor).damage() == 50f,
                            "re-entry reruns gate once and allows exactly one next response");
                    helper.succeed();
                } finally {
                    ItemStepContactSystem.removeTriggerGate(gate);
                    ItemStepContactSystem.removeTriggerGate(latchObserver);
                    SlipSystem.removeListener(listener);
                    if (fixtureSource.get() != null) fixtureSource.get().discard();
                    actor.discard();
                }
            });
        } catch (RuntimeException | Error failure) {
            if (gateAdded) ItemStepContactSystem.removeTriggerGate(gate);
            ItemStepContactSystem.removeTriggerGate(latchObserver);
            if (listenerAdded) SlipSystem.removeListener(listener);
            if (fixtureSource.get() != null) fixtureSource.get().discard();
            if (player != null) player.discard();
            throw failure;
        }
    }

    private static LooseItemEntity source(ServerLevel level, FakePlayer actor, ItemStack stack) {
        var item = LooseItemEntity.create(level, actor.position().add(0, 0, .1), stack);
        require(level.addFreshEntity(item), "real loose item added");
        return item;
    }

    private static void putAt(net.minecraft.world.entity.Entity entity, GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }

    private static boolean feetOverlap(FakePlayer actor, LooseItemEntity source) {
        var bounds = actor.getBoundingBox();
        var feet = new net.minecraft.world.phys.AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX,
                bounds.minY + Math.min(.25d, bounds.getYsize()), bounds.maxZ);
        return SlipSystem.contactIntersectionRatio(feet, source.getBoundingBox()) > 0d;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

