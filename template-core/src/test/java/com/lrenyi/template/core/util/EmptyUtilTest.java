package com.lrenyi.template.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyUtilTest {

    @Test
    void isEmpty_isNotEmpty_Collection() {
        assertTrue(EmptyUtil.isEmpty((Collection<?>) null));
        assertTrue(EmptyUtil.isEmpty(new ArrayList<>()));
        assertFalse(EmptyUtil.isEmpty(Collections.singletonList(1)));
        assertFalse(EmptyUtil.isNotEmpty((Collection<?>) null));
        assertFalse(EmptyUtil.isNotEmpty(new ArrayList<>()));
        assertTrue(EmptyUtil.isNotEmpty(Collections.singletonList(1)));
    }

    @Test
    void isEmpty_isNotEmpty_ObjectArray() {
        assertTrue(EmptyUtil.isEmpty((Object[]) null));
        assertTrue(EmptyUtil.isEmpty(new Object[0]));
        assertFalse(EmptyUtil.isEmpty(new Object[]{1}));
        assertFalse(EmptyUtil.isNotEmpty((Object[]) null));
        assertFalse(EmptyUtil.isNotEmpty(new Object[0]));
        assertTrue(EmptyUtil.isNotEmpty(new Object[]{"a"}));
    }

    @Test
    void isEmpty_isNotEmpty_Map() {
        assertTrue(EmptyUtil.isEmpty((Map<?, ?>) null));
        assertTrue(EmptyUtil.isEmpty(new HashMap<>()));
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        assertFalse(EmptyUtil.isEmpty(m));
        assertTrue(EmptyUtil.isNotEmpty(m));
    }

    @Test
    void isEmpty_isNotEmpty_String() {
        assertTrue(EmptyUtil.isEmpty((String) null));
        assertTrue(EmptyUtil.isEmpty(""));
        assertTrue(EmptyUtil.isEmpty("   "));
        assertFalse(EmptyUtil.isEmpty("a"));
        assertFalse(EmptyUtil.isNotEmpty((String) null));
        assertTrue(EmptyUtil.isNotEmpty("a"));
    }

    @Test
    void isEmpty_isNotEmpty_Object() {
        assertTrue(EmptyUtil.isEmpty((Object) null));
        assertTrue(EmptyUtil.isEmpty(""));
        assertFalse(EmptyUtil.isEmpty("x"));
        assertFalse(EmptyUtil.isEmpty(1));
        assertTrue(EmptyUtil.isNotEmpty(1));
    }
}
