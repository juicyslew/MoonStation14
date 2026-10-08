package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.blood.BloodComponent;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"medical-treatment", "physiology", "syringe"})
public final class BloodstreamSolutionAccessGameTests {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> BLOOD = ModReagents.createKey("blood");

    private BloodstreamSolutionAccessGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void initializedPigAcceptsDrugWithinResolvedCapacity(GameTestHelper helper) {
        Pig pig = initializedPig(helper, new BlockPos(1, 1, 1));
        var snapshot = requireSnapshot(pig);
        Map<ResourceKey<ReagentData>, Long> after = new HashMap<>(snapshot.units());
        after.merge(WATER, 1500L, Long::sum);
        var staged = BloodstreamSolutionAccess.previewStoreChange(snapshot, after)
                .orElseThrow(() -> new GameTestAssertException("15 units must fit above pig reference mixture"));
        require(staged.totalUnits() == snapshot.units().values().stream().mapToLong(Long::longValue).sum() + 1500,
                "staged drug admission must retain the full requested dose");
        require(BloodstreamSolutionAccess.commit(snapshot, staged), "fresh valid snapshot must commit");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void mixedSolutionDrawIsProportionalConservedAndStaleWriteRejected(GameTestHelper helper) {
        Pig pig = initializedPig(helper, new BlockPos(1, 1, 1));
        var initial = requireSnapshot(pig);
        Map<ResourceKey<ReagentData>, Long> enriched = new HashMap<>(initial.units());
        enriched.put(WATER, 1000L);
        var injected = BloodstreamSolutionAccess.previewStoreChange(initial, enriched).orElseThrow();
        require(BloodstreamSolutionAccess.commit(initial, injected), "fixture mixture injection should commit");

        var beforeDraw = requireSnapshot(pig);
        ReagentAttachment remainder = new ReagentAttachment(
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(beforeDraw.units()));
        var removed = remainder.splitUnits(500L);
        require(removed.get(BLOOD) == 469L && removed.get(WATER) == 31L,
                "5-unit draw must use exact proportional cent split");
        require(BloodstreamSolutionAccess.commit(beforeDraw,
                BloodstreamSolutionAccess.previewStoreChange(beforeDraw, remainder.snapshotUnits()).orElseThrow()),
                "proportional remainder should commit");
        var result = requireSnapshot(pig);
        require(result.units().get(BLOOD) + removed.get(BLOOD) == beforeDraw.units().get(BLOOD)
                        && result.units().get(WATER) + removed.get(WATER) == beforeDraw.units().get(WATER),
                "draw and remainder must conserve every reagent");
        require(!BloodstreamSolutionAccess.commit(beforeDraw, injected), "stale expected snapshot must reject");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void rejectsUninitializedAndUnsupportedAndAllowsRetainedDeadBody(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setData(ModDataAttachments.BLOOD.get(), new BloodComponent(0, false).toAttachment());
        pig.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment(Map.of(BLOOD, 150f)));
        require(BloodstreamSolutionAccess.inspect(pig).isEmpty(), "uninitialized metadata must not authorize access");
        require(!pig.getData(ModDataAttachments.BLOOD.get()).initialized(), "inspection must not initialize blood");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        require(BloodstreamSolutionAccess.inspect(zombie).isEmpty(), "unsupported policy must reject access");

        Pig dead = initializedPig(helper, new BlockPos(5, 1, 1));
        dead.setHealth(0);
        require(!dead.isAlive(), "fixture must be dead");
        require(BloodstreamSolutionAccess.inspect(dead).isPresent(), "retained dead initialized body remains eligible");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void initializedHumanUsesResolvedPolicyCapacityAndRejectsOverflow(GameTestHelper helper) {
        var human = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(1, 1, 1));
        CompoundTag saved = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        human.readAdditionalSaveData(saved);
        if (!CharacterIdentitySystem.enroll(human, helper.getLevel(), ModCharacters.HUMAN_ID)
                || !BloodSystem.reconcile(human))
            throw new GameTestAssertException("human fixture must initialize through its character policy");
        var snapshot = requireSnapshot(human);
        Map<ResourceKey<ReagentData>, Long> after = new HashMap<>(snapshot.units());
        after.put(WATER, 1500L);
        require(BloodstreamSolutionAccess.previewStoreChange(snapshot, after).isPresent(),
                "15-unit human drug admission must fit resolved capacity");
        Map<ResourceKey<ReagentData>, Long> full = new HashMap<>(snapshot.units());
        full.put(WATER, snapshot.capacityCents() - snapshot.units().values().stream().mapToLong(Long::longValue).sum());
        var fullSnapshot = BloodstreamSolutionAccess.previewStoreChange(snapshot, full)
                .orElseThrow(() -> new GameTestAssertException("filling policy capacity must succeed"));
        var fullUnits = new HashMap<>(fullSnapshot.snapshotUnits());
        fullUnits.merge(WATER, 1L, Long::sum);
        require(BloodstreamSolutionAccess.previewStoreChange(snapshot, fullUnits).isEmpty(),
                "over-capacity staged mixture must reject");
        helper.succeed();
    }

    private static Pig initializedPig(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawn(EntityType.PIG, pos);
        pig.setNoAi(true);
        if (!BloodSystem.reconcile(pig)) throw new GameTestAssertException("pig must have initialized policy state");
        return pig;
    }

    private static BloodstreamSolutionAccess.Snapshot requireSnapshot(LivingEntity body) {
        return BloodstreamSolutionAccess.inspect(body)
                .orElseThrow(() -> new GameTestAssertException("initialized supported body must be inspectable"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
