#!/usr/bin/env bash
# scripts/new-issue.sh — 创建标准 GitHub Issue（用户反馈问题时必须先建 Issue）
#
# 用法：
#   bash scripts/new-issue.sh bug   "<标题>" "<描述>"         # BUG 类
#   bash scripts/new-issue.sh feat  "<标题>" "<描述>"         # 新功能类
#   bash scripts/new-issue.sh tc    "<TC编号>" "<标题>" "<描述>"  # 测试用例类（含 TC 编号）
#
# 示例：
#   bash scripts/new-issue.sh bug \
#       "游戏按屏幕蘑菇不跳" \
#       "点击游戏屏幕蘑菇没有跳跃反应"
#
#   bash scripts/new-issue.sh feat \
#       "双击已完成任务删除并扣回奖励" \
#       "双击已完成任务时弹出确认框，确认后删除任务并扣回已获得蘑菇奖励"
#
#   bash scripts/new-issue.sh tc \
#       "TC-2.8.1-01" \
#       "统计页默认显示近30天记录，应为近7天" \
#       "统计页打开默认时间范围应为近7天，但实际显示近30天"
#
# 行为：
#   1. 创建 Issue，自动加对应 label（bug/enhancement）
#   2. 打印 Issue 编号，供后续 commit message 引用

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TYPE="${1:?用法: new-issue.sh <bug|feat|tc> <标题> [<TC编号>] <描述>}"

case "${TYPE}" in
  bug)
    TITLE="${2:?bug 类型需要标题}"
    BODY="${3:?bug 类型需要描述}"
    LABEL="bug"
    FULL_TITLE="[BUG] ${TITLE}"
    ;;
  feat)
    TITLE="${2:?feat 类型需要标题}"
    BODY="${3:?feat 类型需要描述}"
    LABEL="enhancement"
    FULL_TITLE="${TITLE}"
    ;;
  tc)
    TC_ID="${2:?tc 类型需要 TC 编号（如 TC-2.8.1-01）}"
    TITLE="${3:?tc 类型需要标题}"
    BODY="${4:?tc 类型需要描述}"
    LABEL="bug"
    FULL_TITLE="[${TC_ID}] ${TITLE}"
    ;;
  *)
    echo "[new-issue.sh] 未知类型：${TYPE}，支持：bug | feat | tc" >&2
    exit 1
    ;;
esac

ISSUE_URL=$(gh issue create \
    --title "${FULL_TITLE}" \
    --body "${BODY}" \
    --label "${LABEL}" \
    2>&1)

ISSUE_NUM=$(echo "${ISSUE_URL}" | grep -oE '[0-9]+$')
echo "[new-issue.sh] Issue #${ISSUE_NUM} 已创建：${ISSUE_URL}"
echo "[new-issue.sh] 提交时引用：refs #${ISSUE_NUM} 或 fix #${ISSUE_NUM}"
