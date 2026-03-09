package com.lrenyi.template.dataforge.validation;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.DataforgeErrorCodes;
import com.lrenyi.template.dataforge.support.DataforgeHttpException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssociationValidatorTest {

    @Mock
    private EntityRegistry entityRegistry;
    @Mock
    private EntityCrudService crudService;

    private AssociationValidator validator;
    private EntityMeta meta;
    private EntityMeta refMeta;
    private Map<String, Object> entityMap;

    @BeforeEach
    void setUp() {
        validator = new AssociationValidator(entityRegistry, crudService);
        refMeta = new EntityMeta();
        refMeta.setEntityName("Dept");
        meta = new EntityMeta();
        meta.setEntityName("User");
        FieldMeta deptField = new FieldMeta();
        deptField.setName("deptId");
        deptField.setForeignKey(true);
        deptField.setReferencedEntity("Dept");
        deptField.setRequired(false);
        meta.setFields(List.of(deptField));
        meta.setAccessor(new MapBackedAccessor());
        entityMap = new HashMap<>();
    }

    @Test
    void validateAssociations_doesNothingWhenMetaNull() {
        assertThatCode(() -> validator.validateAssociations(null, entityMap)).doesNotThrowAnyException();
    }

    @Test
    void validateAssociations_doesNothingWhenEntityNull() {
        assertThatCode(() -> validator.validateAssociations(meta, null)).doesNotThrowAnyException();
    }

    @Test
    void validateAssociations_doesNothingWhenForeignKeyValueNull() {
        entityMap.put("deptId", null);
        assertThatCode(() -> validator.validateAssociations(meta, entityMap)).doesNotThrowAnyException();
    }

    @Test
    void validateAssociations_passesWhenRefExists() {
        entityMap.put("deptId", 1L);
        when(entityRegistry.getByEntityName("Dept")).thenReturn(refMeta);
        when(crudService.get(eq(refMeta), eq(1L))).thenReturn(new Object());
        assertThatCode(() -> validator.validateAssociations(meta, entityMap)).doesNotThrowAnyException();
    }

    @Test
    void validateAssociations_throwsWhenRefNotFound() {
        entityMap.put("deptId", 999L);
        when(entityRegistry.getByEntityName("Dept")).thenReturn(refMeta);
        when(crudService.get(eq(refMeta), eq(999L))).thenReturn(null);
        assertThatThrownBy(() -> validator.validateAssociations(meta, entityMap))
                .isInstanceOf(DataforgeHttpException.class)
                .satisfies(e -> {
                    DataforgeHttpException ex = (DataforgeHttpException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(ex.getErrorCode()).isEqualTo(DataforgeErrorCodes.ASSOCIATION_TARGET_NOT_FOUND);
                });
    }

    /** 用 Map 模拟实体，Accessor 按 key 读 Map */
    private class MapBackedAccessor implements com.lrenyi.template.dataforge.support.BeanAccessor {
        @Override
        public Object get(Object bean, String propertyName) {
            return bean instanceof Map ? ((Map<?, ?>) bean).get(propertyName) : null;
        }

        @Override
        public void set(Object bean, String propertyName, Object value) {
            if (bean instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) bean;
                map.put(propertyName, value);
            }
        }

        @Override
        public <T> T newInstance() {
            throw new UnsupportedOperationException();
        }
    }
}
