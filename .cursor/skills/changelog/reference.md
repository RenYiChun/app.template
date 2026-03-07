# Changelog 参考

## Conventional Commits 类型

| Type | 说明 |
|------|----|

| feat | 新功能 |
| fix | Bug 修复 |
| docs | 文档变更 |
| style | 代码格式（不影响逻辑） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试相关 |
| chore | 构建、依赖、配置等 |
| ci | CI 相关 |

## Git 命令参考

```bash
# 格式化输出：hash|subject|date
git log -50 --pretty=format:"%h|%s|%ad" --date=short

# 带作者
git log -50 --pretty=format:"%h|%s|%an|%ad" --date=short

# 指定分支
git log origin/main..HEAD --oneline

# 两次 tag 之间
git log v1.0.0..v2.0.0 --pretty=format:"%h %s"
```

## Keep a Changelog 规范

参考 https://keepachangelog.com/ 的版本格式：

- Added: 新功能
- Changed: 行为变更
- Deprecated: 即将废弃
- Removed: 已移除
- Fixed: Bug 修复
- Security: 安全相关
