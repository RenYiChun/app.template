package com.lrenyi.template.dataforge.service;

/**
 * 按实体 pathSegment 提供 CRUD 的标记接口。实现此接口的 Bean 会被 {@link EntityCrudServiceRouter} 收集，
 * 仅处理 {@link #getPathSegment()} 返回的实体请求，其余实体仍走默认实现。
 * <p>
 * 业务方可为不同实体提供独立实现类（如 UsersCrudService、OrdersCrudService），
 * 通常继承 {@link DelegatingEntityCrudService} 并只重写该实体需要的方法；同一 pathSegment 仅应有一个实现。
 */
public interface PathSegmentAwareCrudService extends EntityCrudService {
    
    /**
     * 该实现负责的实体 pathSegment（如 "users"、"orders"），与
     * {@link com.lrenyi.template.dataforge.meta.EntityMeta#getPathSegment()} 一致。
     */
    String getPathSegment();
}
