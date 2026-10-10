package com.juicyslew.moonstation14.ms14.devicenet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
/** Adapter boundary implemented by block and portable-device owners. */
public interface DeviceEndpoint {
 DeviceIdentity identity(); ResourceKey<Level> dimension(); BlockPos position();
 DeviceComposition composition(); boolean available(); void receive(DevicePayload payload, String sourceAddress);
 /** False when the authoritative address is stored by a non-world owner (for example, an item stack). */
 default boolean ownsAddressOutsideWorldSavedData() { return false; }
 /** Whether a clicked block position names this endpoint (including owned multiblock aliases). */
 default boolean acceptsBlockTarget(BlockPos target) { return position().equals(target); }
 default boolean powered() { return true; }
 /** Human-readable prototype/item name for configurator rows; addresses are shown separately. */
 default String displayName() { return "Device"; }
 default DeviceList deviceList() { return null; }
 /** Owner-authoritative persisted list mutation. Override when the list has domain-owned membership. */
 default boolean mutateDeviceListMembers(DeviceList replacement) { return false; }
 default double linkRange() { return composition().sourceConfig().map(DeviceComposition.SourceConfig::range).orElse(30.0); }
 default int invokeLimit() { return composition().sinkConfig().map(DeviceComposition.SinkConfig::limit).orElse(10); }
 default void onOverload() { }
 /** Optional endpoint-side BeforePacketSent gate after network/range eligibility is established. */
 default boolean beforePacketReceived(DeviceEndpoint sender, DeviceNetworkPacket packet) { return true; }
}
