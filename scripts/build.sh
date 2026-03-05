#!/usr/bin/env bash
# scripts/build.sh — 编译 & 构建标准入口
#
# 用法：
#   bash scripts/build.sh compile [module]   # 编译指定模块（默认全量）
#   bash scripts/build.sh release            # 构建 release APK
#   bash scripts/build.sh debug              # 构建 debug APK
#   bash scripts/build.sh test [module]      # 运行单元测试
#
# 示例：
#   bash scripts/build.sh compile :feature:feature-task
#   bash scripts/build.sh release
#   bash scripts/build.sh test :feature:feature-task

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME="D:/tools/Android/Android Studio/jbr"

ACTION="${1:?用法: build.sh <compile|release|debug|test> [module]}"
MODULE="${2:-}"

case "${ACTION}" in
  compile)
    if [[ -n "${MODULE}" ]]; then
      TASK="${MODULE}:compileDebugKotlin"
    else
      TASK="compileDebugKotlin"
    fi
    echo "[build.sh] 编译：${TASK}"
    ./gradlew "${TASK}" --quiet
    echo "[build.sh] 编译成功"
    ;;
  release)
    echo "[build.sh] 构建 release APK..."
    ./gradlew assembleRelease --quiet
    APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -1)
    echo "[build.sh] Release APK：${APK:-（未找到，请检查）}"
    ;;
  debug)
    echo "[build.sh] 构建 debug APK..."
    ./gradlew assembleDebug --quiet
    APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
    echo "[build.sh] Debug APK：${APK:-（未找到，请检查）}"
    ;;
  test)
    if [[ -n "${MODULE}" ]]; then
      TASK="${MODULE}:testDebugUnitTest"
    else
      TASK="testDebugUnitTest"
    fi
    echo "[build.sh] 运行测试：${TASK}"
    ./gradlew "${TASK}"
    ;;
  *)
    echo "[build.sh] 未知操作：${ACTION}，支持：compile | release | debug | test" >&2
    exit 1
    ;;
esac
