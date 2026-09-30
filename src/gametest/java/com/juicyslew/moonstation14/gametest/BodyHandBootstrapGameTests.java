package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyHandBootstrapGameTests {
    private BodyHandBootstrapGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void committedBodyBootstrapIsIdempotentAndPersists(GameTestHelper helper) {
        Villager human = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(!human.hasData(ModDataAttachments.LIVE_HANDS.get()), "no eager initialization");
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.INITIALIZED, "human starts hands");
            LiveHands initial = human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(initial != null && initial.handIds().equals(List.of("left", "right"))
                    && initial.revision() == 0 && initial.compatible(human)
                    && HandCapability.resolve(human).orElseThrow().equals(initial.handIds()), "prototype layout");
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.PRESERVED
                    && human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == initial,
                    "repeated Ready leaves exact snapshot untouched");
            CompoundTag saved = human.saveWithoutId(new CompoundTag());
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "restored villager");
            restored.load(saved);
            LiveHands loaded = restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(loaded != null && BodyHandBootstrap.ensure(restored) == BodyHandBootstrap.Result.PRESERVED
                    && loaded == restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()),
                    "saved body state survives reconnect without replacement");
            require(BodyHandBootstrap.ensure(pig) == BodyHandBootstrap.Result.NO_HANDS
                    && !pig.hasData(ModDataAttachments.LIVE_HANDS.get()), "pig is controllable without hands");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void incompatibleAndLegacyOccupancyNeverOverwrite(GameTestHelper helper) {
        Villager human = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Villager legacy = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.INITIALIZED, "fixture");
            LiveHands original = human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            human.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(List.of("left", "right"))
                            .place("left", new ItemToken("legacy-token")).state())));
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.REJECTED
                    && human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == original,
                    "legacy occupancy cannot replace existing live snapshot");
            legacy.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(List.of("left", "right"))
                            .place("right", new ItemToken("old-token")).state())));
            require(BodyHandBootstrap.ensure(legacy) == BodyHandBootstrap.Result.REJECTED
                    && !legacy.hasData(ModDataAttachments.LIVE_HANDS.get()), "legacy-only occupancy remains intact");
            human.removeData(ModDataAttachments.HANDS.get());
            LiveHands wrongLayout = LiveHands.CODEC.parse(JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString("{\"hands\":[{\"id\":\"other\"}],\"active\":\"other\",\"revision\":4}"))
                    .getOrThrow();
            human.setData(ModDataAttachments.LIVE_HANDS.get(), wrongLayout);
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.REJECTED
                    && human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == wrongLayout,
                    "wrong-layout snapshot remains untouched");

            LiveHands carried = occupiedState(helper);
            human.setData(ModDataAttachments.LIVE_HANDS.get(), carried);
            require(BodyHandBootstrap.ensure(human) == BodyHandBootstrap.Result.PRESERVED
                    && human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == carried
                    && carried.stackCopy("left").orElseThrow().getCount() == 3,
                    "compatible occupied snapshot retains item, revision and active hand");
            Pig wrongHost = EntityType.PIG.create(helper.getLevel());
            require(wrongHost != null, "wrong-host fixture");
            CharacterIdentityAttachment falseHuman = new CharacterIdentityAttachment();
            falseHuman.bind(ModCharacters.HUMAN_ID);
            wrongHost.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), falseHuman);
            wrongHost.setData(ModDataAttachments.LIVE_HANDS.get(), carried);
            require(BodyHandBootstrap.ensure(wrongHost) == BodyHandBootstrap.Result.REJECTED
                    && wrongHost.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == carried
                    && HandCapability.resolve(wrongHost).isEmpty() && !carried.compatible(wrongHost),
                    "pig bound human never acquires capability or clears occupied snapshots");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lifecycleHumanHarnessRetainsBodyState(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(1, 1, 1));
        CompoundTag owner = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        owner.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(owner);
        require(body.isAddedToLevel() && helper.getLevel().getEntity(body.getUUID()) == body
                        && body.playerCharacterBinding() != null
                        && CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID)
                        && CharacterIdentitySystem.resolveForActor(body).isPresent(),
                "live, exactly bound human harness resolves as an actor");
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "human harness initialized");
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(state != null && state.handIds().equals(List.of("left", "right"))
                && state.compatible(body) && HandCapability.resolve(body).orElseThrow().equals(state.handIds()),
                "lifecycle human capability and live compatibility");
        LiveHands carried = occupiedState(helper);
        body.setData(ModDataAttachments.LIVE_HANDS.get(), carried);
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.PRESERVED
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == carried
                && carried.compatible(body), "occupied lifecycle state preserved");
        CompoundTag saved = body.saveWithoutId(new CompoundTag());
        PlayerCharacterHarnessEntity restored = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(restored != null, "restored harness constructible");
        restored.load(saved);
        LiveHands loaded = restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        // Codec preservation only: the original UUID still owns the world slot, so this copy is not an actor.
        require(loaded != null && restored.getUUID().equals(body.getUUID())
                && restored.playerCharacterBinding().equals(body.playerCharacterBinding())
                && CharacterIdentitySystem.resolveForActor(restored).isEmpty()
                && HandCapability.resolve(restored).isEmpty()
                && BodyHandBootstrap.ensure(restored) == BodyHandBootstrap.Result.REJECTED
                && restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == loaded
                && loaded.revision() == 7
                && loaded.activeHand().equals("right")
                && loaded.stackCopy("left").orElseThrow().getCount() == 3,
                "detached codec copy retains occupied state without acquiring actor authority");
        PlayerCharacterHarnessEntity unbound = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(2, 1, 1));
        CharacterIdentityAttachment human = new CharacterIdentityAttachment();
        human.bind(ModCharacters.HUMAN_ID);
        unbound.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), human);
        require(CharacterIdentitySystem.resolveForActor(unbound).isEmpty()
                && HandCapability.resolve(unbound).isEmpty()
                && BodyHandBootstrap.ensure(unbound) == BodyHandBootstrap.Result.REJECTED
                && !unbound.hasData(ModDataAttachments.LIVE_HANDS.get()),
                "live HUMAN key without owner binding never grants hands");
        PlayerCharacterHarnessEntity dangling = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(3, 1, 1));
        dangling.readAdditionalSaveData(owner);
        CharacterIdentityAttachment unknown = new CharacterIdentityAttachment();
        unknown.bind(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "missing_character"));
        dangling.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), unknown);
        require(CharacterIdentitySystem.resolveForActor(dangling).isEmpty()
                && HandCapability.resolve(dangling).isEmpty()
                && BodyHandBootstrap.ensure(dangling) == BodyHandBootstrap.Result.REJECTED
                && !dangling.hasData(ModDataAttachments.LIVE_HANDS.get()), "dangling harness rejected");
        helper.succeed();
    }

    private static LiveHands occupiedState(GameTestHelper helper) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        CompoundTag occupied = new CompoundTag();
        occupied.putString("token", "persisted-occupied");
        occupied.put("stack", ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.DIAMOND, 3)).getOrThrow());
        CompoundTag left = new CompoundTag();
        left.putString("id", "left");
        left.put("occupant", occupied);
        CompoundTag right = new CompoundTag();
        right.putString("id", "right");
        ListTag slots = new ListTag();
        slots.add(left);
        slots.add(right);
        CompoundTag raw = new CompoundTag();
        raw.put("hands", slots);
        raw.putString("active", "right");
        raw.putLong("revision", 7);
        return LiveHands.CODEC.parse(ops, raw).getOrThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
