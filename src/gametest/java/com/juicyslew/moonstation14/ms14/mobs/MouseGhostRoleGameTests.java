package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.*;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamStorage;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"mouse-ghost-roles"})
public final class MouseGhostRoleGameTests {
    private MouseGhostRoleGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"mouse-appearance"})
    public static void mouseAppearanceUsesResolvedVariantsAndPersists(GameTestHelper helper) {
        var mouse = mouse(helper);
        var profile = CharacterIdentitySystem.resolveForActor(mouse).orElseThrow();
        var allowed = profile.component(MouseAppearanceComponent.class).orElseThrow().variants();
        require(allowed.equals(java.util.List.of(MouseAppearanceComponent.Variant.WHITE,
                MouseAppearanceComponent.Variant.BROWN, MouseAppearanceComponent.Variant.GRAY)),
                "resolved mouse appearance prototype supplies all three coats");
        mouse.tick(); // first authoritative tick performs the one-time selection
        var selected = mouse.appearance();
        require(allowed.contains(selected), "server selection is from resolved allowed prototype variants");
        var normal = com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures.texture(selected, false);
        var dead = com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures.texture(selected, true);
        String color = selected.name().toLowerCase(java.util.Locale.ROOT);
        require(normal.getPath().equals("textures/entity/mouse/mouse_" + color + ".png")
                && dead.getPath().equals("textures/entity/mouse/mouse_" + color + "_dead.png"),
                "synced appearance selector resolves matching normal and dead authored textures");
        var saved = mouse.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var restored = ModEntities.MOUSE.get().create(helper.getLevel());
        require(restored != null, "restored mouse entity created");
        restored.load(saved);
        require(restored.appearance() == selected, "saved appearance round-trips before first restored tick");
        restored.tick();
        require(restored.appearance() == selected, "restored appearance does not reroll on server tick");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void resolvedMouseProfileDrivesLiveBody(GameTestHelper helper) {
        var mouse = mouse(helper);
        require(!mouse.isPersistenceRequired() && mouse.requiresCustomPersistence(),
                "mouse opts out of vanilla distance despawn without forcing saved-persistence semantics");
        var profile = CharacterIdentitySystem.resolveForActor(mouse).orElseThrow();
        require(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse")
                .equals(mouse.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()).characterId()), "mouse enrolled as mouse profile");
        require(profile.component(HandsPrototypeComponent.class).isEmpty()
                && profile.component(HumanoidProfileComponent.class).isEmpty(), "resolved mouse profile has no hands or humanoid profile");
        require(profile.component(InitialBodyComponent.class).orElseThrow().organs().containsKey(com.juicyslew.moonstation14.ms14.organ.OrganCategory.LUNGS), "mouse resolves initial pig lung composition");
        require(mouse.getExistingDataOrNull(ModDataAttachments.BODY.get()) != null, "body reconciliation materializes initial body");
        require(profile.component(BloodstreamComponent.class).orElseThrow().policy().referenceSolution().get("moonstation14:blood") == 50.0,
                "mouse blood profile resolves to 50");
        require(mouse.hasData(ModDataAttachments.BLOODSTREAM.get()), "bloodstream consumer materializes resolved blood solution");
        var movement = CharacterMovementPolicy.fromCharacterData(profile);
        require(movement.walkSpeedPerSecond() == 3.125 && movement.sprintSpeedPerSecond() == 6.25,
                "resolved grounded motor policy is 3.125 walk / 6.25 sprint");
        var thresholds = MobStateSystem.policy(mouse).thresholds().thresholds();
        require(thresholds.containsKey(10d) && thresholds.containsKey(20d),
                "resolved mob-state policy includes typed thresholds 10 and 20");
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        HungerSystem.initializeIfEligible(mouse, level);
        ThirstSystem.initializeIfEligible(mouse, level);
        var hunger = mouse.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        var thirst = mouse.getExistingDataOrNull(ModDataAttachments.THIRST.get());
        require(hunger != null && hunger.hunger() == 25.0f,
                "live hunger consumer preserves resolved mouse initial hunger of 25");
        require(thirst != null && thirst.thirst() == 25.0f,
                "live thirst consumer preserves resolved mouse initial thirst of 25");
        require(StomachSystem.isEligible(mouse), "live stomach consumer enrolls mouse");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"nutrition", "movement"})
    public static void nutritionModifiersReachResolvedMouseMovement(GameTestHelper helper) {
        var mouse = mouse(helper);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        HungerSystem.initializeIfEligible(mouse, level);
        ThirstSystem.initializeIfEligible(mouse, level);
        mouse.setData(ModDataAttachments.HUNGER.get(), new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(0f)));
        mouse.setData(ModDataAttachments.THIRST.get(), new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(0f)));
        HungerSystem.reconcile(mouse, level);
        ThirstSystem.reconcile(mouse, level);
        require(HungerSystem.isStarving(mouse) && ThirstSystem.isParched(mouse), "resolved mouse nutrition statuses are active");
        var attribute = mouse.getAttribute(Attributes.MOVEMENT_SPEED);
        require(attribute != null && attribute.getModifier(HungerSystem.STARVING_MODIFIER_ID) != null
                && attribute.getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "both existing nutrition attribute projections are present");
        require(attribute.getModifier(HungerSystem.STARVING_MODIFIER_ID).amount()
                        == com.juicyslew.moonstation14.ms14.movement.NutritionMovementFactor.ATTRIBUTE_MODIFIER_AMOUNT
                && attribute.getModifier(ThirstSystem.PARCHED_MODIFIER_ID).amount()
                        == com.juicyslew.moonstation14.ms14.movement.NutritionMovementFactor.ATTRIBUTE_MODIFIER_AMOUNT,
                "native movement attribute receives each nutrition modifier once");
        double factor = com.juicyslew.moonstation14.ms14.movement.NutritionMovementFactor.forActor(mouse, 1d);
        var policy = CharacterMovementPolicy.fromCharacterData(CharacterIdentitySystem.resolveForActor(mouse).orElseThrow());
        require(Math.abs(policy.walkSpeedPerSecond() * factor - 1.7578125d) < 1e-9
                && Math.abs(policy.sprintSpeedPerSecond() * factor - 3.515625d) < 1e-9,
                "stacked snapshot factor slows resolved mouse grounded walk and sprint speeds");
        var environment = new com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment(.05d, 0d, 0d, 1d,
                com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO, 0d, 0d,
                (position, requested, grounded) -> new com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver.CollisionResult(requested, false),
                1d, factor);
        var state = new com.juicyslew.moonstation14.ms14.movement.CharacterMovementState(
                com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO,
                com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO, false);
        var motor = new com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor(policy);
        var predictedWalk = motor.tick(state, 0, 1000, false, false, 0d, environment, false);
        var predictedSprint = motor.tick(state, 0, 1000, false, true, 0d, environment, false);
        require(Math.abs(predictedWalk.velocity().z() - 1.7578125d) < 1e-9
                && Math.abs(predictedSprint.velocity().z() - 3.515625d) < 1e-9,
                "snapshot voluntary factor reaches the grounded predicted motor without double-applying attributes");
        mouse.setData(ModDataAttachments.HUNGER.get(), new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(25f)));
        mouse.setData(ModDataAttachments.THIRST.get(), new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(25f)));
        HungerSystem.reconcile(mouse, level);
        ThirstSystem.reconcile(mouse, level);
        require(!HungerSystem.isStarving(mouse) && !ThirstSystem.isParched(mouse)
                && attribute.getModifier(HungerSystem.STARVING_MODIFIER_ID) == null
                && attribute.getModifier(ThirstSystem.PARCHED_MODIFIER_ID) == null,
                "feeding and drinking restore status and remove both speed modifiers");
        require(com.juicyslew.moonstation14.ms14.movement.NutritionMovementFactor.forActor(mouse, 1d) == 1d,
                "recovered body snapshot speed factor returns to baseline");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void typedBloodlossKillsUnownedMouseAsNativeCorpse(GameTestHelper helper) {
        var mouse = mouse(helper);
        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLOODLOSS, 100f), 1f, true)
                == DamageSystem.Result.APPLIED, "typed bloodloss applied to native mouse");
        require(MobStateSystem.isDead(mouse) && mouse.isDeadOrDying(),
                "lethal typed damage transitions unowned mouse to Dead and native death");
        require(!mouse.isRemoved() && helper.getLevel().getEntity(mouse.getUUID()) == mouse,
                "dead native mouse remains registered as corpse, not a damage carrier");
        helper.succeed();
    }

    private static com.juicyslew.moonstation14.entities.MouseMob mouse(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var mouse = ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "registered native mouse created");
        mouse.setNoAi(true);
        mouse.setNoGravity(true);
        mouse.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(mouse), "native mouse added to world");
        CharacterIdentitySystem.enrollSupportedActor(mouse, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        require(CharacterIdentitySystem.resolveForActor(mouse).isPresent(), "spawn enrolled mouse has resolved actor profile");
        require(BodySystem.reconcile(mouse), "mouse physiology reconciles");
        require(BloodstreamStorage.reconcile(mouse), "mouse bloodstream reconciles from resolved profile");
        return mouse;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
