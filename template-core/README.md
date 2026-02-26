# Template Core

核心模块，提供加密、JSON、Flow 流聚合等能力。

- **Flow 流聚合**：多路数据按 key 汇聚，支持拉取/推送两种模式。使用说明见 [Flow 使用指导](../docs/flow-usage-guide.md)。
- **加密与 Coder**：`TemplateEncryptService` 统一密码 encode/matches 与配置 `aENC(...)` 解密，格式与 Spring Security `{id}...` 一致，支持多算法 SPI。设计与对比详见 [加密与 Coder 设计说明](../docs/encryption-and-coder-design.md)。
