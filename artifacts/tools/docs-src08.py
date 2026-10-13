# FR-SRC-08 文档先行：RULES §3.4（多图规则）+ UI §5 详情页照片区 + ARCHITECTURE（引用计数遍历全部行）
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


# ============ 1) RULES §3.4 ============
rules_new = """### 3.4 一件物品多张照片（`FR-SRC-08`，2026-10-13 定，`ADR-26`）

**封面 📐 恒等于 `sortOrder` 最小的那张。** 墙上格子、任何"只要一张缩略图"的地方（`thumbPathOf`）都取它。
不许改成"最新一张当封面"——用户拍完特写会突然发现墙上那格的图变了，像丢了一张。

**加一张**：`sortOrder = 现有最大 + 1`（**追加到末尾，不插中间**）。插入中间会让"封面"跟着变，
而封面变化应当只由"删掉封面那张"引起。

**上限 📐 一件物品 9 张。** 到上限时「加照片」置灰，点它说 🗣「一件物品最多 9 张照片」。
理由：够用（全景 + 特写 + 几张细节），且防止自动导出被单件物品拖大。

**删一张**：先删那一行 → 再把**那一行**的原图与缩略图路径交给 `deleteUnreferencedPhotoFiles`（§5.2 的引用计数），
别人还在用就不删文件。删掉封面那张之后，新的第一张自动成为封面（不在界面里做"换封面"这个动作）。

**删掉最后一张是允许的**（无照片物品本来就存在，见 `FR-SRC-09`）；这种情况下确认框要换一句：
🗣「删掉这张照片？这件物品就没有照片了」。

**删物品时必须遍历该物品的 _所有_ 照片行**，把每行的 `filePath` / `thumbPath` 都送进引用计数——
多图之前这里只看第一张（`firstOrNull()`）。只看第一张的后果有两种，都不会报错：
**该删的文件留在盘上**（孤儿字节，谁都不认），或**删掉另一件物品还在用的那张图**（界面上出现破图）。
删除确认框里那句"照片会不会跟着没"（`photoDeletionOf`）同样要按**所有行**来数。

---
"""
sub(D + r"\RULES.md", "---\n\n## 4. 一次分析回来，整批怎么重排",
    rules_new + "\n## 4. 一次分析回来，整批怎么重排", "RULES §3.4")

# ============ 2) UI §5：详情页照片区 ============
ui_new = """### 5.9 照片区（多图，2026-10-13 起）

`DetailScreen.kt` 的正文照片区，`state.photoPaths` 是**列表**（读取时按 `sortOrder` 排好）。

- **有照片**：横向可滑的一段图（`LazyRow`），**第一张就是封面**（`RULES.md` §3.4）；
  每张的右上角一颗小按钮 🗣「删掉这张」；列表末尾一颗 🗣「加照片」。
- **没照片**：一个占位块 + 「加照片」（无照片物品是合法状态，`FR-SRC-09`）。
- 「加照片」走到与录入页同一条来源弹窗（拍照 / 选图），成功后**追加**到末尾；
  到 9 张上限时按钮置灰，点了说 🗣「一件物品最多 9 张照片」（`RULES.md` §3.4 📐）。
- 「删掉这张」弹二次确认：一般 🗣「删掉这张照片？」，**只剩一张时**换成
  🗣「删掉这张照片？这件物品就没有照片了」（删完不自动跳转，留在详情页）。

墙上格子仍只用封面那一张（`GalleryScreen` 不改字形与格子尺寸——多图不改变照片墙的信息架构）。

"""
sub(D + r"\UI.md", "## 6. ", ui_new + "## 6. ", "UI §5.9")

# ============ 3) ARCHITECTURE：引用计数要遍历所有行 ============
sub(D + r"\ARCHITECTURE.md",
    "**这是全项目照片文件回收的唯一一条规则**，物品侧和位置侧都必须走它。\n调用契约同 5.1：先删/换自己的行，再进来。",
    "**这是全项目照片文件回收的唯一一条规则**，物品侧和位置侧都必须走它。\n"
    "调用契约同 5.1：先删/换自己的行，再进来。\n"
    "**多图之后（`ADR-26`）再加一条**：调用方要把**该主人所有照片行**的路径都传进来（`paths` 是列表，"
    "函数内部已经 `distinct()`）——只传第一张会让其余几张变成孤儿字节。",
    "ARCH §5.2 契约")

sub(D + r"\ARCHITECTURE.md",
    "8. `delete(itemId)`：**先删行**（item → tags → photos）再 `deleteUnreferenced`。",
    "8. `delete(itemId)`：**先删行**（item → tags → photos，**photos 是该物品的全部行**，`ADR-26`）"
    "再 `deleteUnreferenced`；`photoDeletionOf` 数「照片会不会跟着没」时同样按全部行数。",
    "ARCH 不变量 8")

sub(D + r"\ARCHITECTURE.md",
    "- `otherOwnersOnPath(ownerType, ownerId, path)` `:114` — 除自己之外**按 ownerType 分开数**（`COUNT(DISTINCT ownerId)`），",
    "- `otherOwnersOnPath(ownerType, ownerId, path)` `:114` — 除自己之外**按 ownerType 分开数**（`COUNT(DISTINCT ownerId)`）。"
    "**多图之后（`ADR-26`）它要按「该主人的每一行」逐个路径调用**，不能只问第一张：",
    "ARCH refcount 说明")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
