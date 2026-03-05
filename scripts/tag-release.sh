#!/usr/bin/env bash
# scripts/tag-release.sh — 打 Tag 并推送发版
#
# 用法：
#   bash scripts/tag-release.sh <version> "<release notes>"
#
# 示例：
#   bash scripts/tag-release.sh v1.5.2 "fix: [TC-xxx] 修复YY问题"
#
# 行为：
#   1. push master 到 remote
#   2. 若本地已存在同名 tag，先删除再重建（force update）
#   3. 创建 annotated tag
#   4. force push tag 到 remote（触发 CI 构建）

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

VERSION="${1:?用法: tag-release.sh <version> <notes>}"
NOTES="${2:?用法: tag-release.sh <version> <notes>}"

# 推送 master
echo "[tag-release.sh] 推送 master..."
git push origin master

# 删除旧 tag（如存在）
if git tag -l "${VERSION}" | grep -q "${VERSION}"; then
    echo "[tag-release.sh] 删除本地旧 tag ${VERSION}..."
    git tag -d "${VERSION}"
fi

# 创建 annotated tag
echo "[tag-release.sh] 创建 tag ${VERSION}..."
git tag -a "${VERSION}" -m "${VERSION}

${NOTES}"

# 推送 tag（force，覆盖 remote 旧 tag）
echo "[tag-release.sh] 推送 tag ${VERSION} 到 remote..."
git push origin "${VERSION}" --force

echo "[tag-release.sh] 完成！tag ${VERSION} 已推送，CI 构建已触发。"
