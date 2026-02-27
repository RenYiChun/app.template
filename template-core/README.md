# Template Core

核心模块，提供加密、JSON、配置属性等基础能力。

- **加密与 Coder**：`TemplateEncryptService` 统一密码 encode/matches 与配置 `aENC(...)` 解密，格式与 Spring Security `{id}...` 一致，支持多算法 SPI。设计与对比详见 [加密与 Coder 设计说明](../docs/design/encryption-and-coder.md)。

> **注意**：Flow 流聚合引擎已独立为 `template-flow` 模块，使用说明见 [Flow 使用指导](../docs/guides/flow-usage-guide.md)。
