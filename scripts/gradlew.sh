#!/usr/bin/env bash
# scripts/gradlew.sh — 统一入口，自动注入 JAVA_HOME
#
# 用法：
#   bash scripts/gradlew.sh <task> [args...]
#
# 示例：
#   bash scripts/gradlew.sh :feature:feature-task:compileDebugKotlin --quiet
#   bash scripts/gradlew.sh assembleRelease

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

export JAVA_HOME="D:/tools/Android/Android Studio/jbr"
./gradlew "$@"
