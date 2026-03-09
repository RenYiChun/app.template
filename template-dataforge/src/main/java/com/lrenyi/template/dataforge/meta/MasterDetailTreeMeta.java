package com.lrenyi.template.dataforge.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 左树右表布局下左侧树的元数据。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MasterDetailTreeMeta {

    private String treeEntity = "";
    private String treeEntityLabel = "";
    private String treeIdField = "id";
    private String treeParentField = "parentId";
    private String treeLabelField = "name";
    private String treeSortField = "sortOrder";
    private String relationField = "";
    private String rootSelectionMode = "all";
    private boolean includeDescendants = false;
    private boolean hideTableSearchRelationField = true;
}
