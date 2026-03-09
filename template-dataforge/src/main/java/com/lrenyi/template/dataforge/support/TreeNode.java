package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * tree 接口节点：id、label、parentId、children，可选 disabled、leaf。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TreeNode(
        Object id,
        String label,
        Object parentId,
        List<TreeNode> children,
        Boolean disabled,
        Boolean leaf) {

    public TreeNode(Object id, String label, Object parentId, List<TreeNode> children) {
        this(id, label, parentId, children, null, null);
    }
}
