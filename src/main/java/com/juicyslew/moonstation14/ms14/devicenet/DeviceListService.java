package com.juicyslew.moonstation14.ms14.devicenet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Applies atomic membership changes through the endpoint that owns persisted list state. */
public final class DeviceListService {
    public enum Operation { SET, ADD, CLEAR, REMOVE }

    /** Set/Add use members; Remove uses member. Other fields are ignored. */
    public record Mutation(Operation operation, List<String> members, String member) {
        public Mutation {
            Objects.requireNonNull(operation, "operation");
            members = members == null ? List.of() : List.copyOf(members);
            if (members.stream().anyMatch(value -> value == null || value.isBlank()))
                throw new IllegalArgumentException("device members must be non-empty");
            if (operation == Operation.REMOVE && (member == null || member.isBlank()))
                throw new IllegalArgumentException("removed member must be non-empty");
        }
        public static Mutation set(List<String> members) { return new Mutation(Operation.SET, members, null); }
        public static Mutation add(List<String> members) { return new Mutation(Operation.ADD, members, null); }
        public static Mutation clear() { return new Mutation(Operation.CLEAR, List.of(), null); }
        public static Mutation remove(String member) { return new Mutation(Operation.REMOVE, List.of(), member); }
    }

    /** Returns false without changing state for missing lists, invalid capacity, or rejected persistence. */
    public boolean apply(DeviceEndpoint owner, Mutation mutation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mutation, "mutation");
        DeviceList current = owner.deviceList();
        if (current == null) return false;
        LinkedHashSet<String> members = new LinkedHashSet<>(current.addresses());
        switch (mutation.operation()) {
            case SET -> { members.clear(); members.addAll(mutation.members()); }
            case ADD -> members.addAll(mutation.members());
            case CLEAR -> members.clear();
            case REMOVE -> members.remove(mutation.member());
        }
        if (members.size() > current.capacity()) return false;
        if (members.equals(new LinkedHashSet<>(current.addresses()))) return true;
        DeviceList replacement = new DeviceList(List.copyOf(members), current.capacity(),
                current.allowList(), current.filterIncoming());
        return owner.mutateDeviceListMembers(replacement);
    }
}
