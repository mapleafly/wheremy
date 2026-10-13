# DELTA 的两行状态跟着 PRD 改（⬜ → 🟡），并补一句本轮说明
import io
import sys

sys.stdout.reconfigure(errors="replace")
P = r"D:\src\dsh\wheremy\docs\DELTA.md"
ok, fail = 0, []


def sub(old, new, label):
    global ok
    t = io.open(P, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(P, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


sub("| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | ⬜ | ⬜ | 同参照未做：多角度要靠多拍几张，本实现没做关联 | — |",
    "| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | 🟡 | ⬜ | "
    "**本实现开始做**（2026-10-13 用户拍板，`ADR-26`）：一件物品可挂多张、封面=第一张；"
    "**参照实现未做**（多角度只能多拍几张、彼此不关联）——所以这一条从「同参照未做」变成**本实现超出参照** | — |",
    "DELTA FR-SRC-08")
sub("| `FR-BAK-09` | 备份加密 | ⬜ | ⬜ | 同参照未做：备份加密 | — |",
    "| `FR-BAK-09` | 备份加密 | 🟡 | ⬜ | "
    "**本实现开始做**（2026-10-13 用户拍板，`ADR-27`）：全包加密（AES-256-GCM + PBKDF2，格式 v2），口令自选；"
    "**参照实现未做** | — |",
    "DELTA FR-BAK-09")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
