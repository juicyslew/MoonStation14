package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Registry key for network device-item prototypes. Registration is owned by application bootstrap. */
public final class ModNetworkDeviceItems {
    private ModNetworkDeviceItems() { }
    public static final PrototypeType<NetworkDeviceItemData> TYPE = new PrototypeType<>(
            ModDeviceNetworks.id("network_device_item"), "moonstation14/network_device_item",
            NetworkDeviceItemData.CODEC, new NetworkDeviceItemMergeStrategy());

    /** Resolves the stack's prototype reference from the selected runtime catalog. */
    public static Optional<NetworkDeviceItemData> resolve(ItemStack stack, boolean client) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        var profileId = stack.get(ModDataComponents.NETWORK_DEVICE_ITEM_PROFILE.get());
        if (profileId == null) return Optional.empty();
        var catalog = client ? PrototypeRuntime.clientNetworkDeviceItems() : PrototypeRuntime.serverNetworkDeviceItems();
        return Optional.ofNullable(catalog.get(profileId));
    }

    public static boolean isConfigurator(ItemStack stack) {
        return resolve(stack, false).flatMap(NetworkDeviceItemData::configurator).isPresent();
    }

    public static boolean isSignaller(ItemStack stack) {
        return resolve(stack, false).filter(data -> data.signallerPort().isPresent()).isPresent();
    }
}

