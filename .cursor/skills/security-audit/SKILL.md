---
name: security-audit
description: Perform security audit for code and configuration. Use when user asks for security review, vulnerability scan, security audit, or secure coding check.
---

# 安全审计

## Quick Start

1. Search for common vulnerability patterns
2. Check sensitive data handling
3. Review authentication/authorization
4. Output findings with severity and remediation

## Audit Checklist

### 1. 注入类

- SQL 拼接（未用 PreparedStatement/ORM 参数化）
- 命令注入（Runtime.exec、ProcessBuilder 传入未校验输入）
- 路径遍历（File 操作使用用户输入）
- XSS（输出未转义的用户输入）

### 2. 敏感信息

- 密码、密钥、Token 硬编码
- 敏感数据明文日志
- 异常信息泄露堆栈给前端

### 3. 认证与授权

- 弱密码策略
- 缺少 CSRF/XSS 防护
- 越权访问（未校验资源归属）

### 4. 依赖与配置

- 已知漏洞依赖（CVE）
- 不安全的反序列化
- 调试/Admin 接口暴露在生产

### 5. 加密与传输

- 弱加密算法（MD5、DES、弱 TLS）
- 自实现加解密
- HTTP 传输敏感数据

## Output Template

```markdown
# 安全审计报告

## 🔴 Critical
| 问题 | 位置 | 风险 | 修复建议 |

## 🟡 High
...

## 🟢 Medium/Low
...
```

## Severity Definitions

- **Critical**: 可直接导致数据泄露或系统沦陷
- **High**: 需特定条件利用，但影响大
- **Medium**: 需较多前置条件
- **Low**: 理论风险或加固建议
