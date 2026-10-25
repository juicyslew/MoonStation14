package com.juicyslew.moonstation14.ms14.hands.client;

/** Resolves body world gestures from their actual key mappings, rather than inferred held-item state. */
final class BodyInputGesturePolicy {
    enum Route { NONE, SMART_BAG, SMART_BELT, ALTERNATE_WORLD_USE, NORMAL_WORLD_USE }
    enum NormalWorldRoute { STORAGE, TARGET_USE, ACTIVATE_TARGET, BLOCK_ACTIVATE, NONE }

    private BodyInputGesturePolicy() { }

    static Route resolve(boolean equipBag, boolean equipBelt, boolean alternateWorldUse, boolean worldStorage) {
        return resolve(equipBag, equipBelt, alternateWorldUse, worldStorage, false);
    }

    /** Captured ALT+E is already qualified as the ordinary world key at press time. */
    static Route resolve(boolean equipBag, boolean equipBelt, boolean alternateWorldUse, boolean worldStorage,
                         boolean altWorldStorage) {
        if (equipBag) return Route.SMART_BAG;
        if (equipBelt) return Route.SMART_BELT;
        if (alternateWorldUse || altWorldStorage) return Route.ALTERNATE_WORLD_USE;
        if (worldStorage) return Route.NORMAL_WORLD_USE;
        return Route.NONE;
    }

    static Route dispatch(boolean screenOpen, boolean mouseGrabbed, boolean equipBag, boolean equipBelt,
                          boolean alternateWorldUse, boolean worldStorage) {
        return dispatch(screenOpen, mouseGrabbed, equipBag, equipBelt, alternateWorldUse, worldStorage, false);
    }

    static Route dispatch(boolean screenOpen, boolean mouseGrabbed, boolean equipBag, boolean equipBelt,
                          boolean alternateWorldUse, boolean worldStorage, boolean altWorldStorage) {
        if (screenOpen || !mouseGrabbed) return Route.NONE;
        return resolve(equipBag, equipBelt, alternateWorldUse, worldStorage, altWorldStorage);
    }

    static boolean suppressNativeInventory(boolean nativeInventory, boolean bodyControlled) {
        return nativeInventory && bodyControlled;
    }

    static NormalWorldRoute normalWorldRoute(boolean storageTarget, boolean looseTarget, boolean heldItem) {
        if (storageTarget) return NormalWorldRoute.STORAGE;
        if (looseTarget) return heldItem ? NormalWorldRoute.TARGET_USE : NormalWorldRoute.ACTIVATE_TARGET;
        return NormalWorldRoute.BLOCK_ACTIVATE;
    }

    static com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent aimedActivationIntent(
            boolean hydroponicsTray, boolean ordinaryActivationTarget) {
        if (hydroponicsTray) return com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE;
        return ordinaryActivationTarget
                ? com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.ACTIVATE_WORLD : null;
    }
}

