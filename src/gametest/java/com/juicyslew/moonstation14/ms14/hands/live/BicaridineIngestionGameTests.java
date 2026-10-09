package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

@GameTestTags({"timed-interaction", "ingestion", "chemistry", "damage", "physiology"})
@PrefixGameTestTemplate(false)
public final class BicaridineIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> BICARIDINE =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "bicaridine"));

    private BicaridineIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 260)
    @GameTestTags("suite-stabilization")
    public static void heldBicaridineDoseMetabolizesAndHealsBruteDamage(GameTestHelper helper) {
        Villager treated = body(helper, new BlockPos(2, 1, 2));
        Villager control = body(helper, new BlockPos(4, 1, 2));
        damage(treated);
        damage(control);
        long startGameTime = helper.getLevel().getGameTime();
        int treatedStartTick = treated.tickCount, controlStartTick = control.tickCount;
        float treatedInitial = blunt(treated);
        float controlInitial = blunt(control);

        LiveHands hands = treated.getData(ModDataAttachments.LIVE_HANDS.get());
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(BICARIDINE, 5f)));
        var inserted = hands.putWhole(hands.revision(), hands.activeHand(), new ItemToken("bicaridine-dose"), bottle);
        require(inserted.succeeded(), "filled bicaridine bottle held");
        treated.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        String token = inserted.state().token(inserted.state().activeHand()).orElseThrow();
        require(BodyTimedIngestionService.startHeldOwned(treated, inserted.state().activeHand(), token,
                inserted.state().revision(), 1, () -> true).name().equals("OK"), "production held timed ingestion starts");
        require(Math.abs(blunt(treated) - treatedInitial) < .001f, "starting drink does not heal instantly");

        helper.runAfterDelay(15, () -> {
            var remaining = treated.getData(ModDataAttachments.LIVE_HANDS.get()).stackCopy(inserted.state().activeHand()).orElseThrow()
                    .get(ModDataComponents.REAGENT.get());
            require(remaining != null && Math.abs(remaining.contents().getOrDefault(BICARIDINE, 0f) - 5f) < .001f,
                    "5-unit source dose remains in bottle before ingestion deadline");
            require(stomach(treated).isEmpty(), "dose has not reached stomach before ingestion deadline");
            require(Math.abs(blunt(treated) - blunt(control)) < .001f, "no immediate healing before scheduled metabolism");
        });
        helper.runAfterDelay(23, () -> {
            var remaining = treated.getData(ModDataAttachments.LIVE_HANDS.get()).stackCopy(inserted.state().activeHand()).orElseThrow()
                    .get(ModDataComponents.REAGENT.get());
            require(remaining != null && remaining.contents().isEmpty(), "source bottle is retained and emptied after ingestion");
            require(stomach(treated).getOrDefault(BICARIDINE, 0f) > 0f, "bicaridine admitted to stomach after ingestion deadline");
        });
        helper.runAfterDelay(220, () -> {
            float treatedDamage = blunt(treated);
            float controlDamage = blunt(control);
            require(treatedDamage < treatedInitial - .01f, "scheduled oral metabolism reduces actual blunt damage: "
                    + "startTime=" + startGameTime + ", now=" + helper.getLevel().getGameTime()
                    + ", ticks=" + treatedStartTick + "->" + treated.tickCount + "/"
                    + controlStartTick + "->" + control.tickCount + ", treated=" + treated.position()
                    + ", removed=" + treated.isRemoved() + ", noGravity=" + treated.isNoGravity()
                    + ", entityTicking=" + helper.getLevel().isPositionEntityTicking(treated.blockPosition())
                    + ", damage=" + treatedDamage + "/" + treatedInitial + ", control=" + controlDamage);
            require(treatedDamage < controlDamage - .01f, "bicaridine body heals more than matched untreated body");
            require(treatedDamage >= 0 && controlDamage <= controlInitial + .01f, "damage ledger remains valid under natural recovery");
            helper.succeed();
        });
    }

    private static Villager body(GameTestHelper helper, BlockPos local) {
        BlockPos pos = helper.absolutePos(local);
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "human body entity created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(body) && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID), "body enrolled as human");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body physiology and hands initialized");
        return body;
    }

    private static void damage(Villager body) {
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 20f), 1f, true) == DamageSystem.Result.APPLIED,
                "known typed blunt damage applied");
        require(blunt(body) >= 19.9f, "initial blunt ledger records accepted damage");
    }

    private static float blunt(Villager body) {
        DamageData data = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return data == null ? 0f : data.getMap().getOrDefault(DamageKeys.BLUNT, 0f);
    }

    private static Map<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Float> stomach(Villager body) {
        ReagentAttachment state = body.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
        return state == null ? Map.of() : state.getMap();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
