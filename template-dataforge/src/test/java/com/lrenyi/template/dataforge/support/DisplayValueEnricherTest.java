package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisplayValueEnricherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void enrich_returnsEmptyWhenListNull() {
        List<Map<String, Object>> out = DisplayValueEnricher.enrich(
                new EntityMeta(), null, mock(EntityRegistry.class), mock(EntityCrudService.class), MAPPER);
        assertThat(out).isEmpty();
    }

    @Test
    void enrich_returnsMapsWhenNoForeignKey() {
        EntityMeta meta = new EntityMeta();
        meta.setFields(List.of());
        List<?> list = List.of(Map.of("id", 1L, "name", "a"));
        List<Map<String, Object>> out = DisplayValueEnricher.enrich(
                meta, list, mock(EntityRegistry.class), mock(EntityCrudService.class), MAPPER);
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).containsEntry("id", 1L).containsEntry("name", "a");
    }

    @Test
    void enrich_returnsMapsWhenListEmpty() {
        List<Map<String, Object>> out = DisplayValueEnricher.enrich(
                new EntityMeta(), List.of(), mock(EntityRegistry.class), mock(EntityCrudService.class), MAPPER);
        assertThat(out).isEmpty();
    }
}
