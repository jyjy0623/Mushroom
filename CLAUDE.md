# 蘑菇大冒险 - Claude Code 项目配置

## 允许的工具权限

以下工具已预先授权，无需每次确认：

- **Read**：读取项目内任意文件
- **Bash**：执行 shell 命令（含 Gradle 构建、测试运行等）
- **Bash export**：导出环境变量（如 JAVA_HOME、ANDROID_HOME 等）

## 项目信息

- Android 多模块项目，14个模块
- Gradle 构建系统（gradle-8.13）
- JVM 目标版本：17
- JBR 路径：`D:/tools/Android/Android Studio/jbr`
- Android SDK：`D:/tools/Android/SDK`

---

## Bug Issue 处理规则

处理 Bug 时必须遵循以下流程，无需用户每次提醒。

### 1. 创建 Issue

用户报告 bug 后，使用 `gh` CLI 创建 Issue：

```bash
gh issue create \
  --title "[BUG] 简短描述" \
  --body "$(cat <<'EOF'
## 问题描述
...

## 复现步骤
1. ...

## 实际结果
...

## 预期结果
...

## 关联测试用例
TC 编号：TC-x.x.x-xx

## 环境信息
- 设备：
- Android 版本：
- App 版本：
EOF
)"
```

Milestone 只有两个：`next`（下个版本修）或 `backlog`（暂不排期）。

### 2. 修复分支命名

```
fix/issue-{编号}-{简短描述}

示例：fix/issue-42-statistics-crash
```

### 3. Commit 格式

commit message 必须包含 `closes #N`，合并后自动关闭 Issue：

```
fix: 修复统计页 combine 溢出导致崩溃 closes #42

根因：...
方案：...
```

### 4. 更新 BugIssueTestCase.md

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
```

验收在真机/模拟器确认后，将 `- [ ] 通过` 改为 `- [x] 通过` 并注明日期和设备。

### 5. 参考文档

- Issue 模板：`.github/ISSUE_TEMPLATE/bug_report.md`
- 流程说明：`docs/issue-workflow.md`
- 验收记录：`docs/BugIssueTestCase.md`
- 章节测试用例：`docs/tc-*.md`
