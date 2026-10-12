package com.juicyslew.moonstation14.ms14.devicenet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
public record DeviceComposition(List<DevicePort> sources, List<DevicePort> sinks, Optional<NetworkInterface> network, Optional<DeviceList.Config> deviceList, Optional<SourceConfig> sourceConfig, Optional<SinkConfig> sinkConfig, Optional<ConnectionConfig> connection, Optional<SoundOnOverload> soundOnOverload) {
 public record SoundOnOverload(Optional<net.minecraft.resources.ResourceLocation> sound, float volumeModifier) {
  public SoundOnOverload { Objects.requireNonNull(sound); if (!Float.isFinite(volumeModifier)) throw new IllegalArgumentException("invalid overload volume modifier"); }
 }
 public record NetworkInterface(int networkId, boolean transmit, boolean receive, String address, String addressPrefix, int transmitFrequency, int receiveFrequency, boolean receiveAll, boolean requiresPower, boolean autoConnect, boolean savableAddress, boolean customAddress, boolean sendBroadcastAttempt) {
  public NetworkInterface { if(networkId<0) throw new IllegalArgumentException("negative network ID"); Objects.requireNonNull(address); Objects.requireNonNull(addressPrefix); if(transmitFrequency<0||receiveFrequency<0) throw new IllegalArgumentException("negative frequency"); }
  public NetworkInterface withAddress(String boundAddress) { return new NetworkInterface(networkId, transmit, receive, Objects.requireNonNull(boundAddress), addressPrefix, transmitFrequency, receiveFrequency, receiveAll, requiresPower, autoConnect, savableAddress, customAddress, sendBroadcastAttempt); }
 }
   public record SourceConfig(double range, java.util.Map<String, DeviceSignal> lastSignals) { public SourceConfig { if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("invalid source range"); lastSignals = java.util.Map.copyOf(lastSignals); } }
  public record SinkConfig(int limit) { }
  public record ConnectionConfig(boolean wired, boolean wireless, double wirelessRange) { public ConnectionConfig { if (!Double.isFinite(wirelessRange) || wirelessRange < 0) throw new IllegalArgumentException("invalid connection range"); } }
   public DeviceComposition { sources=List.copyOf(Objects.requireNonNull(sources)); sinks=List.copyOf(Objects.requireNonNull(sinks)); network=Objects.requireNonNull(network); deviceList=Objects.requireNonNull(deviceList); sourceConfig=Objects.requireNonNull(sourceConfig); sinkConfig=Objects.requireNonNull(sinkConfig); connection=Objects.requireNonNull(connection); soundOnOverload=Objects.requireNonNull(soundOnOverload); }
  public DeviceComposition(List<DevicePort> sources, List<DevicePort> sinks, Optional<NetworkInterface> network, Optional<DeviceList.Config> deviceList, Optional<SourceConfig> sourceConfig, Optional<SinkConfig> sinkConfig, Optional<ConnectionConfig> connection) { this(sources,sinks,network,deviceList,sourceConfig,sinkConfig,connection,Optional.empty()); }
  public DeviceComposition(List<DevicePort> sources, List<DevicePort> sinks, Optional<NetworkInterface> network, Optional<DeviceList.Config> deviceList) { this(sources,sinks,network,deviceList,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty()); }
 public static DeviceComposition empty(){return new DeviceComposition(List.of(),List.of(),Optional.empty(),Optional.empty());}
 /** Bind the generated/persisted endpoint address; addressPrefix is only a generation hint. */
  public DeviceComposition withAddress(String address) { return new DeviceComposition(sources, sinks, network.map(net -> net.withAddress(address)), deviceList, sourceConfig, sinkConfig, connection,soundOnOverload); }
}
