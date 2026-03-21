package com.lrenyi.template.dataforge.service;

import java.util.List;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.BeanAccessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InMemoryEntityCrudServiceTest {

    @Test
    void updatePreservesUnspecifiedFields() {
        InMemoryEntityCrudService service = new InMemoryEntityCrudService();
        EntityMeta meta = entityMeta();

        TestEntity created = (TestEntity) service.create(meta, new TestEntity(null, "alice", "secret"));
        TestEntity updated = (TestEntity) service.update(meta, created.getId(), new TestEntity(null, "bob", null));

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("bob", updated.getName());
        assertEquals("secret", updated.getPassword());
    }

    @Test
    void updateBatchPreservesUnspecifiedFields() {
        InMemoryEntityCrudService service = new InMemoryEntityCrudService();
        EntityMeta meta = entityMeta();

        TestEntity first = (TestEntity) service.create(meta, new TestEntity(null, "alice", "secret"));
        TestEntity updated = (TestEntity) service.updateBatch(meta,
                List.of(new TestEntity(first.getId(), "bob", null))).getFirst();

        assertEquals(first.getId(), updated.getId());
        assertEquals("bob", updated.getName());
        assertEquals("secret", updated.getPassword());
    }

    private static EntityMeta entityMeta() {
        EntityMeta meta = new EntityMeta();
        meta.setEntityName("test");
        meta.setPathSegment("tests");
        meta.setEntityClass(TestEntity.class);
        meta.setPrimaryKeyType(Long.class);
        meta.setAccessor(new TestEntityAccessor());
        return meta;
    }

    private static final class TestEntityAccessor implements BeanAccessor {
        @Override
        public Object get(Object bean, String propertyName) {
            TestEntity entity = (TestEntity) bean;
            return switch (propertyName) {
                case "id" -> entity.getId();
                case "name" -> entity.getName();
                case "password" -> entity.getPassword();
                default -> null;
            };
        }

        @Override
        public void set(Object bean, String propertyName, Object value) {
            TestEntity entity = (TestEntity) bean;
            switch (propertyName) {
                case "id" -> entity.setId((Long) value);
                case "name" -> entity.setName((String) value);
                case "password" -> entity.setPassword((String) value);
                default -> {
                }
            }
        }

        @Override
        public <T> T newInstance() {
            @SuppressWarnings("unchecked")
            T instance = (T) new TestEntity();
            return instance;
        }
    }

    private static final class TestEntity {
        private Long id;
        private String name;
        private String password;

        private TestEntity() {
        }

        private TestEntity(Long id, String name, String password) {
            this.id = id;
            this.name = name;
            this.password = password;
        }

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
