---
name: tech-debt
description: Identify and suggest improvements for technical debt. Use when user asks for tech debt analysis, code quality assessment, refactoring opportunities, or technical debt review.
---

# 技术债务分析

## Quick Start

1. Explore codebase structure and key modules
2. Identify debt patterns from checklist
3. Prioritize by impact and effort
4. Output structured report with actionable items

## Debt Categories

### 1. 代码质量

- 重复代码（DRY 违反）
- 过长方法/类（>50 行方法，职责不清的类）
- 复杂度过高（圈复杂度、嵌套过深）
- 命名不清、魔法数字
- 空 catch、异常吞掉

### 2. 架构与设计

- 违反单一职责、开闭原则
- 循环依赖、紧耦合
- 缺少抽象、过度继承
- 上帝类、贫血模型

### 3. 测试与可维护性

- 缺少单元测试的关键逻辑
- 测试依赖外部资源（未 mock）
- 硬编码配置、环境耦合

### 4. 依赖与版本

- 过期依赖、已知漏洞
- 版本冲突、传递依赖臃肿

### 5. 文档与规范

- 缺少关键注释/文档
- 与 Checkstyle、项目规范不一致

## Output Template

```markdown
# 技术债务分析报告

## 高优先级
| 问题 | 位置 | 建议 | 预估effort |
|------|------|------|------------|

## 中优先级
...

## 低优先级
...
```

## Prioritization

- **高**: 影响安全/稳定性，或严重阻碍开发
- **中**: 影响可维护性，建议近期处理
- **低**: 改进项，可择机处理

## Additional Resources

- See [checklist.md](checklist.md) for detailed checklist
