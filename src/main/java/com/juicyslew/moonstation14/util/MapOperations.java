package com.juicyslew.moonstation14.util;

import java.util.Map;

public class MapOperations {
    public static float getTotal(Map<?, Float> map) {
        float total = 0;
        for (float val : map.values()) total += val;
        return total;
    }
}
