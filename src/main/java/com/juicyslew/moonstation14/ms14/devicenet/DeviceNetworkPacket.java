package com.juicyslew.moonstation14.ms14.devicenet;
import java.util.Objects;
/** Addressed or broadcast packet enqueued for a later transport update. */
public record DeviceNetworkPacket(DeviceIdentity sender,int networkId,int frequency,String senderAddress,String destinationAddress,boolean broadcast,DevicePayload payload,int senderNetworkId,int senderTransmitFrequency) {
 public DeviceNetworkPacket(DeviceIdentity sender,int networkId,int frequency,String senderAddress,String destinationAddress,boolean broadcast,DevicePayload payload) { this(sender,networkId,frequency,senderAddress,destinationAddress,broadcast,payload,networkId,frequency); }
 public DeviceNetworkPacket { Objects.requireNonNull(sender); Objects.requireNonNull(senderAddress); Objects.requireNonNull(destinationAddress); Objects.requireNonNull(payload); if(networkId<0||frequency<1||senderNetworkId<0||senderTransmitFrequency<0) throw new IllegalArgumentException("invalid network packet"); if(senderAddress.isBlank()) throw new IllegalArgumentException("transmitter address required"); if(!broadcast&&destinationAddress.isBlank()) throw new IllegalArgumentException("destination required"); }
}
