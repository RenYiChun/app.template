# 加密与 Coder 设计说明

本文档说明框架的加密与 Coder 设计、与主流方案的差异，以及这样设计的优势。

---

## 一、设计概览

框架通过 `TemplateEncryptService` 统一处理两类需求：

1. **密码编码**：`encode`、`matches`（单向哈希，用于用户密码、OAuth2 client secret 等）
2. **配置解密**：`decode`（可逆解密，用于配置文件中的敏感值，如 `aENC(...)` 包装的数据库密码）

**编码格式**与 Spring Security 的 `DelegatingPasswordEncoder` 一致：`{encoderId}ciphertext`，例如：

- `{bcrypt}$2a$10$...` — bcrypt 哈希（仅 matches，不可 decode）
- `{RSA2048}Base64密文...` — RSA 加密封包（可 decode，用于配置解密）
- `{default}v2:100000:salt:hash` — PBKDF2 哈希（仅 matches）

**配置密文**使用 `aENC(...)` 包装，以区别于普通明文及 Jasypt 的 `ENC(...)`。采用**按需解密**：首次访问该配置项时才解密并缓存，减少启动时全量遍历与解密开销。

```yaml
spring:
  datasource:
    password: "aENC({RSA2048}Base64...)"  # 首次访问时解密
```

---

## 二、与主流方案的差异

### 2.1 与 Spring Security PasswordEncoder

| 维度 | 本框架 (TemplateEncryptService) | Spring Security (PasswordEncoder) |
|------|-------------------------------|----------------------------------|
| **能力** | encode + matches + **decode** | 仅 encode + matches |
| **格式** | `{id}encoded`（与 Delegating 一致） | 同上 |
| **可逆性** | 部分实现可逆（如 RSA2048），用于配置解密 | 仅单向哈希，不提供解密 |
| **扩展** | SPI 自定义 + 复用 Delegating 内所有 id | 通过 Delegating 或自定义 Bean 扩展 |

**核心区别**：本框架在 PasswordEncoder 上增加 `decode` 与 SPI 可插拔，既用于密码校验，又用于配置密文解密；Spring 只做密码哈希，不做解密。

---

### 2.2 与 Jasypt

| 维度 | 本框架 | Jasypt |
|------|--------|--------|
| **配置密文格式** | `aENC({id}ciphertext)`，内层自带 `{id}` | 通常 `ENC(ciphertext)`，算法/密钥单独配置 |
| **算法与密钥** | 每个值自带 `{id}`，不同 key 可用不同算法 | 全局一种算法（如 PBE），密钥在 `jasypt.encryptor.*` |
| **与密码体系** | 与 PasswordEncoder 统一，一套 encode/matches/decode | 只做配置加解密，与密码编码无关 |
| **扩展** | SPI 增加新 Coder 即可支持新算法 | 更换算法/配置或换 Encryptor 实现 |

**核心区别**：本框架将「密码编码」与「配置解密」统一在一套接口和格式中，且按值携带 `{id}` 支持多算法；Jasypt 是独立的配置加解密方案，格式与密码体系分离，通常单算法。

---

### 2.3 与 Spring Cloud Config 加解密

| 维度 | 本框架 | Spring Cloud Config |
|------|--------|---------------------|
| **依赖** | 仅依赖 Spring + 本模块，无需 Config Server | 依赖 Config Server 或客户端加解密组件 |
| **格式** | `aENC({id}...)`，自定 | 常见 `ENC(ciphertext)` |
| **密钥/算法** | 由 Coder 实现与 `security-key` 等配置决定 | 由 Config 的 encrypt 配置 / keystore 决定 |
| **场景** | 通用配置 + 密码编码 | 主要为远程配置加解密 |

**核心区别**：本框架是应用内、与密码编码统一的一套 Coder；Spring Cloud Config 属于配置中心生态，不负责密码编码，也不提供 `{id}` 多算法能力。

---

## 三、设计优势

### 3.1 统一能力入口

- **一个接口**同时承担：密码 encode/matches、配置 decode
- **一套格式** `{id}...` 既用于密码存储，也用于配置密文（再包一层 `aENC(...)`）
- 业务方无需同时引入 Jasypt + PasswordEncoder，也无需维护两套密钥与格式

### 3.2 多算法可插拔

- **SPI**：`ServiceLoader.load(TemplateEncryptService.class)` 加载所有实现（如 `DefaultTemplateDataCoder`、`TemplateRsa2048Coder`）
- **Spring Security 复用**：通过反射将 DelegatingPasswordEncoder 中的 bcrypt、noop、pbkdf2 等并入同一张表
- 新算法只需实现 `TemplateEncryptService` 并注册 SPI，即可同时支持密码与配置解密（若实现 decode）

### 3.3 按值选择算法

- 每个密文自带 `{id}` 前缀，同一应用内可混用 bcrypt、RSA2048、default 等
- 不受 Jasypt 那种「全局一种算法」限制，迁移、升级更灵活

### 3.4 默认编码器可配置

- `app.template.security.security-key` 指定默认算法（如 `default`、`bcrypt`、`RSA2048`）
- encode 时按默认算法，matches/decode 时按密文自带的 `{id}` 选择编码器

---

## 四、相关文档

- [详细配置教程 - 数据加密配置](详细配置教程.md#数据加密配置) — aENC、security-key、RSA 配置示例
- [框架收益](framework-benefits.md) — 声明式安全与本 Coder 的集成
- [框架设计优势 - 安全体系](architecture-advantages.md#六安全体系) — 安全能力一览
- [质量评分卡](quality-scorecard.md) — 安全相关评分与建议
