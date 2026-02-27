package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 认证域虚拟实体，用于挂载 EntityAction（captcha、login、logout、me）。
 * crudEnabled=false，仅通过 POST/GET /api/auth/0/{actionName} 调用。
 */
@Setter
@Getter
@DataforgeEntity(pathSegment = "auth", displayName = "认证", crudEnabled = false, generateDtos = false)
public class Auth extends BaseEntity<Long> {
}
