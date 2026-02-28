# Changelog 示例

## 示例 1：基于 Conventional Commits

**Commits:**

```
feat(auth): add JWT support
fix(api): handle null response
docs: update README
refactor(core): simplify FlowManager
```

**Output:**

```markdown
### Features

- (auth) Add JWT support

### Bug Fixes

- (api) Handle null response

### Documentation

- Update README

### Refactoring

- (core) Simplify FlowManager
```

## 示例 2：中文自由格式

**Commits:**

```
优化代码
增加单元测试
修复空指针问题
增加文档
```

**Output:**

```markdown
### Refactoring

- 优化代码

### Tests

- 增加单元测试

### Bug Fixes

- 修复空指针问题

### Documentation

- 增加文档
```

## 示例 3：合并重复项

**Commits:**

```
优化代码
优化代码
优化全局资源管理
```

**Output:**

```markdown
### Refactoring

- 优化代码
- 优化全局资源管理
```
