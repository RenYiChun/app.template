---
name: readme
description: Generate or update README from project structure and configuration. Use when user asks for README, project documentation, or to create/update README.md.
---

# README 生成

## Quick Start

1. Analyze project: pom.xml, package structure, modules, main entry
2. Extract: name, description, version, dependencies, build commands
3. Generate README following template
4. Prefer existing content when updating; only fill empty or improve sections

## Project Analysis Sources

- Root `pom.xml`: name, description, url, modules
- `README.md`: preserve existing sections
- Directory structure: modules, packages
- `docs/`: link to existing docs

## README Template

```markdown
# [Project Name]

[One-line description from pom.xml or user]

## Features

- Feature 1
- Feature 2

## Requirements

- Java X
- Maven X

## Getting Started

### Build

\`\`\`bash
mvn clean install
\`\`\`

### Usage

[Basic usage or module intro]

## Modules

| Module | Description |
|--------|-------------|
| module-a | ... |

## Documentation

- [Link to docs](docs/xxx.md)

## License

[From pom.xml license]
```

## Rules

- Keep concise; avoid redundant content
- Use project's actual name, description, and modules
- For updates: merge with existing README, don't overwrite user-written content
- Support both English and 中文 based on project language
