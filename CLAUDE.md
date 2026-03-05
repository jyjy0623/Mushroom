# 蘑菇大冒险 - Claude Code 项目配置

## 允许的工具权限

以下工具已预先授权，无需每次确认：

- **Read**：读取项目内任意文件
- **Bash**：执行 shell 命令（含 Gradle 构建、测试运行等）
- **Bash export**：导出环境变量（如 JAVA_HOME、ANDROID_HOME 等）
- **Bash scripts/**：执行 `scripts/` 目录下所有脚本，无需逐次确认

## 标准化脚本（scripts/）

| 脚本 | 用法 | 说明 |
|---|---|---|
| `commit.sh` | `bash scripts/commit.sh "message" [files...]` | 暂存指定文件并 commit（自动附加 Co-Authored-By） |
| `tag-release.sh` | `bash scripts/tag-release.sh <version> "<notes>"` | 推 master → 建 tag → force push 触发 CI |
| `new-fix-branch.sh` | `bash scripts/new-fix-branch.sh <issue-N> <desc>` | 基于 master 创建 `fix/issue-N-desc` 分支 |
| `finish-fix.sh` | `bash scripts/finish-fix.sh <issue-N> "<根因>" "<方案>"` | merge fix 分支到 master + push + Issue comment |
| `sync-tc-pass.sh` | `bash scripts/sync-tc-pass.sh` | 读取所有已关闭 Issue，批量把对应 TC 标记为通过并 commit |

## 脚本沉淀规则

完成某个操作后，**满足以下任意两条**时，我会主动提示用户是否将其沉淀为脚本：

1. **重复性**：同一操作在不同 Issue/Sprint 中出现 2 次以上
2. **步骤多**：操作本身需要 3 步以上 bash 调用
3. **格式固定**：输出有规范要求（如 commit message、Issue comment 格式）
4. **权限敏感**：涉及 push、gh 写操作，需要反复确认

提示语格式：
> 💡 这个操作满足沉淀条件，是否要生成脚本写入 `scripts/` 以节省后续 token？

详细说明见 `scripts/README.md`。

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
  commit 使用 refs #N（不使用 closes，Issue 保持 Open）
        │
        ▼
更新 BugIssueTestCase.md，追加验收 TC 块
        │
        ▼
打 tag 发布新版本
        │
        ▼
测试人员在真机/模拟器完成回归验收
  验收通过 → 将 BugIssueTestCase.md 中对应 TC 改为 [x] 通过
           → 手动关闭 GitHub Issue（或由用户操作）
  验收不通过 → 重新提 Bug，进入下一轮修复
```

> **重要**：Issue 必须在真机回归验收通过后才能关闭。
> 代码提交阶段只用 `refs #N` 引用，**绝不使用 `closes #N`**，
> 避免 GitHub 在 merge 时自动关闭尚未验收的 Issue。

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
   - **Issue 标题命名规范**：必须以用例编号开头，格式为 `[TC-X.X.X-XX] 简短描述`
   - 示例：`[TC-2.5.2-01] 拼图格子未区分蘑菇等级`
5. 将返回的 Issue 编号回填到对应用例的 `Issue：#` 处，commit 保存

### 2. 修复分支命名

```
fix/issue-{编号}-{简短描述}

示例：fix/issue-42-statistics-crash
```

### 3. Commit 格式

commit message 使用 `refs #N` 引用 Issue，**不使用 `closes #N`**：

```
fix: [TC-2.6.2-03] 里程碑分数段输入框退格遗留"1" refs #31

根因：...
方案：...
```

> `closes #N` 会让 GitHub 在 push 后自动关闭 Issue，跳过回归验收环节，禁止使用。
> Issue 只能在真机验收通过后由测试人员手动关闭。

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

验收流程：
1. 测试人员在真机/模拟器安装新版本，按步骤验证
2. 验收通过 → 将 `- [ ] 通过` 改为 `- [x] 通过` 并注明日期和设备
3. **验收通过后由测试人员手动关闭对应 GitHub Issue**
4. 验收不通过 → 重新填写实际结果，进入下一轮修复循环

### 6. 参考文档

- Issue 模板：`.github/ISSUE_TEMPLATE/bug_report.md`
- 流程说明：`docs/issue-workflow.md`
- 验收记录：`docs/BugIssueTestCase.md`
- 章节测试用例：`docs/tc-*.md`
