# 安全审计报告

**审计日期**：2025-02  
**项目**：app.template  
**最近更新**：已完成 High 修复，文档与注释已补充

---

## 🔴 Critical

| 问题 | 位置 | 风险 | 修复建议 |
|----|----|----|------|
| 无  | -  | -  | -    |

---

## 🟡 High（已修复）

| 问题                           | 状态    | 修复说明                                                                             |
|------------------------------|-------|----------------------------------------------------------------------------------|
| RSA 使用默认 PKCS#1 v1.5 padding | ✅ 已修复 | `RsaUtils` 已改用 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`，新加密数据带 `OAEP:` 前缀，旧数据保持兼容 |
| 密码哈希使用 SHA-1                 | ✅ 已修复 | `DefaultTemplateDataCoder` 已迁移至 PBKDF2-HMAC-SHA256（新格式 `v2:`），旧 SHA-1 格式仍可验证     |

---

## 🟢 Medium（已处理）

| 问题          | 状态    | 修复说明                                                                   |
|-------------|-------|------------------------------------------------------------------------|
| 默认密钥打包在资源中  | ✅ 已处理 | 已在 [配置参考](../getting-started/config-reference.md) 生产环境配置中补充 RSA 密钥安全说明 |
| 路径遍历风险（已接受） | -     | spotbugs 已排除，调用方仅传入可信路径                                                |

---

## 🟢 Low（已处理）

| 问题                    | 状态    | 修复说明                                                                         |
|-----------------------|-------|------------------------------------------------------------------------------|
| 依赖版本检查                | 建议定期  | 执行 `.\mvnw.cmd versions:display-dependency-updates` 和 OWASP Dependency-Check |
| Digests.md5/sha1 使用说明 | ✅ 已处理 | 已在 `Digests.java` 类及方法添加 Javadoc，注明仅用于非安全场景                                  |

---

## ✅ 已通过检查

| 检查项      | 结果                                                                                    |
|----------|---------------------------------------------------------------------------------------|
| 命令注入     | 未发现 Runtime.exec / ProcessBuilder                                                     |
| SQL 注入   | 未发现原生 SQL 拼接                                                                          |
| 反序列化     | 未发现 ObjectInputStream.readObject                                                      |
| 敏感数据明文日志 | OAuth2 password/client_secret 已从 authorization 中过滤；OauthUtilService 仅记录 host、expireAt |
| CSRF     | OAuth2 endpoints 已配置 CSRF 忽略（符合 OAuth2 规范）                                            |
| 密码模式     | OAuth2 密码模式使用 PasswordEncoder.matches，未明文存储                                           |

---

## 修复优先级建议

1. **高**：RsaUtils 使用 OAEP padding
2. **高**：DefaultTemplateDataCoder 迁移至 bcrypt（或与 Spring Security PasswordEncoder 对齐）
3. **中**：补充生产环境密钥配置说明
4. **低**：定期依赖漏洞扫描
