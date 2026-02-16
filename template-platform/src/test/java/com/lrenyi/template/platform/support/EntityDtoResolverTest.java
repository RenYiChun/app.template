package com.lrenyi.template.platform.support;

import com.lrenyi.template.platform.meta.EntityMeta;
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
        meta.setEntityClass(String.class);
        assertThat(EntityDtoResolver.resolveCreateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveUpdateDto(meta)).isNull();
        assertThat(EntityDtoResolver.resolveResponseDto(meta)).isNull();
    }

    @Test
    void resolve_whenEntityHasGeneratedDtos() {
        EntityMeta meta = new EntityMeta();
        meta.setEntityClass(com.lrenyi.template.platform.domain.Permission.class);
        Class<?> createDto = EntityDtoResolver.resolveCreateDto(meta);
        Class<?> updateDto = EntityDtoResolver.resolveUpdateDto(meta);
        Class<?> responseDto = EntityDtoResolver.resolveResponseDto(meta);
        if (createDto != null) {
            assertThat(createDto.getSimpleName()).isEqualTo("PermissionCreateDTO");
        }
        if (updateDto != null) {
            assertThat(updateDto.getSimpleName()).isEqualTo("PermissionUpdateDTO");
        }
        if (responseDto != null) {
            assertThat(responseDto.getSimpleName()).isEqualTo("PermissionResponseDTO");
        }
    }
}
