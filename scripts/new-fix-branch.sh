#!/usr/bin/env bash
# scripts/new-fix-branch.sh — 创建标准修复分支
#
# 用法：
#   bash scripts/new-fix-branch.sh <issue-number> <short-desc>
#
# 示例：
#   bash scripts/new-fix-branch.sh 34 key-date-entry-missing
#
# 行为：
#   基于当前 master HEAD 创建 fix/issue-{N}-{desc} 分支并切换

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

NUM="${1:?用法: new-fix-branch.sh <issue-number> <short-desc>}"
DESC="${2:?用法: new-fix-branch.sh <issue-number> <short-desc>}"

BRANCH="fix/issue-${NUM}-${DESC}"

# 确保基于最新 master
git checkout master
git pull origin master --ff-only 2>/dev/null || true

git checkout -b "${BRANCH}"
echo "[new-fix-branch.sh] 已切换到分支：${BRANCH}"
