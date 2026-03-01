#!/usr/bin/env bash
# scripts/check-env.sh
# 蘑菇大冒险 - 构建与测试环境检查脚本
#
# 行为：
#   1. 自动探测本机已有的 JDK、Android SDK、adb 安装路径
#   2. 已安装则采用检测到的值，并设置缺失的环境变量
#   3. 未安装则给出具体安装建议
#   4. 最终输出每一项的状态和实际使用的路径
#
# 用法：
#   chmod +x scripts/check-env.sh
#   ./scripts/check-env.sh
#
# 如需将自动检测到的环境变量写入 shell 配置，追加 --apply：
#   ./scripts/check-env.sh --apply

set -e
APPLY_MODE=false
[ "$1" = "--apply" ] && APPLY_MODE=true

PASS=0
FAIL=0

# 颜色（终端支持时启用）
if [ -t 1 ]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
    CYAN='\033[0;36m'; RESET='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; CYAN=''; RESET=''
fi

pass() { echo -e "  ${GREEN}✅${RESET}  $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}❌${RESET}  $1"; FAIL=$((FAIL+1)); }
warn() { echo -e "  ${YELLOW}⚠️ ${RESET}  $1"; }
info() { echo -e "     ${CYAN}→${RESET}  $1"; }

echo "========================================"
echo "  蘑菇大冒险 - 环境检查"
echo "  $(date)"
echo "========================================"

# ─────────────────────────────────────────
# 第一阶段：自动探测已有环境
# ─────────────────────────────────────────
echo ""
echo "【第一阶段】自动探测本机已有环境..."

# ── 探测 JAVA_HOME ──────────────────────
detect_java_home() {
    # 1. 已有环境变量且有效
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME"; return
    fi
    # 2. java 命令可用，反推路径
    if command -v java &>/dev/null; then
        local java_bin
        java_bin=$(command -v java)
        # 解析软链
        while [ -L "$java_bin" ]; do
            java_bin=$(readlink "$java_bin")
        done
        echo "${java_bin%/bin/java}"; return
    fi
    # 3. macOS：/usr/libexec/java_home
    if command -v /usr/libexec/java_home &>/dev/null; then
        /usr/libexec/java_home 2>/dev/null; return
    fi
    # 4. Windows Git Bash / MSYS：常见安装路径
    for dir in \
        "/c/Program Files/Java/jdk-17" \
        "/c/Program Files/Eclipse Adoptium/jdk-17"* \
        "/c/Program Files/Microsoft/jdk-17"*; do
        [ -x "$dir/bin/java" ] && echo "$dir" && return
    done
    echo ""
}

# ── 探测 ANDROID_HOME ────────────────────
detect_android_home() {
    # 1. 已有环境变量且有效
    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/platforms" ]; then
        echo "$ANDROID_HOME"; return
    fi
    if [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT/platforms" ]; then
        echo "$ANDROID_SDK_ROOT"; return
    fi
    # 2. macOS 默认路径
    if [ -d "$HOME/Library/Android/sdk" ]; then
        echo "$HOME/Library/Android/sdk"; return
    fi
    # 3. Linux 默认路径
    if [ -d "$HOME/Android/Sdk" ]; then
        echo "$HOME/Android/Sdk"; return
    fi
    # 4. Windows Git Bash / MSYS 常见路径
    for dir in \
        "$LOCALAPPDATA/Android/Sdk" \
        "/c/Users/$USER/AppData/Local/Android/Sdk"; do
        [ -d "$dir/platforms" ] && echo "$dir" && return
    done
    # 5. 从 Android Studio 安装路径旁查找（Windows）
    for dir in \
        "/c/Program Files/Android/Android Studio" \
        "$PROGRAMFILES/Android/Android Studio"; do
        [ -d "$dir/../sdk" ] && echo "$(realpath "$dir/../sdk")" && return
    done
    echo ""
}

DETECTED_JAVA_HOME=$(detect_java_home)
DETECTED_ANDROID_HOME=$(detect_android_home)

# 显示探测结果
echo ""
if [ -n "$DETECTED_JAVA_HOME" ]; then
    info "检测到 JAVA_HOME  : $DETECTED_JAVA_HOME"
else
    warn "未检测到 JDK 安装路径"
fi

if [ -n "$DETECTED_ANDROID_HOME" ]; then
    info "检测到 ANDROID_HOME: $DETECTED_ANDROID_HOME"
else
    warn "未检测到 Android SDK 路径"
fi

# 用检测到的值覆盖（仅本次脚本运行范围内生效）
[ -n "$DETECTED_JAVA_HOME" ]    && export JAVA_HOME="$DETECTED_JAVA_HOME"
[ -n "$DETECTED_ANDROID_HOME" ] && export ANDROID_HOME="$DETECTED_ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools/bin:$PATH"

# --apply 模式：将检测到的变量写入 shell 配置文件
if $APPLY_MODE; then
    SHELL_RC=""
    [ -f "$HOME/.zshrc" ]  && SHELL_RC="$HOME/.zshrc"
    [ -f "$HOME/.bashrc" ] && SHELL_RC="$HOME/.bashrc"
    if [ -n "$SHELL_RC" ] && [ -n "$DETECTED_JAVA_HOME" ]; then
        if ! grep -q "JAVA_HOME.*$DETECTED_JAVA_HOME" "$SHELL_RC" 2>/dev/null; then
            echo "" >> "$SHELL_RC"
            echo "# 蘑菇大冒险 - 自动添加" >> "$SHELL_RC"
            echo "export JAVA_HOME=\"$DETECTED_JAVA_HOME\"" >> "$SHELL_RC"
            echo "export ANDROID_HOME=\"$DETECTED_ANDROID_HOME\"" >> "$SHELL_RC"
            echo "export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/tools/bin\"" >> "$SHELL_RC"
            warn "--apply：已将环境变量追加到 $SHELL_RC，请执行 source $SHELL_RC 使其生效"
        else
            info "--apply：$SHELL_RC 中已有相关配置，跳过写入"
        fi
    fi
fi

# ─────────────────────────────────────────
# 第二阶段：逐项验证
# ─────────────────────────────────────────
echo ""
echo "【第二阶段】逐项验证..."

# ── JDK ─────────────────────────────────
echo ""
echo "[ JDK ]"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)
    if echo "$JAVA_VERSION" | grep -q "17\."; then
        pass "JDK 17 已安装"
        info "版本：$JAVA_VERSION"
        info "路径：$JAVA_HOME"
    else
        fail "JDK 版本不符（需要 17，当前：$JAVA_VERSION）"
        info "当前路径：$JAVA_HOME"
        info "修复：安装 JDK 17，下载地址 https://adoptium.net"
    fi
else
    fail "JDK 未找到"
    info "修复（macOS）：brew install openjdk@17"
    info "修复（Windows）：从 https://adoptium.net 下载 JDK 17 安装包"
    info "修复（Linux）：sudo apt install openjdk-17-jdk"
fi

if [ -x "$JAVA_HOME/bin/javac" ]; then
    pass "javac 可用"
else
    fail "javac 不可用（JDK 安装可能不完整）"
fi

# ── Android SDK ──────────────────────────
echo ""
echo "[ Android SDK ]"
if [ -n "$ANDROID_HOME" ]; then
    pass "ANDROID_HOME 已设置"
    info "路径：$ANDROID_HOME"

    # platform android-34
    if [ -d "$ANDROID_HOME/platforms/android-34" ]; then
        pass "SDK Platform API 34 已安装"
    else
        fail "SDK Platform API 34 未安装"
        # 列出已有 platform 供参考
        if [ -d "$ANDROID_HOME/platforms" ]; then
            INSTALLED_PLATFORMS=$(ls "$ANDROID_HOME/platforms" 2>/dev/null | tr '\n' ' ')
            info "已安装的 Platform：${INSTALLED_PLATFORMS:-（无）}"
        fi
        info "修复：Android Studio → SDK Manager → SDK Platforms → Android 14.0 (API 34) → Install"
    fi

    # build-tools 34.0.0
    if [ -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
        pass "Build-Tools 34.0.0 已安装"
    else
        fail "Build-Tools 34.0.0 未安装"
        INSTALLED_BT=$(ls "$ANDROID_HOME/build-tools" 2>/dev/null | tr '\n' ' ')
        info "已安装的 Build-Tools：${INSTALLED_BT:-（无）}"
        info "修复：Android Studio → SDK Manager → SDK Tools → Android SDK Build-Tools 34 → Install"
    fi

    # adb
    ADB_BIN="$ANDROID_HOME/platform-tools/adb"
    [ ! -x "$ADB_BIN" ] && ADB_BIN=$(command -v adb 2>/dev/null || echo "")
    if [ -x "$ADB_BIN" ]; then
        ADB_VERSION=$("$ADB_BIN" version 2>&1 | head -1)
        pass "adb 可用"
        info "版本：$ADB_VERSION"
    else
        fail "adb 不可用"
        info "修复：Android Studio → SDK Manager → SDK Tools → Android SDK Platform-Tools → Install"
    fi
else
    fail "ANDROID_HOME 未设置且未能自动检测"
    info "修复：安装 Android Studio 后，SDK 通常位于："
    info "  macOS：$HOME/Library/Android/sdk"
    info "  Linux：$HOME/Android/Sdk"
    info "  Windows：%LOCALAPPDATA%\\Android\\Sdk"
    info "然后在终端中运行：export ANDROID_HOME=<上述路径>"
    info "或使用 --apply 模式自动写入 shell 配置：./scripts/check-env.sh --apply"
fi

# ── Gradle ───────────────────────────────
echo ""
echo "[ Gradle ]"
if [ -f "./gradlew" ]; then
    GRADLE_VERSION=$(./gradlew --version 2>/dev/null | grep "^Gradle " | head -1)
    if echo "$GRADLE_VERSION" | grep -q "Gradle 8"; then
        pass "Gradle Wrapper 可用"
        info "版本：$GRADLE_VERSION"
    else
        fail "Gradle 版本不符（需要 8.x，当前：${GRADLE_VERSION:-未知}）"
        info "修复：更新 gradle/wrapper/gradle-wrapper.properties 中的 distributionUrl"
    fi
else
    fail "gradlew 不存在（项目目录是否正确？）"
    info "当前目录：$(pwd)"
    info "修复：确保在项目根目录执行此脚本"
fi

# ── 构建验证 ─────────────────────────────
echo ""
echo "[ 构建验证 ]"
echo "     → 执行 ./gradlew assembleDebug（首次约 1-3 分钟）..."
if ./gradlew assembleDebug -q 2>/dev/null; then
    pass "assembleDebug 构建成功"
else
    fail "assembleDebug 构建失败"
    info "修复：运行 ./gradlew assembleDebug 查看详细错误"
fi

# ── 测试环境 ─────────────────────────────
echo ""
echo "[ 测试环境 ]"
echo "     → 验证 UT 可运行（core-domain）..."
if ./gradlew :core-domain:test -q 2>/dev/null; then
    pass "UT 可运行（core-domain）"
else
    fail "UT 运行失败（core-domain）"
    info "修复：运行 ./gradlew :core-domain:test 查看详细错误"
    info "常见原因：测试依赖 JUnit5 未声明，或 JVM 版本不兼容"
fi

echo "     → 验证 UT 可运行（core-data）..."
if ./gradlew :core-data:test -q 2>/dev/null; then
    pass "UT 可运行（core-data）"
else
    fail "UT 运行失败（core-data）"
    info "修复：运行 ./gradlew :core-data:test 查看详细错误"
    info "常见原因：Robolectric 依赖未声明，或 Room 版本冲突"
fi

# ─────────────────────────────────────────
# 第三阶段：汇总
# ─────────────────────────────────────────
echo ""
echo "【第三阶段】检查结果汇总"
echo ""
echo "  当前生效的环境变量："
echo "    JAVA_HOME    = ${JAVA_HOME:-（未设置）}"
echo "    ANDROID_HOME = ${ANDROID_HOME:-（未设置）}"
echo ""
echo "========================================"
if [ $FAIL -eq 0 ]; then
    echo -e "  ${GREEN}结果：全部通过 ✅  ($PASS 项)${RESET}"
    echo "  环境就绪，可以开始开发。"
    echo ""
    if ! $APPLY_MODE; then
        echo "  提示：如果重新打开终端后环境变量丢失，运行："
        echo "    ./scripts/check-env.sh --apply"
        echo "  可将检测到的路径自动写入 shell 配置文件。"
    fi
    exit 0
else
    echo -e "  ${RED}结果：$FAIL 项未通过 ❌  ($PASS 通过 / $FAIL 失败)${RESET}"
    echo "  请按上方 「修复」提示处理后，重新运行：./scripts/check-env.sh"
    exit 1
fi
echo "========================================"
