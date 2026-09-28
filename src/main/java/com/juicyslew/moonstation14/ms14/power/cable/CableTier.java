package com.juicyslew.moonstation14.ms14.power.cable;

import net.minecraft.util.StringRepresentable;

/** Independent power classes; sharing a host face never implies conversion. */
public enum CableTier implements StringRepresentable {
    HV("hv"), MV("mv"), APC("apc");

    private final String id;
    CableTier(String id) { this.id = id; }
    @Override public String getSerializedName() { return id; }
}
