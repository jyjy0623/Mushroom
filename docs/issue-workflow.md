# 蘑菇大冒险 — Issue 处理工作流

**文档版本**：v1.0
**日期**：2026-03-03

---

## 概览

```
发现 Bug
   │
   ▼
创建 GitHub Issue（使用 bug_report 模板）
   │
   ▼
分类 & 排期（打 Label，关联 Milestone）
   │
   ▼
修复（创建 fix/ 分支，commit 关联 Issue）
   │
   ▼
PR 审查 & 合并
   │
   ▼
验收（BugIssueTestCase.md 打勾）
   │
   ▼
Issue 关闭 + 发版 tag
```

---

## 第一步：发现 & 创建 Issue

**触发条件**：测试发现 bug、用户反馈、自测中发现的问题

**操作**：
1. 在 GitHub → Issues → New Issue 选择「Bug 报告」模板
2. 填写以下必填项：
   - **标题**：`[BUG] 简短描述问题`（如 `[BUG] 统计页点击后 App 崩溃`）
   - **复现步骤**：能稳定复现的最短操作序列
   - **关联 TC 编号**：从 `docs/tc-*.md` 中找到对应用例编号填入
   - **环境信息**：设备、Android 版本、App 版本
3. 提交 Issue，记录 Issue 编号（如 `#42`）

---

## 第二步：分类 & 排期

**打 Label**：

| Label | 含义 |
|-------|------|
| `bug` | 功能异常（自动应用） |
| `crash` | App 崩溃类问题，优先处理 |
| `ui` | 纯视觉/布局问题 |
| `regression` | 之前修好、又重新出现的 bug |
| `pending-repro` | 无法稳定复现，待进一步确认 |

**关联 Milestone**（= 计划在哪个版本修复）：

| Milestone | 含义 |
|-----------|------|
| `v1.4.1` | 下个 patch 版本，紧急修复 |
| `v1.5.0` | 下个 minor 版本，计划修复 |
| `backlog` | 暂不排期 |

---

## 第三步：修复

**分支命名**：

```
fix/issue-{编号}-{简短描述}

示例：
fix/issue-42-statistics-crash
fix/issue-38-pin-dialog-focus
```

**Commit 关联 Issue**（合并时自动关闭 Issue）：

```
fix: 修复统计页 combine 溢出导致崩溃 closes #42

根因：Subject.values() 超过 Kotlin combine 参数上限（5个）
方案：拆分为分批 combine，最终 zip 合并结果
```

> 使用 `closes #42` / `fixes #42` / `resolves #42` 任意一种，合并到 main 时 Issue 自动关闭。

---

## 第四步：PR & 合并

**PR 检查清单**（在 PR 描述中复制使用）：

```markdown
## 修复内容
- 根因：...
- 方案：...

## 关联
- closes #42
- 关联 TC：TC-2.8.1-01

## 验收检查
- [ ] 在真机/模拟器上验证复现步骤不再触发 bug
- [ ] 关联 TC 用例通过
- [ ] 未引入新的崩溃或 UI 异常
- [ ] BugIssueTestCase.md 已更新（添加对应 TC 块并打勾）
```

---

## 第五步：验收 & 关闭

**更新 BugIssueTestCase.md**：

在文件中按版本号新增一节，格式如下：

```markdown
## v1.4.1 — 统计页崩溃修复

**发布日期**：2026-03-10
**关联 Issue**：#42
**关联提交**：`abc1234`
**修复范围**：统计页 combine 溢出

---

#### TC-B42 统计页点击底部导航不崩溃

**前置条件**：App 已启动，存在历史打卡数据

**操作步骤**：
1. 点击底部导航「统计」图标

**预期结果**：
1. 进入统计页，显示三个 Tab
2. App 不崩溃，无 ANR

- [x] 通过 — 2026-03-10，Pixel 7，Android 14
```

**发版**：

```bash
git tag v1.4.1
git push origin v1.4.1
```

在 GitHub 创建 Release，Milestone `v1.4.1` 中已关闭的 Issue 自动列入。

---

## 现有 Pending Bug 示例

### Issue #1（待创建）：统计页崩溃

**建议标题**：`[BUG] 点击底部导航「统计」App 直接退出`

**已知信息**：
- 已尝试：将 `combine(Subject.values().map {...})` 拆分为按5个分批合并，仍复现
- 真实原因未确定，需 `adb logcat | grep -E "FATAL|AndroidRuntime"` 抓栈
- 建议 Label：`bug` + `crash`
- 建议 Milestone：`v1.4.1`

---

## 文档联动关系

```
GitHub Issue（发现 & 追踪）
      │
      │ closes #N（commit/PR）
      ▼
BugIssueTestCase.md（验收记录，TC 打勾）
      │
      │ 通用功能回归参考
      ▼
docs/tc-2.x-*.md（章节测试用例）
      │
      │ 发版
      ▼
git tag vX.Y.Z + GitHub Release
```
