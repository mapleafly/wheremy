# CHANGELOG：FR-SRC-08 数据层（红→绿）+ 工具改进
import io
import sys

sys.stdout.reconfigure(errors="replace")
C = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
section = """## 2026-10-13 · FR-SRC-08 数据层落地（先红后绿）+ 修掉两处"只看第一张"

按 `PROCESS.md` §3.1 的顺序走：**文档 → 红测试 → 实现 → 判绿**。这一批只做数据层与文档，界面下一批。

**文档先行**（三处）：

- `RULES.md` 新增 **§3.4 一件物品多张照片**：封面 📐 恒等于 `sortOrder` 最小的那张；加一张**追加到末尾**；
  上限 📐 **9 张**（到上限按钮置灰，逐字文案 🗣「一件物品最多 9 张照片」）；删一张走引用计数；
  **删最后一张是允许的**（无照片物品本就存在，`FR-SRC-09`），确认文案改成 🗣「删掉这张照片？这件物品就没有照片了」；
  并写明删物品时**必须遍历全部照片行**，只看第一张的两种后果（留下孤儿字节 / 删掉别人在用的图）。
- `UI.md` 新增 **§5.9 照片区（多图）**：横向图 + 每张右上角 🗣「删掉这张」+ 末尾 🗣「加照片」；无照片时占位 + 「加照片」；
  墙上格子仍只用封面那一张。
- `ARCHITECTURE.md` 三处：`deleteUnreferencedPhotoFiles` 的调用契约补"要把**所有**照片行的路径传进来"；
  不变量 8 的 `delete(itemId)` 写明 photos 是**全部行**、`photoDeletionOf` 同样按全部行数；
  引用计数查询那条补"多图之后要按每一行逐个路径调用"。

**红测试**（`app/src/test/.../repo/ItemPhotoMultiTest.kt`，7 例）：先写、先跑，**两条当红**——
「删物品会回收该物品全部照片的文件，不是只看第一张」与「删除确认框要按全部照片数别的物品」都失败，
正是 `ADR-26` 预言的两个坑：**孤儿字节**与**界面说了不成立的话**（`ADR-22` 判它 P1）。
第三条「封面恒是第一张」当时就是绿的（现状已对，钉住它防止以后被改坏）。

**实现**：

- `PhotoDao` 加 `byId(id)` 与 `deleteById(id)`（只加查询，**不动表结构、没有 Migration**）。
- `ItemRepository.photoDeletionOf`：从"只看第一张"改成**遍历该物品每一行的每条路径**（`distinct()`），
  每类主人取 max 的口径不变。
- `ItemRepository.delete`：先把**全部**照片行的路径收好，再在一个事务里删三处行，最后按引用计数回收文件。
- 新增 `addPhotoToItem(itemId, jpeg)`（追加到末尾、到上限不写、行不存在不写，失败时把写出的文件扫干净）
  与 `removePhoto(photoId)`（先删行再按引用计数回收）；上限常量 `MAX_PHOTOS_PER_ITEM = 9` 放进已有的 companion object。

**判绿**：本地四道门全绿，**44 份 XML / 276 例**（+8），对账零不一致。

**一次未能复现的偶发红 + 工具改进**：其中一轮 CI 的"截图回归"步报 1 例失败，但我删了日志，
**没能抓到是哪条**；随后三次强制重跑（含 `--rerun-tasks`）都是绿的。教训写进工具：
`ci-local.ps1` 现在在汇总里**直接列出失败的用例名**（`类 > 用例`），下次不用再靠翻日志。

---

"""
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])
print("CHANGELOG 已插入")
