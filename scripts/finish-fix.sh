#!/usr/bin/env bash
# scripts/finish-fix.sh — 完成修复：merge 到 master + push + Issue 修改记录评论
#
# 用法（在 fix/* 分支上运行，也支持直接在 master 上调用）：
#   bash scripts/finish-fix.sh <issue> <version> "<方案>" "<修改文件，逗号分隔>" "<待回归项，逗号分隔>"
#
# 示例：
#   bash scripts/finish-fix.sh 62 v1.6.21 \
#       "themes.xml 增加 statusBarColor=transparent + windowLightStatusBar=true" \
#       "app/src/main/res/values/themes.xml" \
#       "打开 App，状态栏时间/信号图标清晰可见"
#
# 行为：
#   1. 若在 fix/* 分支：切回 master，merge（no-ff），push master
#   2. 调用 issue-comment-fix.sh 写入标准修改记录评论

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

ISSUE="${1:?用法: finish-fix.sh <issue> <version> <方案> <文件> <待回归>}"
VERSION="${2:?用法: finish-fix.sh <issue> <version> <方案> <文件> <待回归>}"
SOLUTION="${3:?用法: finish-fix.sh <issue> <version> <方案> <文件> <待回归>}"
FILES="${4:?用法: finish-fix.sh <issue> <version> <方案> <文件> <待回归>}"
REGRESSION="${5:?用法: finish-fix.sh <issue> <version> <方案> <文件> <待回归>}"

FIX_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [[ "${FIX_BRANCH}" != "master" ]]; then
    echo "[finish-fix.sh] 切回 master 并 merge ${FIX_BRANCH}..."
    git checkout master
    git merge "${FIX_BRANCH}" --no-ff -m "Merge ${FIX_BRANCH}

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>"
    echo "[finish-fix.sh] 推送 master..."
    git push origin master
else
    echo "[finish-fix.sh] 当前在 master，跳过 merge 步骤"
fi

echo "[finish-fix.sh] 写入 Issue #${ISSUE} 修改记录..."
bash scripts/issue-comment-fix.sh "${ISSUE}" "${VERSION}" "${SOLUTION}" "${FILES}" "${REGRESSION}"

echo "[finish-fix.sh] 完成！Issue #${ISSUE} 修改记录已写入。"
