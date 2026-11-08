package com.juicyslew.moonstation14.ms14.equipment;
import java.util.List;
public final class EquipmentSlots {
    private EquipmentSlots() { }
    /** Canonical local equipment slot identifier; identity must not depend on layout order. */
    public static final String GLOVES = "gloves";
    public static final String OUTER_CLOTHING = "outerClothing";
    public static final List<String> SLOT_ORDER = List.of("shoes", "jumpsuit", "outerClothing", "gloves", "neck", "mask", "eyes", "ears", "head", "pocket1", "pocket2", "suitstorage", "id", "belt", "back");
}

