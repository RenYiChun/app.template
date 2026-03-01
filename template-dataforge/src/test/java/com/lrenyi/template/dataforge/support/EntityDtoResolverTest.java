package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityDtoResolverTest {
    
    @Test
    void resolve_whenEntityClassNull() {
        EntityMeta meta = new EntityMeta();
        meta.setEntityClass(null);
        assertThat(EntityDtoResolver.resolveCreateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveUpdateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveResponseDto(meta)).isNull();
    }
    
    @Test
    void resolve_whenDtoClassNotExists() {
        EntityMeta meta = new EntityMeta();
        meta.setEntityClass(Object.class);
        assertThat(EntityDtoResolver.resolveCreateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveUpdateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveResponseDto(meta)).isNull();
    }
}
