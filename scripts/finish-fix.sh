#!/usr/bin/env bash
# scripts/finish-fix.sh — 完成修复：merge 到 master + push + Issue comment
#
# 用法（在 fix/* 分支上运行）：
#   bash scripts/finish-fix.sh <issue-number> "<根因>" "<方案>"
#
# 示例：
#   bash scripts/finish-fix.sh 34 \
#       "设置页缺少关键奖励时间入口" \
#       "在 SettingsScreen 新增 KeyDate 入口，注册导航路由"
#
# 行为：
#   1. 获取当前分支名和最新 commit hash
#   2. 切回 master，merge（no-ff）
#   3. push master
#   4. gh issue comment 写入根因+方案+commit hash

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

ISSUE="${1:?用法: finish-fix.sh <issue-number> <根因> <方案>}"
ROOT_CAUSE="${2:?用法: finish-fix.sh <issue-number> <根因> <方案>}"
SOLUTION="${3:?用法: finish-fix.sh <issue-number> <根因> <方案>}"

FIX_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
COMMIT_HASH="$(git log --oneline -1 | awk '{print $1}')"

if [[ "${FIX_BRANCH}" == "master" ]]; then
    echo "[finish-fix.sh] 错误：请在 fix/* 分支上运行此脚本" >&2
    exit 1
fi

echo "[finish-fix.sh] 切回 master 并 merge ${FIX_BRANCH}..."
git checkout master
git merge "${FIX_BRANCH}" --no-ff -m "Merge ${FIX_BRANCH}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"

echo "[finish-fix.sh] 推送 master..."
git push origin master

echo "[finish-fix.sh] 写入 Issue #${ISSUE} comment..."
gh issue comment "${ISSUE}" --body "已修复，根因：${ROOT_CAUSE} 方案：${SOLUTION} 关联提交：${COMMIT_HASH}"

echo "[finish-fix.sh] 完成！分支 ${FIX_BRANCH} 已 merge，Issue #${ISSUE} 已 comment。"
