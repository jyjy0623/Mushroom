# 待提交 Issues（网络恢复后手动执行）

## Issue 1: [TC-2.10-09] 游戏页点击屏幕无法跳跃

**命令**：
```bash
gh issue create --title "[TC-2.10-09] 游戏页点击屏幕无法跳跃" --body-file docs/pending-issues-body-tc2.10-09.md
```

待创建后：
1. 关闭该 issue（已修复）：`gh issue close <号码> --comment "已修复，见提交 459e0e7"`
2. 更新 `docs/tc-2.10-game.md`：TC-2.10-09 的 `- [ ]` → `- [x]`，Issue 号填入
3. 更新 `docs/untested-cases.md`：2.10 章节 26 条 → 25 条，合计 120 → 119 条

---

## Issue 2: 任务卡片改为双击删除，移除右侧删除图标

**命令**：
```bash
gh issue create --title "任务卡片删除方式改为双击触发，移除右侧删除图标" --body-file docs/pending-issues-body-double-click-delete.md
```

待创建后：
1. 关闭该 issue（已修复）：`gh issue close <号码> --comment "已修复，见提交 e150472"`

---

## Issue 3: TC-2.8.1-01 统计页默认周期应为「近7天」

**命令**：
```bash
gh issue create --title "[TC-2.8.1-01] 统计页默认显示近30天记录，应为近7天" --body-file docs/pending-issues-body-tc2.8.1-01.md
```

待创建后：
1. 关闭该 issue（已修复）：`gh issue close <号码> --comment "已修复，见提交 83c3fc5"`
2. 更新 `docs/tc-2.8-statistics.md`：TC-2.8.1-01 Issue 号由 #52 改为实际号码

---

## Issue 4: 统计页 Tab 标题折行不对齐（学习情况/蘑菇收支/成绩趋势/跑酷游戏）

**命令**：
```bash
gh issue create --title "统计页 Tab 标题四字词折行不均匀，改为每行两字居中对齐" --body-file docs/pending-issues-body-tab-label.md
```

待创建后：
1. 关闭该 issue（已修复）：`gh issue close <号码> --comment "已修复，Tab 标题改为两行各两字，见本次提交"`

---
