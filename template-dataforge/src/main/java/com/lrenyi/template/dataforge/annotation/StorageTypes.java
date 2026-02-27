package com.lrenyi.template.dataforge.annotation;

/**
 * 存储类型常量，用于按 storage 路由到对应的 {@link com.lrenyi.template.dataforge.service.StorageTypeAwareCrudService}。
 * <p>
 * 采用 String 标识，便于 SPI 式扩展：新增存储实现时无需修改框架，只需实现
 * {@link com.lrenyi.template.dataforge.service.StorageTypeAwareCrudService} 并注册为 Bean，
 *  返回自定义类型名（如 "redis"、"elasticsearch"），
 * 实体注解使用 {@code @DataforgeEntity(storage = "redis")} 即可。
 * </p>
 */
public final class StorageTypes {
    
    /** JPA 关系型数据库 */
    public static final String JPA = "jpa";
    
    /** MongoDB 文档数据库 */
    public static final String MONGO = "mongo";
    
    private StorageTypes() {
    }
}
