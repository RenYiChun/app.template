package com.lrenyi.template.dataforge.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.permission.DataPermissionApplicator;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.BeanAccessor;
import com.lrenyi.template.dataforge.support.DataforgeExceptionHandler;
import com.lrenyi.template.dataforge.support.DataforgeServices;
import com.lrenyi.template.dataforge.support.EntityMapperProvider;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.Op;
import com.lrenyi.template.dataforge.support.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GenericEntityControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityRegistry entityRegistry = new EntityRegistry();
    private final EntityCrudService crudService = mock(EntityCrudService.class);
    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    private MockMvc mockMvc;
    private EntityMeta meta;
    private DataforgeProperties properties;
    private DataforgePermissionChecker permissionChecker;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("tester", "password", "ROLE_USER"));
        beanFactory.destroySingletons();
        properties = new DataforgeProperties();
        properties.setExposeExceptionMessage(true);
        properties.setValidationEnabled(false);
        permissionChecker = mock(DataforgePermissionChecker.class);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);

        meta = new EntityMeta();
        meta.setEntityName("test-entity");
        meta.setPathSegment("tests");
        meta.setEntityClass(TestEntity.class);
        meta.setPrimaryKeyType(Long.class);
        meta.setTreeEntity(true);
        meta.setAccessor(new ReflectionBeanAccessor(TestEntity.class));
        entityRegistry.register(meta);
        rebuildMockMvc();
    }

    private void rebuildMockMvc() {
        ObjectProvider<jakarta.validation.Validator> validatorProvider =
                beanFactory.getBeanProvider(jakarta.validation.Validator.class);
        ObjectProvider<com.lrenyi.template.dataforge.validation.AssociationValidator> associationValidatorProvider =
                beanFactory.getBeanProvider(com.lrenyi.template.dataforge.validation.AssociationValidator.class);
        ObjectProvider<com.lrenyi.template.dataforge.service.CascadeDeleteService> cascadeDeleteServiceProvider =
                beanFactory.getBeanProvider(com.lrenyi.template.dataforge.service.CascadeDeleteService.class);
        ObjectProvider<com.lrenyi.template.dataforge.permission.DataPermissionApplicator> dataPermissionApplicatorProvider =
                beanFactory.getBeanProvider(com.lrenyi.template.dataforge.permission.DataPermissionApplicator.class);
        ObjectProvider<com.lrenyi.template.dataforge.support.EntityChangeNotifier> entityChangeNotifierProvider =
                beanFactory.getBeanProvider(com.lrenyi.template.dataforge.support.EntityChangeNotifier.class);
        ObjectProvider<com.lrenyi.template.dataforge.support.AssociationChangeAuditor> associationChangeAuditorProvider =
                beanFactory.getBeanProvider(com.lrenyi.template.dataforge.support.AssociationChangeAuditor.class);

        GenericEntityController controller = new GenericEntityController(new DataforgeServices(
                entityRegistry,
                new ActionRegistry(),
                crudService,
                properties,
                permissionChecker,
                objectMapper,
                validatorProvider,
                new DefaultConversionService(),
                new EntityMapperProvider(List.of()),
                associationValidatorProvider,
                cascadeDeleteServiceProvider,
                dataPermissionApplicatorProvider,
                entityChangeNotifierProvider,
                associationChangeAuditorProvider));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DataforgeExceptionHandler(properties))
                .build();
    }

    @Test
    void searchReturnsPagedData() throws Exception {
        when(crudService.list(eq(meta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(entity(1L, "alpha", null), entity(2L, "beta", 1L))));

        mockMvc.perform(post("/api/tests/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SearchRequest.empty())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("alpha"))
                .andExpect(jsonPath("$.data.content[1].parentId").value(1));
    }

    @Test
    void treeBuildsNestedResponse() throws Exception {
        when(crudService.list(eq(meta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(
                        entity(1L, "root", null),
                        entity(2L, "child", 1L),
                        entity(3L, "leaf", 2L))));

        mockMvc.perform(get("/api/tests/tree").param("maxDepth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("root"))
                .andExpect(jsonPath("$.data[0].children[0].label").value("child"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].label").value("leaf"));
    }

    @Test
    void getReturnsNotFoundWhenDataPermissionRejectsRow() throws Exception {
        meta.setEnableDataPermission(true);
        beanFactory.registerSingleton("dataPermissionApplicator", (DataPermissionApplicator)
                entityMeta -> List.of(new FilterCondition("id", Op.EQ, 1L)));
        rebuildMockMvc();
        when(crudService.list(eq(meta), any(Pageable.class), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/tests/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        verify(crudService, never()).get(meta, 2L);
    }

    @Test
    void createMapsBodyAndReturnsCreatedEntity() throws Exception {
        when(crudService.create(eq(meta), any(TestEntity.class)))
                .thenAnswer(invocation -> {
                    TestEntity entity = invocation.getArgument(1);
                    entity.setId(10L);
                    return entity;
                });

        mockMvc.perform(post("/api/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"created\",\"parentId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("created"));
    }

    @Test
    void updateUsesPathIdAndReturnsUpdatedEntity() throws Exception {
        when(crudService.get(meta, 5L)).thenReturn(entity(5L, "before", null));
        when(crudService.update(eq(meta), eq(5L), any(TestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));

        mockMvc.perform(put("/api/tests/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"after\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("after"));
    }

    @Test
    void updateBatchReturnsUpdatedEntities() throws Exception {
        when(crudService.get(meta, 1L)).thenReturn(entity(1L, "old-1", null));
        when(crudService.get(meta, 2L)).thenReturn(entity(2L, "old-2", null));
        when(crudService.updateBatch(eq(meta), any()))
                .thenReturn(List.of(entity(1L, "new-1", null), entity(2L, "new-2", null)));

        mockMvc.perform(put("/api/tests/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":1,\"name\":\"new-1\"},{\"id\":2,\"name\":\"new-2\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("new-1"))
                .andExpect(jsonPath("$.data[1].name").value("new-2"));
    }

    @Test
    void deleteBatchRejectsInvalidIds() throws Exception {
        mockMvc.perform(delete("/api/tests/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"bad-id\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id 列表格式错误"));

        verify(crudService, never()).deleteBatch(eq(meta), any());
    }

    @Test
    void deleteBatchDeletesConvertedIds() throws Exception {
        mockMvc.perform(delete("/api/tests/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<List<?>> captor = ArgumentCaptor.forClass(List.class);
        verify(crudService).deleteBatch(eq(meta), captor.capture());
        @SuppressWarnings("unchecked")
        List<Object> parsedIds = (List<Object>) captor.getValue();
        assertThat(parsedIds).containsExactly(1L, 2L);
    }

    @Test
    void deleteBatchReturnsNotFoundWhenAnyRowIsOutsideDataPermission() throws Exception {
        meta.setEnableDataPermission(true);
        beanFactory.registerSingleton("dataPermissionApplicator", (DataPermissionApplicator)
                entityMeta -> List.of(new FilterCondition("id", Op.EQ, 1L)));
        rebuildMockMvc();
        when(crudService.list(eq(meta), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(entity(1L, "one", null))));

        mockMvc.perform(delete("/api/tests/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        verify(crudService, never()).deleteBatch(eq(meta), any());
    }

    private static TestEntity entity(Long id, String name, Long parentId) {
        TestEntity entity = new TestEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setParentId(parentId);
        return entity;
    }

    static class TestEntity {
        private Long id;
        private String name;
        private Long parentId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }
    }

    static class ReflectionBeanAccessor implements BeanAccessor {
        private final Class<?> type;

        ReflectionBeanAccessor(Class<?> type) {
            this.type = type;
        }

        @Override
        public Object get(Object bean, String propertyName) {
            try {
                Field field = findField(propertyName);
                field.setAccessible(true); // NOSONAR
                return field.get(bean);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void set(Object bean, String propertyName, Object value) {
            try {
                Field field = findField(propertyName);
                field.setAccessible(true); // NOSONAR
                field.set(bean, value); // NOSONAR
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <T> T newInstance() {
            try {
                @SuppressWarnings("unchecked")
                T instance = (T) type.getDeclaredConstructor().newInstance();
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private Field findField(String propertyName) throws NoSuchFieldException {
            return type.getDeclaredField(propertyName);
        }
    }
}
