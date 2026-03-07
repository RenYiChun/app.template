---
name: release-notes
description: Generate release notes from git history and version changes. Use when user asks for release notes, version announcement, or what's new in version X.
---

# 发布说明生成

## Quick Start

1. Determine version range (e.g. v2.4.0..v2.4.2 or since last tag)
2. Run `git log` for commits in range
3. Categorize and summarize changes
4. Output user-facing release notes (not raw commit log)

## Differences from Changelog

- **Changelog**: Technical, commit-oriented, for maintainers
- **Release notes**: User-facing, feature-focused, for consumers

Focus on **what changed for users**, not internal refactors. Merge minor fixes into "Bug fixes" etc.

## Output Template

```markdown
# Release Notes - [Version]

**Release Date**: YYYY-MM-DD

## Highlights

[2-3 sentence summary of the release]

## What's New

### [Feature Category]
- **Feature name**: Description in user terms

### Improvements
- Improvement 1
- Improvement 2

### Bug Fixes
- Fixed issue X
- Fixed issue Y

### Breaking Changes (if any)
- Change and migration note

## Upgrade Guide

[If needed: version requirement, config changes, migration steps]
```

## Workflow

1. `git log <from>..<to> --pretty=format:"%h|%s"` to get commits
2. Group by: Features, Improvements, Bug Fixes, Breaking
3. Rewrite commit messages in user language (avoid internal jargon)
4. Add Highlights and Upgrade Guide when relevant
5. Omit: chore, refactor-only, internal-only changes unless significant
