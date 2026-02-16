package com.lrenyi.template.platform.support;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdParserTest {

    @Test
    void parseId_long() {
        assertThat(IdParser.parseId("1", Long.class)).isEqualTo(1L);
        assertThat(IdParser.parseId("123", long.class)).isEqualTo(123L);
        assertThat(IdParser.parseId("  42  ", Long.class)).isEqualTo(42L);
    }

    @Test
    void parseId_longInvalid() {
        assertThatThrownBy(() -> IdParser.parseId("abc", Long.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式错误");
        assertThatThrownBy(() -> IdParser.parseId("", Long.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseId_string() {
        assertThat(IdParser.parseId("abc", String.class)).isEqualTo("abc");
        assertThat(IdParser.parseId("  x  ", String.class)).isEqualTo("x");
    }

    @Test
    void parseId_uuid() {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        UUID uuid = (UUID) IdParser.parseId(uuidStr, UUID.class);
        assertThat(uuid).isEqualTo(UUID.fromString(uuidStr));
    }

    @Test
    void parseId_uuidInvalid() {
        assertThatThrownBy(() -> IdParser.parseId("not-a-uuid", UUID.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式错误");
    }

    @Test
    void parseId_integer() {
        assertThat(IdParser.parseId("99", Integer.class)).isEqualTo(99);
        assertThat(IdParser.parseId("0", int.class)).isEqualTo(0);
    }

    @Test
    void parseId_nullThrows() {
        assertThatThrownBy(() -> IdParser.parseId(null, Long.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void parseIdFromObject_numberToLong() {
        assertThat(IdParser.parseIdFromObject(1, Long.class)).isEqualTo(1L);
        assertThat(IdParser.parseIdFromObject(1.5, Long.class)).isEqualTo(1L);
    }

    @Test
    void parseIdFromObject_stringToLong() {
        assertThat(IdParser.parseIdFromObject("100", Long.class)).isEqualTo(100L);
    }

    @Test
    void parseIds() {
        List<Object> ids = IdParser.parseIds(List.of(1, 2, 3), Long.class);
        assertThat(ids).containsExactly(1L, 2L, 3L);
    }

    @Test
    void parseIds_emptyOrNull() {
        assertThat(IdParser.parseIds(null, Long.class)).isEmpty();
        assertThat(IdParser.parseIds(List.of(), Long.class)).isEmpty();
    }
}
