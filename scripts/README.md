# scripts/ — 标准化操作脚本

## 设计目标

将高频重复操作脚本化，减少 Claude 每次调用的 token 消耗，同时通过 CLAUDE.md 预授权避免权限确认往返。

## Token 节省估算

每次 Issue 修复周期对比：

| 操作 | 无脚本 | 有脚本 | 节省 |
|---|---|---|---|
| 创建修复分支 | ~200 tokens | ~30 tokens | 170 |
| Commit（多文件） | ~300 tokens | ~40 tokens | 260 |
| merge + push + issue comment | ~500 tokens | ~50 tokens | 450 |
| 打 Tag 发版 | ~400 tokens | ~30 tokens | 370 |
| TC 同步标记通过 | ~600 tokens | ~20 tokens | 580 |
| 权限确认往返 | ~300 tokens | 0 tokens | 300 |
| **合计** | **~2300 tokens** | **~170 tokens** | **~2130（93%）** |

每天处理 3~5 个 Issue，每天节省约 **6000~10000 tokens**。

---

## 现有脚本

### `commit.sh` — 暂存并提交

```bash
bash scripts/commit.sh "fix: 修复XX问题 refs #N" file1.kt file2.kt
```

- 暂存指定文件（不传文件则 `git add -u`）
- 自动附加 `Co-Authored-By: Claude Sonnet 4.6`

---

### `new-fix-branch.sh` — 创建修复分支

```bash
bash scripts/new-fix-branch.sh 34 key-date-entry-missing
# 创建并切换到 fix/issue-34-key-date-entry-missing
```

- 基于最新 master HEAD 创建 `fix/issue-{N}-{desc}` 分支

---

### `finish-fix.sh` — 完成修复

```bash
bash scripts/finish-fix.sh 34 "设置页缺少关键奖励时间入口" "新增 KeyDate 入口并注册导航路由"
```

- merge fix 分支到 master（no-ff）
- push master
- 在 Issue 写入根因 + 方案 + commit hash comment

---

### `tag-release.sh` — 打 Tag 发版

```bash
bash scripts/tag-release.sh v1.5.2 "fix: [TC-xxx] 修复YY问题"
```

- push master
- 删除同名旧 tag（如存在）→ 创建 annotated tag → force push 触发 CI

---

### `sync-tc-pass.sh` — 同步 TC 通过状态

```bash
bash scripts/sync-tc-pass.sh
```

- 从 GitHub 读取所有已关闭 Issue
- 扫描 `docs/tc-*.md`，将对应 `- [ ] 通过` 改为 `- [x] 通过`
- 自动 commit（有变更时）

---

## 沉淀新脚本的判断标准

满足以下任意两条，建议沉淀为脚本：

1. **重复性**：同一操作在不同 Issue/Sprint 中出现 2 次以上
2. **步骤多**：操作本身需要 3 步以上 bash 调用
3. **格式固定**：输出格式有规范要求（如 commit message、Issue comment）
4. **权限敏感**：涉及 push、gh 操作，需要反复确认

## 后续计划

| 候选脚本 | 触发场景 |
|---|---|
| `scan-and-issue.sh` | 扫描 tc-*.md 失败用例 + 批量 gh issue create + 回填编号 |
| `append-bug-tc.sh` | 修复完成后向 BugIssueTestCase.md 追加 TC-B 验收块 |
