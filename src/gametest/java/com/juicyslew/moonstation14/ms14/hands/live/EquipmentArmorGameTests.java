package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generic temporary-item consumer fixture; this is not melon acquisition or binding evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"equipment-armor", "damage"})
public final class EquipmentArmorGameTests {
    private EquipmentArmorGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"equipment-noslip"})
    public static void liveWornFeetNoSlipQuery(GameTestHelper helper) {
        var bodies = new java.util.ArrayList<Villager>();
        helper.runAfterDelay(1, () -> {
            var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog();
            Map<net.minecraft.resources.ResourceLocation, EquipmentItemData> prior = new LinkedHashMap<>(catalog.asMap());
            try {
                var updated = new LinkedHashMap<>(prior);
                var marker = Optional.of(new EquipmentItemData.NoSlip());
                var shoesId = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("black_shoes");
                var shoes = prior.get(shoesId);
                require(shoes != null, "registered footwear prototype exists");
                updated.put(shoesId, new EquipmentItemData(shoes.item(), shoes.clothing(), shoes.allowSuitStorage(), shoes.tags(), shoes.armor(), marker));
                var capId = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("grey_cap");
                var cap = prior.get(capId);
                updated.put(capId, new EquipmentItemData(cap.item(), cap.clothing(), cap.allowSuitStorage(), cap.tags(), cap.armor(), marker));
                var helmetId = net.minecraft.resources.ResourceLocation.parse("minecraft:leather_helmet");
                var helmetClothing = new EquipmentItemData.Clothing(List.of(
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.FEET,
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET),
                        EquipmentItemData.AppearanceRegion.FEET, -1, 0, Optional.empty(), Optional.empty());
                updated.put(helmetId, new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.of(helmetClothing),
                        Optional.empty(), List.of(), Optional.empty(), marker));
                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, updated);

                Villager worn = body(helper, new BlockPos(1, 1, 1), bodies);
                putInHand(worn, "shoes", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get()));
                var state = worn.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(BodyEquipmentTransfer.transferOwned(worn, "left", "shoes", "shoes", state.revision(), true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "real transfer equips NoSlip shoes into FEET slot");
                require(com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(worn),
                        "actual worn shoes resolve NoSlip from live declared FEET slot");

                Villager held = body(helper, new BlockPos(3, 1, 1), bodies);
                putInHand(held, "shoes", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get()));
                require(!com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(held), "held shoes do not protect");

                Villager head = body(helper, new BlockPos(5, 1, 1), bodies);
                putCapInHand(head);
                state = head.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(BodyEquipmentTransfer.transferOwned(head, "left", "head", "cap", state.revision(), true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "marker cap transfers to head");
                require(!com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(head), "head marker does not protect");

                Villager pocket = body(helper, new BlockPos(7, 1, 1), bodies);
                putInHand(pocket, "jumpsuit", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_JUMPSUIT.get()));
                state = pocket.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(BodyEquipmentTransfer.transferOwned(pocket, "left", "jumpsuit", "jumpsuit", state.revision(), true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "jumpsuit equips for dependent pocket");
                putInHand(pocket, "helmet", new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
                state = pocket.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(BodyEquipmentTransfer.transferOwned(pocket, "left", "pocket1", "helmet", state.revision(), true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "marker surrogate transfers into pocket");
                require(!com.juicyslew.moonstation14.ms14.equipment.EquipmentNoSlipPolicy.hasWornFeetNoSlip(pocket), "pocket marker does not protect despite FEET item flag");
                helper.succeed();
            } finally {
                try { com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, prior); }
                finally { bodies.forEach(Villager::discard); }
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void wornArmorTypedNativeHeldAndBypassDamage(GameTestHelper helper) {
        var bodies = new java.util.ArrayList<Villager>();
        helper.runAfterDelay(1, () -> {
            var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog();
            Map<net.minecraft.resources.ResourceLocation, EquipmentItemData> prior = new LinkedHashMap<>(catalog.asMap());
            try {
            var id = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("grey_cap");
            EquipmentItemData base = prior.get(id);
            require(base != null, "registered grey cap prototype exists");
            var temporary = new EquipmentItemData(base.item(), base.clothing(), base.allowSuitStorage(), base.tags(),
                    Optional.of(EquipmentItemData.Armor.fromCoefficients(Map.of(DamageKeys.BLUNT, .95f, DamageKeys.CAUSTIC, .95f))));
            var updated = new LinkedHashMap<>(prior); updated.put(id, temporary);
            var clothing = base.clothing().orElseThrow();
            var pocketCompatibleClothing = new EquipmentItemData.Clothing(
                    List.of(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.HEAD,
                            com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET),
                    clothing.region(), clothing.color(), clothing.equipDelay(), clothing.equipSound(), clothing.unequipSound(),
                    clothing.quickEquip(), clothing.wornModel(), clothing.stripDelay(), clothing.quickEquipPreferredSlotFlags());
            var pocketSurrogate = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.of(pocketCompatibleClothing),
                    Optional.empty(), List.of(), Optional.of(EquipmentItemData.Armor.fromCoefficients(Map.of(DamageKeys.BLUNT, .95f))));
            var vanillaHelmet = net.minecraft.resources.ResourceLocation.parse("minecraft:leather_helmet");
            updated.put(vanillaHelmet, pocketSurrogate);
            com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                    .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, updated);
                Villager worn = body(helper, new BlockPos(1, 1, 1), bodies);
                equipCap(worn);
                require(DamageSystem.applyHealthChange(worn, Map.of(DamageKeys.BLUNT, 5f), 1f, false)
                        == DamageSystem.Result.APPLIED, "typed damage accepted");
                requireClose(4.75f, typed(worn, DamageKeys.BLUNT), "worn typed armor applies once");
                requireClose(19.05f, worn.getHealth(), "worn typed armor projects matching health");

                Villager nativeHit = body(helper, new BlockPos(3, 1, 1), bodies);
                equipCap(nativeHit);
                require(nativeHit.hurt(nativeHit.level().damageSources().generic(), 1f), "native hit accepted");
                requireClose(4.75f, typed(nativeHit, DamageKeys.BLUNT), "native incoming armor maps through typed ledger once");
                requireClose(19.05f, nativeHit.getHealth(), "native vanilla health stays in agreement");

                Villager held = body(helper, new BlockPos(5, 1, 1), bodies);
                putCapInHand(held);
                DamageSystem.applyHealthChange(held, Map.of(DamageKeys.BLUNT, 5f), 1f, false);
                requireClose(5f, typed(held, DamageKeys.BLUNT), "held cap is not worn armor");

                Villager pocketed = body(helper, new BlockPos(11, 1, 1), bodies);
                putInHand(pocketed, "jumpsuit", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_JUMPSUIT.get()));
                var pocketState = pocketed.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                long jumpsuitRevision = pocketState.revision();
                require(BodyEquipmentTransfer.transferOwned(pocketed, "left", "jumpsuit", "jumpsuit", jumpsuitRevision, true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "real jumpsuit equip admits dependent pocket");
                assertTransferred(pocketed, "jumpsuit", "jumpsuit", jumpsuitRevision + 1,
                        com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_JUMPSUIT.get());
                putInHand(pocketed, "helmet", new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
                pocketState = pocketed.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                long helmetRevision = pocketState.revision();
                require(BodyEquipmentTransfer.transferOwned(pocketed, "left", "pocket1", "helmet", helmetRevision, true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "pocket-compatible temporary helmet enters pocket through transfer owner");
                assertTransferred(pocketed, "pocket1", "helmet", helmetRevision + 1, net.minecraft.world.item.Items.LEATHER_HELMET);
                DamageSystem.applyHealthChange(pocketed, Map.of(DamageKeys.BLUNT, 5f), 1f, false);
                requireClose(5f, typed(pocketed, DamageKeys.BLUNT), "pocket-only worn item does not relay armor");

                Villager bypass = body(helper, new BlockPos(7, 1, 1), bodies);
                equipCap(bypass);
                DamageSystem.applyHealthChange(bypass, Map.of(DamageKeys.BLUNT, 5f), 1f, true);
                requireClose(5f, typed(bypass, DamageKeys.BLUNT), "ignoreResistances bypasses equipment armor");

                var zeroBlunt = new EquipmentItemData(base.item(), base.clothing(), base.allowSuitStorage(), base.tags(),
                        Optional.of(EquipmentItemData.Armor.fromCoefficients(Map.of(DamageKeys.BLUNT, 0f, DamageKeys.CAUSTIC, .95f))));
                var zeroCatalog = new LinkedHashMap<>(updated); zeroCatalog.put(id, zeroBlunt);
                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, zeroCatalog);
                Villager mixed = body(helper, new BlockPos(9, 1, 1), bodies);
                equipCap(mixed);
                DamageSystem.applyHealthChange(mixed, Map.of(DamageKeys.BLUNT, 10f, DamageKeys.CAUSTIC, 10f), 1f, true);
                DamageSystem.applyHealthChange(mixed, Map.of(DamageKeys.BLUNT, 5f, DamageKeys.CAUSTIC, -5f), 1f, false);
                requireClose(10f, typed(mixed, DamageKeys.BLUNT), "zeroed positive type does not add damage");
                requireClose(5f, typed(mixed, DamageKeys.CAUSTIC), "mixed negative healing survives zeroed positive armor");
                requireClose(17f, mixed.getHealth(), "mixed healing projects normally after positive zeroing");

                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, updated);
                requireClose(.95f, com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog()
                        .get(id).armor().orElseThrow().coefficients().get(DamageKeys.BLUNT),
                        "nested guard fixture restores its .95 blunt modifier");
                Villager nested = body(helper, new BlockPos(13, 1, 1), bodies);
                equipCap(nested);
                var triggered = new java.util.concurrent.atomic.AtomicBoolean();
                var nestedAccepted = new java.util.concurrent.atomic.AtomicBoolean();
                var eventCount = new java.util.concurrent.atomic.AtomicInteger();
                var outerAmount = new java.util.concurrent.atomic.AtomicReference<Float>();
                var nestedAmount = new java.util.concurrent.atomic.AtomicReference<Float>();
                java.util.function.Consumer<LivingIncomingDamageEvent> listener = event -> {
                    if (event.getEntity() != nested) return;
                    eventCount.incrementAndGet();
                    if (triggered.compareAndSet(false, true)) {
                        outerAmount.set(event.getAmount());
                        nestedAccepted.set(nested.hurt(nested.level().damageSources().generic(), .25f));
                        // Let the outer hit proceed as a distinct vanilla hit after the nested one.
                        nested.invulnerableTime = 0;
                    } else {
                        nestedAmount.set(event.getAmount());
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, listener);
                try {
                    DamageSystem.applyHealthChange(nested, Map.of(DamageKeys.BLUNT, 5f), 1f, false);
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                }
                require(triggered.get() && nestedAccepted.get() && eventCount.get() == 2,
                        "outer typed and one distinct nested native incoming event both execute");
                requireClose(.95f, outerAmount.get(), "matching pending typed source retains its already-armored incoming amount");
                requireIngressAmount(.236f, nestedAmount.get(), "unmatched nested native source receives one source-cent armor quantization");
                requireClose(5.93f, typed(nested, DamageKeys.BLUNT), "both nested native and outer typed post-ledgers commit exactly once");
                requireClose(18.814f, nested.getHealth(), "both vanilla hits reconcile to the resulting typed ledger");
                helper.succeed();
            } finally {
                try {
                    com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                            .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, prior);
                } finally {
                    bodies.forEach(Villager::discard);
                }
            }
        });
    }

    private static Villager body(GameTestHelper helper, BlockPos position, List<Villager> cleanup) {
        Villager body = helper.spawn(EntityType.VILLAGER, position);
        cleanup.add(body);
        if (LiveHands.initialize(body).isEmpty()) throw new GameTestAssertException("bound human villager inventory initializes");
        return body;
    }
    private static void putCapInHand(Villager body) {
        putInHand(body, "cap", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_CAP.get()));
    }
    private static void putInHand(Villager body, String token, ItemStack stack) {
        var state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        body.setData(ModDataAttachments.LIVE_HANDS.get(), state.putWhole(state.revision() + 1, "left", new ItemToken(token), stack).state());
    }
    private static void equipCap(Villager body) {
        putCapInHand(body);
        var state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        long beforeRevision = state.revision();
        require(BodyEquipmentTransfer.transferOwned(body, "left", "head", "cap", state.revision(), true, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS, "real body equipment transfer commits cap to head slot");
        assertTransferred(body, "head", "cap", beforeRevision + 1,
                com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_CAP.get());
    }
    private static void assertTransferred(Villager body, String slotId, String expectedToken,
                                          long expectedRevision, net.minecraft.world.item.Item expectedItem) {
        var state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var slot = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                .filter(value -> value.id().equals(slotId)).findFirst().orElseThrow();
        require(state.revision() == expectedRevision, slotId + " transfer advances revision exactly once");
        require(state.stackCopy("left").isEmpty() && state.token("left").isEmpty(), slotId + " transfer releases source hand");
        var stack = slot.stack().orElseThrow();
        require(slot.token().equals(Optional.of(expectedToken)) && stack.is(expectedItem) && stack.getCount() == 1,
                slotId + " destination owns expected actual item, stack count, and original token");
    }
    private static float typed(Villager body, String key) {
        var damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault(key, 0f);
    }
    private static void requireClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > .011f) throw new GameTestAssertException(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void requireIngressAmount(float expected, Float actual, String message) {
        if (actual == null || Math.abs(expected - actual) > .0001f)
            throw new GameTestAssertException(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
