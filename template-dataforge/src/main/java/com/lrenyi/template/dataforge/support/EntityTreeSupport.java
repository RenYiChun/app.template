package com.lrenyi.template.dataforge.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

/**
 * 树形实体的查询辅助：统一处理 parentId 解析和树构建，避免控制器堆积细节逻辑。
 */
public class EntityTreeSupport {

    private final ConversionService conversionService;

    public EntityTreeSupport(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public int resolveDepth(EntityMeta meta, Integer maxDepth) {
        if (maxDepth != null && maxDepth > 0) {
            return maxDepth;
        }
        return meta.getTreeMaxDepth() > 0 ? meta.getTreeMaxDepth() : 10;
    }

    public Object parseParentId(EntityMeta meta, String parentId) {
        if (parentId == null || parentId.isBlank() || "null".equalsIgnoreCase(parentId)) {
            return null;
        }
        try {
            Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
            return conversionService.convert(parentId.trim(), pkType);
        } catch (ConversionException | IllegalArgumentException e) {
            return null;
        }
    }

    public List<TreeNode> buildTree(List<?> allNodes, EntityMeta meta, Object parentId, int maxDepth) {
        if (allNodes == null || allNodes.isEmpty() || maxDepth <= 0 || meta.getAccessor() == null) {
            return List.of();
        }
        String parentField = resolveParentField(meta);
        String nameField = resolveNameField(meta);
        Map<Object, List<Object>> groupedByParent = new LinkedHashMap<>();
        for (Object node : allNodes) {
            Object nodeParentId = meta.getAccessor().get(node, parentField);
            groupedByParent.computeIfAbsent(nodeParentId, ignored -> new ArrayList<>()).add(node);
        }
        return buildTree(groupedByParent, meta, parentField, nameField, parentId, maxDepth);
    }

    private List<TreeNode> buildTree(Map<Object, List<Object>> groupedByParent,
            EntityMeta meta,
            String parentField,
            String nameField,
            Object parentId,
            int maxDepth) {
        if (maxDepth <= 0) {
            return List.of();
        }
        List<Object> children = groupedByParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<TreeNode> result = new ArrayList<>(children.size());
        for (Object node : children) {
            Object id = meta.getAccessor().get(node, "id");
            Object nodeParentId = meta.getAccessor().get(node, parentField);
            Object nameVal = meta.getAccessor().get(node, nameField);
            String label = nameVal != null ? nameVal.toString() : "";
            List<TreeNode> childNodes = buildTree(groupedByParent, meta, parentField, nameField, id, maxDepth - 1);
            result.add(new TreeNode(id, label, nodeParentId, childNodes, null, childNodes.isEmpty()));
        }
        return result;
    }

    private static String resolveParentField(EntityMeta meta) {
        return meta.getTreeParentField() != null && !meta.getTreeParentField().isBlank()
                ? meta.getTreeParentField() : "parentId";
    }

    private static String resolveNameField(EntityMeta meta) {
        return meta.getTreeNameField() != null && !meta.getTreeNameField().isBlank()
                ? meta.getTreeNameField() : "name";
    }
}
