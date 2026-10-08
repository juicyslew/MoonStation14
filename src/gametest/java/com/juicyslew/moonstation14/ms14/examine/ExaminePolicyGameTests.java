package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.StunnableComponent;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real loaded-world resolver and resolved-profile fixture coverage; not authenticated-player protocol coverage. */
@PrefixGameTestTemplate(false)
@GameTestTags("examine_policy")
public final class ExaminePolicyGameTests {
    private ExaminePolicyGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void contiguousSteelWallFacesAreVisibleButOpaqueWallBehindBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(4, 1, 2));
        Villager actor = enrolledHuman(helper, new BlockPos(4, 1, 2));
        actor.setPos(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
        BlockPos first = foot.offset(-2, 0, 2);
        for (int x = 0; x < 5; x++) {
            BlockPos face = first.offset(x, 0, 0);
            level.setBlock(face, com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
            level.setBlock(face.above(), com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        }
        for (int x = 0; x < 5; x++) {
            BlockPos face = first.offset(x, 0, 0);
            var resolved = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(face)).orElseThrow(
                    () -> new GameTestAssertException("contiguous steel wall face resolves"));
            var visibility = ExamineTargetResolver.visibility(level, actor, resolved);
            require(visibility.visible(), "endpoint-containing steel wall face remains examinable: " + face
                    + ", point=" + resolved.point() + ", distance=" + resolved.distance()
                    + ", eye=" + actor.getEyePosition() + ", cells="
                    + ExamineTargetResolver.crossedCells(actor.getEyePosition(), resolved.point())
                    + ", reason=" + visibility.reason() + ", blocker=" + visibility.blocker());
        }
        BlockPos behind = foot.offset(0, 0, 4);
        level.setBlock(behind, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(behind.above(), Blocks.STONE.defaultBlockState(), 3);
        var blocked = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(behind)).orElseThrow();
        require(!ExamineTargetResolver.visible(level, actor, blocked), "front opaque steel wall still blocks target behind it");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void resolvedHumanObserverAndMedicalPolicyBoundaries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager human = enrolledHuman(helper, new BlockPos(2, 1, 2));
        var humanData = CharacterIdentitySystem.resolveForActor(human).orElseThrow(
                () -> new GameTestAssertException("enrolled human prototype resolves"));
        ExaminerComponent examiner = humanData.component(ExaminerComponent.class).orElseThrow(
                () -> new GameTestAssertException("resolved human inherits Examiner"));
        require(examiner.equals(ExaminerComponent.DEFAULT), "human inherits Examiner false/true defaults");
        require(humanData.component(StunnableComponent.class).isPresent(), "resolved human supports stun state");

        var observer = PrototypeRuntime.serverCharacters().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "observer"));
        require(observer != null, "resolved observer character prototype exists");
        ExaminerComponent ghostExaminer = observer.component(ExaminerComponent.class).orElseThrow(
                () -> new GameTestAssertException("observer inherits Examiner"));
        require(ghostExaminer.skipChecks() && ghostExaminer.checkInRangeUnOccluded(),
                "observer override keeps inherited range flag and skips checks");

        require(CharacterControlSystem.applyStun(human, 40), "healthy resolved human accepts finite stun");
        require(CharacterControlSystem.isStunned(human), "fixture confirms actor is currently stunned");
        var ordinary = new ExaminationPolicy.Examiner(examiner.skipChecks(), examiner.checkInRangeUnOccluded());
        require(ExaminationPolicy.canExamine(ordinary, ExaminationPolicy.State.ALIVE, false, 0,
                16, true, true, true), "stun does not gate ordinary examination at 16 blocks");
        require(ExaminationPolicy.detailsAllowed(ordinary, ExaminationPolicy.State.ALIVE, false,
                3, true, true), "stun does not gate authorized close details");

        assertRange(ordinary, ExaminationPolicy.State.ALIVE, 16, true, "alive normal range endpoint");
        assertRange(ordinary, ExaminationPolicy.State.ALIVE, 16.01, false, "alive beyond normal range");
        assertRange(ordinary, ExaminationPolicy.State.CRITICAL, 1.3, true, "critical range endpoint");
        assertRange(ordinary, ExaminationPolicy.State.CRITICAL, 1.31, false, "critical beyond range");
        assertRange(ordinary, ExaminationPolicy.State.DEAD, .75, true, "dead range endpoint");
        assertRange(ordinary, ExaminationPolicy.State.DEAD, .76, false, "dead beyond range");
        require(!ExaminationPolicy.detailsAllowed(ordinary, ExaminationPolicy.State.CRITICAL, false, 1, true, true),
                "critical cannot receive detail rows");
        require(!ExaminationPolicy.detailsAllowed(ordinary, ExaminationPolicy.State.DEAD, false, .5, true, true),
                "dead cannot receive detail rows");
        require(ExaminationPolicy.detailsAllowed(new ExaminationPolicy.Examiner(
                        ghostExaminer.skipChecks(), ghostExaminer.checkInRangeUnOccluded()),
                ExaminationPolicy.State.DEAD, true, 100, false, false), "resolved observer role bypasses detail range");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void physicalEyeRangeAndTransparentCollisionLos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager actor = enrolledHuman(helper, new BlockPos(2, 1, 2));
        actor.setPos(foot.getX() + .5, foot.getY(), foot.getZ());
        BlockPos nearPos = new BlockPos(foot.getX(), foot.getY() + 1, foot.getZ() + 3);
        level.setBlock(nearPos, Blocks.STONE.defaultBlockState(), 3);
        ExamineTarget nearTarget = ExamineTarget.block(nearPos);
        var near = ExamineTargetResolver.resolve(level, actor, nearTarget).orElseThrow(
                () -> new GameTestAssertException("loaded near block resolves"));
        require(Math.abs(near.distance() - 3.0) < 1e-6,
                "range uses physical actor eye to nearest visible shape point (distance=" + near.distance() + ")");
        require(ExaminationPolicy.detailsAllowed(ExaminationPolicy.Examiner.DEFAULT,
                ExaminationPolicy.State.ALIVE, false, near.distance(), true,
                ExamineTargetResolver.visible(level, actor, near)), "3D point at exactly three permits details");

        BlockPos panePos = new BlockPos(foot.getX(), nearPos.getY(), foot.getZ() + 1);
        level.setBlock(panePos, Blocks.GLASS.defaultBlockState(), 3);
        var glassTarget = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(panePos)).orElseThrow();
        require(ExamineVision.isTransparent(level.getBlockState(panePos)), "glass vision classification is explicit");
        require(ExamineTargetResolver.visible(level, actor, glassTarget), "transparent glass itself remains examinable");
        require(ExamineTargetResolver.visible(level, actor, near), "collision LOS continues through tagged glass");

        level.setBlock(panePos, Blocks.STONE.defaultBlockState(), 3);
        require(!ExamineTargetResolver.visible(level, actor, near), "opaque stone between actor and target blocks LOS");
        level.setBlock(panePos, Blocks.TINTED_GLASS.defaultBlockState(), 3);
        require(!ExamineVision.isTransparent(level.getBlockState(panePos)), "tinted glass is not in transparent tag");
        require(!ExamineTargetResolver.visible(level, actor, near), "tinted glass collision geometry blocks LOS");

        level.setBlock(panePos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(nearPos, Blocks.AIR.defaultBlockState(), 3);
        // Probe horizontally within the template's actual loaded bounds (36x5x8);
        // the short Y dimension cannot contain a vertical 16-block ray.
        BlockPos farPos = new BlockPos(foot.getX() + 17, foot.getY() + 1, foot.getZ());
        actor.setPos(foot.getX() + 1, foot.getY(), foot.getZ() + .5);
        var farPoint = new net.minecraft.world.phys.Vec3(farPos.getX(), farPos.getY() + .5, farPos.getZ() + .5);
        var fixtureBounds = helper.getBounds();
        require(fixtureBounds.contains(actor.getEyePosition()) && fixtureBounds.contains(farPoint)
                        && fixtureBounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(farPos)),
                "far actor eye and endpoint remain inside actual fixture bounds " + fixtureBounds);
        level.setBlock(farPos, Blocks.STONE.defaultBlockState(), 3);
        var far = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(farPos)).orElseThrow();
        require(Math.abs(far.distance() - 16.0) < 1e-6,
                "ordinary range measures to target AABB point (distance=" + far.distance() + ")");
        ExamineTargetResolver.VisibilityResult farVisibility = ExamineTargetResolver.visibility(level, actor, far);
        boolean farVisible = farVisibility.visible();
        String blocker = farVisibility.blocker() == null ? "none" : farVisibility.blocker()
                + (level.hasChunkAt(farVisibility.blocker())
                ? "=" + level.getBlockState(farVisibility.blocker()).getBlock().builtInRegistryHolder().key().location()
                : " (unloaded)");
        require(farVisible, "nominal far endpoint must have clear LOS (eye=" + actor.getEyePosition()
                + ", point=" + far.point() + ", distance=" + precise(far.distance()) + ", visible=" + farVisible
                + ", reason=" + farVisibility.reason() + ", blocker=" + blocker + ")");
        boolean nominalAllowed = ExaminationPolicy.canExamine(ExaminationPolicy.Examiner.DEFAULT,
                ExaminationPolicy.State.ALIVE, false, 0, far.distance(), true, farVisible, true);
        require(nominalAllowed == (far.distance() <= ExaminationPolicy.NORMAL_RANGE),
                "nominal endpoint follows exact policy comparison (eye=" + actor.getEyePosition()
                        + ", point=" + far.point() + ", distance=" + precise(far.distance())
                        + ", visible=" + farVisible + ", allowed=" + nominalAllowed + ")");

        actor.setPos(foot.getX() + 1.5, foot.getY(), foot.getZ() + .5);
        var inside = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(farPos)).orElseThrow();
        boolean insideVisible = ExamineTargetResolver.visible(level, actor, inside);
        require(Math.abs(inside.distance() - 15.5) < 1e-6 && insideVisible
                        && ExaminationPolicy.canExamine(ExaminationPolicy.Examiner.DEFAULT,
                        ExaminationPolicy.State.ALIVE, false, 0, inside.distance(), true, insideVisible, true),
                "live 15.5-block probe is visible and eligible (eye=" + actor.getEyePosition()
                        + ", point=" + inside.point() + ", distance=" + precise(inside.distance())
                        + ", visible=" + insideVisible + ")");

        actor.setPos(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
        var outside = ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(farPos)).orElseThrow();
        boolean outsideVisible = ExamineTargetResolver.visible(level, actor, outside);
        require(Math.abs(outside.distance() - 16.5) < 1e-6 && outsideVisible
                        && !ExaminationPolicy.canExamine(ExaminationPolicy.Examiner.DEFAULT,
                        ExaminationPolicy.State.ALIVE, false, 0, outside.distance(), true, outsideVisible, true),
                "live 16.5-block probe is visible but ineligible (eye=" + actor.getEyePosition()
                        + ", point=" + outside.point() + ", distance=" + precise(outside.distance())
                        + ", visible=" + outsideVisible + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void staleEntityIdentityAndUnloadedRootsFailClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager actor = enrolledHuman(helper, new BlockPos(2, 1, 2));
        Villager targetEntity = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 5));
        ExamineTarget stable = ExamineTarget.entity(targetEntity);
        require(ExamineTargetResolver.resolve(level, actor, stable).isPresent(), "current runtime ID/UUID resolves");
        require(ExamineTargetResolver.resolve(level, actor,
                ExamineTarget.entity(stable.entityId(), UUID.randomUUID())).isEmpty(), "wrong UUID for runtime ID is rejected");
        require(ExamineTargetResolver.resolve(level,
                actor, ExamineTarget.entity(stable.entityId() + 10000, stable.entityUuid())).isEmpty(), "stale runtime ID is rejected");
        targetEntity.discard();
        require(ExamineTargetResolver.resolve(level, actor, stable).isEmpty(), "removed target is rejected");

        BlockPos unloaded = new BlockPos(1_000_000, 32, 1_000_000);
        require(!level.hasChunkAt(unloaded), "test root is outside loaded chunk set");
        require(ExamineTargetResolver.resolve(level, actor, ExamineTarget.block(unloaded)).isEmpty(),
                "unloaded block root is rejected without loading its chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void controllerIncarnationSurvivesStateUpdatesAndChangesOnReplacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager actor = enrolledHuman(helper, new BlockPos(2, 1, 2));
        BlockPos root = helper.absolutePos(new BlockPos(2, 2, 6));
        var target = ExamineTarget.block(root.above());
        BlockState lower = ModBlocks.AIRLOCK.get().defaultBlockState().setValue(AirlockBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity first = level.getBlockEntity(root) instanceof AirlockBlockEntity be ? be : null;
        require(first != null, "airlock root controller is present");
        var initial = ExamineTargetResolver.resolve(level, actor, target).orElseThrow(
                () -> new GameTestAssertException("upper airlock half resolves to a loaded root"));
        require(initial.root().equals(root) && first == initial.blockEntity(), "upper target canonicalizes to lower controller root");
        UUID firstStamp = initial.controllerIncarnation();
        require(firstStamp != null, "server controller receives a transient incarnation stamp");

        level.setBlock(root, lower.setValue(AirlockBlock.OPEN, true), 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(AirlockBlock.OPEN, true), 3);
        var updated = ExamineTargetResolver.resolve(level, actor, target).orElseThrow();
        require(updated.blockEntity() == first && firstStamp.equals(updated.controllerIncarnation()),
                "ordinary state changes retain the same block-entity incarnation");

        level.setBlock(root, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity replacement = level.getBlockEntity(root) instanceof AirlockBlockEntity be ? be : null;
        require(replacement != null && replacement != first, "same-type replacement has a new runtime controller object");
        var replaced = ExamineTargetResolver.resolve(level, actor, target).orElseThrow();
        require(!firstStamp.equals(replaced.controllerIncarnation()), "replacement receives a distinct incarnation");
        require(!ExamineService.matchesControllerIncarnation(firstStamp, replaced.controllerIncarnation()),
                "production stamp gate denies a stale replacement query");
        helper.succeed();
    }

    private static Villager enrolledHuman(GameTestHelper helper, BlockPos local) {
        ServerLevel level = helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, local);
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "villager resolves as enrolled human");
        require(CharacterIdentitySystem.resolveForActor(body).isPresent(), "enrolled human has live resolved data");
        require(com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.ensureProjection(body),
                "human mob-state runtime consumer initializes");
        if (body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) == null)
            body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(MobStateData.DEFAULT));
        require(body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) != null,
                "human fixture has live mob state consumer attachment");
        return body;
    }

    private static void assertRange(ExaminationPolicy.Examiner examiner, ExaminationPolicy.State state,
                                    double distance, boolean expected, String label) {
        require(ExaminationPolicy.canExamine(examiner, state, false, 0, distance, true, true, true) == expected,
                label);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static String precise(double value) {
        return String.format(java.util.Locale.ROOT, "%.16f", value);
    }
}
