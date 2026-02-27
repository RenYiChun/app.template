package com.lrenyi.template.dataforge.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储无关的实体契约，定义统一主键及审计字段的语义。
 * <p>
 * JPA 实体通过 BaseEntity 实现，Mongo 实体通过 MongoBaseDocument 实现。
 * </p>
 *
 * @param <ID> 主键类型
 */
public interface DataforgePersistable<ID extends Serializable> extends Serializable {

    ID getId();

    void setId(ID id);

    LocalDateTime getCreateTime();

    void setCreateTime(LocalDateTime createTime);

    LocalDateTime getUpdateTime();

    void setUpdateTime(LocalDateTime updateTime);

    String getCreateBy();

    void setCreateBy(String createBy);

    String getUpdateBy();

    void setUpdateBy(String updateBy);

    Boolean getDeleted();

    void setDeleted(Boolean deleted);

    String getRemark();

    void setRemark(String remark);

    Long getVersion();

    void setVersion(Long version);
}
