# CHANGELOG：G2 变异验牙 + 填好发布记录 + 修签名摘要/模板
import io
import sys

sys.stdout.reconfigure(errors="replace")
CH = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
t = io.open(CH, encoding="utf-8", newline="").read()

section = """## 2026-10-13 · 补 G2 变异验牙 + 填好发布记录 + 修两处记录错误

**① 补上 G2（本批改动的变异验牙）**。`RELEASE.md` §10.3 是 10-09 的快照，而发布门禁要求"本批**每一处**改动都有验牙记录"。
本批新增的无障碍/FilterChip 断言此前**没验过牙**，于是把生产代码逐个改坏、确认对应的那条真的会红，再还原：

| # | 改坏了什么 | 实际变红的用例 |
|---|---|---|
| M1 | `ReviewScreen.kt` 的 `contentDescription = "名称，必填"` → `"名称"` | `ReviewAccessibilityTest > 名称框的读屏文案带必填` |
| M2 | `SettingsScreen.kt` 的自动导出行从"整行一个可切换项"还原成"`Switch` 独立 + 标签另起" | `SettingsAccessibilityTest > 自动导出的开关与标签是同一个无障碍节点` |
| M3 | `SettingsScreen.kt` 的预设芯片 `selected = state.leadDays == days` → `false` | `SettingsAccessibilityTest > 提前天数的预设芯片带选中状态` |
| M4 | `GalleryScreen.kt` 的 `selected = state.filters.onlyInbox` → `false` | `GalleryAccessibilityTest > 芯片的标签与状态在同一个无障碍节点上` |

**4/4 都红**，改坏后都已还原（`git status app/src/main` 零差异，随后完整 CI 再跑一遍仍全绿）。

验牙脚本自己踩了两个坑，都写进脚本注释：① **读写都要 `newline=""`**，否则 Python 把 CRLF 读成 `\\n` 再写回 LF，
整文件变"已修改"、把验牙变成一次假的代码改动（第一次就把三个文件搞成这样，靠 `git checkout --` 还原）；
② **期望的用例名别凭记忆写**——我按记忆填「每天自动导出的…」，真名是「自动导出的…」，于是脚本把"有牙"误判成"没牙"。

**② 填好一份发布记录**：`docs/release-0.1.0-2026-10-13.md`——照 `templates/release-checklist.md` 七节填满，
G0–G5 用本批实数（43 份 XML / 268 例 / 87 行矩阵 / G2 四次验牙），迁移演练与备份自查贴回执，
第 6 节（发布后 24 小时观察）**整节留空并写明理由**（还没正式发布），第 7 节回滚指向 §7.1.1。
只留"发布负责人签字 + 渠道"两格给用户。

**③ 修两处记录错误**（都是这一轮核对出来的）：

1. **§10.6 的签名摘要写错了**（原先 `B070DD26…`）。依据两条：10-10 做 ⭐FR-RLS-01 时 `apksigner` 与
   `signingReport` 互证同一证书 `8118b97d…`；10-13 用 `install -r` 覆盖安装到 10-12 那个包上**成功**——
   签名不同会直接失败。已改正并附证据链。
2. **`templates/release-checklist.md` 里写死了过期数字**（G1 的"39 / 481 例"、G3 的"39 类 / 481 例"）——
   空白表单里写死数字会误导填表人（本批实际是 43 类 / 268 例）。已改成空格子并注明"填本轮实数"。

另外把 `RELEASE.md` §8 那句"复制一份填"指向了已填好的这份记录，照着抄就知道每格写什么。

---

"""
i = t.index("---\n\n") + len("---\n\n")
t = t[:i] + section + t[i:]
io.open(CH, "w", encoding="utf-8", newline="").write(t)
print("CHANGELOG 已插入新节")
