package com.juicyslew.moonstation14.ms14.containment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Small server-thread membership index. Identity equality is intentional: an owner UUID alone is insufficient. */
public final class ContainedBodyMembership<O> {
    private final Map<UUID, O> owners = new HashMap<>();

    public boolean bind(UUID body, O owner) {
        if (body == null || owner == null || owners.containsKey(body)) return false;
        owners.put(body, owner);
        return true;
    }

    public boolean unbind(UUID body, O expectedOwner) {
        if (body == null || expectedOwner == null || owners.get(body) != expectedOwner) return false;
        owners.remove(body);
        return true;
    }

    public O get(UUID body) { return owners.get(body); }
    public Map<UUID, O> snapshot() { return Map.copyOf(owners); }
}
