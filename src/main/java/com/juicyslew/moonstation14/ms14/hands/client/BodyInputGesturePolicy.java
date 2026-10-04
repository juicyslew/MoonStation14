package com.juicyslew.moonstation14.ms14.hands.client;

/** Resolves body world gestures from their actual key mappings, rather than inferred held-item state. */
final class BodyInputGesturePolicy {
    enum Route { NONE, SMART_BAG, SMART_BELT, ALTERNATE_WORLD_USE, NORMAL_WORLD_USE }

    private BodyInputGesturePolicy() { }

    static Route resolve(boolean equipBag, boolean equipBelt, boolean alternateWorldUse, boolean worldStorage) {
        if (equipBag) return Route.SMART_BAG;
        if (equipBelt) return Route.SMART_BELT;
        if (alternateWorldUse) return Route.ALTERNATE_WORLD_USE;
        if (worldStorage) return Route.NORMAL_WORLD_USE;
        return Route.NONE;
    }

    static Route dispatch(boolean screenOpen, boolean mouseGrabbed, boolean equipBag, boolean equipBelt,
                          boolean alternateWorldUse, boolean worldStorage) {
        if (screenOpen || !mouseGrabbed) return Route.NONE;
        return resolve(equipBag, equipBelt, alternateWorldUse, worldStorage);
    }

    static boolean suppressNativeInventory(boolean nativeInventory, boolean bodyControlled) {
        return nativeInventory && bodyControlled;
    }
}
