实际结果：进入游戏页后点击屏幕，蘑菇没有任何反应，既无法从 IDLE 状态开始游戏，RUNNING 状态下也无法跳跃。

根因：GameScreen.kt 中 pointerInput 以固定 key Unit 创建并通过条件 Modifier.then 挂载。IDLE 时无点击处理；状态切换到 RUNNING 时因 key 未变 Compose 不重建 gesture detector，点击无法到达 viewModel.jump()。

修复：以 gameState 为 key 始终挂载 pointerInput，IDLE 点击触发 startGame()，RUNNING 点击触发 jump()，移除 IdleOverlay 中多余的 START Button。

修复提交：459e0e7
