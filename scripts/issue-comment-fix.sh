#!/usr/bin/env bash
# scripts/issue-comment-fix.sh — 合入后向 Issue 写入标准修改记录评论
#
# 用法：
#   bash scripts/issue-comment-fix.sh <issue-number> <version> "<方案简述>" "<文件列表>" "<待回归项>"
#
# 示例：
#   bash scripts/issue-comment-fix.sh 62 v1.6.21 \
#       "themes.xml 增加 statusBarColor=transparent + windowLightStatusBar=true" \
#       "app/src/main/res/values/themes.xml" \
#       "打开 App，状态栏时间/信号/电量图标清晰可见（深色图标）"
#
# 行为：
#   1. 获取当前最新 commit hash 和 message
#   2. 在 Issue 写入标准修改记录评论（版本/方案/文件/commit/待回归）

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

ISSUE="${1:?用法: issue-comment-fix.sh <issue> <version> <方案> <文件> <待回归>}"
VERSION="${2:?用法: issue-comment-fix.sh <issue> <version> <方案> <文件> <待回归>}"
SOLUTION="${3:?用法: issue-comment-fix.sh <issue> <version> <方案> <文件> <待回归>}"
FILES="${4:?用法: issue-comment-fix.sh <issue> <version> <方案> <文件> <待回归>}"
REGRESSION="${5:?用法: issue-comment-fix.sh <issue> <version> <方案> <文件> <待回归>}"

COMMIT_HASH="$(git log --oneline -1 | awk '{print $1}')"
COMMIT_MSG="$(git log --oneline -1 | cut -d' ' -f2-)"

# 将文件列表每行加 "- " 前缀
FILES_FORMATTED=$(echo "${FILES}" | tr ',' '\n' | sed 's/^[[:space:]]*/- /')

# 将待回归每行加 "- [ ] " 前缀
REGRESSION_FORMATTED=$(echo "${REGRESSION}" | tr ',' '\n' | sed 's/^[[:space:]]*/- [ ] /')

gh issue comment "${ISSUE}" --body "## 修改记录

**版本：${VERSION}**

### 方案
${SOLUTION}

### 修改文件
${FILES_FORMATTED}

### 关联提交
\`${COMMIT_HASH}\` ${COMMIT_MSG}

### 待回归
${REGRESSION_FORMATTED}"

echo "[issue-comment-fix.sh] Issue #${ISSUE} 修改记录已写入（版本 ${VERSION}，commit ${COMMIT_HASH}）"
