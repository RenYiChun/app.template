package com.lrenyi.template.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int cacheSize;
    
    public LruCache(int size) {
        super(16, 0.75f, true);
        cacheSize = size;
    }
    
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() >= cacheSize;
    }
}
