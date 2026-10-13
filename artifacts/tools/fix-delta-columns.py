# 修正 DELTA 两行的列：参照保持 ⬜，**本实现**那一列改 🟡
# 表头是 | FR | 标题 | 参照状态 | 本实现 | 更优点/差异 | 证据 | ——别再改错列（这一节今天已经错第二回了）
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


sub("| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | 🟡 | ⬜ |",
    "| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | ⬜ | 🟡 |",
    "FR-SRC-08 列")
sub("| `FR-BAK-09` | 备份加密 | 🟡 | ⬜ |",
    "| `FR-BAK-09` | 备份加密 | ⬜ | 🟡 |",
    "FR-BAK-09 列")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
