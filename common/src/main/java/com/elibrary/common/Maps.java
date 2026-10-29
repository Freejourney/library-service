package com.elibrary.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** 无序 typed-map 响应组装辅助。 */
public final class Maps {
    private Maps() {}

    public static Map<String, Object> of(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}