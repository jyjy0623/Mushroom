#!/usr/bin/env bash
# scripts/build.sh — 编译 & 构建标准入口
#
# 用法：
#   bash scripts/build.sh compile [flavor|module]  # 编译（mushroom/ukdream/模块路径）
#   bash scripts/build.sh release [flavor]          # 构建 release APK
#   bash scripts/build.sh debug [flavor]            # 构建 debug APK
#   bash scripts/build.sh test [module]             # 运行单元测试
#
# 示例：
#   bash scripts/build.sh compile mushroom          # 编译蘑菇版
#   bash scripts/build.sh compile ukdream           # 编译英伦版
#   bash scripts/build.sh compile                   # 编译两个 flavor
#   bash scripts/build.sh compile :feature:feature-task  # 编译指定模块
#   bash scripts/build.sh release mushroom          # 构建蘑菇版 release APK
#   bash scripts/build.sh release ukdream           # 构建英伦版 release APK
#   bash scripts/build.sh test :feature:feature-task

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME="D:/tools/Android/Android Studio/jbr"

ACTION="${1:?用法: build.sh <compile|release|debug|test> [flavor|module]}"
ARG="${2:-}"

# 判断第二参数是 flavor 还是 module
is_flavor() {
  [[ "$1" == "mushroom" || "$1" == "ukdream" ]]
}

# 首字母大写
capitalize() {
  echo "$(echo "${1:0:1}" | tr '[:lower:]' '[:upper:]')${1:1}"
}

case "${ACTION}" in
  compile)
    if is_flavor "${ARG}"; then
      FLAVOR="$(capitalize "${ARG}")"
      TASK="compile${FLAVOR}DebugKotlin"
      echo "[build.sh] 编译 ${ARG} flavor：${TASK}"
      ./gradlew "${TASK}" --quiet
    elif [[ -n "${ARG}" ]]; then
      TASK="${ARG}:compileDebugKotlin"
      echo "[build.sh] 编译模块：${TASK}"
      ./gradlew "${TASK}" --quiet
    else
      echo "[build.sh] 编译全部 flavor..."
      ./gradlew compileMushroomDebugKotlin compileUkdreamDebugKotlin --quiet
    fi
    echo "[build.sh] 编译成功"
    ;;
  release)
    if is_flavor "${ARG}"; then
      FLAVOR="$(capitalize "${ARG}")"
      echo "[build.sh] 构建 ${ARG} release APK..."
      ./gradlew "assemble${FLAVOR}Release" --quiet
      APK=$(find app/build/outputs/apk/"${ARG}"/release -name "*.apk" 2>/dev/null | head -1)
    else
      echo "[build.sh] 构建全部 release APK..."
      ./gradlew assembleMushroomRelease assembleUkdreamRelease --quiet
      APK=$(find app/build/outputs/apk -name "*.apk" 2>/dev/null | head -1)
    fi
    echo "[build.sh] Release APK：${APK:-（未找到，请检查）}"
    ;;
  debug)
    if is_flavor "${ARG}"; then
      FLAVOR="$(capitalize "${ARG}")"
      echo "[build.sh] 构建 ${ARG} debug APK..."
      ./gradlew "assemble${FLAVOR}Debug" --quiet
      APK=$(find app/build/outputs/apk/"${ARG}"/debug -name "*.apk" 2>/dev/null | head -1)
    else
      echo "[build.sh] 构建全部 debug APK..."
      ./gradlew assembleMushroomDebug assembleUkdreamDebug --quiet
      APK=$(find app/build/outputs/apk -name "*.apk" 2>/dev/null | head -1)
    fi
    echo "[build.sh] Debug APK：${APK:-（未找到，请检查）}"
    ;;
  test)
    if [[ -n "${ARG}" ]]; then
      TASK="${ARG}:testDebugUnitTest"
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
