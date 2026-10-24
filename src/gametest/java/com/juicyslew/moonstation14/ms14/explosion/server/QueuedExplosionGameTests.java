package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"explosion-queue", "loose-blast"})
@PrefixGameTestTemplate(false)
public final class QueuedExplosionGameTests {
    private QueuedExplosionGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-queued-explosion-tomato")
    public static void adjacentOriginsMergeAndExecuteThroughLevelTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        var firstOrigin = helper.absolutePos(new BlockPos(8, 2, 8));
        var secondOrigin = firstOrigin.east();
        if (!level.hasChunkAt(firstOrigin) || !level.hasChunkAt(secondOrigin)
                || !level.getBlockState(firstOrigin).isAir() || !level.getBlockState(secondOrigin).isAir())
            throw new GameTestAssertException("both blast origins are loaded air");

        var manager = PrototypeRuntime.serverManager();
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var original = new HashMap<>(manager.snapshot(type).asMap());
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var previous = original.get(tomatoId);
        var damage = previous.itemDamage();
        var lowThreshold = new LooseObjectProfile.ItemDamageComposition(damage.allowedGroups(), damage.highSpeedImpact(),
                damage.minimumImpactSpeed(), damage.impactFactor(), damage.impactCooldownTicks(), damage.throwLandDamage(),
                10f, damage.destructionSound(), damage.spillCurrentReagents(), damage.destructionOutputs());
        var changed = new HashMap<>(original);
        changed.put(tomatoId, previous.withItemDamage(lowThreshold));
        manager.publishDecoded(type, changed);
        var restored = new AtomicBoolean();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void restore() { if (restored.compareAndSet(false, true)) manager.publishDecoded(type, original); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
        });
        LooseItemEntity first = null, second = null;
        try {
            first = tomato(level, firstOrigin);
            second = tomato(level, secondOrigin);
            var firstStack = first.getItem();
            var secondStack = second.getItem();
            var secondVelocity = second.getDeltaMovement();
            var proposal = new ExplosiveSourceData(.1, 1, .001,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "default"));
            require(QueuedExplosionSystem.enqueue(level, firstOrigin, proposal) == QueuedExplosionSystem.Outcome.QUEUED,
                    "first request accepted");
            require(QueuedExplosionSystem.enqueue(level, secondOrigin, proposal) == QueuedExplosionSystem.Outcome.MERGED,
                    "second request merged into the first anchor");
            var a = first; var b = second;
            helper.runAfterDelay(2, () -> {
                try {
                    ItemDamageState aLedger = a.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get());
                    ItemDamageState bLedger = b.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get());
                    require(!a.isRemoved() && a.getItem() == firstStack && a.getItem().getCount() == 1,
                            "first anchor entity and owned stack survive the sub-threshold blast");
                    require(aLedger != null && aLedger.damage().equals(Map.of("blunt", 1f, "piercing", 1f, "heat", 1f)),
                            "only first anchor receives merged three-channel damage");
                    require(bLedger == null && b.getItem() == secondStack && b.getItem().getCount() == 1
                                    && b.getDeltaMovement().equals(secondVelocity),
                            "second anchor remains undamaged and unimpulsed");
                    helper.succeed();
                } catch (Throwable failure) { helper.fail("queued merge runtime effect: " + failure); }
                finally { if (!a.isRemoved()) a.discard(); if (!b.isRemoved()) b.discard(); }
            });
        } catch (Throwable failure) {
            if (first != null && !first.isRemoved()) first.discard();
            if (second != null && !second.isRemoved()) second.discard();
            restored.compareAndSet(false, true);
            manager.publishDecoded(type, original);
            helper.fail("queued explosion fixture: " + failure);
        }
    }

    private static LooseItemEntity tomato(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var entity = LooseItemEntity.create(level, new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5),
                new ItemStack(ModItems.TOMATO.get()));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        if (!level.addFreshEntity(entity)) throw new GameTestAssertException("tomato enters fixture");
        return entity;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
