#!/usr/bin/env bash
# scripts/release.sh — 完整发版流程：compile check → tag → issue comment
#
# 用法：
#   bash scripts/release.sh <version> "<release-notes>" [issue1] [issue2] ...
#
# 示例：
#   bash scripts/release.sh v1.6.21 \
#       "fix: 增加游戏触发诊断日志，修复状态栏图标白底（#61 #62）" \
#       61 62
#
# 行为：
#   1. 全量编译检查（compileDebugKotlin）
#   2. 构建 release APK
#   3. 打 Tag 并推送（触发 CI）
#   4. 向每个关联 Issue 写入发版通知评论（版本号 + APK 下载提示）

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME="D:/tools/Android/Android Studio/jbr"

VERSION="${1:?用法: release.sh <version> <notes> [issue...]}"
NOTES="${2:?用法: release.sh <version> <notes> [issue...]}"
shift 2
ISSUES=("$@")

echo "[release.sh] ===== 发版流程开始：${VERSION} ====="

# Step 1: 全量编译检查
echo "[release.sh] Step 1/4 全量编译检查..."
./gradlew compileDebugKotlin --quiet
echo "[release.sh] 编译通过"

# Step 2: 构建 release APK
echo "[release.sh] Step 2/4 构建 release APK..."
./gradlew assembleRelease --quiet
APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -1)
echo "[release.sh] APK：${APK:-（未找到）}"

# Step 3: 打 Tag
echo "[release.sh] Step 3/4 打 Tag ${VERSION}..."
git push origin master

if git tag -l "${VERSION}" | grep -q "${VERSION}"; then
    git tag -d "${VERSION}"
fi

git tag -a "${VERSION}" -m "${VERSION}

${NOTES}"
git push origin "${VERSION}" --force
echo "[release.sh] Tag ${VERSION} 已推送，CI 构建已触发"

# Step 4: 向关联 Issue 写入发版通知
if [[ ${#ISSUES[@]} -gt 0 ]]; then
    echo "[release.sh] Step 4/4 写入 Issue 发版通知..."
    for ISSUE in "${ISSUES[@]}"; do
        gh issue comment "${ISSUE}" --body "## 发版通知

**版本：${VERSION}** 已发布，CI 构建已触发。

请安装新版本进行回归测试，测试通过后回复确认，Issue 将关闭。

> ${NOTES}"
        echo "[release.sh]   → Issue #${ISSUE} 已通知"
    done
else
    echo "[release.sh] Step 4/4 无关联 Issue，跳过通知"
fi

echo "[release.sh] ===== 发版完成：${VERSION} ====="
