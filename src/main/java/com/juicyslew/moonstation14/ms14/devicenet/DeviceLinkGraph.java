package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.*;

/** Persistent directed links and remembered source-port values; mutations notify the owning save data. */
public final class DeviceLinkGraph {
    public record PortRef(DeviceIdentity device, String port) {
        public PortRef { Objects.requireNonNull(device); if (port == null || port.isBlank()) throw new IllegalArgumentException("blank port"); }
    }
    public record Link(PortRef source, PortRef sink) { public Link { Objects.requireNonNull(source); Objects.requireNonNull(sink); } }
    private final Set<Link> links = new LinkedHashSet<>();
    private final Map<PortRef, DeviceSignal> remembered = new HashMap<>();
    private final Runnable onMutation;
    public DeviceLinkGraph() { this(() -> { }); }
    DeviceLinkGraph(Runnable onMutation) { this.onMutation=Objects.requireNonNull(onMutation); }
    private void changed() { onMutation.run(); }
    public Set<Link> links() { return Set.copyOf(links); }
    public Optional<DeviceSignal> remembered(PortRef source) { return Optional.ofNullable(remembered.get(source)); }
    /** Seeds prototype defaults once; restored/runtime state always wins over defaults. */
    public void initializeRemembered(DeviceIdentity device, Map<String, DeviceSignal> defaults) {
        Objects.requireNonNull(device); Objects.requireNonNull(defaults);
        defaults.forEach((port, signal) -> {
            new PortRef(device, port);
            if (signal != DeviceSignal.HIGH && signal != DeviceSignal.LOW) throw new IllegalArgumentException("initial remembered state must be High or Low");
        });
        boolean[] mutated={false};
        defaults.forEach((port, signal) -> {
            if(remembered.putIfAbsent(new PortRef(device, port), signal)==null) mutated[0]=true;
        });
        if(mutated[0])changed();
    }
    public boolean link(Link value) { boolean changed=links.add(Objects.requireNonNull(value)); if(changed)changed(); return changed; }
    public boolean unlink(Link value) { boolean changed=links.remove(value); if(changed)changed(); return changed; }
    public List<Link> linksFrom(PortRef source) { return links.stream().filter(l -> l.source().equals(source)).toList(); }
    public List<Link> linksTo(DeviceIdentity sink) { return links.stream().filter(l -> l.sink().device().equals(sink)).toList(); }
    public void retireLinksTo(DeviceIdentity sink) { if(links.removeIf(link -> link.sink().device().equals(sink)))changed(); }
    public void signal(PortRef source, DeviceSignal signal) {
        Objects.requireNonNull(signal);
        boolean mutated=false;
        if (signal == DeviceSignal.HIGH || signal == DeviceSignal.LOW) mutated=!signal.equals(remembered.put(source, signal));
        else if (signal == DeviceSignal.UNKNOWN) mutated=remembered.remove(source)!=null;
        if(mutated)changed();
    }
    public void retire(DeviceIdentity id) {
        boolean linksChanged=links.removeIf(l -> l.source().device().equals(id) || l.sink().device().equals(id));
        boolean stateChanged=remembered.keySet().removeIf(p -> p.device().equals(id));
        if(linksChanged||stateChanged)changed();
    }
    void restore(Collection<Link> savedLinks, Map<PortRef, DeviceSignal> states) {
        links.clear(); links.addAll(savedLinks); remembered.clear();
        states.forEach((port, signal) -> { if (signal == DeviceSignal.HIGH || signal == DeviceSignal.LOW) remembered.put(port, signal); });
    }
    Map<PortRef, DeviceSignal> rememberedSnapshot() { return Map.copyOf(remembered); }
}

