#!/usr/bin/env bash
# scripts/sync-tc-pass.sh — 同步 GitHub 已关闭 Issue → TC 文件标记通过
#
# 用法：
#   bash scripts/sync-tc-pass.sh
#
# 行为：
#   1. 从 GitHub 读取所有 CLOSED issue 编号
#   2. 扫描 docs/tc-*.md，找出 [ ] 通过 且 Issue 编号在已关闭列表中的条目
#   3. 将 [ ] 改为 [x]
#   4. 自动 commit（如有变更）

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "[sync-tc-pass.sh] 获取已关闭 Issue 列表..."
CLOSED_ISSUES=$(gh issue list --state closed --limit 200 --json number --jq '.[].number' | tr '\n' ',')
echo "[sync-tc-pass.sh] 已关闭 Issues: ${CLOSED_ISSUES}"

echo "[sync-tc-pass.sh] 扫描 TC 文件并更新..."
python3 - "${CLOSED_ISSUES}" <<'PYEOF'
import sys, re
from pathlib import Path

closed_set = set(int(x) for x in sys.argv[1].split(',') if x.strip())

tc_files = sorted(Path('docs').glob('tc-*.md'))
total_updated = 0

for fpath in tc_files:
    lines = fpath.read_text(encoding='utf-8').splitlines(keepends=True)
    updated = []
    changed = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        # 找到 - [ ] 通过
        if '- [ ] 通过' in line:
            # 向后最多找 15 行内的 Issue 编号
            for j in range(i+1, min(i+16, len(lines))):
                m = re.search(r'Issue：#(\d+)', lines[j])
                if m:
                    issue_num = int(m.group(1))
                    if issue_num in closed_set:
                        line = line.replace('- [ ] 通过', '- [x] 通过', 1)
                        changed += 1
                        total_updated += 1
                    break
        updated.append(line)
        i += 1
    if changed:
        fpath.write_text(''.join(updated), encoding='utf-8')
        print(f"  {fpath}: {changed} 条更新")

print(f"[sync-tc-pass.sh] 共更新 {total_updated} 条 TC 记录")
PYEOF

# 如有变更则 commit
if ! git diff --quiet docs/tc-*.md 2>/dev/null; then
    git add docs/tc-*.md
    git commit -m "docs: 同步 GitHub 已关闭 Issue → TC 标记通过

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
    echo "[sync-tc-pass.sh] 已提交更新。"
else
    echo "[sync-tc-pass.sh] 无需更新，所有已关闭 Issue 对应 TC 均已通过。"
fi
