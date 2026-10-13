# FR-SRC-08 收口：PRD/DELTA → ✅、提案 → 已落地、CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


sub(D + r"\PRD.md",
    "**FR-SRC-08 同一件物品的多角度（一张照片一件事、多张照片）** 🟡 **实施中**（2026-10-13 用户拍板「完整做」，ADR-26）",
    "**FR-SRC-08 同一件物品的多角度（一张照片一件事、多张照片）** ✅ **已落地**（2026-10-13 用户拍板「完整做」，`ADR-26`）："
    "一件物品可挂多张（上限 9）、封面恒为第一张、可加可删；`ItemPhotoMultiTest` 7 例 + `DetailScreenPhotoTest` 5 例 + "
    "备份多图往返 1 例守着，G2 六处变异 6/6 都红。"
    "**本实现超出参照实现**（参照只做一件一张）",
    "PRD FR-SRC-08")
sub(D + r"\DELTA.md",
    "| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | ⬜ | 🟡 |",
    "| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | ⬜ | ✅ |",
    "DELTA FR-SRC-08")
sub(D + r"\proposals\FR-SRC-08-多图.md",
    "| 一句话标题 + 状态 | 「一件东西可以挂多张照片（全景 + 吊牌特写）」——**✅ 已通过（2026-10-13 用户拍板：选 A 完整做）**，决策记在 `DECISIONS.md` `ADR-26` |",
    "| 一句话标题 + 状态 | 「一件东西可以挂多张照片（全景 + 吊牌特写）」——**✅ 已落地（2026-10-13 立项当天做完）**："
    "`PRD.md` 状态已改 ✅，决策记在 `DECISIONS.md` `ADR-26`，守门测试 `ItemPhotoMultiTest` / `DetailScreenPhotoTest` / "
    "`BackupRepositoryTest` 的多图往返 |",
    "提案 SRC-08 状态")

section = """## 2026-10-13 · FR-SRC-08 收口：界面 + 多图往返 + 变异验牙（6/6 都红）

**界面**（`UI.md` §5.9 落地）：

- 详情页照片区改成"真能加、真能删"：横向图（`LazyRow`，第一张就是封面）+ 每张右上角 🗣「删掉这张」+ 末尾 🗣「加照片」；
  没照片时给占位块 + 「加照片」（无照片物品是合法状态，`FR-SRC-09`）。
- **「加照片」走外壳那条唯一收口**：把 `DetailViewModel` 提到外壳层（与 `treeVm` 同一个理由），
  `deliverPhoto` 多一条分支 `backStack.lastOrNull() is DetailKey -> detailVm.addPhoto(bytes)`——
  于是拍照/相册**还是同一条路**（`UI.md` §2.4 的"唯一收口"没有被破）。
- 到 9 张上限时按钮置灰；真到上限还说一句 🗣「一件物品最多 9 张照片」（`addPhotoToItem` 返回 false 时一个字节都不落盘）。
- 删单张弹确认：一般 🗣「删掉这张照片？」，**只剩一张时**换成 🗣「删掉这张照片？这件物品就没有照片了」。
  对话框标题只点题（「删掉这张照片」），结论那句放正文——否则同一句话在语义树里出现两次，
  测试会因为"找到 2 个节点"而红（这一版就踩了）。

**测试**（+9 例，总 286）：`DetailViewModelTest` 补 4 条（加一张后封面不动 / 到上限那句逐字文案 / 删单张先确认 / 取消不动）；
新增 `DetailScreenPhotoTest` 5 条（按钮在且点了请外壳弹来源 / 每张都有删掉这张 / 两种确认文案 / 上限置灰）；
`BackupRepositoryTest` 补 **多图往返** 1 条（一件三张、`sortOrder` 顺序原样回来、共用一张仍只算一份文件，
顺带钉住"导出数文件、导入数行"两种口径）。

**变异验牙（G2）6 处，6/6 都红，且红的正是预期那一条**：

| # | 改坏了什么 | 实际变红 |
|---|---|---|
| M5 | `delete()` 退回"只看第一张" | 删物品会回收该物品全部照片的文件 |
| M6 | `photoDeletionOf` 退回"只数第一张" | 删除确认框要按全部照片数别的物品 |
| M7 | 加照片的 `sortOrder` 写死 0（插到最前） | 加一张追加在末尾，封面不动 |
| M8 | 去掉 9 张上限 | 到 9 张上限就不再写，且一个字节都不落盘 |
| M9 | 「加照片」的 `enabled` 写死 true | 到 9 张上限时加照片置灰 |
| M10 | 删单张的两句文案合成一句 | 只剩一张时说清删完就没有照片了 |

改坏后全部还原（`git status app/src/main` 只剩本批有意改动），随后完整 CI 再跑一遍仍全绿。

**判绿**：本地四道门全绿，**45 份 XML / 286 例**，对账零不一致；`PRD.md` 该条状态 → ✅、`DELTA.md` 同步（并注明**本实现超出参照**）。

---

"""
C = D + r"\CHANGELOG.md"
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
