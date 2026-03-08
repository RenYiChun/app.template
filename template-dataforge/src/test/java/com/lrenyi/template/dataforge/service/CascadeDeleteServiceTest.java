package com.lrenyi.template.dataforge.service;

import com.lrenyi.template.dataforge.annotation.CascadeStrategy;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.support.DataforgeErrorCodes;
import com.lrenyi.template.dataforge.support.DataforgeHttpException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CascadeDeleteServiceTest {

    @Mock
    private EntityRegistry entityRegistry;
    @Mock
    private EntityCrudService crudService;

    private CascadeDeleteService cascadeDeleteService;
    private EntityMeta departmentMeta;
    private EntityMeta userMeta;
    private FieldMeta userDeptField;

    @BeforeEach
    void setUp() {
        cascadeDeleteService = new CascadeDeleteService(entityRegistry, crudService);
        departmentMeta = new EntityMeta();
        departmentMeta.setEntityName("Department");
        userMeta = new EntityMeta();
        userMeta.setEntityName("User");
        userMeta.setAccessor(new MapAccessor());
        userDeptField = new FieldMeta();
        userDeptField.setName("deptId");
        userDeptField.setForeignKey(true);
        userDeptField.setReferencedEntity("Department");
        userMeta.setFields(List.of(userDeptField));
    }

    @Test
    void checkCascadeConstraints_throwsWhenRESTRICTAndReferencesExist() {
        when(entityRegistry.getAll()).thenReturn(List.of(userMeta));
        Object deptId = 1L;
        Map<String, Object> refUser = Map.of("id", 10L, "deptId", deptId);
        when(crudService.list(eq(userMeta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(refUser)));
        userDeptField.setCascadeDelete(CascadeStrategy.RESTRICT);

        assertThatThrownBy(() -> cascadeDeleteService.checkCascadeConstraints(departmentMeta, deptId))
                .isInstanceOf(DataforgeHttpException.class)
                .satisfies(e -> {
                    DataforgeHttpException ex = (DataforgeHttpException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT.value());
                    assertThat(ex.getErrorCode()).isEqualTo(DataforgeErrorCodes.CASCADE_DELETE_RESTRICT);
                });
    }

    @Test
    void checkCascadeConstraints_doesNotThrowWhenNoReferences() {
        when(entityRegistry.getAll()).thenReturn(List.of(userMeta));
        when(crudService.list(eq(userMeta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of()));
        userDeptField.setCascadeDelete(CascadeStrategy.RESTRICT);

        cascadeDeleteService.checkCascadeConstraints(departmentMeta, 1L);
    }

    @Test
    void executeCascadeDelete_setNullUpdatesRefs() {
        when(entityRegistry.getAll()).thenReturn(List.of(userMeta));
        Map<String, Object> refUser = new java.util.HashMap<>(Map.of("id", 10L, "deptId", 1L));
        when(crudService.list(eq(userMeta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(refUser)));
        userDeptField.setCascadeDelete(CascadeStrategy.SET_NULL);

        cascadeDeleteService.executeCascadeDelete(departmentMeta, 1L);

        verify(crudService).update(eq(userMeta), eq(10L), any());
    }

    @Test
    void executeCascadeDelete_cascadeDeletesRefs() {
        when(entityRegistry.getAll()).thenReturn(List.of(userMeta));
        Map<String, Object> refUser = Map.of("id", 10L, "deptId", 1L);
        when(crudService.list(eq(userMeta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(refUser)));
        userDeptField.setCascadeDelete(CascadeStrategy.CASCADE);

        cascadeDeleteService.executeCascadeDelete(departmentMeta, 1L);

        verify(crudService).delete(userMeta, 10L);
    }

    private static class MapAccessor implements com.lrenyi.template.dataforge.support.BeanAccessor {
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
