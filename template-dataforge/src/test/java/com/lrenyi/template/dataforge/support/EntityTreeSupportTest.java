package com.lrenyi.template.dataforge.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTreeSupportTest {

    private EntityTreeSupport entityTreeSupport;
    private EntityMeta meta;

    @BeforeEach
    void setUp() {
        entityTreeSupport = new EntityTreeSupport(new DefaultConversionService());
        meta = new EntityMeta();
        meta.setPrimaryKeyType(Long.class);
        meta.setAccessor(new MapBackedAccessor());
    }

    @Test
    void buildTreeUsesParentGroupingAndRespectsDepth() {
        List<Map<String, Object>> nodes = List.of(
                node(1L, null, "root"),
                node(2L, 1L, "child-a"),
                node(3L, 1L, "child-b"),
                node(4L, 2L, "grandchild"));

        List<TreeNode> result = entityTreeSupport.buildTree(nodes, meta, null, 2);

        assertThat(result).hasSize(1);
        TreeNode root = result.getFirst();
        assertThat(root.label()).isEqualTo("root");
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().getFirst().children()).isEmpty();
    }

    @Test
    void parseParentIdReturnsNullWhenValueInvalid() {
        assertThat(entityTreeSupport.parseParentId(meta, "bad-id")).isNull();
    }

    @Test
    void resolveDepthFallsBackToEntityConfig() {
        meta.setTreeMaxDepth(6);
        assertThat(entityTreeSupport.resolveDepth(meta, null)).isEqualTo(6);
        assertThat(entityTreeSupport.resolveDepth(meta, 2)).isEqualTo(2);
    }

    private static Map<String, Object> node(Long id, Long parentId, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("parentId", parentId);
        node.put("name", name);
        return node;
    }

    private static class MapBackedAccessor implements BeanAccessor {
        @Override
        public Object get(Object bean, String propertyName) {
            return bean instanceof Map<?, ?> map ? map.get(propertyName) : null;
        }

        @Override
        public void set(Object bean, String propertyName, Object value) {
            if (bean instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                map.put(propertyName, value);
            }
        }

        @Override
        public <T> T newInstance() {
            throw new UnsupportedOperationException();
        }
    }
}
