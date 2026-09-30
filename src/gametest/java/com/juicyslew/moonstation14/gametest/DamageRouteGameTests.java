package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import java.util.Map;
import java.util.UUID;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DamageRouteGameTests {
    private static final ResourceKey<ReagentData> POLYTRINIC_ACID = ModReagents.createKey("polytrinicacid");

    private DamageRouteGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creativePlayerRejectsBothReagentDamageSourcesWithoutCommittingLedger(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player creative = player(helper, "creative-reagent-route", true);
        creative.setHealth(19f);
        DamageData before = new DamageData(new DamageMap(Map.of(DamageKeys.SLASH, 5f)));
        creative.setData(ModDataAttachments.DAMAGE.get(), before);
        float healthBefore = creative.getHealth();
        require(creative.isCreative() && creative.getAbilities().invulnerable,
                "fixture must have vanilla Creative invulnerability");
        creative.invulnerableTime = 0;
        require(!creative.hurt(level.damageSources().generic(), 2f),
                "vanilla generic damage must be rejected by the Creative Player.hurt path");
        var afterGeneric = creative.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(creative.getHealth() == healthBefore
                        && afterGeneric != null && before.getMap().equals(afterGeneric.getMap()),
                "rejected generic hit must not change health or the pre-existing ledger");

        for (String id : new String[]{"reagent", "reagent_bypass"}) {
            // Do not let a previous hit's vanilla damage cooldown explain the rejection.
            creative.invulnerableTime = 0;
            require(!creative.hurt(reagentSource(level, id), 2f),
                    id + " must be rejected by the Creative Player.hurt path");
            creative.invulnerableTime = 0;
            require(DamageSystem.applyHealthChange(creative, Map.of(DamageKeys.BLUNT, 10f), 1f,
                            id.equals("reagent_bypass")) == DamageSystem.Result.APPLIED,
                    id + " must be handled even when vanilla rejects the hit");
            var after = creative.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(creative.getHealth() == healthBefore && after != null && after.getMap().equals(before.getMap()),
                    id + " rejected hit must leave both health and pre-existing injury untouched: health="
                            + creative.getHealth() + ", ledger=" + (after == null ? "null" : after.getMap()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void survivalPlayersStillTakeReagentDamageAndBypassIgnoresResistance(GameTestHelper helper) {
        Player resistible = player(helper, "survival-reagent-route", false);
        Player bypass = player(helper, "survival-bypass-route", false);
        resistible.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        bypass.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        require(!resistible.isInvulnerableTo(reagentSource((ServerLevel) helper.getLevel(), "reagent"))
                        && !bypass.isInvulnerableTo(reagentSource((ServerLevel) helper.getLevel(), "reagent_bypass")),
                "Survival fixtures must accept both reagent sources");
        require(DamageSystem.applyHealthChange(resistible, Map.of(DamageKeys.BLUNT, 10f), 1f, false)
                        == DamageSystem.Result.APPLIED, "resistible player hit must be handled");
        require(DamageSystem.applyHealthChange(bypass, Map.of(DamageKeys.BLUNT, 10f), 1f, true)
                        == DamageSystem.Result.APPLIED, "bypass player hit must be handled");
        var reduced = resistible.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        var full = bypass.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float reducedBlunt = reduced == null ? 0f : reduced.getMap().getOrDefault(DamageKeys.BLUNT, 0f);
        require(reducedBlunt > 0f && reducedBlunt < 10f
                        && Math.abs(resistible.getHealth() - (20f - reducedBlunt / 5f)) < .001f,
                "Survival reagent must commit the resisted typed health loss");
        require(full != null && full.getMap().equals(Map.of(DamageKeys.BLUNT, 10f))
                        && bypass.getHealth() == 18f,
                "Survival bypass must ignore Resistance but still commit through vanilla hurt");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaPlayerFallDamageEntersBluntLedger(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player player = new Player(level, helper.absolutePos(new BlockPos(1, 1, 1)), 0f,
                new GameProfile(UUID.randomUUID(), "fall-damage-route-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        player.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(),
                helper.absolutePos(new BlockPos(1, 1, 1)).getY(), helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        // Seed a consistent pre-existing ledger without the hurt-resistance window that a
        // preceding damage hit would create on a Player.
        player.setHealth(player.getMaxHealth() - 1f);
        player.setData(ModDataAttachments.DAMAGE.get(), new DamageData(new DamageMap(Map.of(DamageKeys.SLASH, 5f))));
        float before = player.getHealth();

        require(player.hurt(level.damageSources().fall(), 5f), "vanilla player fall damage must be accepted");

        var ledger = player.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(player.getHealth() < before, "vanilla player health must fall");
        float expectedBlunt = (before - player.getHealth()) * DamageSystem.TYPED_PER_HEALTH;
        require(ledger != null && Math.abs(ledger.getMap().getOrDefault(DamageKeys.BLUNT, 0f) - expectedBlunt) < .001f
                        && ledger.getMap().getOrDefault(DamageKeys.SLASH, 0f) == 5f,
                "fall damage must mirror vanilla's actual player health delta as blunt and preserve prior typed damage: "
                        + (ledger == null ? "null" : ledger.getMap()) + " health " + before + " -> " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bottlePolytrinicAcidUsesStomachThenBloodstreamRoute(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.getAttribute(Attributes.ARMOR).setBaseValue(0f);
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(POLYTRINIC_ACID, 5f)));
        // Exercise the same provider-backed source/destination transfer as the registered bottle callback.
        require(StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), villager, level, 5f) == 5f,
                "five-unit bottle dose must be admitted");
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().isEmpty(), "bottle source must be consumed");
        require(MS14Provider.get(villager, MS14Bridges.STOMACH).getMap().get(POLYTRINIC_ACID) == 5f,
                "ingestion must place the full dose in stomach");
        require(MS14Provider.get(villager, MS14Bridges.BLOODSTREAM).getMap().get(POLYTRINIC_ACID) == null,
                "ingestion must not bypass stomach into bloodstream");
        float initialHealth = villager.getHealth();
        require(!villager.hasData(ModDataAttachments.DAMAGE.get()), "no immediate typed damage before metabolism");

        EntityActivitySystem.update(villager, EntityActivity.REAGENT_METABOLISM, true);
        Set<EntityActivity> metabolism = Set.of(EntityActivity.REAGENT_METABOLISM);
        TickHooks.runDueActivities(villager, level, metabolism);
        require(Math.abs(MS14Provider.get(villager, MS14Bridges.STOMACH).getMap().get(POLYTRINIC_ACID) - 4.75f) < .000001f,
                "first metabolism pass transfers 25 stomach cents");
        require(Math.abs(MS14Provider.get(villager, MS14Bridges.BLOODSTREAM).getMap().get(POLYTRINIC_ACID) - .12f) < .000001f,
                "25 stomach cents transfer as 12 body cents");
        require(villager.getHealth() == initialHealth && !villager.hasData(ModDataAttachments.DAMAGE.get()),
                "stomach transfer occurs after body metabolism, so damage waits for another pass");

        TickHooks.runDueActivities(villager, level, metabolism);
        float healthAfterSecondPass = villager.getHealth();
        var damage = villager.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().getOrDefault(DamageKeys.CAUSTIC, 0f) > 0f,
                "second pass must deliver typed caustic damage: " + (damage == null ? "null" : damage.getMap()));
        require(healthAfterSecondPass < initialHealth && initialHealth - healthAfterSecondPass < .1f,
                "low-dose damage must reduce vanilla health subtly, not be scaled up: "
                        + initialHealth + " -> " + healthAfterSecondPass);
        require(damage.getMap().size() == 1 && damage.getMap().containsKey(DamageKeys.CAUSTIC),
                "bloodstream HealthChange must use caustic cause, not an unsupported vanilla fallback");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void threeBodyUnitsApplyConfiguredPolytrinicAcidDose(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.getAttribute(Attributes.ARMOR).setBaseValue(0f);
        MS14Provider.update(villager, MS14Bridges.BLOODSTREAM,
                new ReagentAttachment(Map.of(POLYTRINIC_ACID, 3f)));
        EntityActivitySystem.update(villager, EntityActivity.REAGENT_METABOLISM, true);
        float healthBefore = villager.getHealth();

        TickHooks.runDueActivities(villager, level, Set.of(EntityActivity.REAGENT_METABOLISM));

        var damage = villager.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && Math.abs(damage.getMap().getOrDefault(DamageKeys.CAUSTIC, 0f) - 11f) < .0001f,
                "three units at rate three apply configured 11 caustic each at scale one: "
                        + (damage == null ? "null" : damage.getMap()));
        require(Math.abs((healthBefore - villager.getHealth()) - 2.2f) < .001f,
                "11 typed units project to 2.2 vanilla health damage");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static Player player(GameTestHelper helper, String name, boolean creative) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Player player = new Player(level, helper.absolutePos(new BlockPos(1, 1, 1)), 0f,
                new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isCreative() { return creative; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().invulnerable = creative;
        return player;
    }

    private static DamageSource reagentSource(ServerLevel level, String id) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, id));
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }
}
