#!/usr/bin/env bash
# scripts/check-env.sh
# 蘑菇大冒险 - 构建与测试环境检查脚本
# 用法：chmod +x scripts/check-env.sh && ./scripts/check-env.sh

set -e
PASS=0
FAIL=0

check() {
    local name="$1"
    local cmd="$2"
    local expect="$3"
    if eval "$cmd" 2>&1 | grep -q "$expect"; then
        echo "  ✅  $name"
        PASS=$((PASS+1))
    else
        echo "  ❌  $name  (期望包含: $expect)"
        FAIL=$((FAIL+1))
    fi
}

echo "========================================"
echo "  蘑菇大冒险 - 环境检查"
echo "  $(date)"
echo "========================================"

echo ""
echo "[ JDK ]"
check "JDK 版本 = 17"         "java -version 2>&1"         "17\."
check "javac 可用"             "javac -version 2>&1"        "javac 17"

echo ""
echo "[ Android SDK ]"
check "ANDROID_HOME 已设置"   "echo \"$ANDROID_HOME\""     "Android"
check "platform android-34"   "ls \"$ANDROID_HOME/platforms\" 2>/dev/null" "android-34"
check "build-tools 34.0.0"    "ls \"$ANDROID_HOME/build-tools\" 2>/dev/null" "34.0.0"
check "adb 可用"               "adb version 2>&1"           "Android Debug Bridge"

echo ""
echo "[ Gradle ]"
check "Gradle Wrapper 可用"   "./gradlew --version 2>&1"   "Gradle 8"

echo ""
echo "[ 构建验证 ]"
if ./gradlew assembleDebug -q 2>&1; then
    echo "  ✅  assembleDebug 成功"
    PASS=$((PASS+1))
else
    echo "  ❌  assembleDebug 失败（运行 ./gradlew assembleDebug 查看详细错误）"
    FAIL=$((FAIL+1))
fi

echo ""
echo "[ 测试环境 ]"
if ./gradlew :core-domain:test -q 2>&1; then
    echo "  ✅  UT 可运行（core-domain）"
    PASS=$((PASS+1))
else
    echo "  ❌  UT 运行失败（core-domain）"
    FAIL=$((FAIL+1))
fi

if ./gradlew :core-data:test -q 2>&1; then
    echo "  ✅  UT 可运行（core-data）"
    PASS=$((PASS+1))
else
    echo "  ❌  UT 运行失败（core-data）"
    FAIL=$((FAIL+1))
fi

echo ""
echo "========================================"
if [ $FAIL -eq 0 ]; then
    echo "  结果：全部通过 ✅  ($PASS 项)"
    echo "  环境就绪，可以开始开发。"
    exit 0
else
    echo "  结果：$FAIL 项未通过 ❌  ($PASS 通过 / $FAIL 失败)"
    echo "  请修复以上问题后重新运行：./scripts/check-env.sh"
    exit 1
fi
echo "========================================"
