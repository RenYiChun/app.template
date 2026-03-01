package com.lrenyi.template.dataforge.controller;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 专门用于暴露 Dataforge 完整元数据的控制器。
 * 相比于 OpenApiController (/api/docs)，本接口提供未经 OpenAPI 转换的原始 EntityMeta 数据，
 * 包含更多 Dataforge 特有的配置信息（如 Java 类型、UI 组件配置、审计选项等），
 * 适用于元数据查看器、低代码配置平台或前端离线开发。
 */
@RestController
@RequestMapping("${app.dataforge.api-prefix:/api}/metadata")
public class MetadataController {
    
    private final EntityRegistry entityRegistry;
    
    public MetadataController(EntityRegistry entityRegistry) {
        this.entityRegistry = entityRegistry;
    }
    
    /**
     * 获取所有实体的完整元数据列表。
     *
     * @return 实体元数据列表
     */
    @GetMapping("/entities")
    public List<EntityMeta> getAllEntities() {
        Collection<EntityMeta> all = entityRegistry.getAll();
        // 为了前端展示稳定，按实体名称排序
        return all.stream().sorted(Comparator.comparing(EntityMeta::getEntityName)).toList();
    }
}
