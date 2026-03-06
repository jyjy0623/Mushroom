# 待提交 Issues（网络恢复后手动执行）

## Issue: [TC-2.10-09] 游戏页点击屏幕无法跳跃

**命令**：
```bash
gh issue create --title "[TC-2.10-09] 游戏页点击屏幕无法跳跃" --body-file docs/pending-issues-body-tc2.10-09.md
```

待创建后：
1. 关闭该 issue（已修复）：`gh issue close <号码> --comment "已修复，见提交 459e0e7"`
2. 更新 `docs/tc-2.10-game.md`：TC-2.10-09 的 `- [ ]` → `- [x]`，Issue 号填入
3. 更新 `docs/untested-cases.md`：2.10 章节 26 条 → 25 条，合计 120 → 119 条
4. 提交 + 打 tag v1.6.4 + push

---
