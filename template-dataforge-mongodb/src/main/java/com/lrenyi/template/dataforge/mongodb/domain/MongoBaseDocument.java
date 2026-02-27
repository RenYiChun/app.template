package com.lrenyi.template.dataforge.mongodb.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.lrenyi.template.dataforge.domain.DataforgePersistable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

/**
 * 平台 MongoDB 实体公共基类，实现 {@link DataforgePersistable}，
 * 包含统一主键及审计字段：id、createTime、updateTime、createBy、updateBy、deleted、remark、version。
 * <p>
 * 所有使用 MongoDB 存储的 {@code @DataforgeEntity(storage = StorageTypes.MONGO)} 实体需继承此类。
 * 主键类型支持：String（ObjectId hex）、Long、{@link org.bson.types.ObjectId}。
 * </p>
 *
 * @param <ID> 主键类型
 */
@Getter
@Setter
public abstract class MongoBaseDocument<ID extends Serializable> implements DataforgePersistable<ID> {
    
    @Id
    private ID id;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Boolean deleted = false;
    private String remark;
    private Long version = 0L;
}
