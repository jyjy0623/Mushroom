#!/usr/bin/env bash
# scripts/commit.sh — 暂存文件并提交
#
# 用法：
#   bash scripts/commit.sh "commit message" [file1] [file2] ...
#   不传文件时等同于 git add -p（交互）；传文件时暂存指定文件
#
# 示例：
#   bash scripts/commit.sh "fix: 修复XX问题 refs #33" \
#       feature/feature-task/.../DailyTaskListScreen.kt \
#       feature/feature-task/.../TaskViewModels.kt

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

MSG="${1:?用法: commit.sh <message> [files...]}"
shift

if [[ $# -gt 0 ]]; then
    git add "$@"
else
    echo "[commit.sh] 未指定文件，暂存所有已修改的被跟踪文件..."
    git add -u
fi

git commit -m "${MSG}

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
echo "[commit.sh] 提交完成：$(git log --oneline -1)"
