package com.lrenyi.template.dataforge.permission;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.FilterCondition;
import java.util.List;

/**
 * 数据权限：为 options、tree、batch-lookup、search 等查询追加过滤条件。
 * 应用层实现此接口并注册为 Bean 后，当 {@code meta.isEnableDataPermission()} 为 true 时，
 * Controller 会将返回的 FilterCondition 合并到 ListCriteria 中。
 */
public interface DataPermissionApplicator {

    /**
     * 根据当前用户与实体元数据返回需要追加的过滤条件。
     * 例如部门数据权限：仅能查看本部门及子部门数据；个人数据权限：仅能查看自己创建的数据。
     *
     * @param meta 实体元数据
     * @return 追加的过滤条件，可为空列表，不可为 null
     */
    List<FilterCondition> getDataPermissionFilters(EntityMeta meta);
}
