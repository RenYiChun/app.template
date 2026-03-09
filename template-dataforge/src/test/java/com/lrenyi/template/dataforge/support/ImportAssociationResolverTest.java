package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ImportAssociationResolverTest {

    @Test
    void preloadDisplayToIdReturnsEmptyWhenMetaNull() {
        Map<String, Map<Object, Object>> out = ImportAssociationResolver.preloadDisplayToId(
                null, mock(EntityRegistry.class), mock(EntityCrudService.class));
        assertThat(out).isEmpty();
    }

    @Test
    void preloadDisplayToIdReturnsEmptyWhenNoForeignKey() {
        EntityMeta meta = new EntityMeta();
        meta.setFields(List.of());
        Map<String, Map<Object, Object>> out = ImportAssociationResolver.preloadDisplayToId(
                meta, mock(EntityRegistry.class), mock(EntityCrudService.class));
        assertThat(out).isEmpty();
    }

    @Test
    void preloadDisplayToIdReturnsEmptyWhenForeignKeyNotImportEnabled() {
        FieldMeta f = new FieldMeta();
        f.setForeignKey(true);
        f.setImportEnabled(false);
        f.setReferencedEntity("Ref");
        EntityMeta meta = new EntityMeta();
        meta.setFields(List.of(f));
        Map<String, Map<Object, Object>> out = ImportAssociationResolver.preloadDisplayToId(
                meta, mock(EntityRegistry.class), mock(EntityCrudService.class));
        assertThat(out).isEmpty();
    }

    @Test
    void resolveIdReturnsIdWhenInMap() {
        Map<Object, Object> displayToId = Map.of("研发部", 1L, "销售部", 2L);
        Object id = ImportAssociationResolver.resolveId("deptId", "研发部", displayToId, 2, "部门");
        assertThat(id).isEqualTo(1L);
    }

    @Test
    void resolveIdReturnsNullForNullOrEmptyDisplayValue() {
        Map<Object, Object> displayToId = Map.of("a", 1L);
        assertThat(ImportAssociationResolver.resolveId("f", null, displayToId, 1, "F")).isNull();
        assertThat(ImportAssociationResolver.resolveId("f", "", displayToId, 1, "F")).isNull();
    }

    @Test
    void resolveIdThrowsWhenNotFound() {
        Map<Object, Object> displayToId = Map.of("研发部", 1L);
        assertThatThrownBy(() ->
                ImportAssociationResolver.resolveId("deptId", "不存在的部门", displayToId, 3, "部门"))
                .isInstanceOf(DataforgeHttpException.class)
                .satisfies(e -> {
                    DataforgeHttpException ex = (DataforgeHttpException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(ex.getErrorCode()).isEqualTo(DataforgeErrorCodes.IMPORT_ASSOCIATION_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("第 3 行").contains("部门").contains("不存在的部门");
                });
    }
}
