package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.interaction.ComplexInteractionAttachment;
import com.juicyslew.moonstation14.ms14.interaction.ComplexInteractionSystem;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ComplexInteractionGameTests {
    private ComplexInteractionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void incidentalDefaultAndWrongHostCannotGrant(GameTestHelper helper) {
        var human = EntityType.VILLAGER.create(helper.getLevel());
        require(human != null, "villager can be constructed");
        var defaultState = human.getData(ModDataAttachments.COMPLEX_INTERACTION.get());
        require(!defaultState.initialized() && !defaultState.enabled(), "getData creates only an incidental default");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        human.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(human), "villager joins level");
        require(CharacterIdentitySystem.enroll(human, helper.getLevel(), ModCharacters.HUMAN_ID),
                "valid human host enrolls despite preexisting default");
        require(ComplexInteractionSystem.enabled(human), "first grant is not stranded");

        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        require(!CharacterIdentitySystem.enroll(pig, helper.getLevel(), ModCharacters.HUMAN_ID),
                "pig cannot request human identity");
        var spoof = new CharacterIdentityAttachment();
        spoof.bind(ModCharacters.HUMAN_ID);
        pig.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), spoof);
        require(!CharacterIdentitySystem.enroll(pig, helper.getLevel(), ModCharacters.HUMAN_ID),
                "preexisting wrong-host identity cannot bootstrap");
        require(!ComplexInteractionSystem.enabled(pig) && !ComplexInteractionSystem.setEnabled(pig, true),
                "wrong-host identity cannot access runtime capability");

        var ambiguous = EntityType.VILLAGER.create(helper.getLevel());
        require(ambiguous != null, "second villager can be constructed");
        ambiguous.setData(ModDataAttachments.COMPLEX_INTERACTION.get(), new ComplexInteractionAttachment(true, true));
        BlockPos otherPos = helper.absolutePos(new BlockPos(3, 1, 1));
        ambiguous.setPos(otherPos.getX() + .5, otherPos.getY(), otherPos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(ambiguous), "ambiguous villager joins level");
        require(CharacterIdentitySystem.enroll(ambiguous, helper.getLevel(), ModCharacters.HUMAN_ID),
                "valid host can enroll without blessing ambiguous attachment");
        require(!ComplexInteractionSystem.enabled(ambiguous)
                && !ComplexInteractionSystem.setEnabled(ambiguous, true),
                "initialized attachment without marker fails closed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualEntitySaveLoadKeepsRevokeAndRemoval(GameTestHelper helper) {
        var revoked = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var removed = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(ComplexInteractionSystem.enabled(revoked) && ComplexInteractionSystem.enabled(removed),
                    "villagers enrolled before save");
            require(ComplexInteractionSystem.setEnabled(revoked, false), "revoke succeeds");
            removed.removeData(ModDataAttachments.COMPLEX_INTERACTION.get());
            CompoundTag revokedSave = revoked.saveWithoutId(new CompoundTag());
            CompoundTag removedSave = removed.saveWithoutId(new CompoundTag());
            revoked.discard();
            removed.discard();
            var reloadedRevoke = EntityType.VILLAGER.create(helper.getLevel());
            var reloadedRemoval = EntityType.VILLAGER.create(helper.getLevel());
            require(reloadedRevoke != null && reloadedRemoval != null, "villagers construct for load");
            reloadedRevoke.load(revokedSave);
            reloadedRemoval.load(removedSave);
            require(helper.getLevel().addFreshEntity(reloadedRevoke)
                    && helper.getLevel().addFreshEntity(reloadedRemoval), "loaded bodies rejoin level");
            CharacterIdentitySystem.enrollSupportedActor(reloadedRevoke, helper.getLevel());
            CharacterIdentitySystem.enrollSupportedActor(reloadedRemoval, helper.getLevel());
            var state = reloadedRevoke.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
            require(state != null && state.initialized() && !state.enabled(), "disabled tombstone persisted");
            require(!ComplexInteractionSystem.enabled(reloadedRevoke)
                    && !ComplexInteractionSystem.enabled(reloadedRemoval)
                    && !reloadedRemoval.hasData(ModDataAttachments.COMPLEX_INTERACTION.get()),
                    "rejoin never regrants revoked or removed state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void initialGrantTombstoneAndRemovedAttachmentNeverRebootstrap(GameTestHelper helper) {
        var human = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        var cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(ComplexInteractionSystem.enabled(human), "human receives initial runtime grant");
            require(!ComplexInteractionSystem.enabled(pig), "pig has disabled tombstone");
            require(!ComplexInteractionSystem.enabled(cow)
                    && !cow.hasData(ModDataAttachments.COMPLEX_INTERACTION.get()), "unbound read stays absent");
            var disabled = pig.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
            require(disabled != null && disabled.initialized() && !disabled.enabled(), "pig absence is persisted explicitly");

            require(ComplexInteractionSystem.setEnabled(human, false), "server revoke succeeds");
            var revoked = human.getExistingDataOrNull(ModDataAttachments.COMPLEX_INTERACTION.get());
            var roundTrip = ComplexInteractionAttachment.CODEC.parse(JsonOps.INSTANCE,
                    ComplexInteractionAttachment.CODEC.encodeStart(JsonOps.INSTANCE, revoked).getOrThrow()).getOrThrow();
            human.setData(ModDataAttachments.COMPLEX_INTERACTION.get(), roundTrip);
            CharacterIdentitySystem.enrollSupportedActor(human, helper.getLevel());
            require(!ComplexInteractionSystem.enabled(human), "serialized revoke survives re-enrollment");
            human.removeData(ModDataAttachments.COMPLEX_INTERACTION.get());
            CharacterIdentitySystem.enrollSupportedActor(human, helper.getLevel());
            require(!ComplexInteractionSystem.enabled(human)
                    && !human.hasData(ModDataAttachments.COMPLEX_INTERACTION.get()), "removal cannot rebootstrap");
            require(ComplexInteractionSystem.setEnabled(human, true), "explicit server grant can restore capability");
            require(ComplexInteractionSystem.enabled(human), "restored attachment is authoritative");
            var identity = human.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
            var dangling = new CharacterIdentityAttachment();
            dangling.bind(net.minecraft.resources.ResourceLocation.parse("test:unresolved"));
            human.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), dangling);
            require(!ComplexInteractionSystem.enabled(human)
                    && !ComplexInteractionSystem.setEnabled(human, true), "unresolved identity cannot use capability");
            human.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), identity);
            human.getPersistentData().putString("moonstation14:complex_interaction_initialized", "corrupt");
            require(!ComplexInteractionSystem.enabled(human)
                    && !ComplexInteractionSystem.setEnabled(human, true), "malformed marker fails closed");
            CharacterIdentitySystem.enrollSupportedActor(human, helper.getLevel());
            require(!ComplexInteractionSystem.enabled(human), "malformed persisted marker cannot regrant");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
