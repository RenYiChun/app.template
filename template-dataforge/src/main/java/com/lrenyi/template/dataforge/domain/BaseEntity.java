package com.lrenyi.template.dataforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 平台实体公共基类，包含统一主键及审计字段。
 * <p>
 * 所有 {@code @DataforgeEntity} 实体必须继承此类。
 * 主键类型支持：Long、Integer、UUID、String。
 * </p>
 * <p>创建人、更新人（createBy、updateBy）可：</p>
 * <ul>
 *   <li>由业务在创建/更新时显式赋值；</li>
 *   <li>或通过 JPA Auditing 自动填充：应用启用 {@code @EnableJpaAuditing} 并提供 {@code AuditorAware} Bean 后，
 *       {@link AuditingEntityListener} 会按当前审计员自动填充。</li>
 * </ul>
 *
 * @param <ID> 主键类型
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID extends Serializable> implements Serializable {

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