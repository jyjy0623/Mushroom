实际结果：进入数据统计页，默认显示「近30天」的数据，「近30天」芯片处于选中状态。

预期结果：默认应选中「近7天」周期（TC-2.8.1-01 预期结果第5条）。

根因：StatisticsViewModel 中 _period 及 StatisticsUiState.period 的默认值均为 LAST_30_DAYS。

修复：将两处默认值改为 LAST_7_DAYS。

修复提交：83c3fc5
