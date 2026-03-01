#!/usr/bin/env bash
# scripts/run-tests.sh
# 蘑菇大冒险 - 测试执行与报告生成脚本
# 用法：chmod +x scripts/run-tests.sh && ./scripts/run-tests.sh [ut|it|st|phase1|phase2|phase3|all]

set -e
PHASE=${1:-"ut"}
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_DIR="build/test-reports/${TIMESTAMP}_${PHASE}"
mkdir -p "$REPORT_DIR"

UT_PASS=0; UT_FAIL=0; UT_TOTAL=0
IT_PASS=0; IT_FAIL=0; IT_TOTAL=0
ST_PASS=0; ST_FAIL=0; ST_TOTAL=0
COVERAGE_OK="N/A"

echo "========================================"
echo "  蘑菇大冒险 - 测试执行  [${PHASE}]"
echo "  开始时间：$(date)"
echo "========================================"

run_ut() {
    echo ""
    echo "[ 执行单元测试 (UT) ]"
    if ./gradlew test jacocoTestReport --continue -q; then
        echo "  UT 执行完成"
    else
        echo "  ⚠️  部分 UT 失败，继续生成报告..."
    fi
    # 收集各模块 XML 结果，统计通过/失败数
    for xml in $(find . -path "*/build/test-results/test/*.xml" -not -path "*/build/test-reports/*"); do
        tests=$(grep -o 'tests="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        failures=$(grep -o 'failures="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        errors=$(grep -o 'errors="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        UT_TOTAL=$((UT_TOTAL + tests))
        UT_FAIL=$((UT_FAIL + failures + errors))
    done
    UT_PASS=$((UT_TOTAL - UT_FAIL))
    # 复制报告
    find . -path "*/build/reports/tests/test" -not -path "*/build/test-reports/*" \
        -exec cp -r {} "$REPORT_DIR/ut-results/" \; 2>/dev/null || mkdir -p "$REPORT_DIR/ut-results"
    find . -path "*/build/reports/jacoco" -not -path "*/build/test-reports/*" \
        -exec cp -r {} "$REPORT_DIR/ut-coverage/" \; 2>/dev/null || mkdir -p "$REPORT_DIR/ut-coverage"
    echo "  UT 结果：通过 $UT_PASS / 失败 $UT_FAIL / 总计 $UT_TOTAL"
}

run_it() {
    echo ""
    echo "[ 执行集成测试 (IT) ]"
    if ./gradlew integrationTest --continue -q 2>/dev/null; then
        echo "  IT 执行完成"
    else
        echo "  ⚠️  集成测试部分失败或任务未定义，继续..."
    fi
    for xml in $(find . -path "*/build/test-results/integrationTest/*.xml" 2>/dev/null); do
        tests=$(grep -o 'tests="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        failures=$(grep -o 'failures="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        IT_TOTAL=$((IT_TOTAL + tests))
        IT_FAIL=$((IT_FAIL + failures))
    done
    IT_PASS=$((IT_TOTAL - IT_FAIL))
    mkdir -p "$REPORT_DIR/it-results"
    find . -path "*/build/reports/tests/integrationTest" 2>/dev/null \
        -exec cp -r {} "$REPORT_DIR/it-results/" \; || true
    echo "  IT 结果：通过 $IT_PASS / 失败 $IT_FAIL / 总计 $IT_TOTAL"
}

run_st() {
    echo ""
    echo "[ 执行系统测试 (ST) ]"
    if ./gradlew :system-test:test --continue -q 2>/dev/null; then
        echo "  ST 执行完成"
    else
        echo "  ⚠️  ST 部分失败或模块未创建，继续..."
    fi
    for xml in $(find system-test -path "*/build/test-results/test/*.xml" 2>/dev/null); do
        tests=$(grep -o 'tests="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        failures=$(grep -o 'failures="[0-9]*"' "$xml" | grep -o '[0-9]*' | head -1 || echo 0)
        ST_TOTAL=$((ST_TOTAL + tests))
        ST_FAIL=$((ST_FAIL + failures))
    done
    ST_PASS=$((ST_TOTAL - ST_FAIL))
    mkdir -p "$REPORT_DIR/st-results"
    [ -d "system-test/build/reports/tests/test" ] && \
        cp -r system-test/build/reports/tests/test "$REPORT_DIR/st-results/" || true
    echo "  ST 结果：通过 $ST_PASS / 失败 $ST_FAIL / 总计 $ST_TOTAL"
}

generate_coverage() {
    echo ""
    echo "[ 生成全量覆盖率报告 ]"
    if ./gradlew jacocoMergedReport -q 2>/dev/null; then
        mkdir -p "$REPORT_DIR/coverage-merged"
        [ -d "build/reports/jacoco/merged" ] && \
            cp -r build/reports/jacoco/merged "$REPORT_DIR/coverage-merged/" || true
        COVERAGE_OK="已生成，见 coverage-merged/html/index.html"
    else
        COVERAGE_OK="未生成（jacocoMergedReport 任务未配置）"
    fi
}

generate_summary() {
    generate_coverage

    # 判断整体结果
    OVERALL="✅ PASS"
    [ $UT_FAIL -gt 0 ] && OVERALL="❌ FAIL"
    [ $IT_FAIL -gt 0 ] && OVERALL="❌ FAIL"
    [ $ST_FAIL -gt 0 ] && OVERALL="❌ FAIL"

    cat > "$REPORT_DIR/SUMMARY.md" << EOF
# 测试报告摘要

**阶段**：${PHASE}
**生成时间**：$(date)
**报告目录**：${REPORT_DIR}
**整体结论**：${OVERALL}

## 执行结果汇总

| 测试类型 | 总用例数 | 通过 | 失败 | 结论 |
|---------|---------|------|------|------|
| UT（单元测试） | ${UT_TOTAL} | ${UT_PASS} | ${UT_FAIL} | $([ $UT_FAIL -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL") |
| IT（集成测试） | ${IT_TOTAL} | ${IT_PASS} | ${IT_FAIL} | $([ $IT_FAIL -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL") |
| ST（系统测试） | ${ST_TOTAL} | ${ST_PASS} | ${ST_FAIL} | $([ $ST_FAIL -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL") |

## 覆盖率

${COVERAGE_OK}

详细覆盖率见：coverage-merged/html/index.html

## 报告文件

- UT 结果：ut-results/test/index.html
- UT 覆盖率：ut-coverage/test/html/index.html
- IT 结果：it-results/integrationTest/index.html（如有）
- ST 结果：st-results/test/index.html（如有）
- 全量覆盖率：coverage-merged/merged/html/index.html

## 门禁检查

| 条件 | 状态 |
|------|------|
| UT 全部通过 | $([ $UT_FAIL -eq 0 ] && echo "✅" || echo "❌ ($UT_FAIL 个失败)") |
| IT 全部通过 | $([ $IT_FAIL -eq 0 ] && echo "✅" || echo "❌ ($IT_FAIL 个失败)") |
| ST 全部通过 | $([ $ST_FAIL -eq 0 ] && echo "✅" || echo "❌ ($ST_FAIL 个失败)") |

EOF

    echo ""
    echo "========================================"
    echo "  测试报告已生成"
    echo "  路径：${REPORT_DIR}/SUMMARY.md"
    echo ""
    echo "  整体结论：${OVERALL}"
    echo "  UT：$UT_PASS/$UT_TOTAL 通过"
    [ $IT_TOTAL -gt 0 ] && echo "  IT：$IT_PASS/$IT_TOTAL 通过"
    [ $ST_TOTAL -gt 0 ] && echo "  ST：$ST_PASS/$ST_TOTAL 通过"
    echo "  结束时间：$(date)"
    echo "========================================"

    # 如有失败，以非零状态退出（供 CI 感知）
    [ $UT_FAIL -gt 0 ] || [ $IT_FAIL -gt 0 ] || [ $ST_FAIL -gt 0 ] && exit 1 || exit 0
}

case "$PHASE" in
    ut)
        run_ut
        generate_summary
        ;;
    it)
        run_it
        generate_summary
        ;;
    st)
        run_st
        generate_summary
        ;;
    phase1)
        run_ut
        run_it
        run_st   # ST-001/002/003
        generate_summary
        ;;
    phase2)
        run_ut
        run_it
        run_st   # ST-001~009
        generate_summary
        ;;
    phase3)
        run_ut
        run_it
        run_st   # ST-001~010（含 Migration）
        generate_summary
        ;;
    all)
        run_ut
        run_it
        run_st
        generate_summary
        ;;
    *)
        echo "用法: $0 [ut|it|st|phase1|phase2|phase3|all]"
        echo ""
        echo "  ut      只运行单元测试（UT）"
        echo "  it      只运行集成测试（IT）"
        echo "  st      只运行系统测试（ST）"
        echo "  phase1  Phase 1 完成验证（UT + IT + ST-001~003）"
        echo "  phase2  Phase 2 完成验证（UT + IT + ST-001~009）"
        echo "  phase3  Phase 3 完成验证（全量：UT + IT + ST-001~010）"
        echo "  all     全量执行"
        exit 1
        ;;
esac
