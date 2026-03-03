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

处理 Bug 时必须遵循以下完整闭环流程，无需用户每次提醒。

---

### 完整闭环流程

```
测试人员填写 tc-*.md
  通过 → [x] 通过
  不通过 → 保留 [ ]，填写实际结果/设备/版本
        │
        ▼
用户说"扫描测试结果，提 Issue"
        │
        ▼
我扫描所有 tc-*.md，识别失败用例：
  条件：「- [ ] 通过」未勾选 且「实际结果：」字段有内容
        │
        ▼
为每个失败用例 gh issue create
  并将 Issue 编号回填到对应用例的「Issue：#」处
  commit 回填结果
        │
        ▼
逐个启动修复（用户确认后）
  fix/issue-{N}-{描述} 分支 → 修复代码
  修复完成 → gh issue comment 记录根因和方案
  commit 包含 closes #N
        │
        ▼
更新 BugIssueTestCase.md，追加验收 TC 块
  合并后 Issue 自动关闭
```

---

### 1. 扫描测试结果 & 自动提 Issue

用户说"扫描测试结果"时，执行以下逻辑：

1. 读取 `docs/tc-*.md` 所有文件
2. 识别失败用例：`- [ ] 通过`（未勾选）且其下方 `实际结果：` 字段非空
3. 从该用例块中提取：
   - **TC 编号**：用例标题行（如 `#### TC-2.8.1-01`）
   - **用例名称**：`**用例名称**：` 字段
   - **复现步骤**：`**操作步骤**：` 内容
   - **预期结果**：`**预期结果**：` 内容
   - **实际结果**：测试人员填写的内容
   - **设备/版本**：测试人员填写的内容
   - **截图或日志**：测试人员填写的内容（若有）
4. 为每个失败用例执行 `gh issue create`，Issue body 对应 `.github/ISSUE_TEMPLATE/bug_report.md` 字段
5. 将返回的 Issue 编号回填到对应用例的 `Issue：#` 处，commit 保存

### 2. 修复分支命名

```
fix/issue-{编号}-{简短描述}

示例：fix/issue-42-statistics-crash
```

### 3. Commit 格式

commit message 必须包含 `closes #N`：

```
fix: 修复统计页 combine 溢出导致崩溃 closes #42

根因：...
方案：...
```

### 4. 修复完成后更新 Issue

```bash
gh issue comment {N} --body "已修复，根因：... 方案：... 关联提交：{hash}"
```

### 5. 更新 BugIssueTestCase.md

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

验收在真机/模拟器确认后，将 `- [ ] 通过` 改为 `- [x] 通过` 并注明日期和设备。

### 6. 参考文档

- Issue 模板：`.github/ISSUE_TEMPLATE/bug_report.md`
- 流程说明：`docs/issue-workflow.md`
- 验收记录：`docs/BugIssueTestCase.md`
- 章节测试用例：`docs/tc-*.md`
