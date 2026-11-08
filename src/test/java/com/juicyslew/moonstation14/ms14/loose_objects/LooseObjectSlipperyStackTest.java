package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LooseObjectSlipperyStackTest {
    @Test void resolvesExplicitOverrideThenSideResolvedProfileAndDisabledWithoutMutation() {
        var server = PrototypeRuntime.serverManager();
        var client = PrototypeRuntime.clientManager();
        var serverBefore = server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).asMap();
        var clientBefore = client.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).asMap();
        var serverAllBefore = server.snapshot(); var clientAllBefore = client.snapshot();
        try {
            var serverDefault = composition(2.75d);
            var clientDefault = composition(4.25d);
            var serverProfile = profile(serverDefault);
            var clientProfile = profile(clientDefault);
            var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "stack_slippery_test");
            var serverProfiles = new java.util.LinkedHashMap<>(serverBefore); serverProfiles.put(id, serverProfile);
            var clientProfiles = new java.util.LinkedHashMap<>(clientBefore); clientProfiles.put(id, clientProfile);
            server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, serverProfiles);
            client.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, clientProfiles);
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), id);
            assertEquals(serverDefault, LooseObjectProfile.resolveSlipperyForStack(stack, false));
            assertEquals(clientDefault, LooseObjectProfile.resolveSlipperyForStack(stack, true));
            var override = composition(6.5d);
            stack.set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), override);
            var beforeComponents = stack.getComponents();
            var combined = new ItemSlipperyComposition(serverDefault.stepTrigger(), override.response());
            assertEquals(combined, LooseObjectProfile.resolveSlipperyForStack(stack, false));
            assertEquals(new ItemSlipperyComposition(clientDefault.stepTrigger(), override.response()), LooseObjectProfile.resolveSlipperyForStack(stack, true));
            assertEquals(beforeComponents, stack.getComponents());
            assertEquals(serverDefault.response(), server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(id).slippery());
            assertEquals(clientDefault.response(), client.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(id).slippery());

            var disabledId = ResourceLocation.fromNamespaceAndPath("moonstation14", "stack_slippery_disabled_test");
            serverProfiles.put(disabledId, profile(null));
            server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, serverProfiles);
            ItemStack disabled = new ItemStack(Items.STONE);
            disabled.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), disabledId);
            assertNull(LooseObjectProfile.resolveSlipperyForStack(disabled, false));
            assertNull(disabled.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get()));
        } finally {
            server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, serverBefore);
            client.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, clientBefore);
            assertEquals(withoutLoose(serverAllBefore), withoutLoose(server.snapshot()));
            assertEquals(withoutLoose(clientAllBefore), withoutLoose(client.snapshot()));
        }
    }

    @Test void stackComponentPersistentAndStreamRoundTrip() {
        var value = composition(5.25d);
        var encoded = ItemSlipperyComposition.CODEC.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, ItemSlipperyComposition.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var componentType = ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get();
        var encodedStack = new ItemStack(Items.STONE);
        encodedStack.set(componentType, value);
        var componentJson = componentType.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, componentType.codec().parse(JsonOps.INSTANCE, componentJson).getOrThrow());
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        componentType.streamCodec().encode(buf, value);
        assertEquals(value, componentType.streamCodec().decode(buf));
        assertEquals(value, encodedStack.get(componentType));
    }

    @Test void inactiveProfileTriggerStaysInactiveWhileStackResponseOverrides() {
        var server = PrototypeRuntime.serverManager();
        var before = server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).asMap();
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "inactive_root_trigger_test");
        try {
            var trigger = new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(false, .2d, 1d, false);
            var response = com.juicyslew.moonstation14.ms14.slip.SlipResponseData.DEFAULT;
            var profiles = new java.util.LinkedHashMap<>(before);
            profiles.put(id, new LooseObjectProfile(1, .5f, 0, .75f, 0, "cube", "flat_sprite", false, 0,
                    8, .1f, 10, .65f, .08f, new LooseObjectProfile.VisualTuning(.65f, .65f, 0, 0, 1, 0), null, trigger, response));
            server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, profiles);
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), id);
            var stackResponse = new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(1, 0, 0, 0, 0, 0, false, false, false);
            var stackTrigger = new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(true, .3d, 6.5d, false);
            stack.set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), new ItemSlipperyComposition(stackTrigger, stackResponse));
            assertEquals(new ItemSlipperyComposition(trigger, stackResponse), LooseObjectProfile.resolveSlipperyForStack(stack, false));
        } finally { server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, before); }
    }

    @Test void stackTriggerSuppliesMissingProfileTriggerButResponseAloneDoesNotCreateContact() {
        var server = PrototypeRuntime.serverManager();
        var before = server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).asMap();
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "missing_root_trigger_test");
        try {
            var profiles = new java.util.LinkedHashMap<>(before);
            profiles.put(id, profile(null));
            server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, profiles);
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), id);
            assertNull(LooseObjectProfile.resolveSlipperyForStack(stack, false));
            var supplied = composition(6.5d);
            stack.set(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get(), supplied);
            assertEquals(supplied, LooseObjectProfile.resolveSlipperyForStack(stack, false));
        } finally { server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, before); }
    }

    @Test void actualItemStackPersistentCodecRetainsRegisteredSlipOverride() {
        var value = new ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(false, .17d, 7.125d, true),
                new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(1.75d, .625d, 1.2d, .3d,
                        .4d, 9d, true, false, true, ResourceLocation.fromNamespaceAndPath("moonstation14", "mimana"), -100d));
        var type = ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get();
        var registry = BuiltInRegistries.DATA_COMPONENT_TYPE;
        assertSame(type, registry.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.getId()),
                "stack serialization requires the registered component type");
        ItemStack original = new ItemStack(Items.STONE, 3);
        original.set(type, value);
        DynamicOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var encoded = ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(Items.STONE, decoded.getItem());
        assertEquals(3, decoded.getCount());
        assertEquals(value, decoded.get(type));
        assertEquals(value, original.get(type));
    }

    @Test void actualItemStackNetworkCodecRetainsRegisteredSlipOverride() {
        var value = new ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(false, .17d, 7.125d, true),
                new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(1.75d, .625d, 1.2d, .3d,
                        .4d, 9d, true, false, true, ResourceLocation.fromNamespaceAndPath("moonstation14", "mimana"), -100d));
        var type = ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get();
        assertSame(type, BuiltInRegistries.DATA_COMPONENT_TYPE.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.getId()),
                "stack serialization requires the registered component type");
        ItemStack original = new ItemStack(Items.STONE, 3);
        original.set(type, value);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, original);
            ItemStack decoded = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            assertEquals(Items.STONE, decoded.getItem());
            assertEquals(3, decoded.getCount());
            assertEquals(value, decoded.get(type));
            assertEquals(value, original.get(type));
        } finally {
            buffer.release();
        }
    }

    private static ItemSlipperyComposition composition(double speed) {
        return new ItemSlipperyComposition(new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(true, .3d, speed, false),
                com.juicyslew.moonstation14.ms14.slip.SlipResponseData.DEFAULT);
    }

    private static LooseObjectProfile profile(ItemSlipperyComposition slippery) {
        return new LooseObjectProfile(1, .5f, 0, .75f, 0, "cube", "flat_sprite", false, 0,
                8, .1f, 10, .65f, .08f, new LooseObjectProfile.VisualTuning(.65f, .65f, 0, 0, 1, 0), null,
                slippery == null ? null : slippery.stepTrigger(), slippery == null ? null : slippery.response());
    }

    private static Map<ResourceLocation, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<?>> withoutLoose(
            Map<ResourceLocation, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<?>> catalogs) {
        var copy = new java.util.LinkedHashMap<>(catalogs);
        copy.remove(ModLooseObjectProfiles.PROFILE_TYPE.typeId());
        return copy;
    }

}
