# 蘑菇大冒险 — Issue 处理工作流

**文档版本**：v2.2
**日期**：2026-03-03

---

## 完整闭环流程

```
测试人员填写 tc-*.md
  通过   → [x] 通过
  不通过 → 保留 [ ]，填写实际结果 / 设备 / 版本
        │
        ▼
告诉 Claude Code："扫描测试结果，提 Issue"
        │
        ▼
Claude Code 自动扫描 tc-*.md
  识别：「- [ ] 通过」且「实际结果：」有内容
  → gh issue create（从用例提取所有字段）
  → Issue 编号回填到用例的「Issue：#」处
        │
        ▼
逐个启动修复（用户确认顺序）
  fix/issue-{N}-{描述} 分支
  修复完成 → gh issue comment 记录根因和方案
  commit 包含 closes #N
  合并到 master → 打 tag 触发发版
        │
        ▼
更新 BugIssueTestCase.md
  追加验收 TC 块，测试人员在真机上确认后打勾
        │
        ▼
回归验收确认
  测试人员在真机上安装 release 版本
  在 BugIssueTestCase.md 对应 TC 打勾并注明版本号
  告诉 Claude Code："#N 回归通过"
  → Claude Code 在 GitHub Issue 留言确认回归通过并关闭
  → 更新 tc-*.md 原始用例为 [x] 通过
```

---

## 第一步：测试人员填写 tc-*.md

测试执行后，在对应用例的 `- [ ] 通过` 处：

**通过**：
```markdown
- [x] 通过
```

**不通过**：保留 `[ ]`，填写下方记录区：
```markdown
- [ ] 通过
> 不通过时记录：
> - 实际结果：（填写实际发生了什么）
> - 设备 / Android 版本 / App 版本：（如 Pixel 7 / Android 14 / v1.4.0）
> - 截图或日志：（可选，粘贴关键 logcat 或描述截图）
> - Issue：#（留空，由 Claude Code 自动回填）
```

---

## 第二步：扫描测试结果，自动提 Issue

测试完成后，告诉 Claude Code：**「扫描测试结果，提 Issue」**

Claude Code 将自动：

1. 扫描所有 `docs/tc-*.md`
2. 识别失败用例：`- [ ] 通过`（未勾选）且 `实际结果：` 字段有内容
3. 从用例块中提取所有字段，调用 `gh issue create`
4. 将 Issue 编号回填到对应用例的 `Issue：#` 处并提交 commit

Issue 标题格式：`[BUG] {TC编号} {用例名称}`

---

## 第三步：修复

**分支命名**：
```
fix/issue-{编号}-{简短描述}

示例：fix/issue-42-statistics-crash
```

**Commit 格式**（合并时自动关闭 Issue）：
```
fix: 修复统计页 combine 溢出导致崩溃 closes #42

根因：...
方案：...
```

**修复完成后更新 Issue**：
```bash
gh issue comment 42 --body "已修复，根因：... 方案：... 关联提交：{hash}"
```

修复 commit 合并到 master 后，立即打 tag 触发发版：

```bash
git tag v1.4.1
git push origin v1.4.1
```

GitHub Actions 自动构建并发布 release APK，Issue 因 `closes #N` 自动关闭并列入 Release Notes。

---

## 第四步：更新 BugIssueTestCase.md

修复完成后，在 `docs/BugIssueTestCase.md` 末尾追加验收 TC 块：

```markdown
## v{版本号} — {修复描述}

**发布日期**：{日期}
**关联 Issue**：#{编号}
**关联提交**：`{commit hash}`
**修复范围**：{一句话说明}

---

#### TC-B{编号} {用例名称}

**前置条件**：...

**操作步骤**：
1. ...

**预期结果**：
1. ...

- [ ] 通过
> 不通过时记录：
> - 实际结果：
> - 设备 / Android 版本 / App 版本：
> - 截图或日志：（可选）
> - Issue：#（创建后填入）
```

测试人员在真机验收后，将 `- [ ] 通过` 改为 `- [x] 通过` 并注明日期和设备。

---

## 第五步：回归验收确认

发版后，测试人员需在真机安装 release APK，对本次修复的 Issue 做回归验收。

**测试人员操作**：

1. 安装 GitHub Release 页面发布的 APK
2. 执行 `BugIssueTestCase.md` 中对应的 TC 用例
3. 通过后，将 `- [ ] 通过` 改为 `- [x] 通过`，并在旁边注明设备和版本：

```markdown
- [x] 通过（Pixel 7 / Android 14 / v1.4.1，2026-03-03）
```

**告知 Claude Code 回归结果**：

- 通过：**「#N 回归通过」**
- 不通过：**「#N 回归不通过，实际结果：...」**（重新进入第三步修复）

**Claude Code 收到"回归通过"后自动执行**：

1. 在 GitHub Issue 追加评论：`✅ v{版本号} 回归验收通过，关闭 Issue`
2. 将原始 `tc-*.md` 中对应用例的 `- [ ] 通过` 更新为 `- [x] 通过`
3. 提交 commit：`docs: #N 回归验收通过，更新 tc-*.md`

---

## 文档联动关系

```
tc-*.md（测试人员填写）
      │
      │ Claude Code 扫描，gh issue create
      ▼
GitHub Issue（追踪 & 修复过程记录）
      │
      │ closes #N（commit 合并 + 打 tag 触发发版，Issue 自动关闭）
      ▼
git tag vX.Y.Z + GitHub Release
      │
      │ 同步更新 BugIssueTestCase.md（追加验收 TC）
      ▼
真机安装 release APK，回归验收
      │
      │ 回归通过确认
      ▼
Issue 评论 + tc-*.md 更新
```
