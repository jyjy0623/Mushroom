# scripts/ — 标准化操作脚本

## 设计目标

将高频重复操作脚本化，减少 Claude 每次调用的 token 消耗，同时通过 CLAUDE.md 预授权避免权限确认往返。

## Token 节省估算

每次 Issue 修复周期对比：

| 操作 | 无脚本 | 有脚本 | 节省 |
|---|---|---|---|
| 创建 Issue（bug/feat/tc） | ~300 tokens | ~30 tokens | 270 |
| 创建修复分支 | ~200 tokens | ~30 tokens | 170 |
| Commit（多文件） | ~300 tokens | ~40 tokens | 260 |
| merge + push + issue comment | ~500 tokens | ~50 tokens | 450 |
| 写 Issue 修改记录评论 | ~600 tokens | ~40 tokens | 560 |
| 完整发版（compile+tag+通知） | ~800 tokens | ~50 tokens | 750 |
| TC 同步标记通过 | ~600 tokens | ~20 tokens | 580 |
| 权限确认往返 | ~300 tokens | 0 tokens | 300 |
| **合计** | **~3600 tokens** | **~260 tokens** | **~3340（93%）** |

每天处理 3~5 个 Issue，每天节省约 **10000~17000 tokens**。

---

## 现有脚本

### `new-issue.sh` — 创建 GitHub Issue（用户反馈必须先建 Issue）

```bash
bash scripts/new-issue.sh bug  "游戏按屏幕蘑菇不跳" "点击游戏屏幕蘑菇没有跳跃反应"
bash scripts/new-issue.sh feat "双击已完成任务删除并扣回奖励" "双击确认后删除并扣回蘑菇"
bash scripts/new-issue.sh tc   "TC-2.8.1-01" "统计页默认近30天，应为近7天" "描述..."
```

- 自动加 label（bug/enhancement）
- 输出 Issue 编号供 commit message 引用

---

### `build.sh` — 编译 & 构建 & 测试

```bash
bash scripts/build.sh compile :feature:feature-task   # 编译单模块
bash scripts/build.sh compile                          # 全量编译
bash scripts/build.sh release                          # 构建 release APK
bash scripts/build.sh debug                            # 构建 debug APK
bash scripts/build.sh test :feature:feature-task       # 运行单元测试
```

- 自动注入 `JAVA_HOME`，无需手动 export
- 所有编译/构建/测试操作统一走此脚本

---

### `gradlew.sh` — Gradle 通用入口（备用）

```bash
bash scripts/gradlew.sh <任意 Gradle task>
```

---



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

### `finish-fix.sh` — 完成修复（含标准修改记录评论）

```bash
bash scripts/finish-fix.sh 62 v1.6.21 \
    "themes.xml 增加 statusBarColor=transparent + windowLightStatusBar=true" \
    "app/src/main/res/values/themes.xml" \
    "打开 App，状态栏图标清晰可见"
```

- 若在 fix/* 分支：merge 到 master（no-ff）+ push
- 调用 `issue-comment-fix.sh` 写入标准修改记录评论（版本/方案/文件/commit/待回归）

---

### `issue-comment-fix.sh` — 向 Issue 写入修改记录评论

```bash
bash scripts/issue-comment-fix.sh 62 v1.6.21 \
    "方案描述" \
    "file1.kt,file2.kt" \
    "回归项1,回归项2"
```

- 合入后必须调用，记录版本/方案/文件/commit/待回归
- 通常由 `finish-fix.sh` 自动调用，无需手动执行

---

### `release.sh` — 完整发版流程

```bash
bash scripts/release.sh v1.6.21 \
    "fix: 修复状态栏白底，增加游戏诊断日志（#61 #62）" \
    61 62
```

- compile check → 构建 release APK → 打 Tag + push → 向关联 Issue 写发版通知
- 替代原来 `build.sh release` + `tag-release.sh` 的分散操作

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
| `close-issue.sh` | 用户确认回归通过后关闭 Issue + 写关闭评论 |
