#!/usr/bin/env bash
# SonarLint CLI 本地扫描（由 mvn verify -Psonarlint 调用，无需 Docker）
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INSTALL_DIR="$PROJECT_ROOT/target/sonarlint-cli"
REPORT_PATH="$PROJECT_ROOT/target/sonarlint-report.json"

cd "$PROJECT_ROOT"
mkdir -p target

# 检查 pip 是否可用
pip_cmd=""
if command -v pip3 &>/dev/null; then
  pip_cmd="pip3"
elif python3 -m pip --version &>/dev/null 2>&1; then
  pip_cmd="python3 -m pip"
fi

if [ -z "$pip_cmd" ]; then
  echo "[WARN] SonarLint CLI 需要 Python pip，当前环境未安装。跳过扫描。"
  echo "      安装方式: apt install python3-pip && pip3 install python3-venv"
  exit 0
fi

# 安装 sonarlint-cli 到 target（若尚未安装）
if [ ! -d "$INSTALL_DIR/sonarlintcli" ]; then
  echo "初次运行：安装 sonarlint-cli..."
  $pip_cmd install --target "$INSTALL_DIR" "git+https://github.com/code-freak/sonarlint-cli.git" || exit 1
fi

echo "=== SonarLint CLI 扫描（仅 src/main/java）==="
PYTHONPATH="$INSTALL_DIR" python3 -m sonarlintcli.cli analyse \
  "$PROJECT_ROOT/**/src/main/java/**/*.java" \
  --output "$REPORT_PATH"

echo "报告: $REPORT_PATH"
