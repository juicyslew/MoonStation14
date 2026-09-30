package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.lung.LungAttachment;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.juicyslew.moonstation14.ms14.organ.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyLungMigrationGameTests {
    private LegacyLungMigrationGameTests() { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedLegacyLungMigratesOnlyOnce(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            for (LungComponent state : new LungComponent[] {
                    LungComponent.from(GasMixture.vacuum(), 0.25, true, LungComponent.Phase.EXHALING),
                    LungComponent.from(new GasMixture(Map.of(GasType.OXYGEN, 0.75), 300), 1.5,
                            true, LungComponent.Phase.INHALING) }) {
                pig.removeData(ModDataAttachments.BODY.get());
                pig.setData(ModDataAttachments.LUNG.get(), new LungAttachment(state));
                CompoundTag saved = pig.saveWithoutId(new CompoundTag());
                check(saved.toString().contains("moonstation14:lung"), "old attachment id must persist");
                pig.removeData(ModDataAttachments.LUNG.get());
                pig.load(saved);
                check(pig.hasData(ModDataAttachments.LUNG.get()) && !pig.hasData(ModDataAttachments.BODY.get()),
                        "old NBT must decode without creating BODY");
                check(BodySystem.reconcile(pig), "legacy conversion must succeed");
                BodyState body = BodySystem.current(pig).orElseThrow();
                check(body.find(OrganCategory.LUNGS).orElseThrow().lung().equals(LungOrganState.from(state))
                                && body.respiration().equals(RespirationState.from(state))
                                && !pig.hasData(ModDataAttachments.LUNG.get()), "gas, phase and status transfer once");
                check(BodySystem.reconcile(pig) && body.equals(BodySystem.current(pig).orElseThrow()),
                        "repeat must not duplicate organs");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleLegacyNeverOverwritesEmptyBodyOrInvalidActor(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            LungAttachment legacy = new LungAttachment(LungComponent.from(GasMixture.vacuum(), 3, true));
            pig.setData(ModDataAttachments.LUNG.get(), legacy);
            pig.setData(ModDataAttachments.BODY.get(), new BodyAttachment(BodyState.EMPTY));
            check(BodySystem.reconcile(pig) && BodySystem.current(pig).orElseThrow().equals(BodyState.EMPTY)
                            && pig.hasData(ModDataAttachments.LUNG.get()), "empty BODY remains authoritative; stale evidence retained");
            pig.removeData(ModDataAttachments.BODY.get());
            CharacterIdentityAttachment wrong = new CharacterIdentityAttachment();
            wrong.bind(ModCharacters.HUMAN_ID);
            pig.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), wrong);
            check(!BodySystem.reconcile(pig) && !pig.hasData(ModDataAttachments.BODY.get())
                            && pig.hasData(ModDataAttachments.LUNG.get()), "wrong host cannot claim human lungs");
            pig.removeData(ModDataAttachments.CHARACTER_IDENTITY.get());
            check(!BodySystem.reconcile(pig) && !pig.hasData(ModDataAttachments.BODY.get())
                            && pig.hasData(ModDataAttachments.LUNG.get()), "unbound actor must retain legacy lungs");
            zombie.setData(ModDataAttachments.LUNG.get(), legacy);
            check(!BodySystem.reconcile(zombie) && !zombie.hasData(ModDataAttachments.BODY.get())
                            && zombie.hasData(ModDataAttachments.LUNG.get()),
                    "unmapped host cannot infer a human from old lung presence");
            var malformed = com.google.gson.JsonParser.parseString("{\"gas_moles\":{\"unknown\":1},\"temperature_kelvin\":300,\"saturation\":1,\"initialized\":true}");
            check(LungAttachment.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent(),
                    "invalid legacy gas must be rejected");
            helper.succeed();
        });
    }
}
