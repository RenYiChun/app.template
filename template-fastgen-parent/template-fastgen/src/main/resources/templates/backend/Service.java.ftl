package ${basePackage}.service;

import ${basePackage}.domain.${entity.simpleName};
import java.util.List;

/**
 * Generated from @Domain ${entity.simpleName}.
 * Implement this interface in your module for custom logic (Strategy C).
 */
public interface ${entity.simpleName}Service {

    /**
     * 分页查询${entity.displayName}列表。
     *
     * @param page 页码(从1开始)
     * @param size 每页大小
     * @return 列表
     */
    List<${entity.simpleName}> listByPage(int page, int size);

    /**
     * 根据 ID 查询${entity.displayName}。
     *
     * @param id 主键
     * @return 实体对象,不存在返回 null
     */
    ${entity.simpleName} getById(Long id);

    /**
     * 新增${entity.displayName}。
     *
     * @param entity 实体对象
     * @return 保存后的实体(含生成的 ID)
     */
    ${entity.simpleName} save(${entity.simpleName} entity);

    /**
     * 更新${entity.displayName}。
     *
     * @param entity 实体对象
     * @return 更新后的实体
     */
    ${entity.simpleName} update(${entity.simpleName} entity);

    /**
     * 根据 ID 删除${entity.displayName}。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
