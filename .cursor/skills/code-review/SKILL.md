---
name: code-review
description: Review code for quality, security, and maintainability following SOLID, KISS and single-responsibility principles. Use when reviewing pull requests, examining code changes, or when the user asks for a code review.
---

# 代码审查

## Quick Start

When reviewing code:

1. Check correctness and potential bugs
2. Verify security best practices
3. Assess readability and maintainability (SOLID, KISS, single responsibility)
4. Ensure adequate tests
5. Check for code duplication and reuse opportunities

## Review Checklist

### 正确性与健壮性

- [ ] 逻辑正确，边界情况已处理
- [ ] 无空指针、越界等潜在问题
- [ ] 异常处理完整合理

### 安全

- [ ] 无 SQL 注入、XSS 等漏洞
- [ ] 敏感信息不硬编码
- [ ] 输入校验完备

### 设计原则

- [ ] 单一职责：每个类/方法职责清晰
- [ ] 符合 SOLID 原则
- [ ] 符合 KISS 原则，避免过度设计
- [ ] 尽量复用已有代码，避免重复

### 可读性与风格

- [ ] 命名清晰易懂
- [ ] 函数/方法规模适中
- [ ] 遵循项目 Checkstyle（Google Java Style，行宽 140）
- [ ] 注释必要且准确

### 测试

- [ ] 变更具备对应测试
- [ ] 测试覆盖关键路径和边界

## Providing Feedback

Format feedback as:

- 🔴 **Critical**: 合并前必须修复
- 🟡 **Suggestion**: 建议改进
- 🟢 **Nice to have**: 可选优化

For each issue provide:

- 问题描述
- 具体代码位置
- 修复建议或示例代码

## Additional Resources

- For detailed coding standards, see [STANDARDS.md](STANDARDS.md)
- For example reviews, see [examples.md](examples.md)
