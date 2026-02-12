---
name: changelog
description: Generate changelog from git commit history. Use when user asks for changelog, release notes, version summary, or CHANGELOG content.
---

# Changelog 生成

## Quick Start

1. Run `git log` to retrieve commit history
2. Parse and categorize commits
3. Format output following the template below
4. Support version/tag filtering when user specifies

## Workflow

### Step 1: Get Commit Range

Default: recent commits (e.g. since last tag or last 50 commits).

```bash
# Since last tag
git log $(git describe --tags --abbrev=0 2>/dev/null || echo "")..HEAD --pretty=format:"%h %s"

# Last N commits
git log -50 --pretty=format:"%h|%s|%ad" --date=short

# Between two tags/versions
git log v2.4.0..v2.4.2 --pretty=format:"%h %s"
```

### Step 2: Categorize Commits

If using Conventional Commits (feat, fix, docs, refactor, etc.), group by type. Otherwise, infer category from keywords:

| 关键词/Type | 分类 |
|-------------|------|
| feat:, 新增, 增加, add | Features |
| fix:, 修复, 修正, fix | Bug Fixes |
| docs:, 文档, document | Documentation |
| refactor:, 重构, 优化, refactor | Refactoring |
| test:, 测试, test | Tests |
| perf:, 性能, performance | Performance |
| chore:, 构建, 配置 | Chores |

### Step 3: Deduplicate & Merge

- Merge similar/same messages (e.g. multiple "优化代码")
- Prefer detailed commits over brief ones

## Output Template

Use this structure:

```markdown
# Changelog

## [Version] (YYYY-MM-DD)

### Features
- Description of new feature

### Bug Fixes
- Description of fix

### Refactoring
- Description of change

### Documentation
- Description

### Tests
- Description

### Performance
- Description

### Chores
- Description
```

## Output Format Rules

- One entry per line, prefix with `- `
- Start with verb in past tense or imperative (中文可用「优化」「修复」等)
- Include short hash in parentheses when useful: `(abc1234)`
- Group by category, omit empty sections
- If no version given, use `[Unreleased]` or current date

## Examples

**Input**: User says "生成最近版本的 changelog"

**Output**:
```markdown
# Changelog

## [2.4.2.1-SNAPSHOT] (2025-02-12)

### Refactoring
- 优化 FlowJoinerEngine 和 DefaultProgressTracker 逻辑
- 增强 CaffeineFlowStorage 并发处理能力
- 简化 FlowProgressDisplay 输出
- 优化全局资源管理
- 移除向后兼容的冗余代码

### Documentation
- 增加文档
- 优化代码质量规则

### Tests
- 增加单元测试
- 删除冗余单元测试代码

### Chores
- 更新版本号至 2.4.2.1-SNAPSHOT
```

## Additional Resources

- For Conventional Commits spec, see [reference.md](reference.md)
- For more output examples, see [examples.md](examples.md)
