package com.lrenyi.template.dataforge.jpa.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.lrenyi.template.dataforge.domain.DataforgePersistable;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 平台 JPA 实体公共基类，实现 {@link DataforgePersistable}，包含统一主键及审计字段。
 * <p>
 * 所有使用 JPA 存储的 {@code @DataforgeEntity} 实体需继承此类。
 * 主键类型支持：Long、Integer、UUID、String。
 * </p>
 *
 * @param <ID> 主键类型
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID extends Serializable> implements DataforgePersistable<ID> {
    
    @Id
    @DataforgeId
    private ID id;
    
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
    
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    
    @CreatedBy
    @Column(name = "create_by", updatable = false, length = 64)
    private String createBy;
    
    @LastModifiedBy
    @Column(name = "update_by", length = 64)
    private String updateBy;
    
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
    
    @Column(name = "remark", length = 512)
    private String remark;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    @PrePersist
    protected void onPrePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }
    
    @PreUpdate
    protected void onPreUpdate() {
        updateTime = LocalDateTime.now();
    }
}
